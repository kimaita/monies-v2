package com.kimaita.monies.services


import com.kimaita.monies.data.dao.dtos.ActivityCell
import com.kimaita.monies.data.dao.dtos.BalanceBucket
import com.kimaita.monies.data.dao.dtos.CategorySpending
import com.kimaita.monies.data.dao.dtos.LineBalanceBucket
import com.kimaita.monies.data.dao.dtos.DailyTotal
import com.kimaita.monies.data.dao.dtos.WeekdayCount
import com.kimaita.monies.data.database.models.TransactionType
import com.kimaita.monies.models.TimeFilter
import com.kimaita.monies.models.analytics.BalancePoint
import com.kimaita.monies.models.export.NamedRef
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

data class StreakSummary(
    val currentStreak: Int,
    val longestActive: Int,
    val longestNoSpend: Int,
    val intensities: Map<LocalDate, Double>,
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
    d = end
    while (!d.isBefore(start) && (byDay[d] ?: 0.0) > 0.0) {
        current++; d = d.minusDays(1)
    }
    return StreakSummary(current, longestActive, longestNoSpend, byDay)
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
    /** How many payments the row adds up. */
    val count: Int = 0,
)

fun categorySlices(rows: List<CategorySpending>, categories: List<NamedRef<Int>>): List<SliceValue> =
    rows.map { r ->
        SliceValue(r.name, r.total.toFloat(), categories.filter { it.name == r.name }.map { it.id }, count = r.count)
    }.sortedByDescending { it.value }

/** The types that share one display name ("Airtime"), shown as a single choice. */
data class TypeGroup(val label: String, val ids: List<Int>)

fun typeGroups(types: List<TransactionType>): List<TypeGroup> =
    types.groupBy { it.finalDisplayName }.map { (label, group) -> TypeGroup(label, group.map { it.id }) }

/** Every id of the group is selected. */
fun TypeGroup.isSelected(selected: Set<Int>): Boolean = ids.isNotEmpty() && selected.containsAll(ids)

/** Selects all of the group's ids, or deselects them when they are all selected already. */
fun Set<Int>.toggleGroup(group: TypeGroup): Set<Int> =
    if (group.isSelected(this)) this - group.ids.toSet() else this + group.ids

/** How many choices the selected ids make up: a group counts once however many of its ids are in. */
fun countTypeChoices(selected: Set<Int>, groups: List<TypeGroup>): Int =
    groups.count { g -> g.ids.any { it in selected } } + selected.count { id -> groups.none { id in it.ids } }

