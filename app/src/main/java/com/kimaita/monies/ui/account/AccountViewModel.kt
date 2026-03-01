package com.kimaita.monies.ui.account

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.kimaita.monies.data.TransactionsRepository
import com.kimaita.monies.data.dao.dtos.SubjectStats
import com.kimaita.monies.data.database.models.Category
import com.kimaita.monies.data.database.toDomainModel
import com.kimaita.monies.models.SortOrder
import com.kimaita.monies.models.Subject
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val repository: TransactionsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val subjectId: Long? = savedStateHandle.get<String>("subjectId")?.toLongOrNull()
    private val _filters = MutableStateFlow(TransactionFilters(subjectFilter = subjectId))
    val filters = _filters.asStateFlow()

    val subjectDetails: StateFlow<Subject?> = flow {
        if (subjectId != null) {
            repository.getSubjects(subjectId).collect { subjects ->
                emit(subjects.firstOrNull()?.toDomainModel())
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val subjectStats: StateFlow<SubjectStats> = _filters.flatMapLatest { filters ->
        val (startDate, endDate) = getDatesForFilter(filters.timeFilter)
        if (subjectId != null) {
            repository.getSubjectStats(
                subjectId = subjectId,
                startDate = startDate?.format(isoDateTimeFormatter),
                endDate = endDate?.format(isoDateTimeFormatter)
            )
        } else {
            flowOf(SubjectStats())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SubjectStats())

    @OptIn(ExperimentalCoroutinesApi::class)
    val subjectTransactions: Flow<PagingData<Transaction>> =
        _filters.flatMapLatest { filters ->
            val (startDate, endDate) = getDatesForFilter(filters.timeFilter)
            repository.getTransactions(
                startDate = startDate?.format(isoDateTimeFormatter),
                endDate = endDate?.format(isoDateTimeFormatter),
                transactionFilters = filters,
            )

        }.cachedIn(viewModelScope)


    val categories: StateFlow<List<Category>> = repository.getCategories()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun setTimeFilter(filter: TimeFilter) {
        _filters.value = _filters.value.copy(timeFilter = filter)
    }

    fun setSortOrder(sortOrder: SortOrder) {
        _filters.value = _filters.value.copy(sortOrder = sortOrder)
    }
}

