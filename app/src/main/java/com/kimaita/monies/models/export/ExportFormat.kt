package com.kimaita.monies.models.export

enum class ExportFormat(val ext: String, val mime: String, val label: String) {
    CSV("csv", "text/csv", "CSV"),
    JSON("json", "application/json", "JSON"),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "Excel"),
    PDF("pdf", "application/pdf", "PDF"),
}
