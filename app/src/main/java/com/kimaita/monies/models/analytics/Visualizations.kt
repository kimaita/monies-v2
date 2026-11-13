package com.kimaita.monies.models.analytics

/** [yearStart] is the year, on the bucket where it begins (see [com.kimaita.monies.services.yearStarts]). */
data class CashFlowWeek(val label: String, val inflow: Float, val outflow: Float, val yearStart: String? = null)

/** [label] is the full date for the tooltip, [axisLabel] the short one under the axis. */
data class BalancePoint(val label: String, val balance: Float, val axisLabel: String)
data class CostMonth(val month: String, val principal: Float, val fee: Float, val yearStart: String? = null)

data class MerchantSpend(val name: String, val amount: Double)
data class HeatRow(
    val day: String,
    val values: List<Int>
)   // 4 blocks: Morning/Afternoon/Evening/Night

