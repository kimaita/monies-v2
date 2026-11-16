package com.kimaita.monies.ui.analytics

import com.kimaita.monies.models.TimeFilter
import com.kimaita.monies.services.FlowKind
import com.kimaita.monies.services.FlowNode
import com.kimaita.monies.services.MoneyWalk
import com.kimaita.monies.services.PaceKind
import com.kimaita.monies.services.PacePeriod
import com.kimaita.monies.services.daysBetween
import com.kimaita.monies.ui.util.formatAmount
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val monthYear = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)
private val monthName = DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH)
private val monthShort = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)

/** The period a card covers, for its small label: "Balance · last 30 days". */
fun periodLabel(tf: TimeFilter, firstDay: LocalDate? = null): String = when (tf) {
    TimeFilter.DAY -> "today"
    TimeFilter.WEEK -> "last 7 days"
    TimeFilter.MONTH -> "last 30 days"
    TimeFilter.SIX_MONTHS -> "last 6 months"
    TimeFilter.YTD -> "this year"
    TimeFilter.ALL -> firstDay?.let { "all time, since ${it.format(monthYear)}" } ?: "all time"
    is TimeFilter.CustomRange -> "${tf.startDate.format(dayMonth)} – ${tf.endDate.format(dayMonth)}"
}

/** The period inside a sentence: "Nothing in or out in the last 30 days." */
fun inPeriod(tf: TimeFilter): String = when (tf) {
    TimeFilter.DAY -> "today"
    TimeFilter.WEEK -> "this week"
    TimeFilter.MONTH -> "in the last 30 days"
    TimeFilter.SIX_MONTHS -> "in the last 6 months"
    TimeFilter.YTD -> "this year"
    TimeFilter.ALL -> "so far"
    is TimeFilter.CustomRange -> "in these dates"
}

/** "since 14 Oct", or "today" for the one-day period. */
fun sinceLabel(tf: TimeFilter, start: LocalDate?): String =
    if (tf == TimeFilter.DAY || start == null) "today" else "since ${start.format(dayMonth)}"

fun dayMonthLabel(d: LocalDate): String = d.format(dayMonth)

private val weekdayShort = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)

/** The pace card's small label: "This month · day 14 of 30". [p] is null for ALL. */
fun paceLabel(p: PacePeriod?, today: LocalDate, historyStart: LocalDate?): String = when (p?.kind) {
    null -> "All time" + (historyStart?.let { " · since ${it.format(monthYear)}" } ?: "")
    PaceKind.DAY -> "Today"
    PaceKind.WEEK -> "This week · day ${daysBetween(p.start, today) + 1} of 7"
    PaceKind.MONTH -> "This month · day ${today.dayOfMonth} of ${today.lengthOfMonth()}"
    PaceKind.YEAR -> "This year · day ${today.dayOfYear} of ${today.lengthOfYear()}"
    PaceKind.HALF_YEAR -> "Last 6 months"
    PaceKind.RANGE -> "${p.start.format(dayMonth)} – ${p.end.format(dayMonth)}"
}

/** The share of a calendar period gone by, for the card's track; null for the others. */
fun paceProgress(p: PacePeriod, today: LocalDate): Float? = when (p.kind) {
    PaceKind.WEEK, PaceKind.MONTH, PaceKind.YEAR -> (daysBetween(p.start, today) + 1) / p.days.toFloat()
    else -> null
}

/** What the change is measured against: "this time last month". */
private fun against(kind: PaceKind): String = when (kind) {
    PaceKind.DAY -> "yesterday"
    PaceKind.WEEK -> "this time last week"
    PaceKind.MONTH -> "this time last month"
    PaceKind.YEAR -> "this time last year"
    PaceKind.HALF_YEAR -> "the 6 months before"
    PaceKind.RANGE -> "the same number of days before"
}

/** Spending so far against the period before, and the category that is most of it. */
fun paceSentence(p: PacePeriod, changePct: Double?, spent: Double, mostly: String?): String {
    val pct = changePct?.let { abs(it).roundToInt() }
    val lead = when {
        changePct == null || pct == null -> "**KES ${formatAmount(spent)}** " + when (p.kind) {
            PaceKind.DAY -> "spent so far today."
            PaceKind.HALF_YEAR -> "spent in the last 6 months."
            PaceKind.RANGE -> "spent in these dates."
            else -> "spent so far."
        }
        pct == 0 -> "You've spent **about the same** as ${against(p.kind)}."
        changePct < 0 -> "You've spent **$pct% less** than ${against(p.kind)}."
        else -> "You've spent **$pct% more** than ${against(p.kind)}."
    }
    return if (mostly != null) "$lead Mostly ${mostly.lowercase(Locale.ENGLISH)}." else lead
}

/** The ALL chip's card: "KES 1,124,300 spent since Mar 2025." */
fun allTimeSentence(spent: Double, historyStart: LocalDate?): String =
    "**KES ${formatAmount(spent)}** spent " + (historyStart?.let { "since ${it.format(monthYear)}." } ?: "so far.")

