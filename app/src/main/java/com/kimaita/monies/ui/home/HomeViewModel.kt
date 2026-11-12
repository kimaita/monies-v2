package com.kimaita.monies.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kimaita.monies.data.TransactionsRepository
import com.kimaita.monies.data.dao.dtos.IncExp
import com.kimaita.monies.models.OtherBalances
import com.kimaita.monies.models.TimeFilter
import com.kimaita.monies.models.Transaction
import com.kimaita.monies.models.getDatesForFilter
import com.kimaita.monies.services.SmsSyncer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject

data class HomeUiState(
    val phoneNumber: String? = null,
    val mpesaBalance: Double = 0.0,
    val incomeVsExpense: IncExp = IncExp(),
    val transactionCount: Int = 0,
    val recentTransactions: List<Transaction> = emptyList(),
    val largestIncome: Transaction? = null,
    val largestExpense: Transaction? = null,
    val availableFuliza: Double? = null,
    val outstandingFuliza: Double? = null,
    val mshwariLoan: Double? = null,
    val lockSavings: Double? = null,
    val mshwariBalance: Double? = null,
    val totalTransactionAmount: Double = 0.0,
    val totalTransactionCost: Double = 0.0,
    val changeInTotal: Double? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

private data class PeriodData(
    val incExp: IncExp,
    val largestIncome: Transaction?,
    val largestExpense: Transaction?,
    val previousIncExp: IncExp?,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: TransactionsRepository,
    private val smsSyncer: SmsSyncer,
) : ViewModel() {


    private val _timeFilter = MutableStateFlow<TimeFilter>(TimeFilter.DAY)
    val filters: StateFlow<TimeFilter> = _timeFilter.asStateFlow()

    val isSyncing: StateFlow<Boolean> =
        smsSyncer.isSyncing.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _errorEvents = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val errorEvents: SharedFlow<String> = _errorEvents.asSharedFlow()

    fun setTimeFilter(filter: TimeFilter) {
        _timeFilter.value = filter
    }

    private val dateRange: Flow<Pair<String?, String?>> = _timeFilter.map { getDatesForFilter(it) }

    private val periodData: Flow<PeriodData> = dateRange.flatMapLatest { (start, end) ->
        val prev = previousPeriod(start, end)
        val previousIncExp: Flow<IncExp?> =
            if (prev != null) repository.getIncomeVsExpense(prev.first, prev.second)
                .fallbackOnError(IncExp())
            else flowOf(null)

        combine(
            repository.getIncomeVsExpense(start, end).fallbackOnError(IncExp()),
            repository.getLargestTransaction(isInc = true, startDate = start, endDate = end)
                .fallbackOnError(null),
            repository.getLargestTransaction(isInc = false, startDate = start, endDate = end)
                .fallbackOnError(null),
            previousIncExp,
        ) { incExp, largestInc, largestExp, prevIncExp ->
            PeriodData(incExp, largestInc, largestExp, prevIncExp)
        }
    }


    val uiState: StateFlow<HomeUiState> = combine(
        repository.getMpesaBalance().fallbackOnError(0.0),
        repository.getRecentTransactions(5).fallbackOnError(emptyList()),
        repository.getOtherBalances().fallbackOnError(OtherBalances()),
        periodData,
    ) { balance, recent, other, period ->
        val totalMoved = period.incExp.income + period.incExp.expense
        val prevMoved = period.previousIncExp?.let { it.income + it.expense }
        HomeUiState(
            mpesaBalance = balance,
            incomeVsExpense = period.incExp,
            transactionCount = period.incExp.incomeCount + period.incExp.expenseCount,
            totalTransactionCost = period.incExp.incomeCost + period.incExp.expenseCost,
            totalTransactionAmount = period.incExp.income - period.incExp.expense,
            recentTransactions = recent,
            largestIncome = period.largestIncome,
            largestExpense = period.largestExpense,
            changeInTotal = percentChange(current = totalMoved, previous = prevMoved),
            isLoading = false,
            availableFuliza = other.availableFuliza,
            outstandingFuliza = other.outstandingFuliza,
            mshwariBalance = other.mshwariBalance,
            lockSavings = other.lockSavings,
            mshwariLoan = other.mshwariLoan,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(isLoading = true),
    )


    private fun <T> Flow<T>.fallbackOnError(fallback: T): Flow<T> = catch { e ->
        Timber.e(e, "Home data stream failed")
        _errorEvents.tryEmit("Couldn't refresh dashboard data.")
        emit(fallback)
    }

    private fun percentChange(current: Double, previous: Double?): Double? {
        if (previous == null || previous == 0.0) return null
        return (current - previous) / previous * 100.0
    }


    private fun previousPeriod(start: String?, end: String?): Pair<String, String>? {
        if (start == null || end == null) return null
        return try {
            val s = LocalDateTime.parse(start)
            val e = LocalDateTime.parse(end)
            val length = Duration.between(s, e)
            s.minus(length).toString() to s.toString()
        } catch (e: Exception) {
            null
        }
    }
}
