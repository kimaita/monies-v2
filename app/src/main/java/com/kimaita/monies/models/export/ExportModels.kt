package com.kimaita.monies.models.export

import com.kimaita.monies.models.TransactionFilters
import com.kimaita.monies.models.getDatesForFilter


data class ExportFilters(
    val startDate: String? = null,
    val endDate: String? = null,
    val subjectId: List<Long>? = null,
    val categoryId: List<Int>? = null,
    val transactionType: List<Int>? = null,
    val account: Int? = null,
    val natureFilter: Boolean? = null,
)


data class NamedRef<ID>(val id: ID, val name: String)

data class FilterOptions(
    val subjects: List<NamedRef<Long>> = emptyList(),
    val categories: List<NamedRef<Int>> = emptyList(),
    val accounts: List<NamedRef<Int>> = emptyList(),
    val types: List<NamedRef<Int>> = emptyList(),
)

data class ExportUiState(
    val format: ExportFormat = ExportFormat.CSV,
    val filters: TransactionFilters = TransactionFilters(),
    val options: FilterOptions = FilterOptions(),
    val selectedSubjects: List<NamedRef<Long>> = emptyList(),
    val loadingOptions: Boolean = true,
    val optionsError: String? = null
)
