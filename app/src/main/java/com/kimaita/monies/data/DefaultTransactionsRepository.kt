package com.kimaita.monies.data

import com.kimaita.monies.services.SmsSyncer
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.kimaita.monies.data.dao.AccountDao
import com.kimaita.monies.data.dao.CategoryDao
import com.kimaita.monies.data.dao.SettingsDao
import com.kimaita.monies.data.dao.SubjectDao
import com.kimaita.monies.data.dao.TransactionTypeDao
import com.kimaita.monies.data.dao.TransactionsDao
import com.kimaita.monies.data.dao.dtos.CategorySpending
import com.kimaita.monies.data.dao.dtos.DailyTotal
import com.kimaita.monies.data.dao.dtos.SubjectStats
import com.kimaita.monies.data.dao.dtos.TopSubject
import com.kimaita.monies.data.database.models.Category
import com.kimaita.monies.data.database.models.Setting
import com.kimaita.monies.data.database.models.Subject
import com.kimaita.monies.data.database.models.TransactionType
import com.kimaita.monies.data.database.models.TransactionWithDetails
import com.kimaita.monies.data.database.toDomainModel
import com.kimaita.monies.models.BalanceField
import com.kimaita.monies.models.OtherBalances
import com.kimaita.monies.models.Transaction
import com.kimaita.monies.models.TransactionFilters
import com.kimaita.monies.models.export.ExportFilters
import com.kimaita.monies.models.export.FilterOptions
import com.kimaita.monies.models.export.NamedRef
import com.kimaita.monies.models.isoDateTimeFormatter
import com.kimaita.monies.services.RecurringDetector
import com.kimaita.monies.utils.getSafeValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import javax.inject.Inject

