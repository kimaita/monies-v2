package com.kimaita.monies.models

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

enum class SortOrder {
    DATE_DESC, DATE_ASC, AMOUNT_DESC, AMOUNT_ASC
}

sealed class TimeFilter {
    object DAY : TimeFilter()
    object WEEK : TimeFilter()
    object MONTH : TimeFilter()
    object SIX_MONTHS : TimeFilter()
    object YTD : TimeFilter()
    object ALL : TimeFilter()
    data class CustomRange(val startDate: LocalDateTime, val endDate: LocalDateTime) : TimeFilter()
}

data class TransactionFilters(
    val timeFilter: TimeFilter = TimeFilter.ALL,
    val subjectFilter: Long? = null,
    val natureFilter: Boolean? = null,
    val searchQuery: String = "",
    val accountFilter: Int? = null,
    val sortOrder: SortOrder = SortOrder.DATE_DESC,
    val typeFilters: List<Int> = emptyList(),
    val categoryFilters: List<Int> = emptyList(),
    val subjectFilters: List<Long> = emptyList(),
    /** Set while tracing: only transactions from this point on. */
    val since: TraceStart? = null,
)

/**
 * Where a trace starts: at [time], or just after the transaction [afterId] made at [time]. M-PESA
 * gives times to the minute, so rows sharing that minute count only when their id is higher: the
 * rows the list shows above that transaction.
 */
data class TraceStart(val time: LocalDateTime, val afterId: Long? = null) {
    /** [time] as the database stores it, so the anchor's own minute compares equal. */
    val start: String get() = time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
}

/** A trace start from navigation arguments; null when [since] is absent or not a date-time. */
fun traceStartOf(since: String?, afterId: Long?): TraceStart? {
    val time = since?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() } ?: return null
    return TraceStart(time, afterId?.takeIf { it >= 0 })
}

/** The list's bounds: a trace's start, open-ended, takes the place of the time filter. */
fun TransactionFilters.listDates(today: LocalDate = LocalDate.now()): Pair<String?, String?> =
    since?.let { it.start to null } ?: getDatesForFilter(timeFilter, today)

fun getDatesForFilter(filter: TimeFilter, today: LocalDate = LocalDate.now()): Pair<String?, String?> {
    val formatter = DateTimeFormatter.ISO_LOCAL_DATE
    return when (filter) {
        TimeFilter.DAY -> today.format(formatter) to today.plusDays(1).format(formatter)

        TimeFilter.WEEK -> today.minusDays(6).format(formatter) to today.plusDays(1)
            .format(formatter)

        TimeFilter.MONTH -> today.minusMonths(1).format(formatter) to today.plusDays(1)
            .format(formatter)

        TimeFilter.SIX_MONTHS -> today.minusMonths(6).format(formatter) to today.plusDays(1)
            .format(formatter)

        TimeFilter.YTD -> today.year.toString() + "-01-01" to today.plusDays(1).format(formatter)

        TimeFilter.ALL -> null to null

        is TimeFilter.CustomRange -> {
            val start = filter.startDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            val end = filter.endDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            start to end
        }
    }
}
