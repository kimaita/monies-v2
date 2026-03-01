package com.kimaita.monies.data.dao

import androidx.room.Dao
import androidx.room.Query
import com.kimaita.monies.data.dao.dtos.CategorySpending
import com.kimaita.monies.data.database.models.TransactionType
import kotlinx.coroutines.flow.Flow
@Dao
interface TransactionTypeDao : BaseDao<TransactionType> {
    @Query("SELECT * FROM transaction_types")
    fun getTransactionTypes(): Flow<List<TransactionType>>

    @Query("SELECT provider FROM transaction_types")
    fun getProviders(): List<String>

    /**
     * Gets the total spending for each transaction type within a given date range.
     */
    @Query(
        """
        SELECT tt.name, SUM(t.amount) as total, SUM(t.cost) as totalCost
        FROM transactions AS t
        JOIN transaction_types AS tt ON t.transactionType = tt.id
        WHERE tt.isInc = 0
        AND (:startDate IS NULL OR t.transactionTime >= :startDate)
        AND (:endDate IS NULL OR t.transactionTime <= :endDate)
        GROUP BY tt.id
        """
    )
    fun getTypeSpending(
        startDate: String?,
        endDate: String?
    ): Flow<List<CategorySpending>>
}