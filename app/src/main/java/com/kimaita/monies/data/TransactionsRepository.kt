package com.kimaita.monies.data


import androidx.paging.PagingData
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
import com.kimaita.monies.data.database.models.Category
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
import java.time.LocalDateTime

interface TransactionsRepository {

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
        transactionType: List<Int>? = null,
        natureFilter: Boolean? = null
    ): Flow<List<CategorySpending>>

    fun getTypeSpending(
        startDate: String?, endDate: String?,
        subjectId: List<Long>? = null, categoryId: List<Int>? = null, natureFilter: Boolean? = null
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

    fun getTypeSpending(startDate: String?, endDate: String?): Flow<List<CategorySpending>>
    fun getLargestTransaction(
        isInc: Boolean,
        startDate: String? = null,
        endDate: String? = null
    ): Flow<Transaction?>

    fun getMpesaBalance(): Flow<Double>
    fun getSubjectStats(subjectId: Long, startDate: String?, endDate: String?): Flow<SubjectStats>
    suspend fun countForExport(filters: ExportFilters): Int
    suspend fun forEachExportBatch(
        filters: ExportFilters,
        onPage: suspend (List<TransactionWithDetails>) -> Unit
    )

    suspend fun topSubjects(): List<NamedRef<Long>>
    suspend fun searchSubjects(trim: String): List<NamedRef<Long>>
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

    fun getActivityByTimeOfDay(
        startDate: String?,
        endDate: String?,
        natureFilter: Boolean?
    ): Flow<List<ActivityCell>>

    fun getWeekdayCounts(
        startDate: String?,
        endDate: String?,
        natureFilter: Boolean?
    ): Flow<List<WeekdayCount>>

    fun getDailyTotals(
        startDate: String?,
        endDate: String?,
        natureFilter: Boolean?
    ): Flow<List<DailyTotal>>

    fun getTopSubjectsByAmount(
        startDate: String?,
        endDate: String?,
        transactionType: List<Int>?,
        transactionCategory: List<Int>?,
        natureFilter: Boolean?,
        limit: Int
    ): Flow<List<SubjectAmount>>

    fun getTopSubjectsByCount(
        startDate: String?,
        endDate: String?,
        limit: Int
    ): Flow<List<SubjectCount>>

    fun getIncomeBySource(
        startDate: String?,
        endDate: String?,
        limit: Int
    ): Flow<List<SourceAmount>>

    suspend fun getRecurringPayments(): List<RecurringPayment>
}

