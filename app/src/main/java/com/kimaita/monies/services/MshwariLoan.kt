package com.kimaita.monies.services

import java.time.LocalDateTime

/**
 * An M-Shwari loan message. [Kind.STATED] carries the outstanding loan in [balance] (the short
 * repayment message). [Kind.CLEARED] is a repayment in full. [Kind.UNKNOWN] changes the loan but
 * states no figure: a new loan (its fees aren't in the message) or a repayment without one.
 */
data class MshwariLoanEvent(val kind: Kind, val time: LocalDateTime, val balance: Double? = null) {
    enum class Kind { STATED, CLEARED, UNKNOWN }
}

/**
 * The outstanding M-Shwari loan after replaying [events] in time order, or null when it isn't
 * known: no message states it, or a later message changed it without saying by how much.
 */
fun computeMshwariLoan(events: List<MshwariLoanEvent>): Double? {
    var loan: Double? = null
    for (e in events.sortedBy { it.time }) {
        loan = when (e.kind) {
            MshwariLoanEvent.Kind.STATED -> e.balance
            MshwariLoanEvent.Kind.CLEARED -> 0.0
            MshwariLoanEvent.Kind.UNKNOWN -> null
        }
    }
    return loan
}
