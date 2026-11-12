package com.kimaita.monies.models.export

import android.net.Uri

sealed interface ExportState {
    data object Idle : ExportState
    data class InProgress(val processed: Int, val total: Int?, val format: ExportFormat) : ExportState
    data class Completed(val uri: Uri, val fileName: String, val mime: String, val count: Int) : ExportState
    data class Failed(val message: String) : ExportState
}
