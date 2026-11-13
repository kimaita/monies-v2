package com.kimaita.monies.data.dao

import androidx.room.Dao
import androidx.room.Query
import com.kimaita.monies.data.database.models.UserAccount
import com.kimaita.monies.services.AccountActivity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao : BaseDao<UserAccount> {

    @Query("""SELECT * FROM accounts""")
    fun getAll(): Flow<List<UserAccount>>

    @Query("SELECT id FROM accounts WHERE provider = :provider AND subscriptionId = :subscriptionId")
    suspend fun getId(provider: String, subscriptionId: Int): Int?

    /** The root of the line an account belongs to; null when the account does not exist. */
    @Query("SELECT COALESCE(mergedInto, id) FROM accounts WHERE id = :id")
    suspend fun getRootId(id: Int): Int?

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
}