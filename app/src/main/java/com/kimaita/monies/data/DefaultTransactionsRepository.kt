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
import com.kimaita.monies.data.dao.dtos.AccountBalance
import com.kimaita.monies.data.dao.dtos.CategorySpending
import com.kimaita.monies.data.dao.dtos.DailyTotal
import com.kimaita.monies.data.dao.dtos.FulizaRow
import com.kimaita.monies.data.dao.dtos.lineNames
import com.kimaita.monies.data.database.models.UserAccount
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
import com.kimaita.monies.services.FulizaEvent
import com.kimaita.monies.services.RecurringDetector
import com.kimaita.monies.services.computeFuliza
import com.kimaita.monies.utils.getSafeValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val storedAccount = MutableStateFlow<Int?>(null)

    /** The stored choice resolved against the current lines, so it follows merges and dormancy. */
    override val selectedAccount: StateFlow<Int?> = combine(
        storedAccount, accountDao.getAll(), transactionsDao.getAccountBalances()
    ) { stored, accounts, lines ->
        effectiveSelection(stored, accounts, lines.map { it.accountId }.toSet())
    }.stateIn(scope, SharingStarted.Eagerly, null)

    init {
        scope.launch {
            storedAccount.value = settingsDao.getSetting(SELECTED_ACCOUNT_KEY)?.toIntOrNull()
        }
    }

    override suspend fun setSelectedAccount(accountId: Int?) {
        storedAccount.value = accountId
        withContext(Dispatchers.IO) {
            if (accountId == null) settingsDao.deleteSetting(SELECTED_ACCOUNT_KEY)
            else settingsDao.saveSetting(Setting(SELECTED_ACCOUNT_KEY, accountId.toString()))
        }
    }

    /** Re-runs [query] with the selected account each time the selection changes. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> forAccount(query: (Int?) -> Flow<T>): Flow<T> =
        selectedAccount.flatMapLatest(query)

    override fun getRecentTransactions(limit: Int): Flow<List<Transaction>> =
        forAccount { transactionsDao.getLatestTransactions(limit, it) }
            .map { list -> list.map { it.toDomainModel() } }

    override fun getTransactions(
        transactionFilters: TransactionFilters?,
        startDate: String?,
        endDate: String?,
    ): Flow<PagingData<Transaction>> {
        val query = transactionFilters?.searchQuery.orEmpty()
        val amountQuery = amountQueryOf(query)
        val fts = if (amountQuery == null) ftsQueryOf(query) else null
        return forAccount { selected ->
            Pager(
                config = PagingConfig(
                    pageSize = 50, enablePlaceholders = false, prefetchDistance = 10
                ), pagingSourceFactory = {
                    transactionsDao.getTransactions(
                        startDate,
                        endDate,
                        transactionFilters?.subjectFilter,
                        transactionFilters?.categoryFilters?.takeIf { it.isNotEmpty() },
                        transactionFilters?.typeFilters?.takeIf { it.isNotEmpty() },
                        transactionFilters?.accountFilter ?: selected,
                        transactionFilters?.natureFilter,
                        transactionFilters?.sortOrder,
                        fts,
                        amountQuery
                    )
                }).flow
        }.map { list -> list.map { it.toDomainModel() } }
    }


    override fun getTransactionTypes(): Flow<List<TransactionType>> =
        transactionTypeDao.getAll()

    override fun getCategories(searchQuery: String?): Flow<List<Category>> =
        categoryDao.getAll(searchQuery)

    override fun getSubjects(subjectID: Long): Flow<List<Subject>> =
        subjectDao.getSubjects(subjectID)


    override fun getLargestTransaction(
        isInc: Boolean, startDate: String?, endDate: String?
    ): Flow<Transaction?> =
        forAccount { transactionsDao.getLargestTransaction(isInc, startDate, endDate, it) }
        .map { it?.toDomainModel() }


    override fun getAccountBalances(): Flow<List<AccountBalance>> =
        transactionsDao.getAccountBalances().map { lines ->
            val names = lineNames(lines)
            lines.map { AccountBalance(it.accountId, names.getValue(it.accountId), it.balance, it.provider) }
        }

    override fun getSubjectStats(
        subjectId: Long,
        startDate: String?,
        endDate: String?
    ): Flow<SubjectStats> =
        forAccount { subjectDao.getSubjectStats(subjectId, startDate, endDate, it) }


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
    ) = forAccount {
        transactionsDao.getIncomeVsExpense(
            startDate,
            endDate,
            subjectId,
            categoryId,
            transactionType,
            it
        )
    }

    override fun getCashFlow(
        bucketFormat: String,
        startDate: String?,
        endDate: String?,
        subjectId: List<Long>?,
        categoryId: List<Int>?,
        transactionType: List<Int>?
    ) = forAccount {
        transactionsDao.getCashFlow(
            bucketFormat,
            startDate,
            endDate,
            subjectId,
            categoryId,
            transactionType,
            it
        )
    }

    override fun getCategorySpending(
        startDate: String?,
        endDate: String?,
        subjectId: List<Long>?,
        categoryId: List<Int>?,
        transactionType: List<Int>?,
        natureFilter: Boolean?
    ) = forAccount {
        categoryDao.getCategorySpending(
            startDate,
            endDate,
            subjectId,
            categoryId,
            transactionType,
            natureFilter,
            it
        )
    }

    override fun getTypeSpending(
        startDate: String?,
        endDate: String?,
        subjectId: List<Long>?,
        categoryId: List<Int>?,
        transactionType: List<Int>?,
        natureFilter: Boolean?
    ) = forAccount {
        transactionTypeDao.getTypeSpending(
            startDate, endDate, subjectId, categoryId, transactionType, natureFilter, it
        )
    }

    override fun getActivityByTimeOfDay(
        startDate: String?,
        endDate: String?,
        subjectId: List<Long>?,
        categoryId: List<Int>?,
        transactionType: List<Int>?,
        natureFilter: Boolean?
    ) = forAccount {
        transactionsDao.getActivityByTimeOfDay(
            startDate,
            endDate,
            subjectId,
            categoryId,
            transactionType,
            natureFilter,
            it
        )
    }

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
        val accounts = async { transactionsDao.getAccountBalances().first() }
        val types = async { transactionTypeDao.getAll().first() }
        FilterOptions(
            categories = categories.await().map { NamedRef(it.id, it.name) },
            accounts = accounts.await().let { lines ->
                val names = lineNames(lines)
                lines.map { NamedRef(it.accountId, names.getValue(it.accountId)) }
            },
            types = types.await().map { NamedRef(it.id, it.name) },
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getOtherBalances(): Flow<OtherBalances> = forAccount { account ->
        transactionTypeDao.getAll().flatMapLatest { types ->

            val regexByType: Map<Int, Regex> = types.mapNotNull { t ->
                t.pattern?.let { p -> t.id to p.toRegex(BALANCE_REGEX_OPTIONS) }
            }.toMap()

            val balanceTypeIds = types.filter { t ->
                val p = t.pattern ?: return@filter false
                BalanceField.entries.any { field -> p.contains("(?<${field.group}>") }
            }.map { it.id }

            val fulizaRegex = types.filter { it.name.startsWith("fuliza.") }
                .mapNotNull { t -> regexByType[t.id]?.let { t.name to it } }.toMap()

            val savings = if (balanceTypeIds.isEmpty()) flowOf(emptyList())
            else transactionsDao.getLatestPerType(balanceTypeIds, account)

            combine(savings, transactionsDao.getFulizaRows(account)) { rows, fulizaRows ->
                val fuliza = computeFuliza(fulizaRows.mapNotNull { fulizaEvent(it, fulizaRegex) })
                extractBalances(rows, regexByType).copy(
                    availableFuliza = fuliza.available,
                    outstandingFuliza = fuliza.outstanding,
                )
            }
        }
    }

    private fun fulizaEvent(row: FulizaRow, regexByName: Map<String, Regex>): FulizaEvent? {
        val kind = when (row.typeName) {
            "fuliza.loan" -> FulizaEvent.Kind.LOAN
            "fuliza.partial_pay" -> FulizaEvent.Kind.PARTIAL
            "fuliza.final_pay" -> FulizaEvent.Kind.FULL
            else -> return null
        }
        val groups = regexByName[row.typeName]?.find(row.message)?.groups as? MatchNamedGroupCollection
        val time = runCatching { LocalDateTime.parse(row.transactionTime) }.getOrNull() ?: return null
        fun group(name: String) = groups?.getSafeValue(name)?.replace(",", "")?.toDoubleOrNull()
        return FulizaEvent(kind, time, row.amount, group("TotalLoan"), group("AvailableFuliza"))
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
        )
    }

    override fun getBalanceTrend(bucketFormat: String, startDate: String?, endDate: String?) =
        forAccount { transactionsDao.getBalanceByBucket(bucketFormat, startDate, endDate, it) }

    override fun getWeekdayCounts(
        startDate: String?,
        endDate: String?,
        subjectId: List<Long>?,
        categoryId: List<Int>?,
        transactionType: List<Int>?,
        natureFilter: Boolean?
    ) = forAccount {
        transactionsDao.getWeekdayCounts(
            startDate, endDate, subjectId, categoryId, transactionType, natureFilter, it
        )
    }

    override fun getDailyTotals(startDate: String?, endDate: String?, natureFilter: Boolean?) =
        forAccount { transactionsDao.getDailyTotals(startDate, endDate, natureFilter, it) }

    override fun getTopSubjectsByAmount(
        startDate: String?,
        endDate: String?,
        subjectId: List<Long>?,
        categoryId: List<Int>?,
        transactionType: List<Int>?,
        natureFilter: Boolean?,
        limit: Int
    ) = forAccount {
        subjectDao.getTopSubjectsByAmount(
            startDate, endDate, subjectId, categoryId, transactionType, natureFilter, limit, it
        )
    }

    override fun getTopSubjectsByCount(
        startDate: String?,
        endDate: String?,
        subjectId: List<Long>?,
        categoryId: List<Int>?,
        transactionType: List<Int>?,
        natureFilter: Boolean?,
        limit: Int
    ) = forAccount {
        subjectDao.getTopSubjectsByCount(
            startDate, endDate, subjectId, categoryId, transactionType, natureFilter, limit, it
        )
    }

    override fun getIncomeBySource(
        startDate: String?,
        endDate: String?,
        subjectId: List<Long>?,
        categoryId: List<Int>?,
        transactionType: List<Int>?,
        limit: Int
    ) = forAccount {
        subjectDao.getIncomeBySource(
            startDate, endDate, subjectId, categoryId, transactionType, limit, it
        )
    }

    override suspend fun getRecurringPayments() =
        recurringDetector.detect(account = selectedAccount.value)

    override suspend fun getEarliestTransactionDate(): LocalDateTime? =
        transactionsDao.getEarliestTransactionTime()?.let {
            runCatching { LocalDateTime.parse(it, DateTimeFormatter.ISO_LOCAL_DATE_TIME) }.getOrNull()
        }

    companion object {
        const val ONBOARDING_COMPLETE_KEY = "onboarding_complete"
        const val SELECTED_ACCOUNT_KEY = "selected_account"
        const val EXPORT_PAGE_SIZE = 500
        private val BALANCE_REGEX_OPTIONS =
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    }

}

/** The FTS4 MATCH expression for a search box: each word, stripped to letters and digits, as a prefix. Null when empty. */
fun ftsQueryOf(query: String): String? =
    query.trim().split(Regex("\\s+"))
        .map { token -> token.filter { it.isLetterOrDigit() } }
        .filter { it.isNotEmpty() }
        .joinToString(" ") { "$it*" }
        .ifEmpty { null }

/** The query as an amount prefix when it is a number, else null. */
fun amountQueryOf(query: String): String? =
    query.trim().replace(",", "").takeIf { it.isNotEmpty() && it.toDoubleOrNull() != null }

/**
 * Resolves a stored account id to a line id: a merged row selects its root; an unknown or dormant
 * (not in [activeLines]) account selects nothing, i.e. All.
 */
internal fun effectiveSelection(
    stored: Int?, accounts: List<UserAccount>, activeLines: Set<Int>
): Int? {
    val row = accounts.firstOrNull { it.id == stored } ?: return null
    return (row.mergedInto ?: row.id).takeIf { it in activeLines }
}