/** The projection's small label, "By 30 Nov", "By Sun", "By 31 Dec"; null when the period has none. */
fun paceEndLabel(p: PacePeriod): String? = when (p.kind) {
    PaceKind.WEEK -> "By ${p.end.format(weekdayShort)}"
    PaceKind.MONTH, PaceKind.YEAR -> "By ${p.end.dayOfMonth} ${p.end.format(monthShort)}"
    else -> null
}

/** Stands in for the projection until it starts: "From the 7th", "From Wed", "From Feb". */
fun paceFromLabel(p: PacePeriod): String {
    val from = p.projectFrom ?: return ""
    return when (p.kind) {
        PaceKind.WEEK -> "From ${from.format(weekdayShort)}"
        PaceKind.YEAR -> "From ${from.format(monthShort)}"
        else -> "From the ${ordinal(from.dayOfMonth)}"
    }
}

private fun ordinal(n: Int): String = n.toString() + when {
    n % 100 in 11..13 -> "th"
    n % 10 == 1 -> "st"
    n % 10 == 2 -> "nd"
    n % 10 == 3 -> "rd"
    else -> "th"
}

/** The period compared with, for its figure and legend: "October", "Last week", "2025". */
fun previousLabel(p: PacePeriod): String = when (p.kind) {
    PaceKind.DAY -> "Yesterday"
    PaceKind.WEEK -> "Last week"
    PaceKind.MONTH -> p.compareStart.format(monthName)
    PaceKind.YEAR -> p.compareStart.year.toString()
    PaceKind.HALF_YEAR -> "6 months before"
    PaceKind.RANGE -> "Before"
}

/** The current period's legend: "November", "This week", "2026". */
fun currentLabel(p: PacePeriod): String = when (p.kind) {
    PaceKind.DAY -> "Today"
    PaceKind.WEEK -> "This week"
    PaceKind.MONTH -> p.start.format(monthName)
    PaceKind.YEAR -> p.start.year.toString()
    PaceKind.HALF_YEAR -> "Last 6 months"
    PaceKind.RANGE -> "These dates"
}

/**
 * The pace chart's x labels as (point, text), points counted from 1: weekdays for a week, days of
 * the month, quarter months for a year, and the first, middle and last dates of a rolling period.
 */
fun paceAxis(p: PacePeriod, today: LocalDate): List<Pair<Int, String>> {
    val now = p.stepOf(minOf(today, p.end))
    return when (p.kind) {
        PaceKind.DAY -> emptyList()
        PaceKind.WEEK -> (1..7).map { it to p.start.plusDays(it - 1L).format(weekdayShort) }
        PaceKind.MONTH -> (listOf(1) + (5..p.days step 5) + now + p.days).distinct().sorted().map { it to it.toString() }
        PaceKind.YEAR -> (listOf(1, 4, 7, 10).map { m -> p.stepOf(p.start.withMonth(m)) to p.start.withMonth(m).format(monthShort) } +
                (now to minOf(today, p.end).format(monthShort))).distinctBy { it.first }.sortedBy { it.first }
        PaceKind.HALF_YEAR, PaceKind.RANGE -> listOf(1, (p.steps + 1) / 2, p.steps).distinct().map {
            it to (if (it == p.steps) p.end else p.start.plusDays((it - 1L) * p.stepDays)).format(dayMonth)
        }
    }
}

/** "Up KES 798 since 14 Oct, now 30,838." */
fun balanceSentence(change: Double, closing: Double, since: String): String {
    val c = change.roundToInt()
    val lead = when {
        c > 0 -> "**Up KES ${formatAmount(c.toDouble())}**"
        c < 0 -> "**Down KES ${formatAmount(-c.toDouble())}**"
        else -> "**No change**"
    }
    return "$lead $since, now **${formatAmount(closing)}**."
}

/** "You came out KES 4,210 ahead." When nothing moved, says so for the period. */
fun inOutHeadline(w: MoneyWalk, tf: TimeFilter, empty: Boolean): String = when {
    empty -> if (tf == TimeFilter.DAY) "Nothing in or out yet today." else "Nothing in or out ${inPeriod(tf)}."
    w.kept >= 0 -> "You came out **KES ${formatAmount(w.kept)} ahead**."
    else -> "You came out **KES ${formatAmount(-w.kept)} short**."
}

/**
 * Where the rest went, under the headline: "798 stayed in your balance. 3,000 went to M-Shwari, 412
 * to fees." When the balance covered a shortfall and fees are the only other move, one sentence
 * says both: "Your balance covered it: 8,602 with fees."
 */
