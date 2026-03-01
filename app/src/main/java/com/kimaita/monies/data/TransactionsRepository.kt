package com.kimaita.monies.data


import androidx.paging.PagingData
import com.kimaita.monies.data.dao.dtos.CategorySpending
import com.kimaita.monies.data.dao.dtos.DailyTotal
import com.kimaita.monies.data.dao.dtos.IncExp
import com.kimaita.monies.data.dao.dtos.SubjectStats
import com.kimaita.monies.data.dao.dtos.TopSubject
import com.kimaita.monies.data.database.models.Category
import com.kimaita.monies.data.database.models.Subject
import com.kimaita.monies.data.database.models.TransactionType
import com.kimaita.monies.models.Transaction
import com.kimaita.monies.models.TransactionFilters
import kotlinx.coroutines.flow.Flow

interface TransactionsRepository {

    /**
     * Get a list of recent transactions.
     *
     * @param limit The maximum number of transactions to retrieve.
     */
    fun getRecentTransactions(limit: Int?): Flow<List<Transaction?>>

    /**
     * Get a list of transactions based on the provided criteria.
     *
     * @param startDate The start date for filtering transactions.
     * @param endDate The end date for filtering transactions.
     * @param TransactionFilters The filters to apply to the query.
     */
    fun getTransactions(
        transactionFilters: TransactionFilters?,
        startDate: String?,
        endDate: String?,
    ): Flow<PagingData<Transaction>>
    /**
     * Get a list of transaction types.
     */
    fun getTransactionTypes(): Flow<List<TransactionType>>

    /**
     * Get income vs expense for a given time period.
     *
     * @param startDate The start date for filtering transactions.
     * @param endDate The end date for filtering transactions.
     */
    fun getIncomeVsExpense(startDate: String?, endDate: String?): Flow<IncExp>

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

    /**
     * Get category spending for a given time period.
     *
     * @param startDate The start date for filtering transactions.
     * @param endDate The end date for filtering transactions.
     * @param transactionCategory The ID of the category to filter transactions by.
     */
    fun getCategorySpending(
        startDate: String? = null, endDate: String? = null, transactionCategory: Int? = null
    ): Flow<List<CategorySpending>>

    /**
     * Get a list of transaction providers.
     */
    fun getTransactionProviders(): List<String>
    fun getTypeSpending(startDate: String?, endDate: String?): Flow<List<CategorySpending>>
    fun getTransactionStatsByDay(startDate: String?, endDate: String?): Flow<List<DailyTotal>>
    fun getTopSubjectPerType(startDate: String?, endDate: String?): Flow<List<TopSubject>>
    fun getLargestTransaction(
        isInc: Boolean,
        startDate: String? = null,
        endDate: String? = null
    ): Flow<Transaction?>

    fun getMpesaBalance(): Flow<Double>

    fun getSubjectStats(subjectId: Long, startDate: String?, endDate: String?): Flow<SubjectStats>
}

