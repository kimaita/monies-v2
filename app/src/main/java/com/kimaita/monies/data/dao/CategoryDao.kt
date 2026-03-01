package com.kimaita.monies.data.dao

import androidx.room.Dao
import androidx.room.Query
import com.kimaita.monies.data.dao.dtos.CategorySpending
import com.kimaita.monies.data.database.models.Category
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao : BaseDao<Category> {

    @Query(
        """SELECT * FROM categories
            WHERE (:search IS NULL OR name LIKE '%' || :search || '%')
        """
    )
    fun getCategories(search: String? = null): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE id = :id")
    fun getCategory(id: Long): Category?

    @Query(
        """
        SELECT c.name, 
            SUM(t.amount) AS total,
            SUM(t.cost) AS totalCost
        FROM transactions AS t
        JOIN categories AS c ON t.transactionCategory = c.id
        WHERE (:transactionCategory IS NULL OR t.transactionCategory = :transactionCategory)
        AND (:startDate IS NULL OR t.transactionTime >= :startDate)
        AND (:endDate IS NULL OR t.transactionTime <= :endDate)
        GROUP BY c.name
        """
    )
    fun getCategorySpending(
        startDate: String? = null,
        endDate: String? = null,
        transactionCategory: Int? = null
    ): Flow<List<CategorySpending>>

}