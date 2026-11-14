package com.kimaita.monies.ui.export

import java.time.format.DateTimeFormatter
import com.kimaita.monies.models.export.ExportFilters
import com.kimaita.monies.models.export.ExportFormat
import com.kimaita.monies.services.TypeGroup
import com.kimaita.monies.services.countTypeChoices
import com.kimaita.monies.models.export.NamedRef
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

data class ExportSelections(
    val format: ExportFormat = ExportFormat.XLSX,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val nature: Boolean? = null,
    val accountId: Int? = null,
    val categoryIds: Set<Int> = emptySet(),
    val typeIds: Set<Int> = emptySet(),
    val subjects: List<NamedRef<Long>> = emptyList(),
    val search: String = "",
) {
    /** The filters in force; [typeGroups] makes types that share a display name count once. */
    fun activeFilterCount(typeGroups: List<TypeGroup> = emptyList()): Int =
        subjects.size + categoryIds.size + countTypeChoices(typeIds, typeGroups) +
                (if (nature != null) 1 else 0) +
                (if (search.isNotBlank()) 1 else 0)

    fun toExportFilters() = ExportFilters(
        startDate = startDate?.atStartOfDay()?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
        endDate = endDate?.plusDays(1)?.atStartOfDay()
            ?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
        subjectId = subjects.map { it.id }.ifEmpty { null },
        categoryId = categoryIds.toList().ifEmpty { null },
        transactionType = typeIds.toList().ifEmpty { null },
        account = accountId,
        natureFilter = nature,
        search = search.trim().ifEmpty { null },
    )
}

/**
 * Ready-made date ranges for Export; a range that matches none of them is a custom one.
 * Order is chip order, shortest span first like Home's chips; on a tie (1 Jan) the first wins.
 */
enum class DateBand(val label: String) {
    THIS_MONTH("This month"),
    LAST_MONTH("Last month"),
    LAST_3_MONTHS("Last 3 months"),
    THIS_YEAR("This year"),
    LAST_YEAR("Last year"),
    ALL_TIME("All time");

    /** Chip text in the style of Home's 1M / YTD chips: "MTD", "OCT", "3M", "YTD", "2025", "ALL". */
    fun shortLabel(today: LocalDate): String = when (this) {
        THIS_MONTH -> "MTD"
        LAST_MONTH -> today.minusMonths(1).month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase(Locale.ENGLISH)
        LAST_3_MONTHS -> "3M"
        THIS_YEAR -> "YTD"
        LAST_YEAR -> (today.year - 1).toString()
        ALL_TIME -> "ALL"
    }

    /** Inclusive first and last day, or null for no limit. */
    fun range(today: LocalDate): Pair<LocalDate?, LocalDate?> = when (this) {
        ALL_TIME -> null to null
        THIS_MONTH -> today.withDayOfMonth(1) to today
        LAST_MONTH -> today.withDayOfMonth(1).let { it.minusMonths(1) to it.minusDays(1) }
        LAST_3_MONTHS -> today.minusMonths(3).plusDays(1) to today
        THIS_YEAR -> today.withDayOfYear(1) to today
        LAST_YEAR -> today.minusYears(1).let { it.withDayOfYear(1) to it.withDayOfYear(it.lengthOfYear()) }
    }
}

/** The band the selected dates equal today, or null for a custom range. */
fun ExportSelections.band(today: LocalDate): DateBand? =
    DateBand.entries.firstOrNull { it.range(today) == (startDate to endDate) }

private val dayMonthYearFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
private val dayMonthOnlyFmt = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

/** Short text for a date range, leaving the year out for dates in the current one. */
fun rangeText(start: LocalDate?, end: LocalDate?, today: LocalDate): String {
    fun LocalDate.text(withYear: Boolean) =
        format(if (withYear) dayMonthYearFmt else dayMonthOnlyFmt)

    fun LocalDate.text() = text(year != today.year)
    return when {
        start == null && end == null -> DateBand.ALL_TIME.label
        start == null -> "Until ${end!!.text()}"
        end == null -> "From ${start.text()}"
        start == end -> start.text()
        start.year == end.year -> "${start.text(false)} – ${end.text()}"
        else -> "${start.text(true)} – ${end.text(true)}"
    }
}

/** The band's name, or the dates when the range is a custom one. */
fun ExportSelections.dateLabel(today: LocalDate): String =
    band(today)?.label ?: rangeText(startDate, endDate, today)
