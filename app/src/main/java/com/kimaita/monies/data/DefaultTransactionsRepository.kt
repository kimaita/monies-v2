package com.kimaita.monies.data

import android.content.Context
import android.provider.Telephony
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.kimaita.monies.data.dao.CategoryDao
import com.kimaita.monies.data.dao.SettingsDao
import com.kimaita.monies.data.dao.SubjectDao
import com.kimaita.monies.data.dao.TransactionTypeDao
import com.kimaita.monies.data.dao.TransactionsDao
import com.kimaita.monies.data.dao.dtos.CategorySpending
import com.kimaita.monies.data.dao.dtos.DailyTotal
import com.kimaita.monies.data.dao.dtos.IncExp
import com.kimaita.monies.data.dao.dtos.SubjectStats
import com.kimaita.monies.data.dao.dtos.TopSubject
import com.kimaita.monies.data.database.models.Category
import com.kimaita.monies.data.database.models.Setting
import com.kimaita.monies.data.database.models.Subject
import com.kimaita.monies.data.database.models.TransactionType
import com.kimaita.monies.data.database.toDomainModel
import com.kimaita.monies.models.Transaction
import com.kimaita.monies.models.TransactionFilters
import com.kimaita.monies.services.SmsParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Date
import javax.inject.Inject

