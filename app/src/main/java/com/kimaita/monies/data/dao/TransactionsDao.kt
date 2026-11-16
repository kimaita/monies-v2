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
import com.kimaita.monies.data.dao.dtos.LargestPayment
import com.kimaita.monies.data.dao.dtos.SubjectOccurrence
import com.kimaita.monies.data.dao.dtos.TraceTotals
import com.kimaita.monies.data.dao.dtos.WalkTotals
import com.kimaita.monies.data.dao.dtos.TypeFees
import com.kimaita.monies.data.dao.dtos.DayPayment
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

/**
 * The rows the recurring detector may see, on alias `t`: payments out, not transfers. Used both for
 * the rows returned and for counting rows per subject, so a subject qualifies only on rows the
 * detector would receive.
 */
private const val RECURRING_ROWS = """
        t.subject IS NOT NULL AND t.transactionTime >= :since
        AND t.transactionType IN (SELECT id FROM transaction_types WHERE isInc = 0 AND isTransfer = 0)
        AND $ACCOUNT_FILTER
"""

/**
 * `lines`: the lines (root accounts) that are not dormant, as [TransactionsDao.getAccountBalances]
 * counts them. `ledger`: every balance of those lines, merged accounts under their root.
 */
private const val LINE_LEDGER = """
    lines AS (
        SELECT a.id AS id FROM accounts a
        WHERE a.mergedInto IS NULL
          AND (SELECT MAX(t.transactionTime) FROM transactions t
               WHERE t.accountId IN (SELECT id FROM accounts WHERE COALESCE(mergedInto, id) = a.id))
              >= (SELECT strftime('%Y-%m-%dT%H:%M:%S', MAX(transactionTime), '-90 days') FROM transactions)
    ), ledger AS (
        SELECT COALESCE(a.mergedInto, a.id) AS line, t.id AS id, t.transactionTime AS time, t.balance AS balance
        FROM transactions t JOIN accounts a ON a.id = t.accountId
        WHERE t.balance IS NOT NULL AND COALESCE(a.mergedInto, a.id) IN (SELECT id FROM lines)
    )
"""

/** Each line's latest balance before :startDate, with a null bucket; no rows when :startDate is null. */
private const val LINE_BALANCES_BEFORE = """
    SELECT line AS lineId, NULL AS bucket, balance FROM (
        SELECT line, balance, MAX(time) AS time FROM (
            SELECT line, time, balance, MAX(id) AS id FROM ledger
            WHERE :startDate IS NOT NULL AND time < :startDate GROUP BY line, time
        ) GROUP BY line
    )
"""

private const val TRANSACTIONS_SELECT = "SELECT t.* FROM transactions t "

/**
 * List params use COALESCE(:p, NULL) IS NULL, not :p IS NULL: Room expands a list into one ? per
 * element, so `(?,? IS NULL` is a SQLite row value and fails with "row value misused" for 2+ ids.
 * A trace that starts after a transaction passes its time as :startDate and its id as :traceAfter: of
 * the rows at that very time, only those with a higher id count, as the list orders them.
 */
private const val TRANSACTIONS_FILTER = """
        WHERE $DATE_RANGE
        AND (:traceAfter IS NULL OR t.transactionTime > :startDate OR t.id > :traceAfter)
        AND (:subjectId IS NULL OR t.subject = :subjectId)
        AND $CATEGORIES_IN
        AND $TYPES_IN
        AND $ACCOUNT_FILTER
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

    /**
     * The four list queries differ only in ORDER BY. Each sorts by a plain indexed column with id as the
     * tie-break, so SQLite reads the index in order instead of sorting every matching row for each page.
     */
    @androidx.room.Transaction
    @Query(TRANSACTIONS_SELECT + TRANSACTIONS_FILTER + SEARCH_FILTER + "ORDER BY t.transactionTime DESC, t.id DESC")
    fun getTransactionsNewest(
        startDate: String?,
        endDate: String?,
        traceAfter: Long?,
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
        traceAfter: Long?,
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
        traceAfter: Long?,
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
        traceAfter: Long?,
        subjectId: Long?,
        categoryId: List<Int>?,
        transactionType: List<Int>?,
        account: Int?,
        natureFilter: Boolean?,
        fts: String?,
        amountQuery: String?,
        subjectLike: String?
    ): PagingSource<Int, TransactionWithDetails>

    /**
     * The totals of the rows the list shows for the same filters: money in and out (not
     * transfers), every fee, how many lines the rows come from, and one of those lines.
     */
    @Query(
        """
        SELECT COUNT(*) AS count,
            SUM(CASE WHEN tt.isTransfer = 0 AND tt.isInc = 1 THEN t.amount ELSE 0 END) AS income,
            SUM(CASE WHEN tt.isTransfer = 0 AND tt.isInc = 0 THEN t.amount ELSE 0 END) AS expense,
            SUM(COALESCE(t.cost, 0)) AS fees,
            COUNT(DISTINCT COALESCE(a.mergedInto, a.id)) AS lines,
            MIN(COALESCE(a.mergedInto, a.id)) AS line
        FROM transactions t
        JOIN transaction_types tt ON tt.id = t.transactionType
        JOIN accounts a ON a.id = t.accountId
    """ + TRANSACTIONS_FILTER + SEARCH_FILTER
    )
    fun getTraceTotals(
        startDate: String?,
        endDate: String?,
        traceAfter: Long?,
        subjectId: Long?,
        categoryId: List<Int>?,
        transactionType: List<Int>?,
        account: Int?,
        natureFilter: Boolean?,
        fts: String?,
        amountQuery: String?,
        subjectLike: String?
    ): Flow<TraceTotals>

    /**
     * The balance at a trace's start, added up as Home adds lines: each active line's last balance
     * before :startDate, or up to and including transaction :traceAfter when the trace starts after
     * it; with :account, that line's only. A null :startDate gives the balance now. Null when no
     * line has a balance by then.
     */
    @Query(
        """
    WITH $LINE_LEDGER
    SELECT SUM(balance) FROM (
        SELECT line, balance, MAX(time) AS time FROM (
            SELECT line, time, balance, MAX(id) AS id FROM ledger
            WHERE (:account IS NULL OR line = :account)
              AND (:startDate IS NULL OR time < :startDate
                   OR :traceAfter IS NOT NULL AND time = :startDate AND id <= :traceAfter)
            GROUP BY line, time
        ) GROUP BY line
    )
