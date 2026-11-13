package com.kimaita.monies.data.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import com.kimaita.monies.data.dao.dtos.ActivityCell
import com.kimaita.monies.data.dao.dtos.BalanceBucket
import com.kimaita.monies.data.dao.dtos.CashFlowBucket
import com.kimaita.monies.data.dao.dtos.DailyTotal
import com.kimaita.monies.data.dao.dtos.FulizaRow
import com.kimaita.monies.data.dao.dtos.LineBalance
import com.kimaita.monies.data.dao.dtos.LineBalanceBucket
import com.kimaita.monies.data.dao.dtos.IncExp
import com.kimaita.monies.data.dao.dtos.SubjectOccurrence
import com.kimaita.monies.data.dao.dtos.WeekdayCount
import com.kimaita.monies.data.database.models.Transaction
import com.kimaita.monies.data.database.models.TransactionWithDetails
import kotlinx.coroutines.flow.Flow

/**
 * The search box as a filter on alias `t`: the message text (FTS), the amount, or the display name
 * of the subject, any one of them. Shared by the list and the export so both show the same rows.
 */
private const val SEARCH_FILTER = """
        AND (:fts IS NULL AND :amountQuery IS NULL AND :subjectLike IS NULL
            OR :fts IS NOT NULL AND t.id IN (SELECT rowid FROM transactions_fts WHERE transactions_fts MATCH :fts)
            OR :amountQuery IS NOT NULL AND CAST(t.amount AS TEXT) LIKE :amountQuery || '%'
            OR :subjectLike IS NOT NULL AND t.subject IN (SELECT id FROM subjects WHERE name LIKE :subjectLike))
"""

private const val TRANSACTIONS_SELECT = "SELECT t.* FROM transactions t "

// List params use COALESCE(:p, NULL) IS NULL, not :p IS NULL: Room expands a list into one ? per
// element, so `(?,? IS NULL` is a SQLite row value and fails with "row value misused" for 2+ ids.
private const val TRANSACTIONS_FILTER = """
        WHERE (:startDate IS NULL OR t.transactionTime >= :startDate)
        AND (:endDate IS NULL OR t.transactionTime <= :endDate)
        AND (:subjectId IS NULL OR t.subject = :subjectId)
        AND (COALESCE(:categoryId, NULL) IS NULL OR t.transactionCategory IN (:categoryId))
        AND (COALESCE(:transactionType, NULL) IS NULL OR t.transactionType IN (:transactionType))
        AND (:account IS NULL OR t.accountId IN (SELECT id FROM accounts WHERE COALESCE(mergedInto, id) = :account))
        AND (:natureFilter IS NULL OR (SELECT isInc FROM transaction_types WHERE id = t.transactionType) = :natureFilter)
"""

@Dao
interface TransactionsDao : BaseDao<Transaction> {

    /** One Airtel till payment sends two SMS with the same code; keep the one that names the merchant. */
    @Query(
        """
        DELETE FROM transactions
        WHERE transactionType IN (SELECT id FROM transaction_types WHERE name = 'airtel.pay.receipt')
        AND transactionCode IN (
            SELECT t.transactionCode FROM transactions t
            JOIN transaction_types ty ON ty.id = t.transactionType
            WHERE ty.name = 'airtel.pay')
        """
    )
    suspend fun deletePairedAirtelReceipts()

    // The four list queries differ only in ORDER BY. Each sorts by a plain indexed column with id as the
    // tie-break, so SQLite reads the index in order instead of sorting every matching row for each page.
    @androidx.room.Transaction
    @Query(TRANSACTIONS_SELECT + TRANSACTIONS_FILTER + SEARCH_FILTER + "ORDER BY t.transactionTime DESC, t.id DESC")
    fun getTransactionsNewest(
        startDate: String?,
        endDate: String?,
        subjectId: Long?,
        categoryId: List<Int>?,
        transactionType: List<Int>?,
        account: Int?,
        natureFilter: Boolean?,
        fts: String?,
        amountQuery: String?,
        subjectLike: String?
    ): PagingSource<Int, TransactionWithDetails>

