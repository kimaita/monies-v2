package com.kimaita.monies.ui.export

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kimaita.monies.data.TransactionsRepository
import com.kimaita.monies.models.TimeFilter
import com.kimaita.monies.models.export.ExportFilters
import com.kimaita.monies.models.export.ExportFormat
import com.kimaita.monies.models.export.ExportState
import com.kimaita.monies.models.export.FilterOptions
import com.kimaita.monies.models.export.NamedRef
import com.kimaita.monies.models.isoDateTimeFormatter
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.LocalTime
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
) {
    val activeFilterCount: Int
        get() = subjects.size + categoryIds.size + typeIds.size +
                (if (nature != null) 1 else 0) + (if (accountId != null) 1 else 0)

    fun toExportFilters() = ExportFilters(
        startDate = startDate?.atStartOfDay()?.format(isoDateTimeFormatter),
        endDate = endDate?.plusDays(1)?.atStartOfDay()
            ?.format(isoDateTimeFormatter),
        subjectId = subjects.map { it.id }.ifEmpty { null },
        categoryId = categoryIds.toList().ifEmpty { null },
        transactionType = typeIds.toList().ifEmpty { null },
        account = accountId,
        natureFilter = nature,
    )

    private fun rangeFilter(s: LocalDate?, e: LocalDate?): TimeFilter =
        if (s == null && e == null) TimeFilter.ALL
        else TimeFilter.CustomRange(
            startDate = (s ?: LocalDate.of(2000, 1, 1)).atStartOfDay(),
            endDate = (e ?: LocalDate.now()).atTime(LocalTime.MAX),
        )
}

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

    val options: StateFlow<FilterOptions?> =
        flow { emit(repository.getFilterOptions()) }.stateIn(viewModelScope, started, null)

    /** Live row count for the current selection, recomputed (debounced) as filters change. */
    val count: StateFlow<Int?> = _sel
        .map { it.toExportFilters() }
        .distinctUntilChanged()
        .debounce(300.milliseconds)
        .mapLatest { runCatching { repository.countForExport(it) }.getOrNull() }
        .stateIn(viewModelScope, started, null)

    // selection setters
    fun setFormat(f: ExportFormat) = _sel.update { it.copy(format = f) }
    fun setStartDate(d: LocalDate?) = _sel.update { it.copy(startDate = d) }
    fun setEndDate(d: LocalDate?) = _sel.update { it.copy(endDate = d) }
    fun setNature(n: Boolean?) = _sel.update { it.copy(nature = n) }
    fun setAccount(id: Int?) = _sel.update { it.copy(accountId = id) }
    fun toggleCategory(id: Int) = _sel.update { it.copy(categoryIds = it.categoryIds.toggle(id)) }
    fun toggleType(id: Int) = _sel.update { it.copy(typeIds = it.typeIds.toggle(id)) }
    fun addSubject(ref: NamedRef<Long>) = _sel.update {
        if (it.subjects.any { s -> s.id == ref.id }) it else it.copy(subjects = it.subjects + ref)
    }

    fun removeSubject(ref: NamedRef<Long>) = _sel.update {
        it.copy(subjects = it.subjects.filterNot { s -> s.id == ref.id })
    }

    fun clearFilters() = _sel.update {
        it.copy(
            nature = null,
            accountId = null,
            categoryIds = emptySet(),
            typeIds = emptySet(),
            subjects = emptyList()
        )
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