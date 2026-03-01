package com.kimaita.monies.ui.home

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kimaita.monies.data.DefaultTransactionsRepository
import com.kimaita.monies.data.dao.dtos.IncExp
import com.kimaita.monies.models.Transaction
import com.kimaita.monies.ui.components.TimeFilter
import com.kimaita.monies.ui.components.getDatesForFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val phoneNumber: String? = null,
    val mpesaBalance: Double = 0.0,
    val incomeVsExpense: IncExp = IncExp(),
    val transactionCount: Int = 0,
    val recentTransactions: List<Transaction?> = emptyList(),
    val largestIncome: Transaction? = null,
    val largestExpense: Transaction? = null,
    val availableFuliza: Double = 0.0,
    val outstandingFuliza: Double = 0.0,
    val mshwariLoan: Double = 0.0,
    val lockSavings: Double = 0.0,
    val mshwariBalance: Double = 0.0,
    val totalTransactionAmount: Double = 0.0,
    val totalTransactionCost: Double = 0.0,
    val changeInTotal: Double = 0.0,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: DefaultTransactionsRepository,
//    private val application: Application
) : ViewModel() {


    private val _timeFilter = MutableStateFlow<TimeFilter>(TimeFilter.DAY)
    val filters: StateFlow<TimeFilter> = _timeFilter.asStateFlow()

    fun setTimeFilter(filter: TimeFilter) {
        _timeFilter.value = filter
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val dateRange = _timeFilter.flatMapLatest { filter ->
        MutableStateFlow(getDatesForFilter(filter))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<HomeUiState> = combine(
        repository.getMpesaBalance(),
        dateRange.flatMapLatest { (start, end) -> repository.getIncomeVsExpense(start, end) },
        repository.getRecentTransactions(5),
        dateRange.flatMapLatest { (start, end) ->
            repository.getLargestTransaction(
                isInc = true, startDate = start, endDate = end
            )
        },
        dateRange.flatMapLatest { (start, end) ->
            repository.getLargestTransaction(
                isInc = false, startDate = start, endDate = end
            )
        },
    ) { bal, incExp, recent, largestInc, largestExp ->
        HomeUiState(
            mpesaBalance = bal,
            incomeVsExpense = incExp,
            transactionCount = incExp.incomeCount + incExp.expenseCount,
            totalTransactionCost = incExp.incomeCost + incExp.expenseCost,
            totalTransactionAmount = incExp.income - incExp.expense,
            recentTransactions = recent,
            largestIncome = largestInc,
            largestExpense = largestExp,
            // TODO: Implement calculations for other balances

        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState(isLoading = true)
    )

    private val _uiState = MutableStateFlow(uiState.value)

    /**
     * Public function that can be called from the UI to start the initial SMS sync.
     * This is useful for a "Sync Now" button or a pull-to-refresh action.
     */
//    fun performInitialSmsSync() {
//        viewModelScope.launch {
//            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
//            try {
//                repository.syncAllSms(application)
//            } catch (e: Exception) {
//                _uiState.update { it.copy(errorMessage = "Failed to sync messages: ${e.message}") }
//            } finally {
//                _uiState.update { it.copy(isLoading = false) }
//            }
//        }
//    }

    /**
     * Call this from the UI to clear any error messages after they've been shown,
     * for example, after a Snackbar has been dismissed.
     */
    fun errorShown() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
