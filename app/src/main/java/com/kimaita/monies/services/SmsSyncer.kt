package com.kimaita.monies.services

import androidx.room.withTransaction
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import com.kimaita.monies.data.SmsDataSource
import com.kimaita.monies.data.SmsSource
import com.kimaita.monies.data.dao.AccountDao
import com.kimaita.monies.data.dao.SettingsDao
import com.kimaita.monies.data.dao.SubjectDao
import com.kimaita.monies.data.dao.TransactionTypeDao
import com.kimaita.monies.data.dao.TransactionsDao
import com.kimaita.monies.data.dao.UnparsedMessageDao
import com.kimaita.monies.data.database.AppDatabase
import com.kimaita.monies.data.database.models.Setting
import com.kimaita.monies.data.database.models.Subject
import com.kimaita.monies.data.database.models.Transaction
import com.kimaita.monies.data.database.models.TransactionType
import com.kimaita.monies.data.database.models.UnparsedMessage
import com.kimaita.monies.data.database.models.UserAccount
import com.kimaita.monies.models.Provider
import com.kimaita.monies.models.SmsBatchResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/** The persistence calls a sync makes; lets JVM tests run the syncer against an in-memory fake. */
interface SyncStore {
    suspend fun cursor(): Long
    suspend fun setCursor(id: Long)
    suspend fun transactionTypes(): List<TransactionType>
    suspend fun accountId(provider: String, subscriptionId: Int): Int?
    suspend fun createAccount(account: UserAccount): Int

    /** Inserts the subject if new; returns the id to use, following any merge. */
    suspend fun subjectId(subject: Subject): Long
    suspend fun subjectCategory(subjectId: Long): Int?
    suspend fun insertTransactions(transactions: List<Transaction>)

    /** Removes an `airtel.pay.receipt` row when an `airtel.pay` row carries the same code. */
    suspend fun deletePairedAirtelReceipts()
    suspend fun insertUnparsed(messages: List<UnparsedMessage>)
    suspend fun inTransaction(block: suspend () -> Unit)
}

class RoomSyncStore(
    private val db: AppDatabase,
    private val transactionTypeDao: TransactionTypeDao,
    private val subjectDao: SubjectDao,
    private val transactionsDao: TransactionsDao,
    private val settingsDao: SettingsDao,
    private val accountDao: AccountDao,
    private val unparsedDao: UnparsedMessageDao,
) : SyncStore {
    override suspend fun cursor() =
        settingsDao.getSetting(SmsSyncer.LAST_SMS_SYNC_ID_KEY)?.toLongOrNull() ?: 0L

    override suspend fun setCursor(id: Long) =
        settingsDao.saveSetting(Setting(SmsSyncer.LAST_SMS_SYNC_ID_KEY, id.toString()))

    override suspend fun transactionTypes() = transactionTypeDao.getAll().first()
    override suspend fun accountId(provider: String, subscriptionId: Int) =
        accountDao.getId(provider, subscriptionId)

    override suspend fun createAccount(account: UserAccount) = accountDao.insert(account).toInt()
    override suspend fun subjectId(subject: Subject) = subjectDao.upsertAndGetId(subject)
    override suspend fun subjectCategory(subjectId: Long) = subjectDao.getCategoryId(subjectId)
    override suspend fun insertTransactions(transactions: List<Transaction>) =
        transactionsDao.insertAll(transactions)

    override suspend fun deletePairedAirtelReceipts() = transactionsDao.deletePairedAirtelReceipts()

    override suspend fun insertUnparsed(messages: List<UnparsedMessage>) =
        unparsedDao.insertAll(messages)

    override suspend fun inTransaction(block: suspend () -> Unit) {
        db.withTransaction { block() }
    }
}

