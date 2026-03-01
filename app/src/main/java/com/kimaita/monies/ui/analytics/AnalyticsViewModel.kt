package com.kimaita.monies.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kimaita.monies.data.DefaultTransactionsRepository
import com.kimaita.monies.data.dao.dtos.CategorySpending
import com.kimaita.monies.data.dao.dtos.DailyTotal
import com.kimaita.monies.data.dao.dtos.IncExp
import com.kimaita.monies.data.dao.dtos.TopSubject
import com.kimaita.monies.ui.components.TimeFilter
import com.kimaita.monies.ui.components.getDatesForFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val repository: DefaultTransactionsRepository
) : ViewModel() {

    private val _timeFilter = MutableStateFlow<TimeFilter>(TimeFilter.ALL)
    val timeFilter: StateFlow<TimeFilter> = _timeFilter.asStateFlow()

    fun setTimeFilter(filter: TimeFilter) {
        _timeFilter.value = filter
    }

    private val dateRange = _timeFilter.flatMapLatest { filter ->
        MutableStateFlow(getDatesForFilter(filter))
    }


    val incomeVsExpense: StateFlow<IncExp> = dateRange.flatMapLatest { (start, end) ->
        repository.getIncomeVsExpense(start, end)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), IncExp(0.0, 0.0))

    val categorySpending: StateFlow<List<CategorySpending>> =
        dateRange.flatMapLatest { (start, end) ->
            repository.getCategorySpending(start, end)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val typeSpending: StateFlow<List<CategorySpending>> = dateRange.flatMapLatest { (start, end) ->
        repository.getTypeSpending(start, end) // Assuming you add this to the repository
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val spendingTotalsByDay: StateFlow<List<DailyTotal>> = dateRange.flatMapLatest { (start, end) ->
        repository.getTransactionStatsByDay(start, end) // Assuming you add this
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val topSubjects: StateFlow<List<TopSubject>> = dateRange.flatMapLatest { (start, end) ->
        repository.getTopSubjectPerType(start, end) // Assuming you add this
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


}

