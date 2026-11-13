package com.kimaita.monies.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.kimaita.monies.data.dao.dtos.LineSummary
import com.kimaita.monies.data.database.models.UserAccount
import com.kimaita.monies.services.AccountActivity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao : BaseDao<UserAccount> {

    @Query("""SELECT * FROM accounts""")
    fun getAll(): Flow<List<UserAccount>>

    @Query("SELECT id FROM accounts WHERE provider = :provider AND subscriptionId = :subscriptionId")
    suspend fun getId(provider: String, subscriptionId: Int): Int?

    /** Each account with the time span of its transactions; accounts without transactions are left out. */
    @Query(
        """
        SELECT a.id AS id, a.provider AS provider, MIN(t.transactionTime) AS minTime, MAX(t.transactionTime) AS maxTime, a.mergedInto AS mergedInto
        FROM accounts a JOIN transactions t ON t.accountId = a.id
        GROUP BY a.id
        """
    )
    suspend fun getActivity(): List<AccountActivity>

    @Query("UPDATE accounts SET mergedInto = :root WHERE id = :id")
    suspend fun setMergedInto(id: Int, root: Int?)

    /** Every line (root account), in order of first activity, with its transaction count and last activity. */
    @Query(
        """
        SELECT a.id AS accountId, a.provider AS provider, a.label AS label,
               (SELECT COUNT(*) FROM transactions t
                WHERE t.accountId IN (SELECT id FROM accounts WHERE COALESCE(mergedInto, id) = a.id)) AS count,
               (SELECT MAX(t.transactionTime) FROM transactions t
                WHERE t.accountId IN (SELECT id FROM accounts WHERE COALESCE(mergedInto, id) = a.id)) AS lastActivity
        FROM accounts a
        WHERE a.mergedInto IS NULL
        ORDER BY (SELECT MIN(t.transactionTime) FROM transactions t
                  WHERE t.accountId IN (SELECT id FROM accounts WHERE COALESCE(mergedInto, id) = a.id)) IS NULL,
                 (SELECT MIN(t.transactionTime) FROM transactions t
                  WHERE t.accountId IN (SELECT id FROM accounts WHERE COALESCE(mergedInto, id) = a.id)),
                 a.id
    """
    )
    fun getLineSummaries(): Flow<List<LineSummary>>

    @Query("UPDATE accounts SET label = :label WHERE id = :id")
    suspend fun setLabel(id: Int, label: String?)

    @Query("SELECT provider FROM accounts WHERE id = :id")
    suspend fun getProvider(id: Int): String?

    @Query(
        """
        UPDATE transactions SET accountId = :target
        WHERE accountId IN (SELECT id FROM accounts WHERE COALESCE(mergedInto, id) = :source)
    """
    )
    suspend fun moveLineTransactions(source: Int, target: Int)

    @Query("DELETE FROM accounts WHERE COALESCE(mergedInto, id) = :source")
    suspend fun deleteLine(source: Int)

    /** Moves every transaction of the [source] line to the [target] line's root and deletes the source's rows. */
    @Transaction
    suspend fun mergeLines(source: Int, target: Int) {
        require(source != target && getProvider(source) == getProvider(target)) { "Lines must differ and share a provider" }
        moveLineTransactions(source, target)
        deleteLine(source)
    }
}