class DefaultTransactionsRepository @Inject constructor(
    private val transactionsDao: TransactionsDao,
    private val categoryDao: CategoryDao,
    private val transactionTypeDao: TransactionTypeDao,
    private val subjectDao: SubjectDao,
    private val settingsDao: SettingsDao,
    private val smsParser: SmsParser
) : TransactionsRepository {

    companion object {
        const val LAST_SMS_SYNC_TIMESTAMP_KEY = "last_sms_sync_timestamp"
    }

    override fun getRecentTransactions(limit: Int?): Flow<List<Transaction?>> =
        transactionsDao.getLatestTransactions(limit)
            .map { list -> list.map { it?.toDomainModel() } }


    override fun getTransactions(
        transactionFilters: TransactionFilters?,
        startDate: String?,
        endDate: String?,
    ): Flow<PagingData<Transaction>> {
        return Pager(
            config = PagingConfig(
                pageSize = 50, enablePlaceholders = false, prefetchDistance = 10
            ), pagingSourceFactory = {
                transactionsDao.getTransactions(
                    startDate,
                    endDate,
                    transactionFilters?.subjectFilter,
                    transactionFilters?.categoryFilters?.takeIf { it.isNotEmpty() },
                    transactionFilters?.typeFilters?.takeIf { it.isNotEmpty() },
                    transactionFilters?.accountFilter,
                    transactionFilters?.natureFilter,
                    transactionFilters?.sortOrder,
                )
            }).flow.map { list -> list.map { it.toDomainModel() } }
    }


    override fun getTransactionTypes(): Flow<List<TransactionType>> =
        transactionTypeDao.getTransactionTypes()


    override fun getIncomeVsExpense(startDate: String?, endDate: String?): Flow<IncExp> =
        transactionsDao.getIncomeVsExpense(startDate, endDate)


    override fun getCategories(searchQuery: String?): Flow<List<Category>> =
        categoryDao.getCategories(searchQuery)

    override fun getSubjects(subjectID: Long): Flow<List<Subject>> =
        subjectDao.getSubjects(subjectID)


    override fun getTransactionProviders(): List<String> {
        return transactionTypeDao.getProviders()
    }

    override fun getTypeSpending(
        startDate: String?, endDate: String?
    ): Flow<List<CategorySpending>> = transactionTypeDao.getTypeSpending(startDate, endDate)

    override fun getTransactionStatsByDay(
        startDate: String?, endDate: String?
    ): Flow<List<DailyTotal>> = transactionsDao.getSpendingTotalsByDay(startDate, endDate)

    override fun getTopSubjectPerType(
        startDate: String?, endDate: String?
    ): Flow<List<TopSubject>> = subjectDao.getTopSubjectPerType(startDate, endDate)


    override fun getLargestTransaction(
        isInc: Boolean, startDate: String?, endDate: String?
    ): Flow<Transaction?> = transactionsDao.getLargestTransaction(isInc, startDate, endDate)
        .map { it?.toDomainModel() }


    override fun getMpesaBalance(): Flow<Double> {
        return transactionsDao.getLatestTransactions(3).map { latestTransactions ->
            if (latestTransactions.isNotEmpty()) {
                val transaction = latestTransactions.first()!!
                val regex = transaction.type.pattern?.toRegex(RegexOption.DOT_MATCHES_ALL)
                val results = regex?.find(transaction.transaction.message)
                try {
                    val bal = results?.groups["Balance"] ?: results?.groups["MpesaBal"]
                    bal?.value?.replace(",", "")?.toDouble() ?: 0.0
                } catch (_: IllegalArgumentException) {
                    0.0
                }
            } else {
                0.0
            }
        }
    }
//
//    fun getBalance(type: String? = "mpesa"): Flow<Double> {
////        val bal: Flow<Double> = 0.0
//        when (type) {
//            "mpesa" -> {
//                return getMpesaBalance()
//            }
//            "mshwari" -> {
//
//            }
//            "lockSaving" -> {
//
//            }
//            "fuliza" -> {
//
//            }
//        }
////        return bal
//        return TODO("Provide the return value")
//    }
//
//    fun getOutstandingLoan(account: String): Flow<Double> {
//
//        if (account == "fuliza") {
//            transactionsDao.getLatestTypeTransaction()
//        } else if (account == "mshwari") {
//
//        }
//
//        return TODO("Provide the return value")
//    }


    override fun getSubjectStats(
        subjectId: Long,
        startDate: String?,
        endDate: String?
    ): Flow<SubjectStats> = subjectDao.getSubjectStats(subjectId, startDate, endDate)


    /**
     * Checks if the initial sync has been performed.
     * @return true if the sync timestamp does not exist, indicating a first run.
     */
    suspend fun isFirstRun(): Boolean {
        return settingsDao.getSetting(LAST_SMS_SYNC_TIMESTAMP_KEY) == null
    }

    override fun getCategorySpending(
        startDate: String?, endDate: String?, transactionCategory: Int?
    ): Flow<List<CategorySpending>> = categoryDao.getCategorySpending(startDate, endDate)

    suspend fun syncAllSms(context: Context) {
        withContext(Dispatchers.IO) {
            val lastSyncTimestamp =
                settingsDao.getSetting(LAST_SMS_SYNC_TIMESTAMP_KEY)?.toLongOrNull() ?: 0L


            val transactionProviders = transactionTypeDao.getProviders().toSet()
            val smsList = mutableListOf<SmsParser.SmsMessage>()

            val selection =
                "${Telephony.Sms.Inbox.ADDRESS} IN (${transactionProviders.joinToString { "'$it'" }}) AND ${Telephony.Sms.Inbox.DATE} > ?"
            val selectionArgs = arrayOf(lastSyncTimestamp.toString())

            val cursor = context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                null,
                selection,
                selectionArgs,
                "${Telephony.Sms.Inbox.DATE} ASC"
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val idIndex = it.getColumnIndexOrThrow(Telephony.Sms.Inbox._ID)
                    val bodyIndex = it.getColumnIndexOrThrow(Telephony.Sms.Inbox.BODY)
                    val addressIndex = it.getColumnIndexOrThrow(Telephony.Sms.Inbox.ADDRESS)
                    val dateIndex = it.getColumnIndexOrThrow(Telephony.Sms.Inbox.DATE)

                    do {
                        val body = it.getString(bodyIndex)
                        val address = it.getString(addressIndex)
                        val date = it.getLong(dateIndex)
                        val id = it.getLong(idIndex)

                        smsList.add(SmsParser.SmsMessage(body, id, address, date))
                    } while (it.moveToNext())
                }
            }
            if (smsList.isNotEmpty()) {
                Timber.d("Fetched %d messages", smsList.size)
                parseAndSaveSms(smsList)
            }
        }
    }

    /**
     * Processes a single batch of SMS messages from the ContentResolver.
     * This is designed to be called by a WorkManager.
     * @return The number of messages processed in this batch.
     */
    suspend fun syncSmsBatch(context: Context, limit: Int, offset: Int): Int {
        val lastSyncTimestamp =
            settingsDao.getSetting(LAST_SMS_SYNC_TIMESTAMP_KEY)?.toLongOrNull() ?: 0L

        val transactionProviders = transactionTypeDao.getProviders().toSet()
        val batchOfSms = mutableListOf<SmsParser.SmsMessage>()

        val selection =
            "${Telephony.Sms.Inbox.ADDRESS} IN (${transactionProviders.joinToString { "'$it'" }}) AND ${Telephony.Sms.Inbox.DATE} > ?"
        val selectionArgs = arrayOf(lastSyncTimestamp.toString())

        Timber.d("Querying ContentResolver with offset: %d, limit: %d", offset, limit)

        val cursor = context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            null,
            selection,
            selectionArgs,
            "${Telephony.Sms.Inbox.DATE} ASC"
        )

        cursor?.use {
            if (it.moveToFirst()) {
                val idIndex = it.getColumnIndexOrThrow(Telephony.Sms.Inbox._ID)
                val bodyIndex = it.getColumnIndexOrThrow(Telephony.Sms.Inbox.BODY)
                val addressIndex = it.getColumnIndexOrThrow(Telephony.Sms.Inbox.ADDRESS)
                val dateIndex = it.getColumnIndexOrThrow(Telephony.Sms.Inbox.DATE)

                var count = 0
                do {
                    val body = it.getString(bodyIndex)
                    val address = it.getString(addressIndex)
                    val date = it.getLong(dateIndex)
                    val id = it.getLong(idIndex)

                    batchOfSms.add(SmsParser.SmsMessage(body, id, address, date))
                    count++
                } while (it.moveToNext() && count < limit)
            }
        }

        Timber.d(
            "Found %d messages in this batch from ContentResolver. Latest date: %s",
            batchOfSms.size,
            Date(lastSyncTimestamp).toString()
        )

        if (batchOfSms.isNotEmpty()) {
            parseAndSaveSms(batchOfSms)
        }

        return batchOfSms.size
    }


    /**
     * Reusable parsing logic for both initial sync and broadcast receiver
     */
    suspend fun parseAndSaveSms(messages: List<SmsParser.SmsMessage>) {
        val transactionTypes = transactionTypeDao.getTransactionTypes().first()

        val parsedData = smsParser.parse(messages, transactionTypes)
        if (parsedData.isEmpty()) return

        val transactionsToInsert = parsedData.map { pair ->
            val transaction = pair.first
            val parsedSubject = pair.second

            if (parsedSubject != null) {
                val subjectId = subjectDao.upsertAndGetId(parsedSubject)
                transaction.copy(subject = subjectId)
            } else {
                Timber.d("No subject found for transaction: %s", transaction.message)
                transaction
            }
        }


        val latestMessageTimestamp = messages.maxOf { it.timestamp }
        val currentSyncTimestamp =
            settingsDao.getSetting(LAST_SMS_SYNC_TIMESTAMP_KEY)?.toLongOrNull() ?: 0L

        if (transactionsToInsert.isNotEmpty() && latestMessageTimestamp > currentSyncTimestamp) {
            transactionsDao.insertAll(transactionsToInsert)
            settingsDao.saveSetting(
                Setting(
                    LAST_SMS_SYNC_TIMESTAMP_KEY, latestMessageTimestamp.toString()
                )
            )
            Timber.d(
                "Saved %d transactions to database. Latest at %s.",
                transactionsToInsert.size,
                Date(latestMessageTimestamp).toString()
            )
        }
    }

}
