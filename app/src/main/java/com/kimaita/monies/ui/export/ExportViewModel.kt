package com.kimaita.monies.ui.export

import java.time.format.DateTimeFormatter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kimaita.monies.data.TransactionsRepository
import com.kimaita.monies.models.TimeFilter
import com.kimaita.monies.models.TransactionFilters
import com.kimaita.monies.models.getDatesForFilter
import com.kimaita.monies.models.export.ExportFilters
import com.kimaita.monies.models.export.ExportFormat
import com.kimaita.monies.models.export.ExportState
import com.kimaita.monies.models.export.FilterOptions
import com.kimaita.monies.services.TypeGroup
import com.kimaita.monies.services.countTypeChoices
import com.kimaita.monies.services.toggleGroup
import com.kimaita.monies.models.export.NamedRef
import com.kimaita.monies.services.ExportManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

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

/** Ready-made date ranges for Export; a range that matches none of them is a custom one. */
enum class DateBand(val label: String) {
    ALL_TIME("All time"),
    THIS_MONTH("This month"),
    LAST_MONTH("Last month"),
    LAST_3_MONTHS("Last 3 months"),
    THIS_YEAR("This year"),
    LAST_YEAR("Last year");

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

/** Short text for a date range, leaving the year out for dates in the current one. */
fun rangeText(start: LocalDate?, end: LocalDate?, today: LocalDate): String {
    fun LocalDate.text(withYear: Boolean) =
        format(DateTimeFormatter.ofPattern(if (withYear) "d MMM yyyy" else "d MMM", Locale.ENGLISH))

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

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class ExportViewModel @Inject constructor(
    private val manager: ExportManager,
    private val repository: TransactionsRepository,
) : ViewModel() {

    private val started = SharingStarted.WhileSubscribed(5_000)
    val exportState: StateFlow<ExportState> = manager.state

    private val _sel = MutableStateFlow(ExportSelections())
    val sel: StateFlow<ExportSelections> = _sel.asStateFlow()

    init {
        // Start on Home's account; a pick made here holds until Home's choice changes again.
        viewModelScope.launch {
            repository.selectedAccount.collect { id -> _sel.update { it.copy(accountId = id) } }
        }
    }

    val options: StateFlow<FilterOptions?> =
        repository.getFilterOptions().stateIn(viewModelScope, started, null)

    /** Live row count for the current selection, recomputed (debounced) as filters change. */
    val count: StateFlow<Int?> = _sel
        .map { it.toExportFilters() }
        .distinctUntilChanged()
        .debounce(300.milliseconds)
        .mapLatest { runCatching { repository.countForExport(it) }.getOrNull() }
        .stateIn(viewModelScope, started, null)

    // selection setters
    fun setFormat(f: ExportFormat) = _sel.update { it.copy(format = f) }
    fun setRange(start: LocalDate?, end: LocalDate?) =
        _sel.update { it.copy(startDate = start, endDate = end) }
    fun setNature(n: Boolean?) = _sel.update { it.copy(nature = n) }
    fun setAccount(id: Int?) = _sel.update { it.copy(accountId = id) }
    fun toggleCategory(id: Int) = _sel.update { it.copy(categoryIds = it.categoryIds.toggle(id)) }
    fun toggleType(group: TypeGroup) = _sel.update { it.copy(typeIds = it.typeIds.toggleGroup(group)) }
    fun addSubject(ref: NamedRef<Long>) = _sel.update {
        if (it.subjects.any { s -> s.id == ref.id }) it else it.copy(subjects = it.subjects + ref)
    }

    fun removeSubject(ref: NamedRef<Long>) = _sel.update {
        it.copy(subjects = it.subjects.filterNot { s -> s.id == ref.id })
    }

    fun setSearch(q: String) = _sel.update { it.copy(search = q) }

    fun clearFilters() = _sel.update {
        it.copy(
            nature = null,
            categoryIds = emptySet(),
            typeIds = emptySet(),
            subjects = emptyList(),
            search = "",
        )
    }

    /** Takes the filters handed over from Transactions, once. True when there were some. */
    fun applyHandoff(): Boolean {
        val handed = repository.takeExportHandoff() ?: return false
        _sel.update { handed.toExportSelections(it) }
        return true
    }

    // subject search (debounced, export-style)
    private val _subjectQuery = MutableStateFlow("")
    val subjectQuery: StateFlow<String> = _subjectQuery.asStateFlow()
    fun setSubjectQuery(q: String) {
        _subjectQuery.value = q
    }

    val subjectResults: StateFlow<List<NamedRef<Long>>> = _subjectQuery
        .debounce(250.milliseconds).distinctUntilChanged()
        .mapLatest { q -> if (q.isBlank()) repository.topSubjects() else repository.searchSubjects(q.trim()) }
        .stateIn(viewModelScope, started, emptyList())

    // actions
    fun export() = manager.start(_sel.value.format, _sel.value.toExportFilters())
    fun cancel() = manager.cancel()
    fun acknowledge() = manager.acknowledge()

    private fun <T> Set<T>.toggle(v: T): Set<T> = if (v in this) this - v else this + v
}

/** The Export selections that show the same rows as these Transactions filters. */
internal fun TransactionFilters.toExportSelections(base: ExportSelections): ExportSelections {
    val (start, end) = getDatesForFilter(timeFilter)
    // Presets end on the day after today (exclusive); a custom range ends on its own last day.
    val lastDay = end?.take(10)?.let(LocalDate::parse)
        ?.let { if (timeFilter is TimeFilter.CustomRange) it else it.minusDays(1) }
    return base.copy(
        startDate = start?.take(10)?.let(LocalDate::parse),
        endDate = lastDay,
        nature = natureFilter,
        accountId = accountFilter,
        categoryIds = categoryFilters.toSet(),
        typeIds = typeFilters.toSet(),
        subjects = emptyList(),
        search = searchQuery.trim(),
    )
}
