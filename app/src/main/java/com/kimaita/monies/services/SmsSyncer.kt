package com.kimaita.monies.services

import androidx.room.withTransaction
import com.kimaita.monies.data.SmsDataSource
import com.kimaita.monies.data.dao.AccountDao
import com.kimaita.monies.data.dao.SettingsDao
import com.kimaita.monies.data.dao.SubjectDao
import com.kimaita.monies.data.dao.TransactionTypeDao
import com.kimaita.monies.data.dao.TransactionsDao
import com.kimaita.monies.data.database.AppDatabase
import com.kimaita.monies.data.database.models.Setting
import com.kimaita.monies.data.database.models.UserAccount
import com.kimaita.monies.models.Provider
import com.kimaita.monies.models.SmsBatchResult
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

@Singleton
class SmsSyncer @Inject constructor(
    private val db: AppDatabase,
    private val smsDataSource: SmsDataSource,
    private val smsParser: SmsParser,
    private val transactionTypeDao: TransactionTypeDao,
    private val subjectDao: SubjectDao,
    private val transactionsDao: TransactionsDao,
    private val settingsDao: SettingsDao,
    private val accountDao: AccountDao,
) {

    private val syncMutex = Mutex()
    private val activeSyncs = AtomicInteger(0)
    private val _isSyncing = MutableStateFlow(false)

    /** True while a foreground [syncAll] is in progress (startup or live observer). */
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    /** Messages waiting to be scanned — the first-run progress denominator. */
    suspend fun pendingCount(): Int = withContext(Dispatchers.IO) {
        val afterId = settingsDao.getSetting(LAST_SMS_SYNC_ID_KEY)?.toLongOrNull() ?: 0L
        smsDataSource.count(Provider.allSenders, afterId)
    }


    /** Reads, parses, and persists one keyset page; advances the cursor atomically. Returns messages read. */
    suspend fun syncBatch(limit: Int = SMS_BATCH_SIZE): SmsBatchResult =
        withContext(Dispatchers.IO) {
            val afterId = settingsDao.getSetting(LAST_SMS_SYNC_ID_KEY)?.toLongOrNull() ?: 0L

            val batch = smsDataSource.readBatch(Provider.allSenders, afterId, limit)
            if (batch.isEmpty()) return@withContext SmsBatchResult(0, null)

            val maxId = batch.maxOf { it.id ?: 0L }
            db.withTransaction {
                parseAndSave(batch)
                settingsDao.saveSetting(Setting(LAST_SMS_SYNC_ID_KEY, maxId.toString()))
            }
            Timber.d("Synced %d SMS up to id %d", batch.size, maxId)
            SmsBatchResult(count = batch.size, newestEpochMillis = batch.maxOf { it.timestamp })

        }

    /** Drains the inbox by looping [syncBatch]. Cancellable. Use for a non-WorkManager "sync now". */
    suspend fun syncAll() {
        if (activeSyncs.getAndIncrement() == 0) _isSyncing.value = true
        try {
            syncMutex.withLock {
                while (true) {
                    currentCoroutineContext().ensureActive()
                    if (syncBatch(SMS_BATCH_SIZE).count < SMS_BATCH_SIZE) break
                }
            }
        } finally {
            if (activeSyncs.decrementAndGet() == 0) _isSyncing.value = false
        }
    }

    private suspend fun parseAndSave(messages: List<SmsParser.SmsMessage>) {
        val types = transactionTypeDao.getAll().first()
        val parsed = smsParser.parse(messages, types)
        if (parsed.isEmpty()) return

        val subjectIds = parsed.mapNotNull { it.second }.distinct()
            .associateWith { subjectDao.upsertAndGetId(it) }

        // Until WP3 every transaction goes to one account row.
        val accountId = accountDao.getId("MPESA", -1)
            ?: accountDao.insert(UserAccount(provider = "MPESA", subscriptionId = -1)).toInt()

        val toInsert = parsed.map { (txn, subject) ->
            (if (subject != null) txn.copy(subject = subjectIds.getValue(subject)) else txn)
                .copy(accountId = accountId)
        }
        transactionsDao.insertAll(toInsert)
    }

    companion object {
        const val SMS_BATCH_SIZE = 512
        const val LAST_SMS_SYNC_ID_KEY = "last_sms_sync_id"
    }
}