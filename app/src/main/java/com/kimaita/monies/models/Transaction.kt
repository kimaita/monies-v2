package com.kimaita.monies.models

import java.time.LocalDateTime

data class Transaction(
    val id: Long,
    val message: String,
    val amount: Double,
    val cost: Double?,
    val code: String,
    val transactionTime: LocalDateTime?,
    val subject: Subject?,
    val type: TransactionType,
    val category: Category?,
    val isInc: Boolean
) {
    val total = amount + (cost ?: 0.0)
}