    @androidx.room.Transaction
    @Query(TRANSACTIONS_SELECT + TRANSACTIONS_FILTER + SEARCH_FILTER + "ORDER BY t.transactionTime ASC, t.id ASC")
    fun getTransactionsOldest(
        startDate: String?,
        endDate: String?,
        subjectId: Long?,
        categoryId: List<Int>?,
        transactionType: List<Int>?,
        account: Int?,
        natureFilter: Boolean?,
        fts: String?,
        amountQuery: String?,
        subjectLike: String?
    ): PagingSource<Int, TransactionWithDetails>

    @androidx.room.Transaction
    @Query(TRANSACTIONS_SELECT + TRANSACTIONS_FILTER + SEARCH_FILTER + "ORDER BY t.amount DESC, t.id DESC")
    fun getTransactionsLargest(
        startDate: String?,
        endDate: String?,
        subjectId: Long?,
        categoryId: List<Int>?,
        transactionType: List<Int>?,
        account: Int?,
        natureFilter: Boolean?,
        fts: String?,
        amountQuery: String?,
        subjectLike: String?
    ): PagingSource<Int, TransactionWithDetails>

    @androidx.room.Transaction
    @Query(TRANSACTIONS_SELECT + TRANSACTIONS_FILTER + SEARCH_FILTER + "ORDER BY t.amount ASC, t.id ASC")
    fun getTransactionsSmallest(
        startDate: String?,
        endDate: String?,
        subjectId: Long?,
        categoryId: List<Int>?,
        transactionType: List<Int>?,
        account: Int?,
        natureFilter: Boolean?,
        fts: String?,
        amountQuery: String?,
        subjectLike: String?
    ): PagingSource<Int, TransactionWithDetails>


    @androidx.room.Transaction
    @Query(
        """
        SELECT * FROM transactions
        WHERE (:account IS NULL OR accountId IN (SELECT id FROM accounts WHERE COALESCE(mergedInto, id) = :account))
        ORDER BY transactionTime DESC
        LIMIT :limit
    """
    )
    fun getLatestTransactions(limit: Int, account: Int? = null): Flow<List<TransactionWithDetails>>


    @Query(
        """
        SELECT
            SUM(CASE WHEN tt.isInc = 1 AND tt.isTransfer = 0 THEN t.amount ELSE 0 END) AS income,
            SUM(CASE WHEN tt.isInc = 1 THEN t.cost ELSE 0 END) AS incomeCost,
            SUM(CASE WHEN tt.isInc = 1 AND tt.isTransfer = 0 THEN 1 ELSE 0 END) AS incomeCount,
            SUM(CASE WHEN tt.isInc = 0 AND tt.isTransfer = 0 THEN t.amount ELSE 0 END) AS expense,
            SUM(CASE WHEN tt.isInc = 0 THEN t.cost ELSE 0 END) AS expenseCost,
            SUM(CASE WHEN tt.isInc = 0 AND tt.isTransfer = 0 THEN 1 ELSE 0 END) AS expenseCount
        FROM transactions AS t
        JOIN transaction_types AS tt ON t.transactionType = tt.id
        WHERE (:startDate IS NULL OR t.transactionTime >= :startDate)
        AND (:endDate IS NULL OR t.transactionTime <= :endDate)
        AND (COALESCE(:subjectId, NULL) IS NULL OR t.subject IN (:subjectId))
        AND (COALESCE(:categoryId, NULL) IS NULL OR t.transactionCategory IN (:categoryId))
        AND (COALESCE(:transactionType, NULL) IS NULL OR t.transactionType IN (:transactionType))
        AND (:account IS NULL OR t.accountId IN (SELECT id FROM accounts WHERE COALESCE(mergedInto, id) = :account))
        """
    )
    fun getIncomeVsExpense(
        startDate: String? = null,
        endDate: String? = null,
        subjectId: List<Long>? = null,
        categoryId: List<Int>? = null,
        transactionType: List<Int>? = null,
        account: Int? = null
    ): Flow<IncExp>

