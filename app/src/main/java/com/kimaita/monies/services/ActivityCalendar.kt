package com.kimaita.monies.services

import com.kimaita.monies.data.dao.dtos.DailyTotal
import com.kimaita.monies.data.dao.dtos.WeekdayCount
import com.kimaita.monies.models.analytics.RecurringPayment
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** One square of the month calendar. [shade] is 0 for no spending, else 0.32..0.95 of the primary colour. */
data class CalendarDay(
    val date: LocalDate,
    val inMonth: Boolean,
    val future: Boolean,
    val spent: Double,
    val shade: Float,
    /** A category naming the day ("Rent") when its biggest payment is a quarter or more of the month. */
    val note: String? = null,
)

/**
 * A month as Monday-first weeks, with the days of the weeks either side so every row is full.
 * [run] is the line drawn under days: this month's current streak, or a past month's longest run.
 */
data class MonthCalendar(
    val month: YearMonth,
    val current: Boolean,
    val weeks: List<List<CalendarDay>>,
    val spent: Double,
    /** Days of the month up to today that had spending, and how many days that is out of. */
    val spendDays: Int,
    val daysSoFar: Int,
    /** Days of the month before today with nothing spent. */
    val noSpendDays: List<LocalDate>,
    val run: ClosedRange<LocalDate>?,
    val longestRun: Int,
    val dueDays: Set<LocalDate>,
)

/** The streak running now (ending today, or yesterday while today has nothing yet) and the longest in [byDay]. */
data class Streak(val current: Int, val record: Int, val run: ClosedRange<LocalDate>?)

fun streakNow(byDay: Map<LocalDate, Double>, today: LocalDate): Streak {
    val spent = { d: LocalDate -> (byDay[d] ?: 0.0) > 0.0 }
    val end = if (spent(today)) today else today.minusDays(1)
    var start = end
    while (spent(start)) start = start.minusDays(1)
    val current = (end.toEpochDay() - start.toEpochDay()).toInt()
    var record = 0
    var run = 0
    byDay.keys.minOrNull()?.let { first ->
        var d = first
        while (!d.isAfter(today)) {
            run = if (spent(d)) run + 1 else 0
            record = maxOf(record, run)
            d = d.plusDays(1)
        }
    }
    return Streak(current, maxOf(record, current), if (current > 0) start.plusDays(1)..end else null)
}

/** The first and last days the calendar for [month] shows: whole Monday-to-Sunday weeks. */
fun calendarRange(month: YearMonth): ClosedRange<LocalDate> =
    month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))..
            month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))

/** How strongly a day is shaded: square root so small days still show, 0.32..0.95 of the primary colour. */
fun dayShade(spent: Double, max: Double): Float =
    if (spent <= 0.0 || max <= 0.0) 0f else (0.32 + 0.63 * sqrt((spent / max).coerceIn(0.0, 1.0))).toFloat()

/**
 * The calendar for [month] from the daily spending totals of [calendarRange] ([daily]). The current
 * month draws [streak]'s run; a past month its own longest run. [biggest] is the month's largest
 * payment out (day, amount, category), which names its day when a quarter or more of the month.
 */
fun monthCalendar(
    month: YearMonth,
    daily: List<DailyTotal>,
    today: LocalDate,
    streak: Streak?,
    dueDays: Set<LocalDate> = emptySet(),
    biggest: Triple<LocalDate, Double, String?>? = null,
): MonthCalendar {
    val byDay = daily.associate { LocalDate.parse(it.day) to it.total }
    val range = calendarRange(month)
    val inMonth = { d: LocalDate -> YearMonth.from(d) == month }
    val max = byDay.filterKeys { inMonth(it) }.values.maxOrNull() ?: 0.0
    val spent = byDay.filterKeys { inMonth(it) && !it.isAfter(today) }.values.sum()
    val noteDay = biggest?.takeIf { (d, amount, cat) -> cat != null && inMonth(d) && spent > 0 && amount >= spent / 4 }

    val days = generateSequence(range.start) { it.plusDays(1) }.takeWhile { !it.isAfter(range.endInclusive) }
        .map { d ->
            val future = d.isAfter(today)
            val s = if (future) 0.0 else byDay[d] ?: 0.0
            CalendarDay(d, inMonth(d), future, s, dayShade(s, max), noteDay?.takeIf { it.first == d }?.third)
        }.toList()

    val monthDays = days.filter { it.inMonth && !it.future }
    val current = month == YearMonth.from(today)
    var longest = 0
    var best: ClosedRange<LocalDate>? = null
    var runStart: LocalDate? = null
    monthDays.forEach { d ->
        if (d.spent > 0) {
            if (runStart == null) runStart = d.date
            val len = (d.date.toEpochDay() - runStart!!.toEpochDay() + 1).toInt()
            if (len > longest) { longest = len; best = runStart!!..d.date }
        } else runStart = null
    }
    return MonthCalendar(
        month = month,
        current = current,
        weeks = days.chunked(7),
        spent = spent,
        spendDays = monthDays.count { it.spent > 0 },
        daysSoFar = monthDays.size,
        noSpendDays = monthDays.filter { it.spent <= 0 && it.date.isBefore(today) }.map { it.date },
        run = if (current) streak?.run else best,
        longestRun = longest,
        dueDays = if (current) dueDays else emptySet(),
    )
}

