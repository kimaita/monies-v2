package com.kimaita.monies.ui.util

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong


private val intFormat: NumberFormat = NumberFormat.getIntegerInstance()

fun formatAmount(v: Double): String = intFormat.format(v.roundToLong())
fun formatKes(v: Double): String = "KES " + intFormat.format(v.roundToLong())
fun formatSignedKes(v: Double): String = (if (v >= 0) "+" else "-") + formatKes(abs(v))

val dayMonthFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

private val dayTimeFmt = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a", Locale.ENGLISH)
private val dayTimeYearFmt = DateTimeFormatter.ofPattern("EEE d MMM yyyy, h:mm a", Locale.ENGLISH)

/** "Mon 3 Nov, 9:12 AM"; the year is added when it is not [thisYear]. */
fun formatDayTime(t: LocalDateTime, thisYear: Int = LocalDate.now().year): String =
    t.format(if (t.year == thisYear) dayTimeFmt else dayTimeYearFmt)

/** Material date pickers speak in UTC midnights; these convert to and from a calendar day. */
fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
fun Long.toUtcDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/** A figure on a chart: 950, 9.4K, 47.4K, 1.2M. */
fun formatShortAmount(v: Double): String {
    val a = abs(v)
    return when {
        a >= 999_950 -> trim(String.format(Locale.US, "%.1f", v / 1_000_000)) + "M"
        a >= 1_000 -> trim(String.format(Locale.US, "%.1f", v / 1_000)) + "K"
        else -> intFormat.format(v.roundToLong())
    }
}

private fun trim(s: String): String =
    if ('.' in s) s.trimEnd('0').trimEnd('.') else s