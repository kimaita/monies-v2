package com.kimaita.monies.data.dao

import androidx.room.Dao
import androidx.room.Query
import com.kimaita.monies.data.database.models.UserAccount
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao : BaseDao<UserAccount> {

    @Query("""SELECT * FROM accounts""")
    fun getAll(): Flow<List<UserAccount>>

    @Query("SELECT id FROM accounts WHERE provider = :provider AND subscriptionId = :subscriptionId")
    suspend fun getId(provider: String, subscriptionId: Int): Int?


}