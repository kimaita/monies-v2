package com.kimaita.monies.data.database.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class UserAccount(
    @PrimaryKey
    val subscriptionId: Int,
    val carrier: String?,
    val number: String?
)

