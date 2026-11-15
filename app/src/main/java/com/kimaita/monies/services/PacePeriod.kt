package com.kimaita.monies.services

import com.kimaita.monies.data.dao.dtos.DailyTotal
import com.kimaita.monies.models.TimeFilter
import com.kimaita.monies.models.analytics.RecurringPayment
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** What the Overview pace card covers for a period chip. */
enum class PaceKind { DAY, WEEK, MONTH, YEAR, HALF_YEAR, RANGE }

/**
 * The period the pace card covers, [start] to [end] inclusive, and what it is compared with: the
 * period just before, from [compareStart] to the day before [start]. With [sameTime] the two are
 * compared over the same number of days from their starts ("this time last week"), else whole;
 * [compare] false shows no change at all. Points are every [stepDays] days. A projection to [end]
 * shows from [projectFrom] on, and never when it is null.
 */
data class PacePeriod(
    val kind: PaceKind,
    val start: LocalDate,
    val end: LocalDate,
    val compareStart: LocalDate,
    val sameTime: Boolean,
    val compare: Boolean = true,
    val stepDays: Int = 1,
    val projectFrom: LocalDate? = null,
) {
    val days: Int get() = daysBetween(start, end) + 1
    val steps: Int get() = (days + stepDays - 1) / stepDays

    /** The point [date] falls in, 1-based. */
    fun stepOf(date: LocalDate): Int = daysBetween(start, date) / stepDays + 1
}

internal fun daysBetween(a: LocalDate, b: LocalDate): Int = ChronoUnit.DAYS.between(a, b).toInt()

/**
 * The calendar period a chip stands for on the pace card. A rolling week, month or year has nothing
 * left to project, so 1W, 1M and YTD become this week, month and year so far. 6M and a custom range
 * stay rolling and are compared whole with the same length before. ALL has no period: null.
 */
fun pacePeriod(tf: TimeFilter, today: LocalDate): PacePeriod? = when (tf) {
    TimeFilter.DAY -> PacePeriod(PaceKind.DAY, today, today, today.minusDays(1), false, compare = false)
    TimeFilter.WEEK -> {
        val monday = today.with(DayOfWeek.MONDAY)
        PacePeriod(
            PaceKind.WEEK, monday, monday.plusDays(6), monday.minusWeeks(1), true,
            projectFrom = monday.plusDays(2),
        )
    }
    TimeFilter.MONTH -> {
        val first = today.withDayOfMonth(1)
        PacePeriod(
            PaceKind.MONTH, first, today.withDayOfMonth(today.lengthOfMonth()), first.minusMonths(1), true,
            projectFrom = first.plusDays(6),
        )
    }
    TimeFilter.YTD -> {
        val first = today.withDayOfYear(1)
        PacePeriod(
            PaceKind.YEAR, first, first.plusYears(1).minusDays(1), first.minusYears(1), true,
            stepDays = 7, projectFrom = first.plusMonths(1),
        )
    }
    TimeFilter.SIX_MONTHS -> {
        val start = today.minusMonths(6)
        PacePeriod(PaceKind.HALF_YEAR, start, today, start.minusMonths(6), false, stepDays = 7)
    }
    TimeFilter.ALL -> null
    is TimeFilter.CustomRange -> {
        val start = tf.startDate.toLocalDate()
        val end = maxOf(start, tf.endDate.toLocalDate())
        val days = daysBetween(start, end) + 1
        PacePeriod(PaceKind.RANGE, start, end, start.minusDays(days.toLong()), false, stepDays = if (days > 62) 7 else 1)
    }
}

/**
 * Spending added up over a [PacePeriod]: [current] from its start to today (or its end), and
 * [previous] over the whole comparison period, one running total per point. [previous] is empty
 * when nothing was spent then; [changePct] is null then too, or when the period isn't compared.
 * "This time last month" counts the days gone by, capped at the shorter period: on 31 May, 1–30 May
 * against 1–30 April.
 */
data class PaceSummary(
    val current: List<Double>,
    val previous: List<Double>,
    val spent: Double,
    val changePct: Double?,
)

fun computePace(daily: List<DailyTotal>, p: PacePeriod, today: LocalDate): PaceSummary {
    val byDay = HashMap<LocalDate, Double>()
    for (d in daily) {
        val date = LocalDate.parse(d.day)
        byDay[date] = (byDay[date] ?: 0.0) + d.total
    }
    fun running(from: LocalDate, to: LocalDate): List<Double> {
        if (to.isBefore(from)) return emptyList()
        val sums = DoubleArray((daysBetween(from, to) + p.stepDays) / p.stepDays)
        for ((date, v) in byDay) if (!date.isBefore(from) && !date.isAfter(to)) sums[daysBetween(from, date) / p.stepDays] += v
        var run = 0.0
        return sums.map { run += it; run }
    }
    fun sum(from: LocalDate, to: LocalDate) =
        byDay.entries.sumOf { (date, v) -> if (!date.isBefore(from) && !date.isAfter(to)) v else 0.0 }

    val current = running(p.start, minOf(today, p.end))
    val previous = running(p.compareStart, p.start.minusDays(1))
    val spent = current.lastOrNull() ?: 0.0
    if ((previous.lastOrNull() ?: 0.0) <= 0.0) return PaceSummary(current, emptyList(), spent, null)
    val (now, then) = if (p.sameTime) {
        val n = minOf(daysBetween(p.start, minOf(today, p.end)) + 1, daysBetween(p.compareStart, p.start)).toLong()
        sum(p.start, p.start.plusDays(n - 1)) to sum(p.compareStart, p.compareStart.plusDays(n - 1))
    } else spent to previous.last()
    val change = if (p.compare && then > 0.0) (now - then) / then * 100.0 else null
    return PaceSummary(current, previous, spent, change)
}

/**
 * Where the period's spending is heading: spent so far, plus the regular payments still due, plus a
 * typical day for each day left. The typical day is the median of the finished days (today isn't
 * over), so one big payment such as rent doesn't pull it up. Null before [PacePeriod.projectFrom].
 */
fun projectPeriodEnd(daily: List<DailyTotal>, p: PacePeriod, today: LocalDate, spent: Double, stillDue: Double): Double? {
    val from = p.projectFrom ?: return null
    if (today.isBefore(from) || today.isAfter(p.end)) return null
    val byDay = HashMap<LocalDate, Double>()
    for (d in daily) {
        val date = LocalDate.parse(d.day)
        byDay[date] = (byDay[date] ?: 0.0) + d.total
    }
    val finished = List(daysBetween(p.start, today)) { byDay[p.start.plusDays(it.toLong())] ?: 0.0 }
    return spent + stillDue + median(finished) * daysBetween(today, p.end)
}

/**
 * The regular payments still to come in the period: the rest of it after today, or today itself for
 * the one-day period. A rolling period is complete, so nothing is due in it.
 */
fun stillDue(recurring: List<RecurringPayment>, p: PacePeriod, today: LocalDate): Double = when (p.kind) {
    PaceKind.DAY -> dueDates(recurring, today, today)
    PaceKind.WEEK, PaceKind.MONTH, PaceKind.YEAR -> dueDates(recurring, today.plusDays(1), p.end)
    PaceKind.HALF_YEAR, PaceKind.RANGE -> emptyList()
}.sumOf { it.payment.typicalAmount }

/** Too early in the period to project: before [PacePeriod.projectFrom]. */
fun PacePeriod.early(today: LocalDate): Boolean = projectFrom?.let { today.isBefore(it) } ?: false
