package com.kimaita.monies.data.database.models

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["transactionTime"]),
        Index(value = ["subject"]),
        Index(value = ["transactionType"]),
        Index(value = ["transactionCategory"]),
        Index(value = ["accountId"]),
        Index(value = ["message"], unique = true)
    ],
    foreignKeys = [
        ForeignKey(
            entity = Subject::class,
            parentColumns = ["id"],
            childColumns = ["subject"]
        ),
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["transactionCategory"]
        ),
        ForeignKey(
            entity = TransactionType::class,
            parentColumns = ["id"],
            childColumns = ["transactionType"]
        ),
        ForeignKey(
            entity = UserAccount::class,
            parentColumns = ["subscriptionId"],
            childColumns = ["accountId"]
        )
    ]
)
@Immutable
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val message: String,
    val messageId: Long?,
    val transactionCode: String?,
    val amount: Double,
    val cost: Double? = 0.0,
    val transactionTime: LocalDateTime,
    val currency: String? = "KES",
    val subject: Long?=null,
    val transactionType: Int,
    val transactionCategory: Int? = null,
    val accountId: Int? = 0
)

@Entity(tableName = "transactions_fts")
@Fts4(contentEntity = Transaction::class)
data class TransactionFts(
    val message: String,
)
