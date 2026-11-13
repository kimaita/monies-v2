package com.kimaita.monies.utils

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE_TIME_FORMATS = listOf(
    DateTimeFormatter.ofPattern("d/M/yy h:mm a", Locale.ENGLISH),
    DateTimeFormatter.ofPattern("d/M/yyyy H:mm", Locale.ENGLISH),
)

fun parseDateTime(
    dateStr: String?, timeStr: String?, fallbackTimestamp: Long
): LocalDateTime {
    if (dateStr != null && timeStr != null) {
        val combinedString = "$dateStr $timeStr"
        for (formatter in DATE_TIME_FORMATS) {
            try {
                return LocalDateTime.parse(combinedString, formatter)
            } catch (e: Exception) {
                // Try the next format
            }
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