package com.kimaita.monies.ui.util

import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong


private val intFormat: NumberFormat = NumberFormat.getIntegerInstance()

fun formatAmount(v: Double): String = intFormat.format(v.roundToLong())
fun formatKes(v: Double): String = "KES " + intFormat.format(v.roundToLong())
fun formatSignedKes(v: Double): String = (if (v >= 0) "+" else "-") + formatKes(abs(v))

val dayMonthFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

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