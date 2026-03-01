package com.kimaita.monies.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.kimaita.monies.data.TransactionsRepository
import com.kimaita.monies.data.database.models.Category
import com.kimaita.monies.data.database.models.TransactionType
import com.kimaita.monies.models.SortOrder
import com.kimaita.monies.models.Transaction
import com.kimaita.monies.models.TransactionFilters
import com.kimaita.monies.models.isoDateTimeFormatter
import com.kimaita.monies.ui.components.TimeFilter
import com.kimaita.monies.ui.components.getDatesForFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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


    val transactionTypes: StateFlow<Map<Int, TransactionType>> =
        repository.getTransactionTypes().map { list -> list.associateBy { it.id } }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyMap()
        )

    val categories: StateFlow<List<Category>> = repository.getCategories().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val filteredTransactions: Flow<PagingData<Transaction>> = _filters.flatMapLatest { filters ->
        val (startDate, endDate) = getDatesForFilter(filters.timeFilter)
        repository.getTransactions(
            startDate = startDate?.format(isoDateTimeFormatter),
            endDate = endDate?.format(isoDateTimeFormatter),
            transactionFilters = filters,
        )

    }.cachedIn(viewModelScope)


    fun updateFilters(updatedFilters: TransactionFilters) {
        _filters.value = updatedFilters
    }

    fun setSearchQuery(query: String) {
        _filters.value = _filters.value.copy(searchQuery = query)
    }

    fun setTimeFilter(timeFilter: TimeFilter) {
        _filters.value = _filters.value.copy(timeFilter = timeFilter)
    }

    fun setTypeFilter(typeId: Int?) {
        _filters.value = _filters.value.copy(typeFilter = typeId)
    }

    fun setCategoryFilter(category: Int?) {
        _filters.value = _filters.value.copy(categoryFilter = category)
    }

    fun setNatureFilter(isInc: Boolean?) {
        _filters.value = _filters.value.copy(natureFilter = isInc)
    }

    fun setSortOrder(sortOrder: SortOrder) {
        _filters.value = _filters.value.copy(sortOrder = sortOrder)
    }

    fun clearFilters() {
        _filters.value = TransactionFilters()
    }
}
