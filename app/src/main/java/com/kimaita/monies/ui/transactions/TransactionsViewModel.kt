package com.kimaita.monies.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.kimaita.monies.data.TransactionsRepository
import com.kimaita.monies.data.dao.dtos.SubjectCount
import com.kimaita.monies.data.database.models.Category
import com.kimaita.monies.data.database.models.TransactionType
import com.kimaita.monies.models.SortOrder
import com.kimaita.monies.models.Transaction
import com.kimaita.monies.models.TransactionFilters
import com.kimaita.monies.models.getDatesForFilter
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject


@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val repository: TransactionsRepository
) : ViewModel() {

    private val _filters = MutableStateFlow(TransactionFilters())
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

    @OptIn(ExperimentalCoroutinesApi::class)
    val filteredTransactions: Flow<PagingData<Transaction>> = combine(
        searchQuery,
        _filters.map { it.copy(searchQuery = "") }.distinctUntilChanged()
    ) { query, filtersWithoutQuery ->
        filtersWithoutQuery.copy(searchQuery = query)
    }.flatMapLatest { filters ->
        val (startDate, endDate) = getDatesForFilter(filters.timeFilter)
        repository.getTransactions(
            startDate = startDate,
            endDate = endDate,
            transactionFilters = filters,
        )

    }.cachedIn(viewModelScope)


    fun updateFilters(updatedFilters: TransactionFilters) {
        _filters.value = updatedFilters
    }

    fun setSearchQuery(query: String) {
        _filters.value = _filters.value.copy(searchQuery = query)
    }

    fun setSortOrder(sortOrder: SortOrder) {
        _filters.value = _filters.value.copy(sortOrder = sortOrder)
    }

    /** Hands the current filters and search to the Export screen. */
    fun exportResults() = repository.handOffToExport(_filters.value)
}
