package com.kimaita.monies.services

import java.time.LocalDateTime
import kotlin.math.max

/** A Fuliza message. [totalLoan] is on loans, [available] on repayments; [amount] is the transaction amount. */
data class FulizaEvent(
    val kind: Kind,
    val time: LocalDateTime,
    val amount: Double,
    val totalLoan: Double? = null,
    val available: Double? = null,
) {
    enum class Kind { LOAN, PARTIAL, FULL }
}

/** What is owed on Fuliza and how much of the limit is still free; null when not known. */
data class FulizaState(val outstanding: Double?, val available: Double?)

/**
 * Replays [events] in time order. A loan states the new total outstanding; a partial repayment
 * reduces it and states the available limit, so the limit is that plus what is still owed; a full
 * repayment clears it and states the whole limit.
 */
fun computeFuliza(events: List<FulizaEvent>): FulizaState {
    var outstanding: Double? = null
    var limit: Double? = null
    for (e in events.sortedBy { it.time }) {
        when (e.kind) {
            FulizaEvent.Kind.LOAN -> e.totalLoan?.let { outstanding = it }
            FulizaEvent.Kind.PARTIAL -> {
                outstanding = outstanding?.let { max(0.0, it - e.amount) }
                val owed = outstanding
                if (e.available != null && owed != null) limit = e.available + owed
            }
            FulizaEvent.Kind.FULL -> {
                outstanding = 0.0
                e.available?.let { limit = it }
            }
        }
    }
    val owed = outstanding
    return FulizaState(
        outstanding = owed?.let(::round2),
        available = limit?.let { round2(max(0.0, it - (owed ?: 0.0))) },
    )
}

private fun round2(v: Double) = Math.round(v * 100) / 100.0
