package com.kimaita.monies.data.database.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "accounts",
    indices = [Index(value = ["provider", "subscriptionId"], unique = true)]
)
data class UserAccount(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val provider: String,
    /** -1 = unknown. */
    val subscriptionId: Int,
    val label: String? = null,
)
