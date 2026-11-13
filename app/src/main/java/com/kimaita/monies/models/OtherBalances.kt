package com.kimaita.monies.models

data class OtherBalances(
    val mshwariBalance: Double? = null,
    val lockSavings: Double? = null,
    val availableFuliza: Double? = null,
    val outstandingFuliza: Double? = null,
)

/** Maps a balance to the named regex group that carries it. Add a row to support a new balance. */
enum class BalanceField(val group: String) {
    MSHWARI_BALANCE("MshwariBal"),
    LOCK_SAVINGS("LockSavingsBal"),
    AVAILABLE_FULIZA("AvailableFuliza"),
    OUTSTANDING_FULIZA("TotalLoan"),
}