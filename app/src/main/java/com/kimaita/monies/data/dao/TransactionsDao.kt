package com.kimaita.monies.data.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import com.kimaita.monies.data.dao.dtos.ActivityCell
import com.kimaita.monies.data.dao.dtos.BalanceBucket
import com.kimaita.monies.data.dao.dtos.BalanceRow
import com.kimaita.monies.data.dao.dtos.CashFlowBucket
import com.kimaita.monies.data.dao.dtos.DailyTotal
import com.kimaita.monies.data.dao.dtos.IncExp
import com.kimaita.monies.data.dao.dtos.SubjectOccurrence
import com.kimaita.monies.data.dao.dtos.WeekdayCount
import com.kimaita.monies.data.database.models.Transaction
import com.kimaita.monies.data.database.models.TransactionWithDetails
import com.kimaita.monies.models.SortOrder
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionsDao : BaseDao<Transaction> {

    @androidx.room.Transaction
    @Query(
        """
        SELECT t.* FROM transactions t
        LEFT JOIN subjects s ON t.subject = s.id
        WHERE (:startDate IS NULL OR t.transactionTime >= :startDate)
        AND (:endDate IS NULL OR t.transactionTime <= :endDate)
        AND (:subjectId IS NULL OR t.subject = :subjectId)
        AND (:categoryId IS NULL OR t.transactionCategory IN (:categoryId))
        AND (:transactionType IS NULL OR t.transactionType IN (:transactionType))
        AND (:account IS NULL OR t.accountId = :account)
        AND (:natureFilter IS NULL OR (SELECT isInc FROM transaction_types WHERE id = t.transactionType) = :natureFilter)
        AND (:searchQuery IS NULL OR :searchQuery = '' OR 
            s.name LIKE '%' || :searchQuery || '%' OR 
            t.transactionTime LIKE '%' || :searchQuery || '%' OR 
            CAST(t.amount AS TEXT) LIKE '%' || :searchQuery || '%')
        ORDER BY 
            CASE WHEN :sortOrder = 'DATE_DESC' THEN t.transactionTime END DESC,
            CASE WHEN :sortOrder = 'DATE_ASC' THEN t.transactionTime END ASC,
            CASE WHEN :sortOrder = 'AMOUNT_DESC' THEN t.amount END DESC,
            CASE WHEN :sortOrder = 'AMOUNT_ASC' THEN t.amount END ASC
        LIMIT IFNULL(:limit, -1)
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
        searchQuery: String? = null,
        limit: Int? = null
    ): PagingSource<Int, TransactionWithDetails>


    @androidx.room.Transaction
    @Query(
        """
        SELECT * FROM transactions
        ORDER BY transactionTime DESC
        LIMIT :limit
    """
    )
    fun getLatestTransactions(limit: Int): Flow<List<TransactionWithDetails>>


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
        AND (:subjectId IS NULL OR t.subject IN (:subjectId))
        AND (:categoryId IS NULL OR t.transactionCategory IN (:categoryId))
        AND (:transactionType IS NULL OR t.transactionType IN (:transactionType))
        """
    )
    fun getIncomeVsExpense(
        startDate: String? = null,
        endDate: String? = null,
        subjectId: List<Long>? = null,
        categoryId: List<Int>? = null,
        transactionType: List<Int>? = null
    ): Flow<IncExp>

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


    @Query(
        """
        SELECT t.* FROM transactions AS t
        INNER JOIN (
            SELECT transactionType, MAX(transactionTime) AS maxTime
            FROM transactions
            WHERE transactionType IN (:typeIds)
            GROUP BY transactionType
        ) AS latest
          ON t.transactionType = latest.transactionType
         AND t.transactionTime = latest.maxTime
    """
    )
    fun getLatestPerType(typeIds: List<Int>): Flow<List<Transaction>>

    @androidx.room.Transaction
    @Query(
        """
    SELECT t.* FROM transactions t
    WHERE (:startDate IS NULL OR t.transactionTime >= :startDate)
      AND (:endDate IS NULL OR t.transactionTime <= :endDate)
      AND (:subjectId IS NULL OR t.subject IN (:subjectId))
      AND (:categoryId IS NULL OR t.transactionCategory IN (:categoryId))
      AND (:transactionType IS NULL OR t.transactionType IN (:transactionType))
      AND (:account IS NULL OR t.accountId = :account)
      AND (:natureFilter IS NULL OR (SELECT isInc FROM transaction_types WHERE id = t.transactionType) = :natureFilter)
      AND (:afterId IS NULL OR t.id > :afterId)
    ORDER BY t.id ASC
    LIMIT :limit
    """
    )
    suspend fun getExportBatch(
        afterId: Long?,
        limit: Int,
        startDate: String? = null,
        endDate: String? = null,
        subjectId: List<Long>? = null,
        categoryId: List<Int>? = null,
        transactionType: List<Int>? = null,
        account: Int? = null,
        natureFilter: Boolean? = null,
    ): List<TransactionWithDetails>

    @Query(
        """
    SELECT COUNT(*) FROM transactions t
    WHERE (:startDate IS NULL OR t.transactionTime >= :startDate)
      AND (:endDate IS NULL OR t.transactionTime <= :endDate)
      AND (:subjectId IS NULL OR t.subject IN (:subjectId))
      AND (:categoryId IS NULL OR t.transactionCategory IN (:categoryId))
      AND (:transactionType IS NULL OR t.transactionType IN (:transactionType))
      AND (:account IS NULL OR t.accountId = :account)
      AND (:natureFilter IS NULL OR (SELECT isInc FROM transaction_types WHERE id = t.transactionType) = :natureFilter)
    """
    )
    suspend fun countForExport(
        startDate: String? = null,
        endDate: String? = null,
        subjectId: List<Long>? = null,
        categoryId: List<Int>? = null,
        transactionType: List<Int>? = null,
        account: Int? = null,
        natureFilter: Boolean? = null,
    ): Int

    @Query(
        """
        SELECT strftime(:bucketFormat, t.transactionTime) AS bucket,
               SUM(CASE WHEN tt.isInc = 1 THEN t.amount ELSE 0 END) AS income,
               SUM(CASE WHEN tt.isInc = 0 THEN t.amount ELSE 0 END) AS expense,
               SUM(t.cost) AS cost
        FROM transactions AS t
        JOIN transaction_types AS tt ON t.transactionType = tt.id
        WHERE (:startDate IS NULL OR t.transactionTime >= :startDate)
          AND (:endDate IS NULL OR t.transactionTime <= :endDate)
          AND (:subjectId IS NULL OR t.subject IN (:subjectId))
          AND (:categoryId IS NULL OR t.transactionCategory IN (:categoryId))
          AND (:transactionType IS NULL OR t.transactionType IN (:transactionType))
        GROUP BY bucket ORDER BY bucket ASC
    """
    )
    fun getCashFlow(
        bucketFormat: String,
        startDate: String? = null,
        endDate: String? = null,
        subjectId: List<Long>? = null,
        categoryId: List<Int>? = null,
        transactionType: List<Int>? = null,
    ): Flow<List<CashFlowBucket>>


    @Query(
        """
        SELECT CAST(strftime('%w', t.transactionTime) AS INTEGER) AS weekday,
               CASE
                   WHEN CAST(strftime('%H', t.transactionTime) AS INTEGER) BETWEEN 5 AND 11 THEN 0
                   WHEN CAST(strftime('%H', t.transactionTime) AS INTEGER) BETWEEN 12 AND 16 THEN 1
                   WHEN CAST(strftime('%H', t.transactionTime) AS INTEGER) BETWEEN 17 AND 20 THEN 2
                   ELSE 3
               END AS slot,
               COUNT(*) AS count
        FROM transactions AS t
        JOIN transaction_types AS tt ON t.transactionType = tt.id
        WHERE (:startDate IS NULL OR t.transactionTime >= :startDate)
          AND (:endDate IS NULL OR t.transactionTime <= :endDate)
          AND (:subjectId IS NULL OR t.subject IN (:subjectId))
          AND (:categoryId IS NULL OR t.transactionCategory IN (:categoryId))
          AND (:transactionType IS NULL OR t.transactionType IN (:transactionType))
          AND (:natureFilter IS NULL OR tt.isInc = :natureFilter)
        GROUP BY weekday, slot
    """
    )
    fun getActivityByTimeOfDay(
        startDate: String? = null, endDate: String? = null,
        subjectId: List<Long>? = null, categoryId: List<Int>? = null,
        transactionType: List<Int>? = null, natureFilter: Boolean? = null,
    ): Flow<List<ActivityCell>>


    @Query(
        """
    SELECT bucket, balance FROM (
        SELECT strftime(:bucketFormat, transactionTime) AS bucket, balance,
            ROW_NUMBER() OVER (
                PARTITION BY strftime(:bucketFormat, transactionTime)
                ORDER BY transactionTime DESC, id DESC
            ) AS rn
        FROM transactions
        WHERE balance IS NOT NULL
          AND (:startDate IS NULL OR transactionTime >= :startDate)
          AND (:endDate IS NULL OR transactionTime <= :endDate)
    ) WHERE rn = 1
    ORDER BY bucket ASC
"""
    )
    fun getBalanceByBucket(
        bucketFormat: String,
        startDate: String?,
        endDate: String?
    ): Flow<List<BalanceBucket>>

    @Query(
        """
    SELECT CAST(strftime('%w', t.transactionTime) AS INTEGER) AS weekday,
        CASE
            WHEN CAST(strftime('%H', t.transactionTime) AS INTEGER) BETWEEN 5 AND 11 THEN 0
            WHEN CAST(strftime('%H', t.transactionTime) AS INTEGER) BETWEEN 12 AND 16 THEN 1
            WHEN CAST(strftime('%H', t.transactionTime) AS INTEGER) BETWEEN 17 AND 21 THEN 2
            ELSE 3
        END AS slot,
        COUNT(*) AS count
    FROM transactions t
    WHERE (:startDate IS NULL OR t.transactionTime >= :startDate)
      AND (:endDate IS NULL OR t.transactionTime <= :endDate)
      AND (:natureFilter IS NULL OR (SELECT isInc FROM transaction_types WHERE id = t.transactionType) = :natureFilter)
    GROUP BY weekday, slot
"""
    )
    fun getActivityByTimeOfDay(
        startDate: String?,
        endDate: String?,
        natureFilter: Boolean?
    ): Flow<List<ActivityCell>>

    @Query(
        """
    SELECT CAST(strftime('%w', transactionTime) AS INTEGER) AS weekday,
        COUNT(*) AS count, SUM(amount) AS total
    FROM transactions t
    WHERE (:startDate IS NULL OR transactionTime >= :startDate)
      AND (:endDate IS NULL OR transactionTime <= :endDate)
      AND (:natureFilter IS NULL OR (SELECT isInc FROM transaction_types WHERE id = t.transactionType) = :natureFilter)
    GROUP BY weekday ORDER BY weekday
"""
    )
    fun getWeekdayCounts(
        startDate: String?,
        endDate: String?,
        natureFilter: Boolean?
    ): Flow<List<WeekdayCount>>

    @Query(
        """
    SELECT DATE(transactionTime) AS day, SUM(amount) AS total, COUNT(id) AS count
    FROM transactions t
    WHERE (:startDate IS NULL OR transactionTime >= :startDate)
      AND (:endDate IS NULL OR transactionTime <= :endDate)
      AND (:natureFilter IS NULL OR (SELECT isInc FROM transaction_types WHERE id = t.transactionType) = :natureFilter)
    GROUP BY day ORDER BY day ASC
"""
    )
    fun getDailyTotals(
        startDate: String?,
        endDate: String?,
        natureFilter: Boolean?
    ): Flow<List<DailyTotal>>

    @Query(
        """
    SELECT t.subject AS subjectId, s.name AS name, tt.name AS typeName,
           t.transactionTime AS transactionTime, t.amount AS amount
    FROM transactions t
    JOIN subjects s ON t.subject = s.id
    JOIN transaction_types tt ON t.transactionType = tt.id
    WHERE t.subject IS NOT NULL AND t.transactionTime >= :since
    ORDER BY t.subject ASC, t.transactionTime ASC
"""
    )
    suspend fun getSubjectOccurrences(since: String): List<SubjectOccurrence>

    @Query("SELECT MIN(transactionTime) FROM transactions")
    suspend fun getEarliestTransactionTime(): String?
}

