package com.kimaita.monies.services

import com.kimaita.monies.data.dao.dtos.ActivityCell
import com.kimaita.monies.data.dao.dtos.CategorySpending
import com.kimaita.monies.models.analytics.RecurringPayment
import java.time.LocalDate
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** One expected payment of a regular. */
data class DuePayment(val date: LocalDate, val payment: RecurringPayment)

/**
 * Each regular's next date and every period after it, kept when within [from]..[to]. Monthly and
 * longer periods step by calendar months, shorter ones by days.
 */
fun dueDates(recurring: List<RecurringPayment>, from: LocalDate, to: LocalDate): List<DuePayment> {
    val out = ArrayList<DuePayment>()
    for (r in recurring) {
        val months = (r.periodDays / DAYS_PER_MONTH).roundToLong()
        val days = r.periodDays.roundToLong().coerceAtLeast(1)
        var d = r.nextEstimate.toLocalDate()
        while (!d.isAfter(to)) {
            if (!d.isBefore(from)) out += DuePayment(d, r)
            d = if (r.periodDays >= 28.0) d.plusMonths(months) else d.plusDays(days)
        }
    }
    return out.sortedBy { it.date }
}

/** What the regulars add up to in an average month. */
fun monthlyRecurringTotal(recurring: List<RecurringPayment>): Double =
    recurring.sumOf { it.typicalAmount * DAYS_PER_MONTH / it.periodDays }

internal fun median(xs: List<Double>): Double {
    if (xs.isEmpty()) return 0.0
    val s = xs.sorted()
    val m = s.size / 2
    return if (s.size % 2 == 1) s[m] else (s[m - 1] + s[m]) / 2.0
}

/** A figure to show after "~": to the nearest 50 below 10,000, else to the nearest 100. */
fun roughly(v: Double): Double {
    val step = if (v < 10_000) 50.0 else 100.0
    return Math.round(v / step) * step
}

/** Whole percentages of [values] that add up to 100 (largest remainders round up); all 0 with no total. */
fun percentShares(values: List<Double>): List<Int> {
    val total = values.sum()
    if (total <= 0.0) return values.map { 0 }
    val exact = values.map { it / total * 100.0 }
    val shares = exact.map { floor(it).toInt() }.toMutableList()
    val short = 100 - shares.sum()
    exact.indices.sortedByDescending { exact[it] - shares[it] }.take(short).forEach { shares[it]++ }
    return shares
}

/** Payment counts per slot of the day (0 morning .. 3 night) as percentages adding up to 100. */
fun slotShares(cells: List<ActivityCell>): List<Int> =
    percentShares((0..3).map { s -> cells.filter { it.slot == s }.sumOf { it.count }.toDouble() })

/** The category that is over half of [rows]' spending, if one is. */
fun mostlyCategory(rows: List<CategorySpending>): String? {
    val top = rows.maxByOrNull { it.total } ?: return null
    return top.name.takeIf { top.total > rows.sumOf { it.total } / 2 }
}

/**
 * How many shillings of spending one shilling of fees comes to: whole below 100, else to two
 * significant figures (141 → 140), as it follows "about". Null without both.
 */
fun shillingsPerFee(fees: Double, spending: Double): Int? {
    if (fees <= 0.0 || spending <= 0.0) return null
    val n = spending / fees
    if (n < 100) return n.roundToInt()
    val step = Math.pow(10.0, floor(Math.log10(n)) - 1)
    return (Math.round(n / step) * step).roundToInt()
}
