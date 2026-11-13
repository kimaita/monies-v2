package com.kimaita.monies.models.analytics

import java.time.LocalDateTime

data class RecurringPayment(
    val name: String,
    val cadenceLabel: String,   // "Weekly", "Monthly", "Every 3 days", "~Monthly"…
    val typicalAmount: Double,
    val lastDate: LocalDateTime,
    val nextEstimate: LocalDateTime,
    val occurrences: Int,
    val irregular: Boolean,
)