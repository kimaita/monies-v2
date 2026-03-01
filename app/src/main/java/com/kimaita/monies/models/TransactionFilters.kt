package com.kimaita.monies.models

import com.kimaita.monies.ui.components.TimeFilter
import java.time.format.DateTimeFormatter

enum class SortOrder {
    DATE_DESC, DATE_ASC, AMOUNT_DESC, AMOUNT_ASC
}

val isoDateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")


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
)