class DefaultTransactionsRepository @Inject constructor(
    private val transactionsDao: TransactionsDao,
    private val categoryDao: CategoryDao,
    private val transactionTypeDao: TransactionTypeDao,
    private val subjectDao: SubjectDao,
    private val accountDao: AccountDao,
    private val settingsDao: SettingsDao,
    private val recurringDetector: RecurringDetector
) : TransactionsRepository {

    override fun getRecentTransactions(limit: Int): Flow<List<Transaction>> =
        transactionsDao.getLatestTransactions(limit)
            .map { list -> list.map { it.toDomainModel() } }

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
                    transactionFilters?.searchQuery
                )
            }).flow.map { list -> list.map { it.toDomainModel() } }
    }


    override fun getTransactionTypes(): Flow<List<TransactionType>> =
        transactionTypeDao.getAll()

    override fun getCategories(searchQuery: String?): Flow<List<Category>> =
        categoryDao.getAll(searchQuery)

    override fun getSubjects(subjectID: Long): Flow<List<Subject>> =
        subjectDao.getSubjects(subjectID)


    override fun getTypeSpending(
        startDate: String?, endDate: String?
    ): Flow<List<CategorySpending>> = transactionTypeDao.getTypeSpending(startDate, endDate)


    override fun getLargestTransaction(
        isInc: Boolean, startDate: String?, endDate: String?
    ): Flow<Transaction?> = transactionsDao.getLargestTransaction(isInc, startDate, endDate)
        .map { it?.toDomainModel() }


    override fun getMpesaBalance(): Flow<Double> {
        return transactionsDao.getLatestTransactions(3).map { latestTransactions ->
            if (latestTransactions.isNotEmpty()) {
                val transaction = latestTransactions.first()
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


    override fun getSubjectStats(
        subjectId: Long,
        startDate: String?,
        endDate: String?
    ): Flow<SubjectStats> = subjectDao.getSubjectStats(subjectId, startDate, endDate)


    override suspend fun forEachExportBatch(
        filters: ExportFilters,
        onPage: suspend (List<TransactionWithDetails>) -> Unit,
    ) {
        var afterId: Long? = null
        while (true) {
            currentCoroutineContext().ensureActive()
            val batch = transactionsDao.getExportBatch(
                afterId = afterId,
                limit = EXPORT_PAGE_SIZE,
                startDate = filters.startDate,
                endDate = filters.endDate,
                subjectId = filters.subjectId,
                categoryId = filters.categoryId?.takeIf { it.isNotEmpty() },
                transactionType = filters.transactionType?.takeIf { it.isNotEmpty() },
                account = filters.account,
                natureFilter = filters.natureFilter,
            )
            if (batch.isEmpty()) break
            onPage(batch)
            afterId =
                batch.last().transaction.id
            if (batch.size < EXPORT_PAGE_SIZE) break
        }
    }

    override suspend fun countForExport(filters: ExportFilters): Int =
        transactionsDao.countForExport(
            startDate = filters.startDate,
            endDate = filters.endDate,
            subjectId = filters.subjectId?.takeIf { it.isNotEmpty() },
            categoryId = filters.categoryId?.takeIf { it.isNotEmpty() },
            transactionType = filters.transactionType?.takeIf { it.isNotEmpty() },
            account = filters.account,
            natureFilter = filters.natureFilter,
        )

    override fun getIncomeVsExpense(
        startDate: String?,
        endDate: String?,
        subjectId: List<Long>?,
        categoryId: List<Int>?,
        transactionType: List<Int>?
    ) =
        transactionsDao.getIncomeVsExpense(
            startDate,
            endDate,
            subjectId,
            categoryId,
            transactionType
        )

    override fun getCashFlow(
        bucketFormat: String,
        startDate: String?,
        endDate: String?,
        subjectId: List<Long>?,
        categoryId: List<Int>?,
        transactionType: List<Int>?
    ) =
        transactionsDao.getCashFlow(
            bucketFormat,
            startDate,
            endDate,
            subjectId,
            categoryId,
            transactionType
        )

    override fun getCategorySpending(
        startDate: String?,
        endDate: String?,
        subjectId: List<Long>?,
        transactionType: List<Int>?,
        natureFilter: Boolean?
    ) =
        categoryDao.getCategorySpending(
            startDate,
            endDate,
            subjectId,
            transactionType,
            natureFilter
        )

    override fun getTypeSpending(
        startDate: String?,
        endDate: String?,
        subjectId: List<Long>?,
        categoryId: List<Int>?,
        natureFilter: Boolean?
    ) =
        transactionTypeDao.getTypeSpending(startDate, endDate, subjectId, categoryId, natureFilter)

    override fun getActivityByTimeOfDay(
        startDate: String?,
        endDate: String?,
        subjectId: List<Long>?,
        categoryId: List<Int>?,
        transactionType: List<Int>?,
        natureFilter: Boolean?
    ) =
        transactionsDao.getActivityByTimeOfDay(
            startDate,
            endDate,
            subjectId,
            categoryId,
            transactionType,
            natureFilter
        )

    /**
     * Checks if the initial sync has been performed.
     * @return true if the sync timestamp does not exist, indicating a first run.
     */
    suspend fun isFirstRun(): Boolean {
        return settingsDao.getSetting(SmsSyncer.LAST_SMS_SYNC_ID_KEY) == null
    }

    override suspend fun isOnboardingComplete(): Boolean = withContext(Dispatchers.IO) {
        settingsDao.getSetting(ONBOARDING_COMPLETE_KEY) == "true"
    }

    override suspend fun setOnboardingComplete() = withContext(Dispatchers.IO) {
        settingsDao.saveSetting(Setting(ONBOARDING_COMPLETE_KEY, "true"))
    }


    override suspend fun searchSubjects(trim: String) =
        subjectDao.searchSubjects(trim).map { NamedRef(it.id, it.name) }

    override suspend fun getSubjects(ids: List<Long>): List<NamedRef<Long>> =
        if (ids.isEmpty()) emptyList() else subjectDao.getSubjects(ids)
            .map { NamedRef(it.id, it.name) }


    override suspend fun topSubjects() = subjectDao.topSubjects().map {
        NamedRef(
            it.id,
            it.name
        )
    }

    override suspend fun getFilterOptions(): FilterOptions = coroutineScope {

        val categories = async { categoryDao.getAll().first() }
        val accounts = async { accountDao.getAll().first() }
        val types = async { transactionTypeDao.getAll().first() }
        FilterOptions(
            categories = categories.await().map { NamedRef(it.id, it.name) },
            accounts = accounts.await()
                .map { NamedRef(it.subscriptionId, it.carrier + ' ' + it.number) },
            types = types.await().map { NamedRef(it.id, it.name) },
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getOtherBalances(): Flow<OtherBalances> =
        transactionTypeDao.getAll().flatMapLatest { types ->

            val regexByType: Map<Int, Regex> = types.mapNotNull { t ->
                t.pattern?.let { p -> t.id to p.toRegex(BALANCE_REGEX_OPTIONS) }
            }.toMap()

            val balanceTypeIds = types.filter { t ->
                val p = t.pattern ?: return@filter false
                BalanceField.entries.any { field -> p.contains("(?<${field.group}>") }
            }.map { it.id }

            if (balanceTypeIds.isEmpty()) flowOf(OtherBalances())
            else transactionsDao.getLatestPerType(balanceTypeIds).map { rows ->
                extractBalances(rows, regexByType)
            }
        }

    private fun extractBalances(
        rows: List<com.kimaita.monies.data.database.models.Transaction>,
        regexByType: Map<Int, Regex>
    ): OtherBalances {

        val best = HashMap<BalanceField, Pair<Double, LocalDateTime>>()
        rows.forEach { row ->
            val regex = regexByType[row.transactionType] ?: return@forEach
            val groups =
                regex.find(row.message)?.groups as? MatchNamedGroupCollection ?: return@forEach
            BalanceField.entries.forEach { field ->
                val value = groups.getSafeValue(field.group)?.replace(",", "")?.toDoubleOrNull()
                    ?: return@forEach
                val current = best[field]
                if (current == null || row.transactionTime.isAfter(current.second)) {
                    best[field] = value to row.transactionTime
                }
            }
        }
        return OtherBalances(
            mshwariBalance = best[BalanceField.MSHWARI_BALANCE]?.first,
            lockSavings = best[BalanceField.LOCK_SAVINGS]?.first,
            availableFuliza = best[BalanceField.AVAILABLE_FULIZA]?.first,
            outstandingFuliza = best[BalanceField.OUTSTANDING_FULIZA]?.first,
            mshwariLoan = best[BalanceField.MSHWARI_LOAN]?.first,
        )
    }

    override fun getBalanceTrend(bucketFormat: String, startDate: String?, endDate: String?) =
        transactionsDao.getBalanceByBucket(bucketFormat, startDate, endDate)

    override fun getActivityByTimeOfDay(startDate: String?, endDate: String?, natureFilter: Boolean?) =
        transactionsDao.getActivityByTimeOfDay(startDate, endDate, natureFilter)

    override fun getWeekdayCounts(startDate: String?, endDate: String?, natureFilter: Boolean?) =
        transactionsDao.getWeekdayCounts(startDate, endDate, natureFilter)

    override fun getDailyTotals(startDate: String?, endDate: String?, natureFilter: Boolean?) =
        transactionsDao.getDailyTotals(startDate, endDate, natureFilter)

    override fun getTopSubjectsByAmount(
        startDate: String?,
        endDate: String?,
        transactionType: List<Int>?,
        transactionCategory: List<Int>?,
        natureFilter: Boolean?,
        limit: Int
    ) =
        subjectDao.getTopSubjectsByAmount(startDate, endDate, transactionType, transactionCategory, natureFilter, limit)

    override fun getTopSubjectsByCount(startDate: String?, endDate: String?, limit: Int) =
        subjectDao.getTopSubjectsByCount(startDate, endDate, limit)

    override fun getIncomeBySource(startDate: String?, endDate: String?, limit: Int) =
        subjectDao.getIncomeBySource(startDate, endDate, limit)

    override suspend fun getRecurringPayments() = recurringDetector.detect()

    override suspend fun getEarliestTransactionDate(): LocalDateTime? =
        transactionsDao.getEarliestTransactionTime()?.let {
            runCatching { LocalDateTime.parse(it, isoDateTimeFormatter) }.getOrNull()
        }

    companion object {
        const val ONBOARDING_COMPLETE_KEY = "onboarding_complete"
        const val EXPORT_PAGE_SIZE = 500
        private val BALANCE_REGEX_OPTIONS =
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    }

}
