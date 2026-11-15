package com.kimaita.monies.services

import androidx.room.withTransaction
import com.kimaita.monies.data.SmsDataSource
import com.kimaita.monies.data.SmsSource
import com.kimaita.monies.data.dao.AccountDao
import com.kimaita.monies.data.dao.CategoryDao
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
import com.kimaita.monies.data.database.models.categoryKey
import com.kimaita.monies.data.database.models.defaultCategoryId
import com.kimaita.monies.models.Provider
import com.kimaita.monies.models.SmsBatchResult
import com.kimaita.monies.models.SyncTimings
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

    /** Every account that has transactions, with the span of its activity. */
    suspend fun accountActivity(): List<AccountActivity>
    suspend fun setMergedInto(accountId: Int, root: Int?)

    /** True when a sync saved transactions but has not yet recomputed account merges. */
    suspend fun mergesStale(): Boolean
    suspend fun setMergesStale(stale: Boolean)

    /** Saved subjects with any of these raw names, each with the id and category to use (following any merge). */
    suspend fun savedSubjects(rawNames: Collection<String>): List<SavedSubject>

    /** Inserts a new subject with its categoryId; returns its id. */
    suspend fun insertSubject(subject: Subject): Long

    /** Every category's id by [categoryKey] of its name. */
    suspend fun categoryIds(): Map<String, Int>

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
    private val categoryDao: CategoryDao,
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
    override suspend fun accountActivity() = accountDao.getActivity()
    override suspend fun setMergedInto(accountId: Int, root: Int?) =
        accountDao.setMergedInto(accountId, root)

    override suspend fun mergesStale() = settingsDao.getSetting(SmsSyncer.MERGES_STALE_KEY) != "0"
    override suspend fun setMergesStale(stale: Boolean) =
        settingsDao.saveSetting(Setting(SmsSyncer.MERGES_STALE_KEY, if (stale) "1" else "0"))

    override suspend fun savedSubjects(rawNames: Collection<String>) =
        rawNames.chunked(500).flatMap { subjectDao.findByRawNames(it) }
    override suspend fun insertSubject(subject: Subject) = subjectDao.insert(subject)
    override suspend fun categoryIds() =
        categoryDao.getAll().first().associate { categoryKey(it.name) to it.id }
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
) {

    @Inject
    constructor(
        db: AppDatabase,
        smsDataSource: SmsDataSource,
        smsParser: SmsParser,
        transactionTypeDao: TransactionTypeDao,
        subjectDao: SubjectDao,
        categoryDao: CategoryDao,
        transactionsDao: TransactionsDao,
        settingsDao: SettingsDao,
        accountDao: AccountDao,
        unparsedDao: UnparsedMessageDao,
    ) : this(
        RoomSyncStore(
            db, transactionTypeDao, subjectDao, categoryDao, transactionsDao, settingsDao, accountDao,
            unparsedDao,
        ),
        smsDataSource, smsParser,
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

    /** Set once this process has checked the saved cursor against the inbox. */
    private var cursorChecked = false

    /**
     * The saved cursor, reset to 0 when it is past every message in the inbox (an Auto Backup
     * restore can bring back a cursor from another phone). A restore happens before the app runs,
     * so each process checks once: asking the SMS provider for its highest id took ~65 ms a call on
     * a Galaxy M14, 15% of an import when it was asked on every batch.
     */
    private suspend fun currentCursor(): Long {
        val cursor = store.cursor()
        if (cursor == 0L || cursorChecked) return cursor
        cursorChecked = true
        if (cursor > smsDataSource.maxId()) {
            store.setCursor(0)
            return 0
        }
        return cursor
    }

    /** Reads, parses, and persists one keyset page; advances the cursor atomically. Returns messages read. */
    suspend fun syncBatch(limit: Int = SMS_BATCH_SIZE): SmsBatchResult =
        syncMutex.withLock {
            withContext(Dispatchers.IO) {
                val timings = SyncTimings()
                val afterId = timings.time("cursor") { currentCursor() }

                val batch = timings.time("read") { smsDataSource.readBatch(Provider.allSenders, afterId, limit) }
                if (batch.isEmpty()) {
                    if (store.mergesStale()) timings.time("merges") { store.inTransaction { recomputeMerges() } }
                    Timber.tag(LOG_TAG).i("Batch: 0 SMS; %s", timings)
                    return@withContext SmsBatchResult(0, null, timings)
                }

                val maxId = batch.maxOf { it.id ?: 0L }
                val timedBefore = timings.totalNanos
                val txStart = System.nanoTime()
                store.inTransaction {
                    val saved = parseAndSave(batch, timings)
                    store.setCursor(maxId)
                    if (batch.size < limit && (saved || store.mergesStale())) timings.time("merges") { recomputeMerges() }
                    else if (saved) store.setMergesStale(true)
                }
                timings.add("other", System.nanoTime() - txStart - (timings.totalNanos - timedBefore))
                Timber.tag(LOG_TAG).i("Batch: %d SMS up to id %d; %s", batch.size, maxId, timings)
                SmsBatchResult(count = batch.size, newestEpochMillis = batch.maxOf { it.timestamp }, timings)
            }
        }

    /** Runs [block] while no sync is running or can start (used to reset the data before a re-import). */
    suspend fun <T> exclusive(block: suspend () -> T): T = syncMutex.withLock { block() }

    /** Drains the inbox by looping [syncBatch]. Cancellable. Use for a non-WorkManager "sync now". */
    suspend fun syncAll() {
        if (activeSyncs.getAndIncrement() == 0) _isSyncing.value = true
        try {
            val run = SyncTimings()
            var messages = 0
            var batches = 0
            val start = System.nanoTime()
            while (true) {
                currentCoroutineContext().ensureActive()
                val result = syncBatch(SMS_BATCH_SIZE)
                run += result.timings
                messages += result.count
                batches++
                if (result.count < SMS_BATCH_SIZE) break
            }
            Timber.tag(LOG_TAG).i("Sync done: %s", run.runSummary(messages, batches, System.nanoTime() - start))
        } finally {
            if (activeSyncs.decrementAndGet() == 0) _isSyncing.value = false
        }
    }

    /** Re-derives `mergedInto` for every account; writes only the rows that change. */
    private suspend fun recomputeMerges() {
        val activity = store.accountActivity()
        val roots = mergeRoots(activity)
        activity.forEach { a ->
            val root = roots[a.id]
            if (root != a.mergedInto) store.setMergedInto(a.id, root)
        }
        store.setMergesStale(false)
    }

    /** Returns true when the batch held transactions (so account spans may have changed). */
    private suspend fun parseAndSave(messages: List<SmsParser.SmsMessage>, timings: SyncTimings): Boolean {
        val types = store.transactionTypes()
        val (parsed, unparsed) = timings.time("parse") {
            val parsed = smsParser.parse(messages, types)
            val parsedIds = parsed.mapTo(HashSet()) { it.first.messageId }
            parsed to messages.filter { it.id !in parsedIds }.filter {
                Provider.forSender(it.originatingAddress)?.looksLikeTransaction(it.body) == true
            }.map { UnparsedMessage(body = it.body, address = it.originatingAddress, date = it.timestamp) }
        }
        if (unparsed.isNotEmpty()) timings.time("save") { store.insertUnparsed(unparsed) }
        if (parsed.isEmpty()) return false

        val subjects = timings.time("subjects") {
            val categoryIds = store.categoryIds()
            val parsedSubjects = parsed.mapNotNull { it.second }.distinctBy { it.rawName to it.number }
            val savedByKey = store.savedSubjects(parsedSubjects.map { it.rawName }.distinct())
                .associateByTo(HashMap()) { it.rawName to it.number }
            // Full numbers first, so a masked one finds a subject this batch has just saved.
            parsedSubjects.sortedBy { '*' in it.number }.associate { s ->
                val key = s.rawName to s.number
                key to (savedByKey[key]
                    ?: unmaskedSubject(s.rawName, s.number, savedByKey.values)
                    ?: defaultCategoryId(s.rawName, categoryIds).let { category ->
                        SavedSubject(s.rawName, s.number, store.insertSubject(s.copy(categoryId = category)), category)
                    }).also { savedByKey.putIfAbsent(key, it) }
            }
        }

        timings.time("save") { saveTransactions(messages, parsed, types, subjects) }
        return true
    }

    private suspend fun saveTransactions(
        messages: List<SmsParser.SmsMessage>,
        parsed: List<Pair<Transaction, Subject?>>,
        types: List<TransactionType>,
        subjects: Map<Pair<String, String>, SavedSubject>,
    ) {
        val subscriptionById = messages.associate { it.id to it.subscriptionId }
        val typeProvider = types.associate { it.id to it.provider }
        val accountIds = HashMap<Pair<String, Int>, Int>()

        val toInsert = parsed.map { (txn, subject) ->
            val provider = typeProvider.getValue(txn.transactionType)
            val subscriptionId = subscriptionById[txn.messageId] ?: -1
            val accountId = accountIds.getOrPut(provider to subscriptionId) {
                store.accountId(provider, subscriptionId) ?: store.createAccount(
                    UserAccount(provider = provider, subscriptionId = subscriptionId)
                )
            }
            val saved = subject?.let { subjects.getValue(it.rawName to it.number) }
            txn.copy(
                accountId = accountId,
                subject = saved?.id,
                transactionCategory = saved?.categoryId,
            )
        }
        store.insertTransactions(toInsert)
        store.deletePairedAirtelReceipts()
    }

    companion object {
        /**
         * Each batch costs ~200 ms on top of its messages (provider query, commit, progress), so
         * fewer, larger batches import faster: 12,039 SMS on a Galaxy M14 took 8.4 s at 512, 6.5 s
         * at 1024 and 4.8 s at 2048. 2048 SMS is under 1 MB, well inside the 2 MB cursor window.
         */
        const val SMS_BATCH_SIZE = 2048
        const val LAST_SMS_SYNC_ID_KEY = "last_sms_sync_id"
        const val MERGES_STALE_KEY = "account_merges_stale"

        /** Logcat tag for sync timing lines: `adb logcat -s SmsSync`. */
        const val LOG_TAG = "SmsSync"
    }
}

/** A saved subject by raw name and number, with the id and category to use: its merge target's when merged. */
data class SavedSubject(val rawName: String, val number: String, val id: Long, val categoryId: Int?)

/** An account's provider and the first and last transaction time (ISO text) it has. */
data class AccountActivity(
    val id: Int,
    val provider: String,
    val minTime: String,
    val maxTime: String,
    val mergedInto: Int? = null,
)

/**
 * Groups accounts of the same provider whose activity periods do not overlap into one line.
 * Returns each account's root id (the earliest account of its line), or null for a root itself.
 * Greedy: an account joins the cluster with the latest end that finished before it started.
 */
fun mergeRoots(activity: List<AccountActivity>): Map<Int, Int?> {
    class Cluster(val root: Int, var max: String)

    val result = HashMap<Int, Int?>()
    activity.groupBy { it.provider }.values.forEach { accounts ->
        val clusters = mutableListOf<Cluster>()
        accounts.sortedWith(compareBy({ it.minTime }, { it.id })).forEach { a ->
            val target = clusters.filter { it.max < a.minTime }.maxByOrNull { it.max }
            if (target == null) {
                clusters += Cluster(a.id, a.maxTime)
                result[a.id] = null
            } else {
                target.max = a.maxTime
                result[a.id] = target.root
            }
        }
    }
    return result
}