"""
    )
    fun getBalanceAt(startDate: String?, traceAfter: Long?, account: Int?): Flow<Double?>


    @androidx.room.Transaction
    @Query(
        """
        SELECT * FROM transactions t
        WHERE $ACCOUNT_FILTER
        ORDER BY t.transactionTime DESC
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
        WHERE $DATE_RANGE
        AND $SUBJECTS_IN
        AND $CATEGORIES_IN
        AND $TYPES_IN
        AND $ACCOUNT_FILTER
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
    WHERE $DATE_RANGE
      AND $SUBJECTS_IN
      AND $CATEGORIES_IN
      AND $TYPES_IN
      AND $ACCOUNT_FILTER
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
    WHERE $DATE_RANGE
      AND $SUBJECTS_IN
      AND $CATEGORIES_IN
      AND $TYPES_IN
      AND $ACCOUNT_FILTER
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
        WHERE $DATE_RANGE
          AND $SUBJECTS_IN
          AND $CATEGORIES_IN
          AND $TYPES_IN
          AND $ACCOUNT_FILTER
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
        WHERE $DATE_RANGE
          AND $SUBJECTS_IN
          AND $CATEGORIES_IN
          AND $TYPES_IN
          AND (:natureFilter IS NULL OR tt.isInc = :natureFilter)
          AND tt.isTransfer = 0
          AND $ACCOUNT_FILTER
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
        FROM transactions t
        WHERE balance IS NOT NULL
          AND $DATE_RANGE
          AND $ACCOUNT_FILTER
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
      AND $SUBJECTS_IN
      AND $CATEGORIES_IN
      AND $TYPES_IN
      AND (:natureFilter IS NULL OR (SELECT isInc FROM transaction_types WHERE id = t.transactionType) = :natureFilter)
      AND t.transactionType NOT IN (SELECT id FROM transaction_types WHERE isTransfer = 1)
      AND $ACCOUNT_FILTER
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
      AND $ACCOUNT_FILTER
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
    WITH $LINE_LEDGER, cells AS (
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
    $LINE_BALANCES_BEFORE
"""
    )
    fun getLineBalanceBuckets(
        bucketFormat: String,
        startDate: String?,
        endDate: String?
    ): Flow<List<LineBalanceBucket>>

    /** Each line's latest balance before [startDate] (lines as [getLineBalanceBuckets] counts them). */
    @Query("WITH $LINE_LEDGER $LINE_BALANCES_BEFORE")
    fun getLineBalancesBefore(startDate: String): Flow<List<LineBalanceBucket>>

    /** One line's latest balance before [startDate]; null when it has none. */
    @Query(
        """
    SELECT t.balance FROM transactions t
    WHERE t.balance IS NOT NULL AND t.transactionTime < :startDate AND $ACCOUNT_FILTER
    ORDER BY t.transactionTime DESC, t.id DESC LIMIT 1
"""
    )
    fun getBalanceBefore(startDate: String, account: Int?): Flow<Double?>

    /**
     * What moved the balance in the range, filters aside: money in and out (not transfers), every
     * fee, and the transfers to and from M-Shwari and Fuliza. Lock Savings withdrawals go to
     * M-Shwari, not M-PESA, so they are left out.
     */
    @Query(
        """
    SELECT
        SUM(CASE WHEN tt.isTransfer = 0 AND tt.isInc = 1 THEN t.amount ELSE 0 END) AS income,
        SUM(CASE WHEN tt.isTransfer = 0 AND tt.isInc = 0 THEN t.amount ELSE 0 END) AS expense,
        SUM(COALESCE(t.cost, 0)) AS fees,
        SUM(CASE WHEN tt.name LIKE 'mshwari.%' AND tt.name <> 'mshwari.lock_savings.withdrawal'
                  AND tt.isInc = 1 THEN t.amount ELSE 0 END) AS mshwariIn,
        SUM(CASE WHEN tt.name LIKE 'mshwari.%' AND tt.name <> 'mshwari.lock_savings.withdrawal'
                  AND tt.isInc = 0 THEN t.amount ELSE 0 END) AS mshwariOut,
        SUM(CASE WHEN tt.name LIKE 'fuliza.%' AND tt.isInc = 1 THEN t.amount ELSE 0 END) AS fulizaIn,
        SUM(CASE WHEN tt.name LIKE 'fuliza.%' AND tt.isInc = 0 THEN t.amount ELSE 0 END) AS fulizaOut,
        SUM(CASE WHEN tt.name LIKE 'fuliza.%' THEN COALESCE(t.cost, 0) ELSE 0 END) AS fulizaFees
    FROM transactions t
    JOIN transaction_types tt ON t.transactionType = tt.id
    WHERE $DATE_RANGE AND $ACCOUNT_FILTER
"""
    )
    fun getWalkTotals(startDate: String?, endDate: String?, account: Int?): Flow<WalkTotals>

    /** The payments out in the range (not transfers), latest first: the calendar's tapped day. */
    @Query(
        """
    SELECT s.name AS name, t.transactionTime AS time, t.amount AS amount
    FROM transactions t
    JOIN transaction_types tt ON t.transactionType = tt.id
    LEFT JOIN subjects s ON t.subject = s.id
    WHERE tt.isInc = 0 AND tt.isTransfer = 0 AND $DATE_RANGE AND $ACCOUNT_FILTER
    ORDER BY t.transactionTime DESC
"""
    )
    fun getPaymentsOut(startDate: String?, endDate: String?, account: Int?): Flow<List<DayPayment>>

    /** Fees by transaction type in the range: only transactions that cost something, biggest first. */
    @Query(
        """
    SELECT tt.name AS name, SUM(t.cost) AS fees, COUNT(*) AS count
    FROM transactions t
    JOIN transaction_types tt ON t.transactionType = tt.id
    WHERE t.cost > 0 AND $DATE_RANGE AND $SUBJECTS_IN AND $CATEGORIES_IN AND $TYPES_IN AND $ACCOUNT_FILTER
    GROUP BY tt.id ORDER BY fees DESC
"""
    )
    fun getTypeFees(
        startDate: String?, endDate: String?, subjectId: List<Long>?, categoryId: List<Int>?,
        transactionType: List<Int>?, account: Int?,
    ): Flow<List<TypeFees>>

    /**
     * The biggest single payment in and the biggest out (not transfers) in the range, with its time
     * and category. SQLite takes the bare id from the row holding each MAX.
     */
    @Query(
        """
    SELECT tt.isInc AS isInc, t.amount AS amount, t.transactionTime AS time, c.name AS category
    FROM transactions t
    JOIN transaction_types tt ON t.transactionType = tt.id
    LEFT JOIN categories c ON c.id = t.transactionCategory
    WHERE t.id IN (
        SELECT id FROM (
            SELECT t.id AS id, MAX(t.amount) FROM transactions t
            JOIN transaction_types tt ON t.transactionType = tt.id
            WHERE tt.isTransfer = 0 AND $DATE_RANGE AND $ACCOUNT_FILTER
            GROUP BY tt.isInc
        )
    )
"""
    )
    fun getLargestPayments(startDate: String?, endDate: String?, account: Int?): Flow<List<LargestPayment>>

    @Query(
        """
    SELECT t.subject AS subjectId, s.name AS name,
           t.transactionTime AS transactionTime, t.amount AS amount
    FROM transactions t
    JOIN subjects s ON t.subject = s.id
    WHERE $RECURRING_ROWS
      AND t.subject IN (
          SELECT t.subject FROM transactions t WHERE $RECURRING_ROWS
          GROUP BY t.subject HAVING COUNT(*) >= :minOccurrences)
    ORDER BY t.subject ASC, t.transactionTime ASC
"""
    )
    suspend fun getSubjectOccurrences(
        since: String,
        minOccurrences: Int,
        account: Int? = null
    ): List<SubjectOccurrence>

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
          AND $ACCOUNT_FILTER
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
          AND $ACCOUNT_FILTER
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
        SELECT MIN(t.transactionTime) FROM transactions t
        WHERE $ACCOUNT_FILTER
    """
    )
    suspend fun getEarliestTransactionTime(account: Int? = null): String?
}