/** Rows by type, under the type's display name. Types sharing a display name ("Airtime") become one row. */
fun typeSlices(rows: List<CategorySpending>, types: List<TransactionType>): List<SliceValue> {
    val labelOf = types.associate { it.name to it.finalDisplayName }
    val idsOf = typeGroups(types).associate { it.label to it.ids }
    return rows.groupBy { labelOf[it.name] ?: it.name }
        .map { (label, group) ->
            SliceValue(label, group.sumOf { it.total }.toFloat(), typeIds = idsOf[label].orEmpty(), count = group.sumOf { it.count })
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

/**
 * For bucket keys that start with the year ("2026-03", "2026-45", "2026-03-14 09"): the year at each
 * bucket where a new one begins, null elsewhere. The first bucket is marked only when the buckets
 * span several years, so one year of data carries no markers.
 */
fun yearStarts(buckets: List<String>): List<String?> {
    val years = buckets.map { it.take(4) }
    val several = years.distinct().size > 1
    return years.mapIndexed { i, y -> if (if (i == 0) several else y != years[i - 1]) y else null }
}

/** At most [max] of [items], evenly picked, always keeping the first and the last. */
fun <T> thin(items: List<T>, max: Int): List<T> {
    if (items.size <= max || max < 2) return items
    return List(max) { i -> items[(i * (items.size - 1).toDouble() / (max - 1)).roundToInt()] }
}

private val dateFmt = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val dateYearFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
private val dateShortYearFmt = DateTimeFormatter.ofPattern("d MMM yy", Locale.ENGLISH)

/**
 * Balance points with dates, from per-day ("2026-03-14") or per-hour ("2026-03-14 09") buckets.
 * The tooltip label is the full date (with the hour for hourly buckets); the axis label is the short
 * date, the hour when every point is on one day, and carries the year when the range spans several.
 */
fun balancePoints(rows: List<BalanceBucket>, maxPoints: Int = 90): List<BalancePoint> {
    val kept = thin(rows, maxPoints)
    if (kept.isEmpty()) return emptyList()
    val dates = kept.map { LocalDate.parse(it.bucket.take(10)) }
    val hourly = kept.first().bucket.length > 10
    val sameDay = dates.first() == dates.last()
    val several = dates.first().year != dates.last().year
    return kept.mapIndexed { i, r ->
        val date = dates[i]
        val hour = if (hourly) r.bucket.substring(11, 13) + ":00" else null
        val label = if (hour != null) "${date.format(dateFmt)} $hour" else date.format(dateYearFmt)
        val axis = when {
            hour != null && sameDay -> hour
            several -> date.format(dateShortYearFmt)
            else -> date.format(dateFmt)
        }
        BalancePoint(label, r.balance, axis, date)
    }
}

/**
 * Fees as a percentage of [expense] (outgoing money excluding transfers and fees), or null when
 * nothing was spent.
 */
fun feeShare(fees: Double, expense: Double): Double? = if (expense > 0.0) fees / expense * 100 else null

private val slotNames = listOf("mornings", "afternoons", "evenings", "nights")
private val slotRanges = listOf("5 am–12 pm", "12–5 pm", "5–10 pm", "10 pm–5 am")

/**
 * One sentence on when the activity is, from the time-of-day cells (slot 0 morning .. 3 night,
 * weekday 0 = Sunday): the busiest slot, and "weekday" or "weekend" when that slot's per-day rate is
 * over 1.5 times the other's. The slot is between `**` marks, to be shown bold. Null when there are
 * fewer than 5 transactions.
 */
fun activeSummary(cells: List<ActivityCell>): String? {
    if (cells.sumOf { it.count } < 5) return null
    val slot = (0..3).maxByOrNull { s -> cells.filter { it.slot == s }.sumOf { it.count } } ?: return null
    val inSlot = cells.filter { it.slot == slot }
    val weekdayRate = inSlot.filter { it.weekday in 1..5 }.sumOf { it.count } / 5.0
    val weekendRate = inSlot.filter { it.weekday == 0 || it.weekday == 6 }.sumOf { it.count } / 2.0
    val name = slotNames[slot]
    val range = slotRanges[slot]
    return when {
        weekdayRate > weekendRate * 1.5 -> "Most active on **weekday $name**, $range."
        weekendRate > weekdayRate * 1.5 -> "Most active on **weekend $name**, $range."
        else -> "Most active in the **$name**, $range."
    }
}

/**
 * The total balance of several lines per bucket: each line's latest balance at or before the end of
 * the bucket, carried forward through buckets where it has no transactions, and added up. A line with
 * no balance yet adds nothing. Rows with a null bucket are the lines' balances before the range; the
 * result has a point for every bucket in which any line has a row.
 */
fun sumLineBalances(rows: List<LineBalanceBucket>): List<BalanceBucket> {
    val current = rows.filter { it.bucket == null }.associate { it.lineId to it.balance.toDouble() }.toMutableMap()
    val inRange = rows.filter { it.bucket != null }
    val byBucket = inRange.groupBy { it.bucket!! }
    return byBucket.keys.sorted().map { b ->
        byBucket.getValue(b).forEach { current[it.lineId] = it.balance.toDouble() }
        BalanceBucket(b, current.values.sum().toFloat())
    }
}

/** The days of [daily] on or after [start]; one query over a wide window serves several shorter ones. */
fun daysFrom(daily: List<DailyTotal>, start: LocalDate): List<DailyTotal> {
    val from = start.toString()
    return daily.filter { it.day >= from }
}

fun bucketFormatFor(tf: TimeFilter): String = when (tf) {
    TimeFilter.DAY -> "%Y-%m-%d %H"
    TimeFilter.WEEK -> "%Y-%m-%d"
    TimeFilter.MONTH -> "%Y-%W"
    TimeFilter.SIX_MONTHS, TimeFilter.YTD, TimeFilter.ALL -> "%Y-%m"
    is TimeFilter.CustomRange -> "%Y-%m-%d"
}

/** Balance points are daily, or hourly for the short ranges. */
fun balanceBucketFormatFor(tf: TimeFilter): String = when (tf) {
    TimeFilter.DAY, TimeFilter.WEEK -> "%Y-%m-%d %H"
    else -> "%Y-%m-%d"
}

private val monthAbbrev =
    arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

fun bucketLabel(bucket: String, tf: TimeFilter): String = when (tf) {
    TimeFilter.DAY -> bucket.substringAfter(" ", bucket) + "h"
    TimeFilter.WEEK, is TimeFilter.CustomRange -> bucket.substringAfterLast("-")
    TimeFilter.MONTH -> "Wk " + bucket.substringAfterLast("-")
    else -> bucket.substringAfterLast("-").toIntOrNull()
        ?.let { monthAbbrev.getOrElse(it - 1) { bucket } } ?: bucket
}

fun daysForFilter(tf: TimeFilter, spanDays: Int?, today: LocalDate = LocalDate.now()): Int = when (tf) {
    TimeFilter.DAY -> 1
    TimeFilter.WEEK -> 7
    TimeFilter.MONTH -> 30
    TimeFilter.SIX_MONTHS -> 182
    TimeFilter.YTD -> today.dayOfYear
    TimeFilter.ALL -> spanDays ?: 1
    is TimeFilter.CustomRange ->
        (ChronoUnit.DAYS.between(
            tf.startDate.toLocalDate(),
            tf.endDate.toLocalDate()
        ) + 1).toInt()
            .coerceAtLeast(1)
}


/**
 * The days a "%Y-%W" bucket covers, as SQLite counts weeks: week 1 starts on the year's first Monday,
 * week 0 is the days before it. Kept within the year. Null for another key.
 */
fun weekDays(bucket: String): ClosedRange<LocalDate>? {
    val year = bucket.take(4).toIntOrNull() ?: return null
    val week = bucket.substringAfter('-', "").toIntOrNull() ?: return null
    val jan1 = LocalDate.of(year, 1, 1)
    val firstMonday = jan1.with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.MONDAY))
    val start = if (week == 0) jan1 else firstMonday.plusWeeks(week - 1L)
    val end = if (week == 0) firstMonday.minusDays(1) else start.plusDays(6)
    return start..minOf(end, LocalDate.of(year, 12, 31))
}

/** "2–8 Nov", or "28 Oct – 3 Nov" across months. */
fun dayRangeLabel(r: ClosedRange<LocalDate>): String =
    if (r.start.month == r.endInclusive.month) "${r.start.dayOfMonth}–${r.endInclusive.format(dateFmt)}"
    else "${r.start.format(dateFmt)} – ${r.endInclusive.format(dateFmt)}"
