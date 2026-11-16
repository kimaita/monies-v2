package com.kimaita.monies.services

import com.kimaita.monies.models.TimeFilter
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The stretch just before the chip's period, of the same length, as `[start, end)` ISO dates in the
 * shape `getDatesForFilter` gives: YTD compares with the same days last year. Null for ALL.
 */
fun previousRange(tf: TimeFilter, today: LocalDate): Pair<LocalDate, LocalDate>? = when (tf) {
    TimeFilter.DAY -> today.minusDays(1) to today
    TimeFilter.WEEK -> today.minusDays(13) to today.minusDays(6)
    TimeFilter.MONTH -> today.minusMonths(2) to today.minusMonths(1)
    TimeFilter.SIX_MONTHS -> today.minusMonths(12) to today.minusMonths(6)
    TimeFilter.YTD -> today.withDayOfYear(1).minusYears(1) to today.minusYears(1).plusDays(1)
    TimeFilter.ALL -> null
    is TimeFilter.CustomRange -> {
        val start = tf.startDate.toLocalDate()
        val days = ChronoUnit.DAYS.between(start, tf.endDate.toLocalDate()).coerceAtLeast(1)
        start.minusDays(days) to start
    }
}

/** The first day of the chip's period. Null for ALL. */
fun periodStart(tf: TimeFilter, today: LocalDate): LocalDate? = when (tf) {
    TimeFilter.DAY -> today
    TimeFilter.WEEK -> today.minusDays(6)
    TimeFilter.MONTH -> today.minusMonths(1)
    TimeFilter.SIX_MONTHS -> today.minusMonths(6)
    TimeFilter.YTD -> today.withDayOfYear(1)
    TimeFilter.ALL -> null
    is TimeFilter.CustomRange -> tf.startDate.toLocalDate()
}

/**
 * Whether recorded history reaches back over the whole comparison period; when it doesn't, how many
 * days it needs and how many there are.
 */
data class History(val enough: Boolean, val needDays: Int, val haveDays: Int)

fun historyFor(tf: TimeFilter, today: LocalDate, firstDay: LocalDate?): History? {
    val prev = previousRange(tf, today) ?: return null
    val start = periodStart(tf, today) ?: return null
    val need = ChronoUnit.DAYS.between(prev.first, prev.second).toInt()
    val have = firstDay?.let { ChronoUnit.DAYS.between(it, start).toInt().coerceIn(0, need) } ?: 0
    return History(firstDay != null && !firstDay.isAfter(prev.first), need, have)
}

/** "Needs 30 days of history before this period. You have 18." in days up to a month, else months. */
fun historyNote(h: History): String =
    if (h.needDays <= 31) "Needs ${h.needDays} days of history before this period. You have ${h.haveDays}."
    else "Needs ${(h.needDays / DAYS_PER_MONTH).roundToInt()} months of history before this period. You have " +
            "${(h.haveDays / DAYS_PER_MONTH).toInt()}."

/** How a figure moved, for its pill: a move of [PILL_PCT]% or more is [UP] or [DOWN], less is [FLAT]. */
enum class Move { UP, DOWN, FLAT, NEW }

const val PILL_PCT = 10.0
const val BIG_MOVE_KES = 500.0

data class Change(val now: Double, val before: Double) {
    val delta: Double get() = now - before
    /** Percent change; null when there was nothing before. */
    val pct: Double? get() = if (before > 0) delta / before * 100 else null
    val move: Move
        get() = when {
            before <= 0 && now > 0 -> Move.NEW
            (pct ?: 0.0) >= PILL_PCT -> Move.UP
            (pct ?: 0.0) <= -PILL_PCT -> Move.DOWN
            else -> Move.FLAT
        }

    /** "+18%", "−23%", "same" (rounds to 0), or "new". */
    val pill: String
        get() = when {
            move == Move.NEW -> "new"
            else -> {
                val p = (pct ?: 0.0).roundToInt()
                if (p == 0) "same" else (if (p > 0) "+" else "−") + abs(p) + "%"
            }
        }

