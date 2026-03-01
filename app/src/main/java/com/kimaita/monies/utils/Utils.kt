package com.kimaita.monies.utils

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

fun currencyToFloat(input: String): Float? {
    val nf = NumberFormat.getInstance(Locale.US)
    return nf.parse(input)?.toFloat()
}

fun parseDateTime(dateTime: String): Instant {
    val formatter = DateTimeFormatter.ofPattern("d/M/uu h:m a")
    val localDateTime = LocalDateTime.parse(dateTime, formatter)
    return localDateTime.toInstant(ZoneOffset.of("+3"))
}

fun parseDateTime(
    dateStr: String?, timeStr: String?, fallbackTimestamp: Long
): LocalDateTime {
    if (dateStr != null && timeStr != null) {
        try {
            val combinedString = "$dateStr $timeStr"
            val formatter = DateTimeFormatter.ofPattern("d/M/yy h:mm a", Locale.ENGLISH)
            return LocalDateTime.parse(combinedString, formatter)
        } catch (e: Exception) {
            // Fallback if parsing fails
        }
    }
    return LocalDateTime.ofInstant(
        Instant.ofEpochMilli(fallbackTimestamp), ZoneId.systemDefault()
    )
}

/**
 * Safely attempts to get a named group value.
 * Returns null if:
 * 1. The group name does not exist in the Regex pattern.
 * 2. The group exists but did not match (optional group).
 */
fun MatchNamedGroupCollection.getSafeValue(name: String): String? {
    return try {
        this[name]?.value
    } catch (e: IllegalArgumentException) {
        null
    }
}