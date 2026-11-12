package com.kimaita.monies.models

data class SmsBatchResult(
    val count: Int,
    val newestEpochMillis: Long?,
)