fun flowNote(w: MoneyWalk): String? {
    class Move(val amount: Double, val verb: String, val target: String)

    val moves = ArrayList<Move>()
    fun add(v: Double, target: String) {
        if (v < -0.5) moves += Move(-v, "went to", target) else if (v > 0.5) moves += Move(v, "came from", target)
    }
    add(w.mshwari, "M-Shwari")
    add(w.fuliza, "Fuliza")
    if (w.fees > 0.5) moves += Move(w.fees, "went to", "fees")

    val change = w.change.roundToInt()
    val sentences = ArrayList<String>()
    var movesSaid = false
    when {
        change > 0 -> sentences += "${formatAmount(change.toDouble())} stayed in your balance."
        change < 0 && w.kept < 0 -> {
            val onlyFees = moves.size == 1 && moves[0].target == "fees"
            sentences += "Your balance covered it: ${formatAmount(-change.toDouble())}" +
                    if (onlyFees) " with fees." else "."
            movesSaid = onlyFees
        }
        change < 0 -> sentences += "Your balance went down ${formatAmount(-change.toDouble())}."
    }
    if (!movesSaid && moves.isNotEmpty()) {
        sentences += moves.mapIndexed { i, m ->
            val sameVerb = i > 0 && moves[i - 1].verb == m.verb
            val verb = if (sameVerb) m.verb.substringAfter(' ') else m.verb
            "${formatAmount(m.amount)} $verb ${m.target}"
        }.joinToString(", ") + "."
    }
    if (abs(w.other) >= 1.0) {
        sentences += "Other changes: ${if (w.other > 0) "+" else "−"}${formatAmount(abs(w.other))}."
    }
    return sentences.takeIf { it.isNotEmpty() }?.joinToString(" ")
}

/** The payers in the "N others" box: "2 others: Wanjiku M., Dividend." Null for fewer than two. */
fun othersLine(names: List<String>): String? = when {
    names.size < 2 -> null
    names.size <= 3 -> "${names.size} others: ${names.joinToString(", ")}."
    else -> "${names.size} others: ${names.take(3).joinToString(", ")} and ${names.size - 3} more."
}

/** The flow box's name; [othersCount] payers make up the Others box. */
fun flowName(n: FlowNode, othersCount: Int): String = when (n.kind) {
    FlowKind.SOURCE -> n.name ?: "Money in"
    FlowKind.OTHERS -> n.name ?: if (othersCount >= 2) "$othersCount others" else "Others"
    FlowKind.BALANCE -> "Your balance"
    FlowKind.MSHWARI -> "M-Shwari"
    FlowKind.FULIZA -> "Fuliza"
    FlowKind.OTHER -> "Other changes"
    FlowKind.SPENDING -> "Spending"
    FlowKind.FEES -> "Fees"
    FlowKind.STAYED -> "Stayed"
}

/** "Rent took the most: 31%." */
fun whereItWentSentence(label: String, pct: Int) = "**$label** took the most: **$pct%**."

/** "KES 412 in fees, about 1 shilling in every 140." */
fun feesSentence(fees: Double, perFee: Int?): String =
    if (perFee != null && perFee >= 2)
        "**KES ${formatAmount(fees)}** in fees, about **1 shilling in every ${formatAmount(perFee.toDouble())}**."
    else "**KES ${formatAmount(fees)}** in fees."

private fun signed(v: Double) = (if (v < 0) "−" else "+") + formatAmount(abs(v))

/** A statement line's name: "Money in", "Kept: in minus out", "To M-Shwari". */
fun ledgerName(l: com.kimaita.monies.services.LedgerLine): String = when (l.kind) {
    com.kimaita.monies.services.LedgerKind.IN -> "Money in"
    com.kimaita.monies.services.LedgerKind.OUT -> "Money out"
    com.kimaita.monies.services.LedgerKind.KEPT -> if (l.change >= 0) "Kept: in minus out" else "Short: out minus in"
    com.kimaita.monies.services.LedgerKind.FEES -> "Fees"
    com.kimaita.monies.services.LedgerKind.MSHWARI -> if (l.change < 0) "To M-Shwari" else "From M-Shwari"
    com.kimaita.monies.services.LedgerKind.FULIZA -> if (l.change < 0) "To Fuliza" else "From Fuliza"
    com.kimaita.monies.services.LedgerKind.OTHER -> "Other changes"
}

/** Under "Why it moved": "Kept +4,210 · fees −412 · to M-Shwari −3,000". */
fun whyItMovedSummary(w: MoneyWalk): String {
    val parts = mutableListOf((if (w.kept >= 0) "Kept " else "Short ") + signed(w.kept))
    if (w.fees >= 0.5) parts += "fees " + signed(-w.fees)
    if (abs(w.mshwari) >= 0.5) parts += (if (w.mshwari < 0) "to M-Shwari " else "from M-Shwari ") + signed(w.mshwari)
    if (abs(w.fuliza) >= 0.5) parts += (if (w.fuliza < 0) "to Fuliza " else "from Fuliza ") + signed(w.fuliza)
    if (abs(w.other) >= 0.5) parts += "other " + signed(w.other)
    return parts.joinToString(" · ")
}

/** The signed figure for a statement line or total: "+4,210", "−412". */
fun signedAmount(v: Double): String = signed(v)
