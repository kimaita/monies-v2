package com.kimaita.monies.data.dao

import androidx.room.Dao
import androidx.room.Query
import com.kimaita.monies.data.database.models.UnparsedMessage
import kotlinx.coroutines.flow.Flow

@Dao
interface UnparsedMessageDao : BaseDao<UnparsedMessage> {

    @Query("SELECT * FROM unparsed_messages ORDER BY date DESC")
    fun getAll(): Flow<List<UnparsedMessage>>

    @Query("SELECT COUNT(*) FROM unparsed_messages")
    fun count(): Flow<Int>
}
