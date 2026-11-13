package com.kimaita.monies.models.export

import com.kimaita.monies.services.TypeGroup


data class ExportFilters(
    val startDate: String? = null,
    val endDate: String? = null,
    val subjectId: List<Long>? = null,
    val categoryId: List<Int>? = null,
    val transactionType: List<Int>? = null,
    val account: Int? = null,
    val natureFilter: Boolean? = null,
    /** The Transactions search box text, matched the same way as in the list. */
    val search: String? = null,
)


data class NamedRef<ID>(val id: ID, val name: String)

data class FilterOptions(
    val categories: List<NamedRef<Int>> = emptyList(),
    val accounts: List<NamedRef<Int>> = emptyList(),
    val types: List<TypeGroup> = emptyList(),
)
