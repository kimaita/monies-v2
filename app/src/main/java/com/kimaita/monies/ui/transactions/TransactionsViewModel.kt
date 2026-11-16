package com.kimaita.monies.ui.transactions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.kimaita.monies.data.TransactionsRepository
import com.kimaita.monies.data.dao.dtos.SubjectCount
import com.kimaita.monies.data.dao.dtos.TraceTotals
import com.kimaita.monies.data.database.models.Category
import com.kimaita.monies.data.database.models.TransactionType
import com.kimaita.monies.models.SortOrder
import com.kimaita.monies.models.TraceStart
import com.kimaita.monies.models.Transaction
import com.kimaita.monies.models.TransactionFilters
import com.kimaita.monies.models.listDates
import com.kimaita.monies.models.traceStartOf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Navigation arguments that open the list already tracing; see [traceRoute]. */
internal const val TRACE_SINCE_ARG = "since"
internal const val TRACE_AFTER_ARG = "after"

/**
 * The trace card: [totals] of the listed rows, and the balance at the start and now. When the rows
 * all come from one line the balances are that line's, so they match the balance on each row.
 */
data class TraceSummary(
    val start: TraceStart,
    val totals: TraceTotals,
    val balanceThen: Double?,
    val balanceNow: Double?,
) {
    /** Rows show their balance only when they all come from one line, so the figures run in one series. */
    val oneLine: Boolean get() = totals.lines <= 1
}

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val repository: TransactionsRepository,
    private val args: SavedStateHandle,
) : ViewModel() {

    private val _filters = MutableStateFlow(
        TransactionFilters(since = traceStartOf(args[TRACE_SINCE_ARG], args[TRACE_AFTER_ARG]))
    )
    val filters = _filters.asStateFlow()


    val transactionTypes: StateFlow<List<TransactionType>> = repository.getTransactionTypes().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val categories: StateFlow<List<Category>> = repository.getCategories().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val hideBalances: StateFlow<Boolean> = repository.hideBalances.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false
    )

    @OptIn(FlowPreview::class)
    private val searchQuery = _filters.map { it.searchQuery }.distinctUntilChanged()
        .debounce { if (it.isEmpty()) 0L else 300L }

    /** Subjects whose name matches the search, shown as rows above the results. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val subjectMatches: StateFlow<List<SubjectCount>> =
        searchQuery.flatMapLatest { repository.getMatchingSubjects(it) }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /** The filters with the search debounced: what the list and the trace card both query. */
    private val listFilters: Flow<TransactionFilters> = combine(
        searchQuery,
        _filters.map { it.copy(searchQuery = "") }.distinctUntilChanged()
    ) { query, filtersWithoutQuery ->
        filtersWithoutQuery.copy(searchQuery = query)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val filteredTransactions: Flow<PagingData<Transaction>> = listFilters.flatMapLatest { filters ->
        val (startDate, endDate) = filters.listDates()
        repository.getTransactions(
            startDate = startDate,
            endDate = endDate,
            transactionFilters = filters,
        )
    }.cachedIn(viewModelScope)

    /** Null when not tracing, and while a new trace loads. The sort order doesn't change the sums. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val trace: StateFlow<TraceSummary?> =
        listFilters.map { it.copy(sortOrder = SortOrder.DATE_DESC) }.distinctUntilChanged()
            .flatMapLatest { f ->
                val start = f.since ?: return@flatMapLatest flowOf(null)
                val (startDate, endDate) = f.listDates()
                repository.getTraceTotals(f, startDate, endDate).flatMapLatest { totals ->
                    val line = totals.line.takeIf { totals.lines == 1 } ?: f.accountFilter
                    repository.getTraceBalances(start, line)
                        .map { (then, now) -> TraceSummary(start, totals, then, now) }
                }.onStart<TraceSummary?> { emit(null) }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null
            )


    fun updateFilters(updatedFilters: TransactionFilters) {
        setFilters(updatedFilters)
    }

    fun setSearchQuery(query: String) {
        _filters.value = _filters.value.copy(searchQuery = query)
    }

    fun setSortOrder(sortOrder: SortOrder) {
        _filters.value = _filters.value.copy(sortOrder = sortOrder)
    }

    /** Starts a trace at [start] in place, or stops tracing with null. */
    fun setTrace(start: TraceStart?) = setFilters(_filters.value.copy(since = start))

    /**
     * Keeps the navigation arguments in step with the trace, so a screen restored after the process
     * was killed does not bring back a trace that was cleared or replaced.
     */
    private fun setFilters(f: TransactionFilters) {
        _filters.value = f
        args[TRACE_SINCE_ARG] = f.since?.start
        args[TRACE_AFTER_ARG] = f.since?.afterId ?: -1L
    }

    /** Hands the current filters and search to the Export screen. */
    fun exportResults() = repository.handOffToExport(_filters.value)
}
