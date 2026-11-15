package com.kimaita.monies.models.analytics

import java.time.LocalDateTime

/** [label] is the full date for the tooltip, [axisLabel] the short one under the axis. */
data class BalancePoint(val label: String, val balance: Float, val axisLabel: String)
data class HeatRow(
    val day: String,
    val values: List<Int>
)

data class RecurringPayment(
    val name: String,
    /** "Weekly", "Monthly", "Every 3 days", "~Monthly"… */
    val cadenceLabel: String,
    val typicalAmount: Double,
    val lastDate: LocalDateTime,
    val nextEstimate: LocalDateTime,
    val occurrences: Int,
    val irregular: Boolean,
)
