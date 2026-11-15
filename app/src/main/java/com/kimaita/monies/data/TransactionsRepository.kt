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
import com.kimaita.monies.data.dao.UnparsedMessageDao
import com.kimaita.monies.data.dao.dtos.CategoryBudget
import com.kimaita.monies.data.dao.dtos.accountLines
import com.kimaita.monies.data.database.models.UnparsedMessage
import com.kimaita.monies.di.ApplicationScope
import com.kimaita.monies.models.CategoryApplyMode
import java.time.LocalDate
import java.time.LocalTime
import com.kimaita.monies.data.dao.dtos.AccountBalance
import com.kimaita.monies.data.dao.dtos.LineBalance
import com.kimaita.monies.data.dao.dtos.SubjectCount
import com.kimaita.monies.data.dao.dtos.lineNames
import com.kimaita.monies.data.database.models.UserAccount
import com.kimaita.monies.data.dao.dtos.SubjectStats
import com.kimaita.monies.data.database.models.Category
import com.kimaita.monies.data.database.models.Setting
import com.kimaita.monies.data.database.models.Subject
import com.kimaita.monies.data.database.models.TransactionType
import com.kimaita.monies.data.database.models.TransactionWithDetails
import com.kimaita.monies.data.database.toDomainModel
import com.kimaita.monies.models.BalanceField
import com.kimaita.monies.models.OtherBalances
import com.kimaita.monies.models.SortOrder
import com.kimaita.monies.models.StatsFilter
import com.kimaita.monies.models.ThemeMode
import com.kimaita.monies.models.Transaction
import com.kimaita.monies.models.TransactionFilters
import com.kimaita.monies.models.export.ExportFilters
import com.kimaita.monies.models.export.FilterOptions
import com.kimaita.monies.models.export.NamedRef
import com.kimaita.monies.services.sumLineBalances
import com.kimaita.monies.services.RecurringDetector
import com.kimaita.monies.services.computeFuliza
import com.kimaita.monies.services.typeGroups
import com.kimaita.monies.services.computeMshwariLoan
import com.kimaita.monies.services.extractBalances
import com.kimaita.monies.services.fulizaEvent
import com.kimaita.monies.services.mshwariLoanEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton: the selected account is state held here, so every screen's view model must share one
 * instance or a choice made on Home would never reach Analytics.
 */
