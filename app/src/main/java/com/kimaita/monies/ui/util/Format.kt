package com.kimaita.monies.ui.util

import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs


private val intFormat: NumberFormat = NumberFormat.getIntegerInstance()

fun formatAmount(v: Double): String = intFormat.format(v.toLong())
fun formatKes(v: Double): String = "KES " + intFormat.format(v.toLong())
fun formatSignedKes(v: Double): String = (if (v >= 0) "+" else "-") + formatKes(abs(v))

val dayMonthFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

fun formatCompactAmount(v: Double): String {
    val a = abs(v)
    return when {
        a >= 1_000_000_000 -> trim(String.format(Locale.US, "%.2f", v / 1_000_000_000)) + "B"
        a >= 1_000_000 -> trim(String.format(Locale.US, "%.2f", v / 1_000_000)) + "M"
        a >= 100_000 -> trim(String.format(Locale.US, "%.1f", v / 1_000)) + "K"
        else -> intFormat.format(v.toLong())
    }
}

fun formatCompactKes(v: Double): String = "KES " + formatCompactAmount(v)

private fun trim(s: String): String =
    if ('.' in s) s.trimEnd('0').trimEnd('.') else s