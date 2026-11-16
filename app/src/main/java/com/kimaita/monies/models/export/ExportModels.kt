package com.kimaita.monies.models.export

import android.net.Uri
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
    val types: List<TypeGroup> = emptyList(),
)

enum class ExportFormat(val ext: String, val mime: String, val label: String) {
    CSV("csv", "text/csv", "CSV"),
    JSON("json", "application/json", "JSON"),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "Excel"),
    PDF("pdf", "application/pdf", "PDF"),
}

sealed interface ExportState {
    data object Idle : ExportState
    data class InProgress(val processed: Int, val total: Int?, val format: ExportFormat) : ExportState
    data class Completed(val uri: Uri, val fileName: String, val mime: String, val count: Int) : ExportState
    data class Failed(val message: String) : ExportState
}
