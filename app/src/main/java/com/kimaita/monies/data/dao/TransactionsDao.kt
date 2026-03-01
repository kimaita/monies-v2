package com.kimaita.monies.data.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import com.kimaita.monies.data.dao.dtos.DailyTotal
import com.kimaita.monies.data.dao.dtos.IncExp
import com.kimaita.monies.data.database.models.Transaction
import com.kimaita.monies.data.database.models.TransactionWithDetails
import com.kimaita.monies.models.SortOrder
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionsDao : BaseDao<Transaction> {

    @androidx.room.Transaction
    @Query(
        """
        SELECT * FROM transactions
        WHERE (:startDate IS NULL OR transactionTime >= :startDate)
        AND (:endDate IS NULL OR transactionTime <= :endDate)
        AND (:subjectId IS NULL OR subject = :subjectId)
        AND (:categoryId IS NULL OR transactionCategory IN (:categoryId))
        AND (:transactionType IS NULL OR transactionType IN (:transactionType))
        AND (:account IS NULL OR accountId = :account)
        AND (:natureFilter IS NULL OR (SELECT isInc FROM transaction_types WHERE id = transactionType) = :natureFilter)
        ORDER BY 
            CASE WHEN :sortOrder = 'DATE_DESC' THEN transactionTime END DESC,
            CASE WHEN :sortOrder = 'DATE_ASC' THEN transactionTime END ASC,
            CASE WHEN :sortOrder = 'AMOUNT_DESC' THEN amount END DESC,
            CASE WHEN :sortOrder = 'AMOUNT_ASC' THEN amount END ASC
        
        """
    )
    fun getTransactions(
        startDate: String? = null,
        endDate: String? = null,
        subjectId: Long? = null,
        categoryId: List<Int>? = null,
        transactionType: List<Int>? = null,
        account: Int? = null,
        natureFilter: Boolean?,
        sortOrder: SortOrder?,
    ): PagingSource<Int, TransactionWithDetails>


    @androidx.room.Transaction
    @Query(
        """
        SELECT * FROM transactions
        ORDER BY transactionTime DESC
        LIMIT :limit
    """
    )
    fun getLatestTransactions(limit: Int? = 5): Flow<List<TransactionWithDetails?>>


    @androidx.room.Transaction
    @Query(
        """
        SELECT * FROM transactions
        WHERE id IN (
            SELECT rowid FROM transactions_fts
            WHERE transactions_fts MATCH :searchQuery
        )
        """
    )
    fun performSearch(searchQuery: String): Flow<List<TransactionWithDetails>>

    fun searchTransactions(searchQuery: String): Flow<List<TransactionWithDetails>> {
        val ftsQuery = "$searchQuery*"
        return performSearch(ftsQuery)
    }

    @Query(
        """
        SELECT
            SUM(CASE WHEN tt.isInc = 1 THEN t.amount ELSE 0 END) AS income,
            SUM(CASE WHEN tt.isInc = 1 THEN t.cost ELSE 0 END) AS incomeCost,
            SUM(CASE WHEN tt.isInc = 1 THEN 1 ELSE 0 END) AS incomeCount,
            SUM(CASE WHEN tt.isInc = 0 THEN t.amount ELSE 0 END) AS expense,
            SUM(CASE WHEN tt.isInc = 0 THEN t.cost ELSE 0 END) AS expenseCost,
            SUM(CASE WHEN tt.isInc = 0 THEN 1 ELSE 0 END) AS expenseCount
        FROM transactions AS t
        JOIN transaction_types AS tt ON t.transactionType = tt.id
        WHERE (:startDate IS NULL OR t.transactionTime >= :startDate)
        AND (:endDate IS NULL OR t.transactionTime <= :endDate)
        """
    )
    fun getIncomeVsExpense(
        startDate: String? = null, endDate: String? = null
    ): Flow<IncExp>

    /**
     * Gets the total expense amount for each day within a given date range.
     * The date is formatted to 'YYYY-MM-DD' for grouping.
     */
    @Query(
        """
        SELECT DATE(transactionTime) as day, SUM(amount) as total, COUNT(id) as count
        FROM transactions
        WHERE (:startDate IS NULL OR transactionTime >= :startDate)
        AND (:endDate IS NULL OR transactionTime <= :endDate)
        GROUP BY day
        ORDER BY day ASC
        """
    )
    fun getSpendingTotalsByDay(
        startDate: String?, endDate: String?
    ): Flow<List<DailyTotal>>

    @androidx.room.Transaction
    @Query(
        """
        SELECT * FROM transactions
        WHERE transactionType IN (SELECT id FROM transaction_types WHERE isInc = :isInc)
        AND (:startDate IS NULL OR transactionTime >= :startDate)
        AND (:endDate IS NULL OR transactionTime <= :endDate)
        ORDER BY amount DESC
        LIMIT 1
    """
    )
    fun getLargestTransaction(
        isInc: Boolean, startDate: String? = null, endDate: String? = null
    ): Flow<TransactionWithDetails?>


    @androidx.room.Transaction
    @Query(
        """
        SELECT * FROM transactions
        WHERE transactionType IN (:types)
        ORDER BY transactionTime DESC
        LIMIT 1
    """
    )
    fun getLatestTypeTransaction(types: List<Int>): Flow<TransactionWithDetails>


}

