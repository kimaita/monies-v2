package com.kimaita.monies.ui.transactions

import java.time.format.DateTimeFormatter
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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
import com.kimaita.monies.models.getDatesForFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds


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

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val filteredTransactions: Flow<PagingData<Transaction>> = combine(
        _filters.map { it.searchQuery }.distinctUntilChanged().debounce(300L.milliseconds),
        _filters.map { it.copy(searchQuery = "") }.distinctUntilChanged()
    ) { query, filtersWithoutQuery ->
        filtersWithoutQuery.copy(searchQuery = query)
    }.flatMapLatest { filters ->
        val (startDate, endDate) = getDatesForFilter(filters.timeFilter)
        repository.getTransactions(
            startDate = startDate?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            endDate = endDate?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
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

}
