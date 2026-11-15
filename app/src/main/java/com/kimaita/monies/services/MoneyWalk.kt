package com.kimaita.monies.services

import com.kimaita.monies.data.dao.dtos.LargestPayment
import com.kimaita.monies.data.dao.dtos.WalkTotals
import java.time.LocalDate
import kotlin.math.abs

/**
 * How the balance got from [opening] to [closing]. Each amount is signed as its effect on the
 * balance: opening + income − spending − fees + mshwari + fuliza + other = closing. [mshwari] and
 * [fuliza] are net, plus when more came back than went; [other] is what the messages don't explain.
 */
data class MoneyWalk(
    val opening: Double,
    val closing: Double,
    val income: Double,
    val spending: Double,
    val fees: Double,
    val mshwari: Double,
    val fuliza: Double,
    val other: Double,
) {
    /** In minus out; negative when more went out. */
    val kept: Double get() = income - spending
    val change: Double get() = closing - opening
}

/**
 * The walk for a period's [totals]. A Fuliza fee is added to what is owed rather than taken from the
 * balance, so it counts with the borrowing and the repayments cover it. A remainder under KES 1 is
 * rounding and dropped.
 */
fun moneyWalk(totals: WalkTotals, opening: Double, closing: Double): MoneyWalk {
    val mshwari = totals.mshwariIn - totals.mshwariOut
    val fuliza = totals.fulizaIn + totals.fulizaFees - totals.fulizaOut
    val explained = totals.income - totals.expense - totals.fees + mshwari + fuliza
    val other = closing - opening - explained
    return MoneyWalk(
        opening, closing, totals.income, totals.expense, totals.fees, mshwari, fuliza,
        if (abs(other) < 1.0) 0.0 else other,
    )
}

enum class FlowKind { SOURCE, OTHERS, BALANCE, MSHWARI, FULIZA, OTHER, SPENDING, FEES, STAYED }

/** One box of the in-and-out flow. [name] is the payer's name for a source; other kinds have fixed names. */
data class FlowNode(val kind: FlowKind, val amount: Double, val name: String? = null)

/** Both sides add up to the same total. [others] names the payers folded into the Others box. */
data class FlowSides(val left: List<FlowNode>, val right: List<FlowNode>, val others: List<String>)

private const val HALF = 0.5

/** How many payers the flow names before folding the rest into one box. */
const val NAMED_SOURCES = 3

/**
 * What came into the balance on the left and where it went on the right. Money in is the
 * [NAMED_SOURCES] biggest payers by name and the rest in one Others box ([FlowSides.others] names
 * them), or by name when only one is left; [sources] are largest first and need not add up to the
 * income. Moves out of the balance go right and moves into it left; the change in the balance is
 * Stayed on the right, or Your balance last on the left when it went down.
 */
fun flowSides(w: MoneyWalk, sources: List<Pair<String, Double>>): FlowSides {
    val left = ArrayList<FlowNode>()
    val right = ArrayList<FlowNode>()
    var others = emptyList<String>()
    if (w.income > HALF) {
        val top = ArrayList<Pair<String, Double>>()
        var named = 0.0
        for ((name, v) in sources.take(NAMED_SOURCES)) {
            if (v <= HALF || named + v > w.income + HALF) break
            top += name to v
            named += v
        }
        if (top.isEmpty()) left += FlowNode(FlowKind.SOURCE, w.income)
        top.forEach { (name, v) -> left += FlowNode(FlowKind.SOURCE, v, name) }
        val rest = w.income - named
        val names = sources.drop(top.size).map { it.first }
        if (top.isNotEmpty() && rest > HALF) {
            if (names.size == 1) left += FlowNode(FlowKind.OTHERS, rest, names[0])
            else {
                left += FlowNode(FlowKind.OTHERS, rest)
                others = names
            }
        }
    }
    if (w.spending > HALF) right += FlowNode(FlowKind.SPENDING, w.spending)
    if (w.fees > HALF) right += FlowNode(FlowKind.FEES, w.fees)
    for ((v, kind) in listOf(w.mshwari to FlowKind.MSHWARI, w.fuliza to FlowKind.FULIZA, w.other to FlowKind.OTHER)) {
        if (v > HALF) left += FlowNode(kind, v) else if (v < -HALF) right += FlowNode(kind, -v)
    }
    if (w.change > HALF) right += FlowNode(FlowKind.STAYED, w.change)
    else if (w.change < -HALF) left += FlowNode(FlowKind.BALANCE, -w.change)
    return FlowSides(left, right, others)
}

/** The day of the biggest payment in, when it is at least half of all [income]: payday. */
fun paydayDate(largest: List<LargestPayment>, income: Double): LocalDate? =
    largest.firstOrNull { it.isInc }?.takeIf { income > 0.0 && it.amount >= income / 2 }
        ?.let { runCatching { LocalDate.parse(it.time.take(10)) }.getOrNull() }

enum class LedgerKind { IN, OUT, KEPT, FEES, MSHWARI, FULIZA, OTHER }

/** A statement line: its change and the balance after it ([balance] is null for the Kept subtotal). */
data class LedgerLine(val kind: LedgerKind, val change: Double, val balance: Double?)

/**
 * The walk as a bank statement from the opening balance: money in, money out, the Kept subtotal,
 * then fees and each move that isn't zero. The last balance is the closing one.
 */
fun MoneyWalk.ledger(): List<LedgerLine> {
    val lines = ArrayList<LedgerLine>()
    var b = opening + income
    lines += LedgerLine(LedgerKind.IN, income, b)
    b -= spending
    lines += LedgerLine(LedgerKind.OUT, -spending, b)
    lines += LedgerLine(LedgerKind.KEPT, kept, null)
    for ((kind, v) in listOf(LedgerKind.FEES to -fees, LedgerKind.MSHWARI to mshwari, LedgerKind.FULIZA to fuliza, LedgerKind.OTHER to other)) {
        if (abs(v) < 0.5) continue
        b += v
        lines += LedgerLine(kind, v, b)
    }
    return lines
}
