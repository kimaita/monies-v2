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
import com.kimaita.monies.models.CategoryApplyMode
import com.kimaita.monies.ui.theme.ThemeMode
import java.time.LocalDate
import java.time.LocalTime
import com.kimaita.monies.data.dao.dtos.AccountBalance
import com.kimaita.monies.data.dao.dtos.FulizaRow
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
import com.kimaita.monies.models.Transaction
import com.kimaita.monies.models.TransactionFilters
import com.kimaita.monies.models.export.ExportFilters
import com.kimaita.monies.models.export.FilterOptions
import com.kimaita.monies.models.export.NamedRef
import com.kimaita.monies.services.sumLineBalances
import com.kimaita.monies.services.FulizaEvent
import com.kimaita.monies.services.MshwariLoanEvent
import com.kimaita.monies.services.RecurringDetector
import com.kimaita.monies.services.computeFuliza
import com.kimaita.monies.services.typeGroups
import com.kimaita.monies.services.computeMshwariLoan
import com.kimaita.monies.utils.groupValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton: the selected account is state held here, so every screen's view model must share one
 * instance or a choice made on Home would never reach Analytics.
 */
@Singleton
class DefaultTransactionsRepository @Inject constructor(
    private val transactionsDao: TransactionsDao,
    private val categoryDao: CategoryDao,
    private val transactionTypeDao: TransactionTypeDao,
    private val subjectDao: SubjectDao,
    private val accountDao: AccountDao,
    private val settingsDao: SettingsDao,
    private val unparsedDao: UnparsedMessageDao,
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
        val search = searchTermsOf(transactionFilters?.searchQuery)
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
                        search.fts,
                        search.amount,
                        search.subjectLike
                    )
                }).flow
        }.map { list -> list.map { it.toDomainModel() } }
    }


    override fun getMatchingSubjects(query: String): Flow<List<SubjectCount>> = forAccount { account ->
        subjectLikeOf(query)?.let { subjectDao.matchingSubjects(it, account) } ?: flowOf(emptyList())
    }

    override fun getTransactionTypes(): Flow<List<TransactionType>> =
        transactionTypeDao.getAll()

    override fun getCategories(searchQuery: String?): Flow<List<Category>> =
        categoryDao.getAll(searchQuery)

    override fun getSubjects(subjectID: Long): Flow<List<Subject>> =
        subjectDao.getSubjects(subjectID)


    /** Names of every line, dormant included, so a line's name never depends on which lines are active. */
    private fun allLineNames(): Flow<Map<Int, String>> = accountDao.getLineSummaries().map { lines ->
        lineNames(lines.map { LineBalance(it.accountId, it.provider, it.label, null) })
    }

    override fun getAccountBalances(): Flow<List<AccountBalance>> =
        combine(transactionsDao.getAccountBalances(), allLineNames()) { lines, names ->
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

    override suspend fun countForExport(filters: ExportFilters): Int {
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

    override fun handOffToExport(filters: TransactionFilters) {
        exportHandoff.value = filters.copy(accountFilter = filters.accountFilter ?: selectedAccount.value)
    }

    override fun takeExportHandoff(): TransactionFilters? = exportHandoff.getAndUpdate { null }

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

    override suspend fun renameSubject(id: Long, name: String) = withContext(Dispatchers.IO) {
        name.trim().takeIf { it.isNotEmpty() }?.let { subjectDao.rename(id, it) } ?: Unit
    }

    override suspend fun mergeSubject(source: Long, target: Long) =
        withContext(Dispatchers.IO) { subjectDao.merge(source, target) }

    override suspend fun getSubjects(ids: List<Long>): List<NamedRef<Long>> =
        if (ids.isEmpty()) emptyList() else subjectDao.getSubjects(ids)
            .map { NamedRef(it.id, it.name) }


    override suspend fun topSubjects() = subjectDao.topSubjects().map {
        NamedRef(
            it.id,
            it.name
        )
    }

    override fun getFilterOptions(): Flow<FilterOptions> = combine(
        categoryDao.getAll(), getAccountBalances(), transactionTypeDao.getAll(),
    ) { categories, accounts, types ->
        FilterOptions(
            categories = categories.map { NamedRef(it.id, it.name) },
            accounts = accounts.map { NamedRef(it.accountId, it.name) },
            types = typeGroups(types),
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
    }

    private fun fulizaEvent(row: FulizaRow, regexByName: Map<String, Regex>): FulizaEvent? {
        val kind = when (row.typeName) {
            "fuliza.loan" -> FulizaEvent.Kind.LOAN
            "fuliza.partial_pay" -> FulizaEvent.Kind.PARTIAL
            "fuliza.final_pay" -> FulizaEvent.Kind.FULL
            else -> return null
        }
        val regex = regexByName[row.typeName]
        val match = regex?.find(row.message)
        val time = runCatching { LocalDateTime.parse(row.transactionTime) }.getOrNull() ?: return null
        fun group(name: String) =
            regex?.let { match?.groupValue(it, name) }?.replace(",", "")?.toDoubleOrNull()
        return FulizaEvent(kind, time, row.amount, group("TotalLoan"), group("AvailableFuliza"))
    }

    /** A repayment states the loan (LoanBal) or says it is repaid in full; a new loan states no figure. */
    private fun mshwariLoanEvent(row: FulizaRow, repaymentRegex: Regex?): MshwariLoanEvent? {
        val time = runCatching { LocalDateTime.parse(row.transactionTime) }.getOrNull() ?: return null
        if (row.typeName != "mshwari.loan_repayment") return MshwariLoanEvent(MshwariLoanEvent.Kind.UNKNOWN, time)
        val stated = repaymentRegex?.let { it.find(row.message)?.groupValue(it, "LoanBal") }
            ?.replace(",", "")?.toDoubleOrNull()
        return when {
            stated != null -> MshwariLoanEvent(MshwariLoanEvent.Kind.STATED, time, stated)
            row.message.contains("repaid in full", ignoreCase = true) -> MshwariLoanEvent(MshwariLoanEvent.Kind.CLEARED, time)
            else -> MshwariLoanEvent(MshwariLoanEvent.Kind.UNKNOWN, time)
        }
    }

    private fun extractBalances(
        rows: List<com.kimaita.monies.data.database.models.Transaction>,
        regexByType: Map<Int, Regex>
    ): OtherBalances {

        val best = HashMap<BalanceField, Pair<Double, LocalDateTime>>()
        rows.forEach { row ->
            val regex = regexByType[row.transactionType] ?: return@forEach
            val match = regex.find(row.message) ?: return@forEach
            BalanceField.entries.forEach { field ->
                val value = match.groupValue(regex, field.group)?.replace(",", "")?.toDoubleOrNull()
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

    /** One line: its balance per bucket. All: the sum of the lines' balances (what Home's total shows). */
    override fun getBalanceTrend(bucketFormat: String, startDate: String?, endDate: String?) =
        forAccount {
            if (it != null) transactionsDao.getBalanceByBucket(bucketFormat, startDate, endDate, it)
            else transactionsDao.getLineBalanceBuckets(bucketFormat, startDate, endDate)
                .map(::sumLineBalances)
        }

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
        transactionsDao.getEarliestTransactionTime(selectedAccount.value)?.let {
            runCatching { LocalDateTime.parse(it, DateTimeFormatter.ISO_LOCAL_DATE_TIME) }.getOrNull()
        }

    // ---- Settings ----

    private fun setting(key: String) = settingsDao.observeSetting(key)
    private suspend fun save(key: String, value: String) =
        withContext(Dispatchers.IO) { settingsDao.saveSetting(Setting(key, value)) }

    override val themeMode: Flow<ThemeMode> = setting(THEME_KEY).map { ThemeMode.fromKey(it) }
    override suspend fun getThemeMode(): ThemeMode =
        withContext(Dispatchers.IO) { ThemeMode.fromKey(settingsDao.getSetting(THEME_KEY)) }

    override suspend fun setThemeMode(mode: ThemeMode) = save(THEME_KEY, mode.key)

    override val hideBalances: Flow<Boolean> = setting(HIDE_BALANCES_KEY).map { it == "true" }
    override suspend fun setHideBalances(hide: Boolean) = save(HIDE_BALANCES_KEY, hide.toString())

    override val categoryApplyMode: Flow<CategoryApplyMode> =
        setting(CATEGORY_APPLY_MODE_KEY).map { CategoryApplyMode.fromKey(it) }

    override suspend fun setCategoryApplyMode(mode: CategoryApplyMode) =
        save(CATEGORY_APPLY_MODE_KEY, mode.key)

    // ---- Accounts ----

    override fun getAccountLines() = accountDao.getLineSummaries().map { accountLines(it) }

    override fun getLineSamples(accountId: Int, limit: Int): Flow<List<Transaction>> =
        transactionsDao.getLatestTransactions(limit, accountId).map { list -> list.map { it.toDomainModel() } }

    override suspend fun renameAccount(accountId: Int, label: String?) = withContext(Dispatchers.IO) {
        accountDao.setLabel(accountId, label?.trim()?.takeIf { it.isNotEmpty() })
    }

    override suspend fun mergeAccounts(sourceId: Int, targetId: Int) =
        withContext(Dispatchers.IO) { accountDao.mergeLines(sourceId, targetId) }

    // ---- Categories ----

    override suspend fun addCategory(name: String, monthlyBudget: Double?) = withContext(Dispatchers.IO) {
        categoryDao.insert(Category(name = name.trim(), monthlyBudget = monthlyBudget)); Unit
    }

    override suspend fun updateCategory(category: Category) =
        withContext(Dispatchers.IO) { categoryDao.update(category) }

    override suspend fun deleteCategory(categoryId: Int) =
        withContext(Dispatchers.IO) { categoryDao.deleteCategory(categoryId) }

    override suspend fun assignCategory(
        transactionId: Long, subjectId: Long?, categoryId: Int, allFromSubject: Boolean
    ) = withContext(Dispatchers.IO) {
        if (allFromSubject && subjectId != null) transactionsDao.setCategoryForAllOfSubject(subjectId, categoryId)
        else transactionsDao.setCategory(transactionId, categoryId)
    }

    override fun getBudgets(): Flow<List<CategoryBudget>> = forAccount { account ->
        val today = LocalDate.now()
        val fmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME
        val start = today.withDayOfMonth(1).atStartOfDay().format(fmt)
        val end = today.withDayOfMonth(today.lengthOfMonth()).atTime(LocalTime.MAX).withNano(0).format(fmt)
        categoryDao.getBudgets(start, end, account)
    }

    // ---- Unrecognised messages and re-import ----

    override fun getUnparsedMessages(): Flow<List<UnparsedMessage>> = unparsedDao.getAll()
    override fun getUnparsedCount(): Flow<Int> = unparsedDao.count()

    override suspend fun clearForReimport() = withContext(Dispatchers.IO) {
        transactionsDao.clearForReimport(SmsSyncer.LAST_SMS_SYNC_ID_KEY)
    }

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

/**
 * The FTS4 MATCH expression for a search box. Each word is split on anything that is not a letter
 * or digit, because the index (simple tokenizer) splits the message the same way: "13/11/26" is the
 * phrase `"13 11 26*"`, "Ksh1,500" is `"Ksh1 500*"`. The last part of each word is a prefix. A date
 * with a four-digit year is matched on its two-digit year, as messages write it. A phone number
 * written in parts ("0712-345-678": digits only, nine or more in all) may also be one token in the
 * message, so the glued form is offered as an alternative of the whole expression. Null when empty.
 */
fun ftsQueryOf(query: String): String? {
    val words = query.trim().split(Regex("\\s+"))
        .map { word -> word.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() } }
        .filter { it.isNotEmpty() }
        .map { parts -> if (isDateWithLongYear(parts)) parts.dropLast(1) + parts.last().takeLast(2) else parts }
    if (words.isEmpty()) return null
    fun phrase(parts: List<String>) = "\"${parts.joinToString(" ")}*\""
    val exact = words.joinToString(" ") { phrase(it) }
    val glued = words.joinToString(" ") { parts ->
        phrase(if (isPhoneInParts(parts)) listOf(parts.joinToString("")) else parts)
    }
    return if (glued == exact) exact else "$exact OR $glued"
}

private fun isPhoneInParts(parts: List<String>) =
    parts.size > 1 && parts.all { p -> p.all(Char::isDigit) } && parts.sumOf { it.length } >= 9

private fun isDateWithLongYear(parts: List<String>) =
    parts.size == 3 && parts.all { p -> p.all(Char::isDigit) } &&
            parts[0].length <= 2 && parts[1].length <= 2 && parts[2].length == 4

/**
 * The query as an amount prefix when it is a number, else null. Commas and a leading Ksh/KES are
 * dropped, and a written-out ".00" matches how amounts are stored ("1,500.00" is "1500.0").
 */
fun amountQueryOf(query: String): String? {
    val number = query.trim().replace(",", "").replace(Regex("^(?i)(ksh|kes)\\.?\\s*"), "")
    if (!Regex("\\d+(\\.\\d*)?").matches(number)) return null
    val dot = number.indexOf('.')
    if (dot < 0 || dot == number.length - 1) return number
    return number.substring(0, dot + 1) + number.substring(dot + 1).trimEnd('0').ifEmpty { "0" }
}

/** A LIKE pattern for subject display names: the words in order, anywhere in the name. Null when empty. */
fun subjectLikeOf(query: String): String? =
    query.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
        .takeIf { it.isNotEmpty() }
        ?.joinToString("%", "%", "%")

/** What one search box text becomes in SQL; the list and the export build it the same way. */
data class SearchTerms(val fts: String?, val amount: String?, val subjectLike: String?)

fun searchTermsOf(query: String?): SearchTerms {
    val q = query.orEmpty()
    return SearchTerms(ftsQueryOf(q), amountQueryOf(q), subjectLikeOf(q))
}

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
