package com.kimaita.monies.utils

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

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
            }
        }
    }
    return LocalDateTime.ofInstant(
        Instant.ofEpochMilli(fallbackTimestamp), ZoneId.systemDefault()
    )
}

private val NAMED_GROUP = Regex("""\(\?<([a-zA-Z][a-zA-Z0-9]*)>""")
private val groupNamesByPattern = ConcurrentHashMap<String, Set<String>>()

/** The named groups [Regex.pattern] declares, read once per pattern. */
private fun Regex.groupNames(): Set<String> = groupNamesByPattern.getOrPut(pattern) {
    NAMED_GROUP.findAll(pattern).mapTo(HashSet()) { it.groupValues[1] }
}

/**
 * The value of a named group of this match of [regex]. Null when [regex] has no group of that name
 * or the group did not match. The name is checked first because asking a match for a group it does
 * not have throws, and an exception for every missing field is slow on low-end phones.
 */
fun MatchResult.groupValue(regex: Regex, name: String): String? =
    if (name in regex.groupNames()) (groups as? MatchNamedGroupCollection)?.get(name)?.value else null