    @Query(
        """
        SELECT t.* FROM transactions AS t
        INNER JOIN (
            SELECT transactionType, MAX(transactionTime) AS maxTime
            FROM transactions
            WHERE transactionType IN (:typeIds)
              AND (:account IS NULL OR accountId IN (SELECT id FROM accounts WHERE COALESCE(mergedInto, id) = :account))
            GROUP BY transactionType
        ) AS latest
          ON t.transactionType = latest.transactionType
         AND t.transactionTime = latest.maxTime
    """
    )
    fun getLatestPerType(typeIds: List<Int>, account: Int? = null): Flow<List<Transaction>>

    @androidx.room.Transaction
    @Query(
        """
    SELECT t.* FROM transactions t
    WHERE (:startDate IS NULL OR t.transactionTime >= :startDate)
      AND (:endDate IS NULL OR t.transactionTime <= :endDate)
      AND (COALESCE(:subjectId, NULL) IS NULL OR t.subject IN (:subjectId))
      AND (COALESCE(:categoryId, NULL) IS NULL OR t.transactionCategory IN (:categoryId))
      AND (COALESCE(:transactionType, NULL) IS NULL OR t.transactionType IN (:transactionType))
      AND (:account IS NULL OR t.accountId IN (SELECT id FROM accounts WHERE COALESCE(mergedInto, id) = :account))
      AND (:natureFilter IS NULL OR (SELECT isInc FROM transaction_types WHERE id = t.transactionType) = :natureFilter)
      AND (:afterId IS NULL OR t.id > :afterId)
    """ + SEARCH_FILTER + """
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
        fts: String? = null,
        amountQuery: String? = null,
        subjectLike: String? = null,
    ): List<TransactionWithDetails>

    @Query(
        """
    SELECT COUNT(*) FROM transactions t
    WHERE (:startDate IS NULL OR t.transactionTime >= :startDate)
      AND (:endDate IS NULL OR t.transactionTime <= :endDate)
      AND (COALESCE(:subjectId, NULL) IS NULL OR t.subject IN (:subjectId))
      AND (COALESCE(:categoryId, NULL) IS NULL OR t.transactionCategory IN (:categoryId))
      AND (COALESCE(:transactionType, NULL) IS NULL OR t.transactionType IN (:transactionType))
      AND (:account IS NULL OR t.accountId IN (SELECT id FROM accounts WHERE COALESCE(mergedInto, id) = :account))
      AND (:natureFilter IS NULL OR (SELECT isInc FROM transaction_types WHERE id = t.transactionType) = :natureFilter)
    """ + SEARCH_FILTER
    )
    suspend fun countForExport(
        startDate: String? = null,
        endDate: String? = null,
        subjectId: List<Long>? = null,
        categoryId: List<Int>? = null,
        transactionType: List<Int>? = null,
        account: Int? = null,
        natureFilter: Boolean? = null,
        fts: String? = null,
        amountQuery: String? = null,
        subjectLike: String? = null,
    ): Int

    @Query(
        """
        SELECT strftime(:bucketFormat, t.transactionTime) AS bucket,
               SUM(CASE WHEN tt.isInc = 1 AND tt.isTransfer = 0 THEN t.amount ELSE 0 END) AS income,
               SUM(CASE WHEN tt.isInc = 0 AND tt.isTransfer = 0 THEN t.amount ELSE 0 END) AS expense,
               SUM(t.cost) AS cost
        FROM transactions AS t
        JOIN transaction_types AS tt ON t.transactionType = tt.id
        WHERE (:startDate IS NULL OR t.transactionTime >= :startDate)
          AND (:endDate IS NULL OR t.transactionTime <= :endDate)
          AND (COALESCE(:subjectId, NULL) IS NULL OR t.subject IN (:subjectId))
          AND (COALESCE(:categoryId, NULL) IS NULL OR t.transactionCategory IN (:categoryId))
          AND (COALESCE(:transactionType, NULL) IS NULL OR t.transactionType IN (:transactionType))
          AND (:account IS NULL OR t.accountId IN (SELECT id FROM accounts WHERE COALESCE(mergedInto, id) = :account))
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
        account: Int? = null,
    ): Flow<List<CashFlowBucket>>


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
        FROM transactions AS t
        JOIN transaction_types AS tt ON t.transactionType = tt.id
        WHERE (:startDate IS NULL OR t.transactionTime >= :startDate)
          AND (:endDate IS NULL OR t.transactionTime <= :endDate)
          AND (COALESCE(:subjectId, NULL) IS NULL OR t.subject IN (:subjectId))
          AND (COALESCE(:categoryId, NULL) IS NULL OR t.transactionCategory IN (:categoryId))
          AND (COALESCE(:transactionType, NULL) IS NULL OR t.transactionType IN (:transactionType))
          AND (:natureFilter IS NULL OR tt.isInc = :natureFilter)
          AND tt.isTransfer = 0
          AND (:account IS NULL OR t.accountId IN (SELECT id FROM accounts WHERE COALESCE(mergedInto, id) = :account))
        GROUP BY weekday, slot
    """
    )
    fun getActivityByTimeOfDay(
        startDate: String? = null, endDate: String? = null,
        subjectId: List<Long>? = null, categoryId: List<Int>? = null,
        transactionType: List<Int>? = null, natureFilter: Boolean? = null, account: Int? = null,
    ): Flow<List<ActivityCell>>


    /**
     * The latest balance in each bucket (ties on time go to the higher id). No window functions:
     * they need SQLite 3.25 and Android 8 to 10 ship 3.18 to 3.22. Instead the row with the highest
     * id per time, then the row with the latest time per bucket, using SQLite's rule that bare columns
     * in a query with one MAX() come from the row holding the maximum (the maxima here are unique).
     */
    @Query(
        """
    WITH cells AS (
        SELECT strftime(:bucketFormat, transactionTime) AS bucket, transactionTime AS time, id, balance
        FROM transactions
        WHERE balance IS NOT NULL
          AND (:startDate IS NULL OR transactionTime >= :startDate)
          AND (:endDate IS NULL OR transactionTime <= :endDate)
          AND (:account IS NULL OR accountId IN (SELECT id FROM accounts WHERE COALESCE(mergedInto, id) = :account))
    )
    SELECT bucket, balance FROM (
        SELECT bucket, balance, MAX(time) AS time FROM (
            SELECT bucket, time, balance, MAX(id) AS id FROM cells GROUP BY bucket, time
        ) GROUP BY bucket
    )
    ORDER BY bucket ASC
"""
    )
    fun getBalanceByBucket(
        bucketFormat: String,
        startDate: String?,
        endDate: String?,
        account: Int? = null
    ): Flow<List<BalanceBucket>>

    @Query(
        """
    SELECT CAST(strftime('%w', transactionTime) AS INTEGER) AS weekday,
        COUNT(*) AS count, SUM(amount) AS total
    FROM transactions t
    WHERE (:startDate IS NULL OR transactionTime >= :startDate)
      AND (:endDate IS NULL OR transactionTime <= :endDate)
      AND (COALESCE(:subjectId, NULL) IS NULL OR t.subject IN (:subjectId))
      AND (COALESCE(:categoryId, NULL) IS NULL OR t.transactionCategory IN (:categoryId))
      AND (COALESCE(:transactionType, NULL) IS NULL OR t.transactionType IN (:transactionType))
      AND (:natureFilter IS NULL OR (SELECT isInc FROM transaction_types WHERE id = t.transactionType) = :natureFilter)
      AND t.transactionType NOT IN (SELECT id FROM transaction_types WHERE isTransfer = 1)
      AND (:account IS NULL OR t.accountId IN (SELECT id FROM accounts WHERE COALESCE(mergedInto, id) = :account))
    GROUP BY weekday ORDER BY weekday
"""
    )
    fun getWeekdayCounts(
        startDate: String? = null, endDate: String? = null,
        subjectId: List<Long>? = null, categoryId: List<Int>? = null,
        transactionType: List<Int>? = null, natureFilter: Boolean? = null, account: Int? = null,
    ): Flow<List<WeekdayCount>>

    @Query(
        """
    SELECT DATE(transactionTime) AS day, SUM(amount) AS total, COUNT(id) AS count
    FROM transactions t
    WHERE (:startDate IS NULL OR transactionTime >= :startDate)
      AND (:endDate IS NULL OR transactionTime <= :endDate)
      AND (:natureFilter IS NULL OR (SELECT isInc FROM transaction_types WHERE id = t.transactionType) = :natureFilter)
      AND t.transactionType NOT IN (SELECT id FROM transaction_types WHERE isTransfer = 1)
      AND (:account IS NULL OR t.accountId IN (SELECT id FROM accounts WHERE COALESCE(mergedInto, id) = :account))
    GROUP BY day ORDER BY day ASC
"""
    )
    fun getDailyTotals(
        startDate: String?,
        endDate: String?,
        natureFilter: Boolean?,
        account: Int? = null
    ): Flow<List<DailyTotal>>

    /**
     * Per line (merged accounts count as one), its latest balance in each bucket, plus one row with a
     * null bucket for its latest balance before [startDate]. Only the lines that [getAccountBalances]
     * counts (not dormant). Summed per bucket by [com.kimaita.monies.services.sumLineBalances].
     */
    @Query(
        """
    WITH lines AS (
        SELECT a.id AS id FROM accounts a
        WHERE a.mergedInto IS NULL
          AND (SELECT MAX(t.transactionTime) FROM transactions t
               WHERE t.accountId IN (SELECT id FROM accounts WHERE COALESCE(mergedInto, id) = a.id))
              >= (SELECT strftime('%Y-%m-%dT%H:%M:%S', MAX(transactionTime), '-90 days') FROM transactions)
    ), ledger AS (
        SELECT COALESCE(a.mergedInto, a.id) AS line, t.id AS id, t.transactionTime AS time, t.balance AS balance
        FROM transactions t JOIN accounts a ON a.id = t.accountId
        WHERE t.balance IS NOT NULL AND COALESCE(a.mergedInto, a.id) IN (SELECT id FROM lines)
    ), cells AS (
        SELECT line, strftime(:bucketFormat, time) AS bucket, time, id, balance
        FROM ledger
        WHERE (:startDate IS NULL OR time >= :startDate) AND (:endDate IS NULL OR time <= :endDate)
    )
    SELECT line AS lineId, bucket, balance FROM (
        SELECT line, bucket, balance, MAX(time) AS time FROM (
            SELECT line, bucket, time, balance, MAX(id) AS id FROM cells GROUP BY line, bucket, time
        ) GROUP BY line, bucket
    )
    UNION ALL
    SELECT line AS lineId, NULL AS bucket, balance FROM (
        SELECT line, balance, MAX(time) AS time FROM (
            SELECT line, time, balance, MAX(id) AS id FROM ledger
            WHERE :startDate IS NOT NULL AND time < :startDate GROUP BY line, time
        ) GROUP BY line
    )
"""
    )
    fun getLineBalanceBuckets(
        bucketFormat: String,
        startDate: String?,
        endDate: String?
    ): Flow<List<LineBalanceBucket>>

    @Query(
        """
    SELECT t.subject AS subjectId, s.name AS name, tt.name AS typeName,
           t.transactionTime AS transactionTime, t.amount AS amount
    FROM transactions t
    JOIN subjects s ON t.subject = s.id
    JOIN transaction_types tt ON t.transactionType = tt.id
    WHERE t.subject IS NOT NULL AND t.transactionTime >= :since
      AND tt.isTransfer = 0
      AND (:account IS NULL OR t.accountId IN (SELECT id FROM accounts WHERE COALESCE(mergedInto, id) = :account))
    ORDER BY t.subject ASC, t.transactionTime ASC
"""
    )
    suspend fun getSubjectOccurrences(since: String, account: Int? = null): List<SubjectOccurrence>

    /**
     * One row per line (root account with transactions), in order of first activity, with the
     * latest non-null balance across every account of the line (null when it has none yet).
     * A line whose last transaction is more than 90 days older than the newest transaction of any
     * line is dormant (an old or borrowed SIM) and left out, so its stale balance is not counted.
     */
    @Query(
        """
        SELECT a.id AS accountId, a.provider AS provider, a.label AS label,
               (SELECT t.balance FROM transactions t
                WHERE t.accountId IN (SELECT id FROM accounts WHERE COALESCE(mergedInto, id) = a.id)
                  AND t.balance IS NOT NULL
                ORDER BY t.transactionTime DESC, t.id DESC LIMIT 1) AS balance
        FROM accounts a
        WHERE a.mergedInto IS NULL
          AND (SELECT MAX(t.transactionTime) FROM transactions t
               WHERE t.accountId IN (SELECT id FROM accounts WHERE COALESCE(mergedInto, id) = a.id))
              >= (SELECT strftime('%Y-%m-%dT%H:%M:%S', MAX(transactionTime), '-90 days') FROM transactions)
        ORDER BY (SELECT MIN(t.transactionTime) FROM transactions t
                  WHERE t.accountId IN (SELECT id FROM accounts WHERE COALESCE(mergedInto, id) = a.id)),
                 a.id
    """
    )
    fun getAccountBalances(): Flow<List<LineBalance>>

    /** Fuliza rows of a line, newest first, for [com.kimaita.monies.services.computeFuliza]. */
    @Query(
        """
        SELECT tt.name AS typeName, t.transactionTime AS transactionTime, t.amount AS amount,
               t.message AS message
        FROM transactions t
        JOIN transaction_types tt ON tt.id = t.transactionType
        WHERE tt.name LIKE 'fuliza.%'
          AND (:account IS NULL OR t.accountId IN (SELECT id FROM accounts WHERE COALESCE(mergedInto, id) = :account))
        ORDER BY t.transactionTime DESC, t.id DESC
        LIMIT 200
    """
    )
    fun getFulizaRows(account: Int? = null): Flow<List<FulizaRow>>

    /** M-Shwari loans and repayments of a line, newest first, for [com.kimaita.monies.services.computeMshwariLoan]. */
    @Query(
        """
        SELECT tt.name AS typeName, t.transactionTime AS transactionTime, t.amount AS amount,
               t.message AS message
        FROM transactions t
        JOIN transaction_types tt ON tt.id = t.transactionType
        WHERE tt.name IN ('mshwari.loan', 'mshwari.loan_repayment')
          AND (:account IS NULL OR t.accountId IN (SELECT id FROM accounts WHERE COALESCE(mergedInto, id) = :account))
        ORDER BY t.transactionTime DESC, t.id DESC
        LIMIT 50
    """
    )
    fun getMshwariLoanRows(account: Int? = null): Flow<List<FulizaRow>>

    @Query("UPDATE transactions SET transactionCategory = :category WHERE id = :id")
    suspend fun setCategory(id: Long, category: Int?)

    @Query("UPDATE transactions SET transactionCategory = :category WHERE subject = :subjectId")
    suspend fun setCategoryForSubject(subjectId: Long, category: Int?)

    @Query("UPDATE subjects SET categoryId = :category WHERE id = :subjectId")
    suspend fun setSubjectCategory(subjectId: Long, category: Int?)

    /** "All from this person or business": the subject's default and every one of its transactions. */
    @androidx.room.Transaction
    suspend fun setCategoryForAllOfSubject(subjectId: Long, category: Int?) {
        setSubjectCategory(subjectId, category)
        setCategoryForSubject(subjectId, category)
    }

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()

    @Query("DELETE FROM unparsed_messages")
    suspend fun deleteAllUnparsed()

    @Query("INSERT OR REPLACE INTO settings (`key`, value) VALUES (:key, '0')")
    suspend fun resetCursor(key: String)

    /** Clears every transaction and unparsed message and rewinds the SMS cursor, keeping subjects, categories and accounts. */
    @androidx.room.Transaction
    suspend fun clearForReimport(cursorKey: String) {
        deleteAllTransactions()
        deleteAllUnparsed()
        resetCursor(cursorKey)
    }

    @Query(
        """
        SELECT MIN(transactionTime) FROM transactions
        WHERE (:account IS NULL OR accountId IN (SELECT id FROM accounts WHERE COALESCE(mergedInto, id) = :account))
    """
    )
    suspend fun getEarliestTransactionTime(account: Int? = null): String?
}

