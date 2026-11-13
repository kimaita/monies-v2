package com.kimaita.monies.data.database.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "unparsed_messages", indices = [Index(value = ["body"], unique = true)])
data class UnparsedMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val body: String,
    val address: String,
    val date: Long,
)