    /** Big enough to list under Biggest changes: 10% and KES 500 or more, or new and KES 500. */
    val big: Boolean get() = abs(delta) >= BIG_MOVE_KES && move != Move.FLAT
}

/** Each label's change, biggest moves (by amount) first; labels only in [before] count as going to 0. */
fun changes(now: Map<String, Double>, before: Map<String, Double>): List<Pair<String, Change>> =
    (now.keys + before.keys).distinct()
        .map { it to Change(now[it] ?: 0.0, before[it] ?: 0.0) }
        .sortedByDescending { abs(it.second.delta) }

/** "2,990 more than the previous 30 days." */
fun totalChangeSentence(c: Change, previous: String, format: (Double) -> String): String = when {
    c.delta > 0 -> "${format(c.delta)} more than $previous."
    c.delta < 0 -> "${format(-c.delta)} less than $previous."
    else -> "The same as $previous."
}

/** What the comparison period is called after "than": "the previous 30 days". */
fun previousPhrase(tf: TimeFilter): String = when (tf) {
    TimeFilter.DAY -> "yesterday"
    TimeFilter.WEEK -> "the previous 7 days"
    TimeFilter.MONTH -> "the previous 30 days"
    TimeFilter.SIX_MONTHS -> "the 6 months before"
    TimeFilter.YTD -> "the same days last year"
    TimeFilter.ALL -> ""
    is TimeFilter.CustomRange -> "the days before"
}

/** The comparison bars' two labels: ("Last 30 days", "30 days before"). */
fun compareLabels(tf: TimeFilter): Pair<String, String> = when (tf) {
    TimeFilter.DAY -> "Today" to "Yesterday"
    TimeFilter.WEEK -> "Last 7 days" to "7 days before"
    TimeFilter.MONTH -> "Last 30 days" to "30 days before"
    TimeFilter.SIX_MONTHS -> "Last 6 months" to "6 months before"
    TimeFilter.YTD -> "This year" to "Last year"
    TimeFilter.ALL -> "All time" to ""
    is TimeFilter.CustomRange -> "These dates" to "Before"
}

/** One tile of a treemap, in the units of the area it was laid out in. */
data class Tile(val index: Int, val x: Float, val y: Float, val w: Float, val h: Float)

/**
 * Squarified treemap (Bruls, Huizing, van Wijk): [values], largest first, laid into [width] × [height]
 * in rows along the shorter side, each row kept while adding a tile makes its worst aspect ratio no
 * worse. Tiles come back in the order of [values]; zero and negative values get none.
 */
fun squarify(values: List<Double>, width: Float, height: Float): List<Tile> {
    val items = values.withIndex().filter { it.value > 0 }.sortedByDescending { it.value }
    val total = items.sumOf { it.value }
    if (total <= 0 || width <= 0 || height <= 0) return emptyList()
    val scale = width * height / total
    val out = mutableListOf<Tile>()
    var x = 0f
    var y = 0f
    var w = width
    var h = height
    var i = 0
    fun worst(row: List<Double>, side: Float): Double {
        val s = row.sum()
        val big = row.max()
        val small = row.min()
        val s2 = s * s
        val side2 = side.toDouble() * side
        return max(side2 * big / s2, s2 / (side2 * small))
    }
    while (i < items.size) {
        val side = min(w, h)
        val row = mutableListOf(items[i].value * scale)
        var j = i + 1
        while (j < items.size) {
            val next = row + items[j].value * scale
            if (worst(next, side) > worst(row, side)) break
            row.add(items[j].value * scale); j++
        }
        val rowArea = row.sum().toFloat()
        if (w >= h) {
            val rowW = rowArea / h
            var cy = y
            for (k in row.indices) {
                val th = (row[k] / rowW).toFloat()
                out.add(Tile(items[i + k].index, x, cy, rowW, th)); cy += th
            }
            x += rowW; w -= rowW
        } else {
            val rowH = rowArea / w
            var cx = x
            for (k in row.indices) {
                val tw = (row[k] / rowH).toFloat()
                out.add(Tile(items[i + k].index, cx, y, tw, rowH)); cx += tw
            }
            y += rowH; h -= rowH
        }
        i = j
    }
    return out.sortedBy { it.index }
}
