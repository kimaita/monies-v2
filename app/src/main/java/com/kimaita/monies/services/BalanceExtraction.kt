package com.kimaita.monies.services

import com.kimaita.monies.data.dao.dtos.FulizaRow
import com.kimaita.monies.models.BalanceField
import com.kimaita.monies.models.OtherBalances
import com.kimaita.monies.utils.groupValue
import java.time.LocalDateTime

internal fun fulizaEvent(row: FulizaRow, regexByName: Map<String, Regex>): FulizaEvent? {
    val kind = when (row.typeName) {
        "fuliza.loan" -> FulizaEvent.Kind.LOAN
        "fuliza.partial_pay" -> FulizaEvent.Kind.PARTIAL
        "fuliza.final_pay" -> FulizaEvent.Kind.FULL
        else -> return null
    }
    val regex = regexByName[row.typeName]
    val match = regex?.find(row.message)
    val time = runCatching { LocalDateTime.parse(row.transactionTime) }.getOrNull() ?: return null
    fun group(name: String) =
        regex?.let { match?.groupValue(it, name) }?.replace(",", "")?.toDoubleOrNull()
    return FulizaEvent(kind, time, row.amount, group("TotalLoan"), group("AvailableFuliza"))
}

/** A repayment states the loan (LoanBal) or says it is repaid in full; a new loan states no figure. */
internal fun mshwariLoanEvent(row: FulizaRow, repaymentRegex: Regex?): MshwariLoanEvent? {
    val time = runCatching { LocalDateTime.parse(row.transactionTime) }.getOrNull() ?: return null
    if (row.typeName != "mshwari.loan_repayment") return MshwariLoanEvent(MshwariLoanEvent.Kind.UNKNOWN, time)
    val stated = repaymentRegex?.let { it.find(row.message)?.groupValue(it, "LoanBal") }
        ?.replace(",", "")?.toDoubleOrNull()
    return when {
        stated != null -> MshwariLoanEvent(MshwariLoanEvent.Kind.STATED, time, stated)
        row.message.contains("repaid in full", ignoreCase = true) -> MshwariLoanEvent(MshwariLoanEvent.Kind.CLEARED, time)
        else -> MshwariLoanEvent(MshwariLoanEvent.Kind.UNKNOWN, time)
    }
}

internal fun extractBalances(
    rows: List<com.kimaita.monies.data.database.models.Transaction>,
    regexByType: Map<Int, Regex>
): OtherBalances {

    val best = HashMap<BalanceField, Pair<Double, LocalDateTime>>()
    rows.forEach { row ->
        val regex = regexByType[row.transactionType] ?: return@forEach
        val match = regex.find(row.message) ?: return@forEach
        BalanceField.entries.forEach { field ->
            val value = match.groupValue(regex, field.group)?.replace(",", "")?.toDoubleOrNull()
                ?: return@forEach
            val current = best[field]
            if (current == null || row.transactionTime.isAfter(current.second)) {
                best[field] = value to row.transactionTime
            }
        }
    }
    return OtherBalances(
        mshwariBalance = best[BalanceField.MSHWARI_BALANCE]?.first,
        lockSavings = best[BalanceField.LOCK_SAVINGS]?.first,
    )
}
