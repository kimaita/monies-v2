package com.kimaita.monies.data


import androidx.paging.PagingData
import com.kimaita.monies.data.dao.dtos.AccountBalance
import com.kimaita.monies.data.dao.dtos.ActivityCell
import com.kimaita.monies.data.dao.dtos.BalanceBucket
import com.kimaita.monies.data.dao.dtos.CashFlowBucket
import com.kimaita.monies.data.dao.dtos.CategorySpending
import com.kimaita.monies.data.dao.dtos.DailyTotal
import com.kimaita.monies.data.dao.dtos.IncExp
import com.kimaita.monies.data.dao.dtos.SourceAmount
import com.kimaita.monies.data.dao.dtos.SubjectAmount
import com.kimaita.monies.data.dao.dtos.SubjectCount
import com.kimaita.monies.data.dao.dtos.SubjectStats
import com.kimaita.monies.data.dao.dtos.TopSubject
import com.kimaita.monies.data.dao.dtos.WeekdayCount
import com.kimaita.monies.data.dao.dtos.AccountLine
import com.kimaita.monies.data.dao.dtos.CategoryBudget
import com.kimaita.monies.data.database.models.Category
import com.kimaita.monies.data.database.models.UnparsedMessage
import com.kimaita.monies.models.CategoryApplyMode
import com.kimaita.monies.ui.theme.ThemeMode
import com.kimaita.monies.data.database.models.Subject
import com.kimaita.monies.data.database.models.TransactionType
import com.kimaita.monies.data.database.models.TransactionWithDetails
import com.kimaita.monies.models.OtherBalances
import com.kimaita.monies.models.Transaction
import com.kimaita.monies.models.TransactionFilters
import com.kimaita.monies.models.analytics.RecurringPayment
import com.kimaita.monies.models.export.ExportFilters
import com.kimaita.monies.models.export.FilterOptions
import com.kimaita.monies.models.export.NamedRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDateTime

interface TransactionsRepository {

    /** The account every list, total and balance is limited to; null means all accounts. */
    val selectedAccount: StateFlow<Int?>
    suspend fun setSelectedAccount(accountId: Int?)

    /**
     * Get a list of recent transactions.
     *
     * @param limit The maximum number of transactions to retrieve.
     */
    fun getRecentTransactions(limit: Int = 5): Flow<List<Transaction>>
    fun getTransactions(
        transactionFilters: TransactionFilters?,
        startDate: String?,
        endDate: String?,
    ): Flow<PagingData<Transaction>>

    /**
     * Get a list of transaction types.
     */
    fun getTransactionTypes(): Flow<List<TransactionType>>

    fun getIncomeVsExpense(
        startDate: String?,
        endDate: String?,
        subjectId: List<Long>? = null,
        categoryId: List<Int>? = null,
        transactionType: List<Int>? = null
    ): Flow<IncExp>


    fun getCategorySpending(
        startDate: String?,
        endDate: String?,
        subjectId: List<Long>? = null,
        categoryId: List<Int>? = null,
        transactionType: List<Int>? = null,
        natureFilter: Boolean? = null
    ): Flow<List<CategorySpending>>

    fun getTypeSpending(
        startDate: String?, endDate: String?,
        subjectId: List<Long>? = null, categoryId: List<Int>? = null,
        transactionType: List<Int>? = null, natureFilter: Boolean? = null
    ): Flow<List<CategorySpending>>

    fun getActivityByTimeOfDay(
        startDate: String?,
        endDate: String?,
        subjectId: List<Long>? = null,
        categoryId: List<Int>? = null,
        transactionType: List<Int>? = null,
        natureFilter: Boolean? = null
    ): Flow<List<ActivityCell>>

    /**
     * Get a list of categories.
     *
     * @param searchQuery The search query to filter categories by.
     */
    fun getCategories(searchQuery: String? = null): Flow<List<Category>>

    /**
     * Get a list of subjects.
     */
    fun getSubjects(subjectID: Long): Flow<List<Subject>>

    fun getLargestTransaction(
        isInc: Boolean,
        startDate: String? = null,
        endDate: String? = null
    ): Flow<Transaction?>

    /** Latest known balance of every account, unaffected by [selectedAccount]. */
    fun getAccountBalances(): Flow<List<AccountBalance>>
    fun getSubjectStats(subjectId: Long, startDate: String?, endDate: String?): Flow<SubjectStats>
    suspend fun countForExport(filters: ExportFilters): Int
    suspend fun forEachExportBatch(
        filters: ExportFilters,
        onPage: suspend (List<TransactionWithDetails>) -> Unit
    )