/** "**9-day** spending streak. Your record is 13." */
fun streakSentence(s: Streak): String = when {
    s.current == 0 -> "No spending streak right now. Your record is ${s.record}."
    s.current >= s.record -> "**${s.current}-day** spending streak, your longest yet."
    else -> "**${s.current}-day** spending streak. Your record is ${s.record}."
}

/** "1 no-spend day this month: the 5th." */
fun noSpendSentence(days: List<LocalDate>): String = when (days.size) {
    0 -> "No no-spend days this month yet."
    1 -> "1 no-spend day this month: the ${ordinal(days[0].dayOfMonth)}."
    2, 3 -> "${days.size} no-spend days this month: the " +
            days.dropLast(1).joinToString(", ") { ordinal(it.dayOfMonth) } + " and ${ordinal(days.last().dayOfMonth)}."
    else -> "${days.size} no-spend days this month."
}

/** "Spending on **26 of 31 days**. Longest run: 7." */
fun pastMonthSentence(c: MonthCalendar): String =
    "Spending on **${c.spendDays} of ${c.daysSoFar} days**." + if (c.longestRun > 1) " Longest run: ${c.longestRun}." else ""

/** What a no-spend day's box says about it. */
fun noSpendDayNote(day: LocalDate, c: MonthCalendar): String? {
    val only = c.noSpendDays.size == 1 && c.noSpendDays[0] == day
    val streakFrom = c.current && c.run?.start == day.plusDays(1)
    return listOfNotNull(
        "Your only no-spend day this month.".takeIf { only },
        "Your streak started the next day.".takeIf { streakFrom },
    ).joinToString(" ").ifEmpty { null }
}

fun ordinal(n: Int): String = n.toString() + when {
    n % 100 in 11..13 -> "th"
    n % 10 == 1 -> "st"
    n % 10 == 2 -> "nd"
    n % 10 == 3 -> "rd"
    else -> "th"
}

private val weekdayPlural = listOf("Sundays", "Mondays", "Tuesdays", "Wednesdays", "Thursdays", "Fridays", "Saturdays")

/** "**Fridays** are busiest, **Sundays** quietest." Null with no payments; weekday 0 = Sunday. */
fun weekdaySentence(counts: List<WeekdayCount>): String? {
    val byDay = (0..6).map { d -> counts.firstOrNull { it.weekday == d }?.count ?: 0 }
    if (byDay.sum() == 0) return null
    val busiest = byDay.indices.maxBy { byDay[it] }
    val quietest = byDay.indices.minBy { byDay[it] }
    if (byDay[busiest] == byDay[quietest]) return "Payments spread **evenly** across the week."
    return "**${weekdayPlural[busiest]}** are busiest, **${weekdayPlural[quietest]}** quietest."
}

private val slotPhrases = listOf(
    "in the **morning, 5 am to 12 pm**", "in the **afternoon, 12 to 5 pm**",
    "in the **evening, 5 to 10 pm**", "at **night, 10 pm to 5 am**",
)

/** "Most happen in the **evening, 5 to 10 pm**." from payment counts per slot (0 morning .. 3 night). */
fun timeOfDaySentence(slotCounts: List<Int>): String? {
    if (slotCounts.sum() < 5) return null
    return "Most happen ${slotPhrases[slotCounts.indices.maxBy { slotCounts[it] }]}."
}

/** "137 payments, about 4.6 a day." */
fun paymentsPerDaySentence(total: Int, days: Int): String {
    val perDay = total.toDouble() / days.coerceAtLeast(1)
    val rate = if (perDay >= 10) perDay.roundToInt().toString() else "%.1f".format(perDay)
    return "$total ${if (total == 1) "payment" else "payments"}, about $rate a day."
}

/** A fraction in words where one is close: half, a third, a quarter…, else "n%". */
fun shareWords(f: Double): String {
    val words = listOf(1.0 to "all", 0.75 to "three quarters", 2.0 / 3 to "two thirds", 0.5 to "half", 1.0 / 3 to "a third", 0.25 to "a quarter")
    words.firstOrNull { (v, _) -> kotlin.math.abs(f - v) <= 0.03 }?.let { return it.second }
    return "${(f * 100).roundToInt()}%"
}

/** "About **29,900 a month**, half of what goes out." The share is left out without spending to compare. */
fun recurringSummary(recurring: List<RecurringPayment>, monthlyOut: Double?, format: (Double) -> String): String {
    val total = monthlyRecurringTotal(recurring)
    val rounded = Math.round(total / 100.0) * 100.0
    val share = monthlyOut?.takeIf { it > 0 }?.let { ", ${shareWords(total / it)} of what goes out" } ?: ""
    return "About **${format(rounded)} a month**$share."
}

/** Two letters for an avatar: the first of the first two words ("Baraka Mart" → "BM"). */
fun initials(name: String): String {
    val words = name.split(' ', '-', '.').filter { it.isNotBlank() && it[0].isLetterOrDigit() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(2).uppercase()
        else -> (words[0].take(1) + words[1].take(1)).uppercase()
    }
}