@Singleton
class TransactionsRepository @Inject constructor(
    private val transactionsDao: TransactionsDao,
    private val categoryDao: CategoryDao,
    private val transactionTypeDao: TransactionTypeDao,
    private val subjectDao: SubjectDao,
    private val accountDao: AccountDao,
    private val settingsDao: SettingsDao,
    private val unparsedDao: UnparsedMessageDao,
    private val recurringDetector: RecurringDetector,
    @ApplicationScope private val scope: CoroutineScope,
) {

    /** The stored choice. No value until it is read from settings, so nothing resolves before that. */
    private val storedAccount = MutableSharedFlow<Int?>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /**
     * Balance of every active line, queried once for the selection, [getAccountBalances] and
     * [getFilterOptions]. The replay cache is dropped when nobody listens so a late subscriber never
     * sees an old balance first.
     */
    private val lines = transactionsDao.getAccountBalances()
        .shareIn(scope, SharingStarted.WhileSubscribed(5_000, replayExpirationMillis = 0), replay = 1)

    /**
     * The stored choice resolved against the current lines, so it follows merges and dormancy. It has
     * no initial value: every query waits for it, so none runs for "All" first when a line is stored.
     */
    private val resolvedSelection: SharedFlow<Int?> = combine(
        storedAccount, accountDao.getAll(), lines
    ) { stored, accounts, active ->
        effectiveSelection(stored, accounts, active.map { it.accountId }.toSet())
    }.distinctUntilChanged()
        .shareIn(scope, SharingStarted.WhileSubscribed(5_000, replayExpirationMillis = 0), replay = 1)

    /**
     * The account every list, total and balance is limited to; null means all accounts. For the UI:
     * [resolvedSelection] as state, null until it resolves, which also means All.
     */
    val selectedAccount: StateFlow<Int?> =
        resolvedSelection.stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        scope.launch {
            storedAccount.emit(settingsDao.getSetting(SELECTED_ACCOUNT_KEY)?.toIntOrNull())
        }
    }

    suspend fun setSelectedAccount(accountId: Int?) {
        storedAccount.emit(accountId)
        if (accountId == null) settingsDao.deleteSetting(SELECTED_ACCOUNT_KEY)
        else settingsDao.saveSetting(Setting(SELECTED_ACCOUNT_KEY, accountId.toString()))
    }

    /** Re-runs [query] with the selected account each time the selection changes. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> forAccount(query: (Int?) -> Flow<T>): Flow<T> =
        resolvedSelection.flatMapLatest(query)

    /** The newest [limit] transactions. */
    fun getRecentTransactions(limit: Int = 5): Flow<List<Transaction>> =
        forAccount { transactionsDao.getLatestTransactions(limit, it) }
            .map { list -> list.map { it.toDomainModel() } }
            .flowOn(Dispatchers.Default)

    fun getTransactions(
        transactionFilters: TransactionFilters?,
        startDate: String?,
        endDate: String?,
    ): Flow<PagingData<Transaction>> {
        val search = searchTermsOf(transactionFilters?.searchQuery)
        val query = when (transactionFilters?.sortOrder) {
            SortOrder.DATE_ASC -> transactionsDao::getTransactionsOldest
            SortOrder.AMOUNT_DESC -> transactionsDao::getTransactionsLargest
            SortOrder.AMOUNT_ASC -> transactionsDao::getTransactionsSmallest
            SortOrder.DATE_DESC, null -> transactionsDao::getTransactionsNewest
        }
        return forAccount { selected ->
            Pager(
                config = PagingConfig(
                    pageSize = 50, prefetchDistance = 10, enablePlaceholders = false, maxSize = 300
                ), pagingSourceFactory = {
                    query(
                        startDate,
                        endDate,
                        transactionFilters?.subjectFilter,
                        transactionFilters?.categoryFilters?.takeIf { it.isNotEmpty() },
                        transactionFilters?.typeFilters?.takeIf { it.isNotEmpty() },
                        transactionFilters?.accountFilter ?: selected,
                        transactionFilters?.natureFilter,
                        search.fts,
                        search.amount,
                        search.subjectLike
                    )
                }).flow
        }.map { list -> list.map { it.toDomainModel() } }.flowOn(Dispatchers.Default)
    }


    /** Subjects whose display name matches [query], for the rows above the search results. */
    fun getMatchingSubjects(query: String): Flow<List<SubjectCount>> = forAccount { account ->
        subjectLikeOf(query)?.let { subjectDao.matchingSubjects(it, account) } ?: flowOf(emptyList())
    }

    fun getTransactionTypes(): Flow<List<TransactionType>> =
        transactionTypeDao.getAll()

    fun getCategories(searchQuery: String? = null): Flow<List<Category>> =
        categoryDao.getAll(searchQuery)

    fun getSubjects(subjectID: Long): Flow<List<Subject>> =
        subjectDao.getSubjects(subjectID)


    /** Names of every line, dormant included, so a line's name never depends on which lines are active. */
    private fun allLineNames(): Flow<Map<Int, String>> = accountDao.getLineSummaries().map { lines ->
        lineNames(lines.map { LineBalance(it.accountId, it.provider, it.label, null) })
    }

    /** Latest known balance of every account, unaffected by [selectedAccount]. */
    fun getAccountBalances(): Flow<List<AccountBalance>> =
        combine(lines, allLineNames()) { active, names ->
            active.map { AccountBalance(it.accountId, names.getValue(it.accountId), it.balance, it.provider) }
        }.flowOn(Dispatchers.Default)

    fun getSubjectStats(
        subjectId: Long,
        startDate: String?,
        endDate: String?
    ): Flow<SubjectStats> =
        forAccount { subjectDao.getSubjectStats(subjectId, startDate, endDate, it) }


    suspend fun forEachExportBatch(
        filters: ExportFilters,
        onPage: suspend (List<TransactionWithDetails>) -> Unit,
    ) {
        val search = searchTermsOf(filters.search)
        var afterId: Long? = null
        while (true) {
            currentCoroutineContext().ensureActive()
            val batch = transactionsDao.getExportBatch(
                afterId = afterId,
                limit = EXPORT_PAGE_SIZE,
                startDate = filters.startDate,
                endDate = filters.endDate,
                subjectId = filters.subjectId?.takeIf { it.isNotEmpty() },
                categoryId = filters.categoryId?.takeIf { it.isNotEmpty() },
                transactionType = filters.transactionType?.takeIf { it.isNotEmpty() },
                account = filters.account,
                natureFilter = filters.natureFilter,
                fts = search.fts,
                amountQuery = search.amount,
                subjectLike = search.subjectLike,
            )
            if (batch.isEmpty()) break
            onPage(batch)
            afterId =
                batch.last().transaction.id
            if (batch.size < EXPORT_PAGE_SIZE) break
        }
    }

    suspend fun countForExport(filters: ExportFilters): Int {
        val search = searchTermsOf(filters.search)
        return transactionsDao.countForExport(
            startDate = filters.startDate,
            endDate = filters.endDate,
            subjectId = filters.subjectId?.takeIf { it.isNotEmpty() },
            categoryId = filters.categoryId?.takeIf { it.isNotEmpty() },
            transactionType = filters.transactionType?.takeIf { it.isNotEmpty() },
            account = filters.account,
            natureFilter = filters.natureFilter,
            fts = search.fts,
            amountQuery = search.amount,
            subjectLike = search.subjectLike,
        )
    }

    private val exportHandoff = MutableStateFlow<TransactionFilters?>(null)

    /** Hands the Transactions filters to the Export screen; the account is the one the list shows. */
    fun handOffToExport(filters: TransactionFilters) {
        exportHandoff.value = filters.copy(accountFilter = filters.accountFilter ?: resolvedSelection.replayCache.firstOrNull())
    }

    /** The filters handed over by [handOffToExport], once; null when there are none. */
    fun takeExportHandoff(): TransactionFilters? = exportHandoff.getAndUpdate { null }

    fun getIncomeVsExpense(f: StatsFilter) = forAccount {
        transactionsDao.getIncomeVsExpense(f.start, f.end, f.subjects, f.categories, f.types, it)
    }

    fun getCashFlow(bucketFormat: String, f: StatsFilter) = forAccount {
        transactionsDao.getCashFlow(bucketFormat, f.start, f.end, f.subjects, f.categories, f.types, it)
    }

    fun getCategorySpending(f: StatsFilter, natureFilter: Boolean? = null) = forAccount {
        categoryDao.getCategorySpending(
            f.start, f.end, f.subjects, f.categories, f.types, natureFilter, it
        )
    }

    fun getTypeSpending(f: StatsFilter, natureFilter: Boolean? = null) = forAccount {
        transactionTypeDao.getTypeSpending(
            f.start, f.end, f.subjects, f.categories, f.types, natureFilter, it
        )
    }

    fun getActivityByTimeOfDay(f: StatsFilter, natureFilter: Boolean? = null) = forAccount {
        transactionsDao.getActivityByTimeOfDay(
            f.start, f.end, f.subjects, f.categories, f.types, natureFilter, it
        )
    }

    /**
     * Checks if the initial sync has been performed.
     * @return true if the sync timestamp does not exist, indicating a first run.
     */
    suspend fun isFirstRun(): Boolean {
        return settingsDao.getSetting(SmsSyncer.LAST_SMS_SYNC_ID_KEY) == null
    }

    /** The theme and the onboarding flag, from one read of the settings table. */
    suspend fun startupState(): StartupState {
        val values = settingsDao.getSettings(listOf(THEME_KEY, ONBOARDING_COMPLETE_KEY))
            .associate { it.key to it.value }
        return StartupState(
            theme = ThemeMode.fromKey(values[THEME_KEY]),
            onboardingComplete = values[ONBOARDING_COMPLETE_KEY] == "true",
        )
    }

    suspend fun setOnboardingComplete() =
        settingsDao.saveSetting(Setting(ONBOARDING_COMPLETE_KEY, "true"))


    suspend fun searchSubjects(trim: String) =
        subjectDao.searchSubjects(trim).map { NamedRef(it.id, it.name) }

    suspend fun renameSubject(id: Long, name: String) {
        name.trim().takeIf { it.isNotEmpty() }?.let { subjectDao.rename(id, it) }
    }

    /** Moves [source]'s transactions to [target] and merges it into it; false if refused. */
    suspend fun mergeSubject(source: Long, target: Long) = subjectDao.merge(source, target)

    suspend fun getSubjects(ids: List<Long>): List<NamedRef<Long>> =
        if (ids.isEmpty()) emptyList() else subjectDao.getSubjects(ids)
            .map { NamedRef(it.id, it.name) }


    suspend fun topSubjects() = subjectDao.topSubjects().map {
        NamedRef(
            it.id,
            it.name
        )
    }

    fun getFilterOptions(): Flow<FilterOptions> = combine(
        categoryDao.getAll(), getAccountBalances(), transactionTypeDao.getAll(),
    ) { categories, accounts, types ->
        FilterOptions(
            categories = categories.map { NamedRef(it.id, it.name) },
            accounts = accounts.map { NamedRef(it.accountId, it.name) },
            types = typeGroups(types),
        )
    }.flowOn(Dispatchers.Default)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getOtherBalances(): Flow<OtherBalances> = forAccount { account ->
        transactionTypeDao.getAll().flatMapLatest { types ->

            val regexByType: Map<Int, Regex> = types.mapNotNull { t ->
                t.pattern?.let { p -> t.id to balanceRegex(p) }
            }.toMap()

            val balanceTypeIds = types.filter { t ->
                val p = t.pattern ?: return@filter false
                BalanceField.entries.any { field -> p.contains("(?<${field.group}>") }
            }.map { it.id }

            val fulizaRegex = types.filter { it.name.startsWith("fuliza.") }
                .mapNotNull { t -> regexByType[t.id]?.let { t.name to it } }.toMap()

            val loanRegex = types.firstOrNull { it.name == "mshwari.loan_repayment" }
                ?.let { regexByType[it.id] }

            val savings = if (balanceTypeIds.isEmpty()) flowOf(emptyList())
            else transactionsDao.getLatestPerType(balanceTypeIds, account)

            combine(
                savings,
                transactionsDao.getFulizaRows(account),
                transactionsDao.getMshwariLoanRows(account),
            ) { rows, fulizaRows, loanRows ->
                val fuliza = computeFuliza(fulizaRows.mapNotNull { fulizaEvent(it, fulizaRegex) })
                val loan = computeMshwariLoan(loanRows.asReversed().mapNotNull { mshwariLoanEvent(it, loanRegex) })
                extractBalances(rows, regexByType).copy(
                    availableFuliza = fuliza.available,
                    outstandingFuliza = fuliza.outstanding,
                    mshwariLoan = loan,
                )
            }
        }
    }.flowOn(Dispatchers.Default)

    /** Compiled once per pattern for the life of the process, not each time the types table emits. */
    private val balanceRegexes = ConcurrentHashMap<String, Regex>()

    private fun balanceRegex(pattern: String) =
        balanceRegexes.getOrPut(pattern) { pattern.toRegex(BALANCE_REGEX_OPTIONS) }

    /** One line: its balance per bucket. All: the sum of the lines' balances (what Home's total shows). */
    fun getBalanceTrend(bucketFormat: String, startDate: String?, endDate: String?) =
        forAccount {
            if (it != null) transactionsDao.getBalanceByBucket(bucketFormat, startDate, endDate, it)
            else transactionsDao.getLineBalanceBuckets(bucketFormat, startDate, endDate)
                .map(::sumLineBalances)
        }.flowOn(Dispatchers.Default)

    fun getWeekdayCounts(f: StatsFilter, natureFilter: Boolean? = null) = forAccount {
        transactionsDao.getWeekdayCounts(
            f.start, f.end, f.subjects, f.categories, f.types, natureFilter, it
        )
    }

    fun getDailyTotals(startDate: String?, endDate: String?, natureFilter: Boolean?) =
        forAccount { transactionsDao.getDailyTotals(startDate, endDate, natureFilter, it) }

    fun getTopSubjectsByAmount(f: StatsFilter, natureFilter: Boolean?, limit: Int) = forAccount {
        subjectDao.getTopSubjectsByAmount(
            f.start, f.end, f.subjects, f.categories, f.types, natureFilter, limit, it
        )
    }

    fun getTopSubjectsByCount(f: StatsFilter, natureFilter: Boolean?, limit: Int) = forAccount {
        subjectDao.getTopSubjectsByCount(
            f.start, f.end, f.subjects, f.categories, f.types, natureFilter, limit, it
        )
    }

    fun getIncomeBySource(f: StatsFilter, limit: Int) = forAccount {
        subjectDao.getIncomeBySource(f.start, f.end, f.subjects, f.categories, f.types, limit, it)
    }

    suspend fun getRecurringPayments() =
        recurringDetector.detect(account = resolvedSelection.first())

    suspend fun getEarliestTransactionDate(): LocalDateTime? =
        transactionsDao.getEarliestTransactionTime(resolvedSelection.first())?.let {
            runCatching { LocalDateTime.parse(it, DateTimeFormatter.ISO_LOCAL_DATE_TIME) }.getOrNull()
        }

    private fun setting(key: String) = settingsDao.observeSetting(key)
    private suspend fun save(key: String, value: String) = settingsDao.saveSetting(Setting(key, value))

    val themeMode: Flow<ThemeMode> = setting(THEME_KEY).map { ThemeMode.fromKey(it) }

    suspend fun setThemeMode(mode: ThemeMode) = save(THEME_KEY, mode.key)

    val hideBalances: Flow<Boolean> = setting(HIDE_BALANCES_KEY).map { it == "true" }
    suspend fun setHideBalances(hide: Boolean) = save(HIDE_BALANCES_KEY, hide.toString())

    val categoryApplyMode: Flow<CategoryApplyMode> =
        setting(CATEGORY_APPLY_MODE_KEY).map { CategoryApplyMode.fromKey(it) }

    suspend fun setCategoryApplyMode(mode: CategoryApplyMode) =
        save(CATEGORY_APPLY_MODE_KEY, mode.key)

    /** Every line, dormant ones included. */
    fun getAccountLines() = accountDao.getLineSummaries().map { accountLines(it) }.flowOn(Dispatchers.Default)

    /** The newest [limit] transactions of a line, including accounts merged into it. */
    fun getLineSamples(accountId: Int, limit: Int): Flow<List<Transaction>> =
        transactionsDao.getLatestTransactions(limit, accountId)
            .map { list -> list.map { it.toDomainModel() } }
            .flowOn(Dispatchers.Default)

    suspend fun renameAccount(accountId: Int, label: String?) =
        accountDao.setLabel(accountId, label?.trim()?.takeIf { it.isNotEmpty() })

    suspend fun mergeAccounts(sourceId: Int, targetId: Int) =
        accountDao.mergeLines(sourceId, targetId)

    suspend fun addCategory(name: String, monthlyBudget: Double?) {
        categoryDao.insert(Category(name = name.trim(), monthlyBudget = monthlyBudget))
    }

    suspend fun updateCategory(category: Category) = categoryDao.update(category)

    suspend fun deleteCategory(categoryId: Int) = categoryDao.deleteCategory(categoryId)

    /** Sets a transaction's category, or the subject's category and all its transactions. */
    suspend fun assignCategory(
        transactionId: Long, subjectId: Long?, categoryId: Int, allFromSubject: Boolean
    ) {
        if (allFromSubject && subjectId != null) transactionsDao.setCategoryForAllOfSubject(subjectId, categoryId)
        else transactionsDao.setCategory(transactionId, categoryId)
    }

    /** Budgeted categories with this calendar month's spending, for the selected account. */
    fun getBudgets(): Flow<List<CategoryBudget>> = forAccount { account ->
        val today = LocalDate.now()
        val fmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME
        val start = today.withDayOfMonth(1).atStartOfDay().format(fmt)
        val end = today.withDayOfMonth(today.lengthOfMonth()).atTime(LocalTime.MAX).withNano(0).format(fmt)
        categoryDao.getBudgets(start, end, account)
    }

    fun getUnparsedMessages(): Flow<List<UnparsedMessage>> = unparsedDao.getAll()
    fun getUnparsedCount(): Flow<Int> = unparsedDao.count()

    /** Deletes all transactions and unparsed messages and rewinds the SMS cursor. */
    suspend fun clearForReimport() = transactionsDao.clearForReimport(SmsSyncer.LAST_SMS_SYNC_ID_KEY)

    companion object {
        const val THEME_KEY = "theme"
        const val HIDE_BALANCES_KEY = "hide_balances"
        const val CATEGORY_APPLY_MODE_KEY = "category_apply_mode"
        const val ONBOARDING_COMPLETE_KEY = "onboarding_complete"
        const val SELECTED_ACCOUNT_KEY = "selected_account"
        const val EXPORT_PAGE_SIZE = 500
        private val BALANCE_REGEX_OPTIONS =
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    }

}

/** What the first frame needs from settings. */
data class StartupState(val theme: ThemeMode, val onboardingComplete: Boolean)

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
