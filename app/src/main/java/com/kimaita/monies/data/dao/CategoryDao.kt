package com.kimaita.monies.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.kimaita.monies.data.dao.dtos.CategoryBudget
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

    /**
     * List params use COALESCE(:p, NULL) IS NULL, not :p IS NULL: Room expands a list into one ? per
     * element, so `(?,? IS NULL` is a SQLite row value and fails with "row value misused" for 2+ ids.
     */
    @Query(
        """
            SELECT c.name, SUM(t.amount) AS total, SUM(t.cost) AS totalCost
            FROM transactions AS t
            JOIN categories AS c ON t.transactionCategory = c.id
            JOIN transaction_types AS tt ON t.transactionType = tt.id
            WHERE $DATE_RANGE
              AND $SUBJECTS_IN
              AND $CATEGORIES_IN
              AND $TYPES_IN
              AND (:natureFilter IS NULL OR tt.isInc = :natureFilter)
              AND tt.isTransfer = 0
              AND $ACCOUNT_FILTER
            GROUP BY c.name ORDER BY total DESC
        """
    )
    fun getCategorySpending(
        startDate: String? = null, endDate: String? = null, subjectId: List<Long>? = null,
        categoryId: List<Int>? = null, transactionType: List<Int>? = null, natureFilter: Boolean? = null, account: Int? = null,
    ): Flow<List<CategorySpending>>

    @Query("UPDATE transactions SET transactionCategory = NULL WHERE transactionCategory = :id")
    suspend fun clearTransactionCategory(id: Int)

    @Query("UPDATE subjects SET categoryId = NULL WHERE categoryId = :id")
    suspend fun clearSubjectCategory(id: Int)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Transaction
    suspend fun deleteCategory(id: Int) {
        clearTransactionCategory(id)
        clearSubjectCategory(id)
        deleteById(id)
    }

    /** Budgeted categories with what they spent between [start] and [end] (ISO text), money out only. */
    @Query(
        """
        SELECT c.id AS id, c.name AS name, c.monthlyBudget AS budget, COALESCE(SUM(t.amount), 0) AS spent
        FROM categories c
        LEFT JOIN transactions t ON t.transactionCategory = c.id
            AND t.transactionTime >= :start AND t.transactionTime <= :end
            AND t.transactionType IN (SELECT id FROM transaction_types WHERE isInc = 0 AND isTransfer = 0)
            AND $ACCOUNT_FILTER
        WHERE c.monthlyBudget IS NOT NULL
        GROUP BY c.id
        ORDER BY c.name
    """
    )
    fun getBudgets(start: String, end: String, account: Int? = null): Flow<List<CategoryBudget>>
}
