package com.kimaita.monies.data.dao

import androidx.room.Dao
import androidx.room.Query
import com.kimaita.monies.data.dao.dtos.CategorySpending
import com.kimaita.monies.data.database.models.TransactionType
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionTypeDao : BaseDao<TransactionType> {
    @Query("SELECT * FROM transaction_types")
    fun getAll(): Flow<List<TransactionType>>

    @Query("SELECT provider FROM transaction_types")
    fun getProviders(): List<String>

    /**
     * Gets the total spending for each transaction type within a given date range.
     */
    @Query(
        """
            SELECT tt.name, SUM(t.amount) AS total, SUM(t.cost) AS totalCost
            FROM transactions AS t
            JOIN transaction_types AS tt ON t.transactionType = tt.id
            WHERE (:startDate IS NULL OR t.transactionTime >= :startDate)
              AND (:endDate IS NULL OR t.transactionTime <= :endDate)
              AND (:subjectId IS NULL OR t.subject IN (:subjectId))
              AND (:categoryId IS NULL OR t.transactionCategory IN (:categoryId))
              AND (:natureFilter IS NULL OR tt.isInc = :natureFilter)
            GROUP BY tt.id
            ORDER BY total DESC
        """
    )
    fun getTypeSpending(
        startDate: String? = null, endDate: String? = null,
        subjectId: List<Long>? = null, categoryId: List<Int>? = null, natureFilter: Boolean? = null,
    ): Flow<List<CategorySpending>>
}