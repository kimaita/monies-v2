package com.kimaita.monies.data.dao

import androidx.room.Dao
import androidx.room.Query
import com.kimaita.monies.data.dao.dtos.CategorySpending
import com.kimaita.monies.data.database.models.Category
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao : BaseDao<Category> {

    @Query(
        """
            SELECT * FROM categories
            WHERE (:search IS NULL OR name LIKE '%' || :search || '%')
        """
    )
    fun getAll(search: String? = null): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE id = :id")
    fun getCategory(id: Long): Category?

    @Query(
        """
            SELECT c.name, SUM(t.amount) AS total, SUM(t.cost) AS totalCost
            FROM transactions AS t
            JOIN categories AS c ON t.transactionCategory = c.id
            JOIN transaction_types AS tt ON t.transactionType = tt.id
            WHERE (:startDate IS NULL OR t.transactionTime >= :startDate)
              AND (:endDate IS NULL OR t.transactionTime <= :endDate)
              AND (:subjectId IS NULL OR t.subject IN (:subjectId))
              AND (:transactionType IS NULL OR t.transactionType IN (:transactionType))
              AND (:natureFilter IS NULL OR tt.isInc = :natureFilter)
            GROUP BY c.name ORDER BY total DESC
        """
    )
    fun getCategorySpending(
        startDate: String? = null, endDate: String? = null, subjectId: List<Long>? = null,
        transactionType: List<Int>? = null, natureFilter: Boolean? = null,
    ): Flow<List<CategorySpending>>

}