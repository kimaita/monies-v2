package com.kimaita.monies.services


import com.kimaita.monies.data.dao.dtos.ActivityCell
import com.kimaita.monies.data.dao.dtos.CategorySpending
import com.kimaita.monies.data.dao.dtos.DailyTotal
import com.kimaita.monies.data.dao.dtos.WeekdayCount
import com.kimaita.monies.data.database.models.TransactionType
import com.kimaita.monies.models.export.NamedRef
import java.time.LocalDate
import java.time.YearMonth

data class StreakSummary(
    val currentStreak: Int,
    val longestActive: Int,
    val longestNoSpend: Int,
    val intensities: Map<LocalDate, Double>,
)

data class RhythmStats(
    val busiestWeekday: Int?,   // 0=Sun..6=Sat
    val mostActiveSlot: Int?,
    val avgPerDay: Double,
    val longestQuietDays: Int,
)

/** Active = a day with any spending. Walks the inclusive [start, end] range. */
fun computeStreaks(daily: List<DailyTotal>, start: LocalDate, end: LocalDate): StreakSummary {
    val byDay = daily.associate { LocalDate.parse(it.day) to it.total }
    var longestActive = 0;
    var longestNoSpend = 0
    var runActive = 0;
    var runIdle = 0;
    var current = 0
    var d = start
    while (!d.isAfter(end)) {
        val active = (byDay[d] ?: 0.0) > 0.0
        if (active) {
            runActive++; runIdle = 0; longestActive = maxOf(longestActive, runActive)
        } else {
            runIdle++; runActive = 0; longestNoSpend = maxOf(longestNoSpend, runIdle)
        }
        d = d.plusDays(1)
    }
    // current streak = consecutive active days ending at `end`
    d = end
    while (!d.isBefore(start) && (byDay[d] ?: 0.0) > 0.0) {
        current++; d = d.minusDays(1)
    }
    return StreakSummary(current, longestActive, longestNoSpend, byDay)
}

fun computeRhythm(
    weekdays: List<WeekdayCount>,
    activity: List<ActivityCell>,
    totalCount: Int,
    rangeDays: Int,
    longestNoSpend: Int,
): RhythmStats {
    val busiest = weekdays.maxByOrNull { it.count }?.weekday
    val slot = activity.groupBy { it.slot }
        .mapValues { (_, cells) -> cells.sumOf { it.count } }
        .maxByOrNull { it.value }?.key
    val avg = if (rangeDays > 0) totalCount.toDouble() / rangeDays else 0.0
    return RhythmStats(busiest, slot, avg, longestNoSpend)
}

/**
 * Cumulative spending by day of month. Index 0 is day 1.
 * [lastMonth] is empty when last month has no spending; [changePct] is then null too.
 */
data class PaceSummary(
    val thisMonth: List<Double>,   // day 1 .. today
    val lastMonth: List<Double>,   // day 1 .. last day of last month
    val spent: Double,             // spent so far this month
    val changePct: Double?,        // this month vs last month, both at min(today's day, last month's length)
)

fun computePace(daily: List<DailyTotal>, today: LocalDate): PaceSummary {
    val thisYm = YearMonth.from(today)
    val lastYm = thisYm.minusMonths(1)
    val thisDays = DoubleArray(today.dayOfMonth)
    val lastDays = DoubleArray(lastYm.lengthOfMonth())
    for (d in daily) {
        val date = LocalDate.parse(d.day)
        when (YearMonth.from(date)) {
            thisYm -> if (date.dayOfMonth <= thisDays.size) thisDays[date.dayOfMonth - 1] += d.total
            lastYm -> lastDays[date.dayOfMonth - 1] += d.total
        }
    }
    fun cumulative(a: DoubleArray): List<Double> {
        var run = 0.0
        return a.map { run += it; run }
    }
    val thisCum = cumulative(thisDays)
    val lastCum = cumulative(lastDays)
    if (lastCum.last() <= 0.0) return PaceSummary(thisCum, emptyList(), thisCum.last(), null)

    val at = minOf(today.dayOfMonth, lastCum.size) - 1
    val change = lastCum[at].takeIf { it > 0.0 }?.let { (thisCum[at] - it) / it * 100.0 }
    return PaceSummary(thisCum, lastCum, thisCum.last(), change)
}

/** The line Analytics is showing: its name, or "All accounts"; null when there is a single line and nothing to tell apart. */
fun scopeLabel(selected: Int?, lines: List<Pair<Int, String>>): String? =
    if (lines.size < 2) null else lines.firstOrNull { it.first == selected }?.second ?: "All accounts"

/** A breakdown row: the label shown, its amount, and the ids tapping it narrows the entries to. */
data class SliceValue(
    val label: String,
    val value: Float,
    val categoryIds: List<Int> = emptyList(),
    val typeIds: List<Int> = emptyList(),
)

fun categorySlices(rows: List<CategorySpending>, categories: List<NamedRef<Int>>): List<SliceValue> =
    rows.map { r ->
        SliceValue(r.name, r.total.toFloat(), categoryIds = categories.filter { it.name == r.name }.map { it.id })
    }.sortedByDescending { it.value }

/** Rows by type, under the type's display name. Types sharing a display name ("Airtime") become one row. */
fun typeSlices(rows: List<CategorySpending>, types: List<TransactionType>): List<SliceValue> {
    val byName = types.associateBy { it.name }
    return rows.groupBy { byName[it.name]?.finalDisplayName ?: it.name }
        .map { (label, group) ->
            SliceValue(
                label, group.sumOf { it.total }.toFloat(),
                typeIds = group.mapNotNull { byName[it.name]?.id },
            )
        }
        .sortedByDescending { it.value }
}

/** Tapping the selected row clears the selection. */
fun toggleSlice(current: String?, tapped: String?): String? = if (current == tapped) null else tapped

/**
 * The (categories, types) the entries below the breakdown are narrowed to: the selected row's ids,
 * else the filters already in force. A selection whose row has gone narrows nothing.
 */
fun sliceFilter(
    slices: List<SliceValue>, selected: String?, categories: List<Int>?, types: List<Int>?,
): Pair<List<Int>?, List<Int>?> {
    val s = slices.firstOrNull { it.label == selected }
    return (s?.categoryIds?.takeIf { it.isNotEmpty() } ?: categories) to
            (s?.typeIds?.takeIf { it.isNotEmpty() } ?: types)
}