    suspend fun topSubjects(): List<NamedRef<Long>>
    suspend fun searchSubjects(trim: String): List<NamedRef<Long>>
    suspend fun renameSubject(id: Long, name: String)

    /** Moves [source]'s transactions to [target] and merges it into it; false if refused. */
    suspend fun mergeSubject(source: Long, target: Long): Boolean
    suspend fun getSubjects(ids: List<Long>): List<NamedRef<Long>>
    suspend fun getFilterOptions(): FilterOptions
    fun getOtherBalances(): Flow<OtherBalances>
    suspend fun isOnboardingComplete(): Boolean
    suspend fun setOnboardingComplete()
    suspend fun getEarliestTransactionDate(): LocalDateTime?
    fun getCashFlow(
        bucketFormat: String, startDate: String?, endDate: String?,
        subjectId: List<Long>?, categoryId: List<Int>?, transactionType: List<Int>?
    ): Flow<List<CashFlowBucket>>

    fun getBalanceTrend(
        bucketFormat: String,
        startDate: String?,
        endDate: String?
    ): Flow<List<BalanceBucket>>

    fun getWeekdayCounts(
        startDate: String?,
        endDate: String?,
        subjectId: List<Long>? = null,
        categoryId: List<Int>? = null,
        transactionType: List<Int>? = null,
        natureFilter: Boolean? = null
    ): Flow<List<WeekdayCount>>

    fun getDailyTotals(
        startDate: String?,
        endDate: String?,
        natureFilter: Boolean?
    ): Flow<List<DailyTotal>>

    fun getTopSubjectsByAmount(
        startDate: String?,
        endDate: String?,
        subjectId: List<Long>?,
        categoryId: List<Int>?,
        transactionType: List<Int>?,
        natureFilter: Boolean?,
        limit: Int
    ): Flow<List<SubjectAmount>>

    fun getTopSubjectsByCount(
        startDate: String?,
        endDate: String?,
        subjectId: List<Long>?,
        categoryId: List<Int>?,
        transactionType: List<Int>?,
        natureFilter: Boolean?,
        limit: Int
    ): Flow<List<SubjectCount>>

    fun getIncomeBySource(
        startDate: String?,
        endDate: String?,
        subjectId: List<Long>?,
        categoryId: List<Int>?,
        transactionType: List<Int>?,
        limit: Int
    ): Flow<List<SourceAmount>>

    suspend fun getRecurringPayments(): List<RecurringPayment>

    // ---- Settings ----
    val themeMode: Flow<ThemeMode>
    suspend fun getThemeMode(): ThemeMode
    suspend fun setThemeMode(mode: ThemeMode)
    val hideBalances: Flow<Boolean>
    suspend fun setHideBalances(hide: Boolean)
    val categoryApplyMode: Flow<CategoryApplyMode>
    suspend fun setCategoryApplyMode(mode: CategoryApplyMode)

    // ---- Accounts ----
    /** Every line, dormant ones included. */
    fun getAccountLines(): Flow<List<AccountLine>>
    /** The newest [limit] transactions of a line, including accounts merged into it. */
    fun getLineSamples(accountId: Int, limit: Int): Flow<List<Transaction>>
    suspend fun renameAccount(accountId: Int, label: String?)
    suspend fun mergeAccounts(sourceId: Int, targetId: Int)

    // ---- Categories ----
    suspend fun addCategory(name: String, monthlyBudget: Double?)
    suspend fun updateCategory(category: Category)
    suspend fun deleteCategory(categoryId: Int)
    /** Sets a transaction's category, or the subject's category and all its transactions. */
    suspend fun assignCategory(transactionId: Long, subjectId: Long?, categoryId: Int, allFromSubject: Boolean)
    /** Budgeted categories with this calendar month's spending, for the selected account. */
    fun getBudgets(): Flow<List<CategoryBudget>>

    // ---- Unrecognised messages and re-import ----
    fun getUnparsedMessages(): Flow<List<UnparsedMessage>>
    fun getUnparsedCount(): Flow<Int>
    /** Deletes all transactions and unparsed messages and rewinds the SMS cursor. */
    suspend fun clearForReimport()
}

