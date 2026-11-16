package com.kimaita.monies.ui.export

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kimaita.monies.data.TransactionsRepository
import com.kimaita.monies.models.TimeFilter
import com.kimaita.monies.models.TransactionFilters
import com.kimaita.monies.models.getDatesForFilter
import com.kimaita.monies.models.export.ExportFormat
import com.kimaita.monies.models.export.ExportState
import com.kimaita.monies.models.export.FilterOptions
import com.kimaita.monies.services.TypeGroup
import com.kimaita.monies.services.toggleGroup
import com.kimaita.monies.models.export.NamedRef
import com.kimaita.monies.services.ExportManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

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
        viewModelScope.launch {
            repository.selectedAccount.collect { id -> _sel.update { it.copy(accountId = id) } }
        }
    }

    val options: StateFlow<FilterOptions?> =
        repository.getFilterOptions().stateIn(viewModelScope, started, null)

    /** Live row count for the current selection: the first at once, later ones debounced as filters change. */
    val count: StateFlow<Int?> = _sel
        .map { it.toExportFilters() }
        .distinctUntilChanged()
        .withIndex()
        .debounce { if (it.index == 0) 0L else 300L }
        .map { it.value }
        .mapLatest {
            runCatching { repository.countForExport(it) }
                .getOrElse { e -> if (e is CancellationException) throw e else null }
        }
        .stateIn(viewModelScope, started, null)

    fun setFormat(f: ExportFormat) = _sel.update { it.copy(format = f) }
    fun setRange(start: LocalDate?, end: LocalDate?) =
        _sel.update { it.copy(startDate = start, endDate = end) }
    fun setNature(n: Boolean?) = _sel.update { it.copy(nature = n) }
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

    private val _subjectQuery = MutableStateFlow("")
    val subjectQuery: StateFlow<String> = _subjectQuery.asStateFlow()
    fun setSubjectQuery(q: String) {
        _subjectQuery.value = q
    }

    val subjectResults: StateFlow<List<NamedRef<Long>>> = _subjectQuery
        .debounce(250.milliseconds).distinctUntilChanged()
        .mapLatest { q -> if (q.isBlank()) repository.topSubjects() else repository.searchSubjects(q.trim()) }
        .stateIn(viewModelScope, started, emptyList())

    fun export() = manager.start(_sel.value.format, _sel.value.toExportFilters())
    fun cancel() = manager.cancel()
    fun acknowledge() = manager.acknowledge()

    private fun <T> Set<T>.toggle(v: T): Set<T> = if (v in this) this - v else this + v
}

/** The Export selections that show the same rows as these Transactions filters. */
internal fun TransactionFilters.toExportSelections(base: ExportSelections): ExportSelections {
    val (start, end) = getDatesForFilter(timeFilter)
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