@Singleton
class SmsSyncer(
    private val store: SyncStore,
    private val smsDataSource: SmsSource,
    private val smsParser: SmsParser,
    private val accountLabel: (subscriptionId: Int) -> String?,
) {

    @Inject
    constructor(
        db: AppDatabase,
        smsDataSource: SmsDataSource,
        smsParser: SmsParser,
        transactionTypeDao: TransactionTypeDao,
        subjectDao: SubjectDao,
        transactionsDao: TransactionsDao,
        settingsDao: SettingsDao,
        accountDao: AccountDao,
        unparsedDao: UnparsedMessageDao,
        @ApplicationContext context: Context,
    ) : this(
        RoomSyncStore(
            db, transactionTypeDao, subjectDao, transactionsDao, settingsDao, accountDao, unparsedDao
        ),
        smsDataSource, smsParser, { simLabel(context, it) },
    )

    private val syncMutex = Mutex()
    private val activeSyncs = AtomicInteger(0)
    private val _isSyncing = MutableStateFlow(false)

    /** True while a foreground [syncAll] is in progress (startup or live observer). */
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    /** Messages waiting to be scanned — the first-run progress denominator. */
    suspend fun pendingCount(): Int = withContext(Dispatchers.IO) {
        smsDataSource.count(Provider.allSenders, currentCursor())
    }

    /**
     * The saved cursor, reset to 0 when it is past every message in the inbox (an Auto Backup
     * restore can bring back a cursor from another phone).
     */
    private suspend fun currentCursor(): Long {
        val cursor = store.cursor()
        if (cursor > 0 && cursor > smsDataSource.maxId()) {
            store.setCursor(0)
            return 0
        }
        return cursor
    }

    /** Reads, parses, and persists one keyset page; advances the cursor atomically. Returns messages read. */
    suspend fun syncBatch(limit: Int = SMS_BATCH_SIZE): SmsBatchResult =
        syncMutex.withLock {
            withContext(Dispatchers.IO) {
                val afterId = currentCursor()

                val batch = smsDataSource.readBatch(Provider.allSenders, afterId, limit)
                if (batch.isEmpty()) return@withContext SmsBatchResult(0, null)

                val maxId = batch.maxOf { it.id ?: 0L }
                store.inTransaction {
                    parseAndSave(batch)
                    store.setCursor(maxId)
                }
                Timber.d("Synced %d SMS up to id %d", batch.size, maxId)
                SmsBatchResult(count = batch.size, newestEpochMillis = batch.maxOf { it.timestamp })
            }
        }

    /** Drains the inbox by looping [syncBatch]. Cancellable. Use for a non-WorkManager "sync now". */
    suspend fun syncAll() {
        if (activeSyncs.getAndIncrement() == 0) _isSyncing.value = true
        try {
            while (true) {
                currentCoroutineContext().ensureActive()
                if (syncBatch(SMS_BATCH_SIZE).count < SMS_BATCH_SIZE) break
            }
        } finally {
            if (activeSyncs.decrementAndGet() == 0) _isSyncing.value = false
        }
    }

    private suspend fun parseAndSave(messages: List<SmsParser.SmsMessage>) {
        val types = store.transactionTypes()
        val parsed = smsParser.parse(messages, types)

        val parsedIds = parsed.mapTo(HashSet()) { it.first.messageId }
        val unparsed = messages.filter { it.id !in parsedIds }.filter {
            Provider.forSender(it.originatingAddress)?.looksLikeTransaction(it.body) == true
        }.map { UnparsedMessage(body = it.body, address = it.originatingAddress, date = it.timestamp) }
        if (unparsed.isNotEmpty()) store.insertUnparsed(unparsed)
        if (parsed.isEmpty()) return

        val subjectIds = parsed.mapNotNull { it.second }.distinct()
            .associateWith { store.subjectId(it) }
        val subjectCategories = subjectIds.values.distinct().associateWith { store.subjectCategory(it) }

        val subscriptionById = messages.associate { it.id to it.subscriptionId }
        val typeProvider = types.associate { it.id to it.provider }
        val accountIds = HashMap<Pair<String, Int>, Int>()

        val toInsert = parsed.map { (txn, subject) ->
            val provider = typeProvider.getValue(txn.transactionType)
            val subscriptionId = subscriptionById[txn.messageId] ?: -1
            val accountId = accountIds.getOrPut(provider to subscriptionId) {
                store.accountId(provider, subscriptionId) ?: store.createAccount(
                    UserAccount(
                        provider = provider,
                        subscriptionId = subscriptionId,
                        label = accountLabel(subscriptionId),
                    )
                )
            }
            val subjectId = subject?.let { subjectIds.getValue(it) }
            txn.copy(
                accountId = accountId,
                subject = subjectId,
                transactionCategory = subjectId?.let { subjectCategories[it] },
            )
        }
        store.insertTransactions(toInsert)
        store.deletePairedAirtelReceipts()
    }

    companion object {
        const val SMS_BATCH_SIZE = 512
        const val LAST_SMS_SYNC_ID_KEY = "last_sms_sync_id"
    }
}

/** "SIM 1 · Safaricom" when READ_PHONE_STATE is granted and the SIM is active; otherwise null. */
private fun simLabel(context: Context, subscriptionId: Int): String? {
    if (subscriptionId < 0) return null
    if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return null
    return try {
        val manager = context.getSystemService(SubscriptionManager::class.java)
        manager?.activeSubscriptionInfoList?.firstOrNull { it.subscriptionId == subscriptionId }
            ?.let { "SIM ${it.simSlotIndex + 1} · ${it.carrierName}" }
    } catch (e: SecurityException) {
        null
    }
}
