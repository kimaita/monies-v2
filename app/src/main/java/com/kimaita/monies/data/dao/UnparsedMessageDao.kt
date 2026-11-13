package com.kimaita.monies.data.dao

import androidx.room.Dao
import com.kimaita.monies.data.database.models.UnparsedMessage

@Dao
interface UnparsedMessageDao : BaseDao<UnparsedMessage>
