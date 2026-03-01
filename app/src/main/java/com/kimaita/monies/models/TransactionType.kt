package com.kimaita.monies.models

data class TransactionType(
    val id: Int,
    val name: String,
    val isInc: Boolean,
    val displayName: String?
)