package com.kimaita.monies.models

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

enum class SortOrder {
    DATE_DESC, DATE_ASC, AMOUNT_DESC, AMOUNT_ASC
}

val isoDateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

sealed class TimeFilter {
    object DAY : TimeFilter()
    object WEEK : TimeFilter()
    object MONTH : TimeFilter()
    object QUARTER : TimeFilter()
    object SIX_MONTHS : TimeFilter()
    object YTD : TimeFilter()
    object ALL : TimeFilter()
    data class CustomRange(val startDate: LocalDateTime, val endDate: LocalDateTime) : TimeFilter()
}

data class TransactionFilters(
    val timeFilter: TimeFilter = TimeFilter.ALL,
    val typeFilter: Int? = null,
    val subjectFilter: Long? = null,
    val categoryFilter: Int? = null,
    val natureFilter: Boolean? = null,
    val searchQuery: String = "",
    val accountFilter: Int? = null,
    val sortOrder: SortOrder = SortOrder.DATE_DESC,
    val limit: Int? = null,
    val typeFilters: List<Int> = emptyList(),
    val categoryFilters: List<Int> = emptyList(),
    val subjectFilters: List<Long> = emptyList(),

    )

fun getDatesForFilter(filter: TimeFilter): Pair<String?, String?> {
    val today = LocalDate.now()
    val formatter = DateTimeFormatter.ISO_LOCAL_DATE
    return when (filter) {
        TimeFilter.DAY -> today.format(formatter) to today.plusDays(1).format(formatter)

        TimeFilter.WEEK -> today.minusDays(6).format(formatter) to today.plusDays(1)
            .format(formatter)

        TimeFilter.MONTH -> today.minusMonths(1).format(formatter) to today.plusDays(1)
            .format(formatter)

        TimeFilter.QUARTER -> today.minusMonths(3).format(formatter) to today.plusDays(1)
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
