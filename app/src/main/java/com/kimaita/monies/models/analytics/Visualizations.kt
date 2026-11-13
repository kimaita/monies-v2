package com.kimaita.monies.models.analytics

data class CashFlowWeek(val label: String, val inflow: Float, val outflow: Float)
data class BalancePoint(val day: String, val balance: Float)
data class CostMonth(val month: String, val principal: Float, val fee: Float)

data class MerchantSpend(val name: String, val amount: Double)
data class HeatRow(
    val day: String,
    val values: List<Int>
)   // 4 blocks: Morning/Afternoon/Evening/Night

