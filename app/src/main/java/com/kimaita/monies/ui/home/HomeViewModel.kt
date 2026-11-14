package com.kimaita.monies.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kimaita.monies.data.TransactionsRepository
import com.kimaita.monies.data.dao.dtos.AccountBalance
import com.kimaita.monies.data.dao.dtos.IncExp
import com.kimaita.monies.models.OtherBalances
import com.kimaita.monies.models.Provider
import com.kimaita.monies.models.TimeFilter
import com.kimaita.monies.models.Transaction
import com.kimaita.monies.models.getDatesForFilter
import com.kimaita.monies.services.SmsSyncer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * What Home shows. A field the screen cannot fill because its stream failed with no earlier value
 * is null (the card shows a dash); "still loading" is the whole state being null.
 */
data class HomeUiState(
    /** The selected account's balance, or the sum across accounts when none is selected. */
    val mpesaBalance: Double? = null,
    val accountBalances: List<AccountBalance> = emptyList(),
    /** Every active line, for the account chips. */
    val accounts: List<AccountBalance> = emptyList(),
    val selectedAccount: Int? = null,
    val hideBalances: Boolean = false,
    val incomeVsExpense: IncExp? = null,
    val recentTransactions: List<Transaction>? = null,
    val availableFuliza: Double? = null,
    val outstandingFuliza: Double? = null,
    val lockSavings: Double? = null,
    val mshwariBalance: Double? = null,
    val mshwariLoan: Double? = null,
    val changeInTotal: Double? = null,
)

private data class PeriodData(
    val incExp: IncExp?,
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

    fun selectAccount(id: Int?) {
        viewModelScope.launch { repository.setSelectedAccount(id) }
    }

    fun toggleHideBalances() {
        val hidden = uiState.value?.hideBalances ?: return
        viewModelScope.launch { repository.setHideBalances(!hidden) }
    }

    fun setTimeFilter(filter: TimeFilter) {
        _timeFilter.value = filter
    }

    /** The calendar day the Day, Week and Month ranges end on; the screen refreshes it on each start. */
    private val today = MutableStateFlow(LocalDate.now())

    /** Called when the screen starts: a process kept alive past midnight moves to the new day. */
    fun refreshToday() {
        today.value = LocalDate.now()
    }

    private val dateRange: Flow<Pair<String?, String?>> =
        combine(_timeFilter, today) { filter, day -> getDatesForFilter(filter, day) }.distinctUntilChanged()

    private val periodData: Flow<PeriodData> = dateRange.flatMapLatest { (start, end) ->
        val prev = previousPeriod(start, end)
        val previousIncExp: Flow<IncExp?> =
            if (prev != null) repository.getIncomeVsExpense(prev.first, prev.second).guarded()
            else flowOf(null)

        combine(
            repository.getIncomeVsExpense(start, end).guarded(),
            previousIncExp,
        ) { incExp, prevIncExp ->
            PeriodData(incExp, prevIncExp)
        }
    }


    /** Null until the first emission. */
    val uiState: StateFlow<HomeUiState?> = combine(
        combine(
            repository.getAccountBalances().guarded(),
            repository.selectedAccount,
            repository.hideBalances,
        ) { lines, selected, hide ->
            Triple(lines?.filter { it.balance != null }, selected, lines.orEmpty()) to hide
        },
        repository.getRecentTransactions(5).guarded(),
        repository.getOtherBalances().guarded(),
        periodData,
    ) { (balancesAndSelected, hide), recent, otherOrNull, period ->
        val (balancesOrNull, selected, lines) = balancesAndSelected
        val balances = balancesOrNull.orEmpty()
        val other = otherOrNull ?: OtherBalances()
        val shown = if (selected == null) balances else balances.filter { it.accountId == selected }
        // Fuliza and M-Shwari belong to M-PESA: hide them when another provider's line is selected.
        val mpesaView = selected == null || lines.any { it.accountId == selected && it.provider == Provider.MPESA.name }
        val totalMoved = period.incExp?.let { it.income + it.expense }
        val prevMoved = period.previousIncExp?.let { it.income + it.expense }
        HomeUiState(
            mpesaBalance = balancesOrNull?.let { shown.sumOf { b -> b.balance ?: 0.0 } },
            accountBalances = balances,
            accounts = lines,
            selectedAccount = selected,
            hideBalances = hide,
            incomeVsExpense = period.incExp,
            recentTransactions = recent,
            changeInTotal = totalMoved?.let { percentChange(current = it, previous = prevMoved) },
            availableFuliza = other.availableFuliza.takeIf { mpesaView },
            outstandingFuliza = other.outstandingFuliza.takeIf { mpesaView },
            mshwariBalance = other.mshwariBalance.takeIf { mpesaView },
            mshwariLoan = other.mshwariLoan.takeIf { mpesaView && it != null && it > 0.0 },
            lockSavings = other.lockSavings.takeIf { mpesaView },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )


    /**
     * Retries a failing stream twice, then gives up with a snackbar. After giving up the stream's last
     * good value stays (re-emitted, so a restart of the screen's collection keeps it); with none it emits
     * null, which the card shows as a dash. "Still loading" is a stream that has not emitted at all.
     */
    private fun <T : Any> Flow<T>.guarded(): Flow<T?> {
        var last: T? = null
        return flow {
            emitAll(
                this@guarded
                    .onEach { last = it }
                    .retryWhen { cause, attempt -> cause !is CancellationException && attempt < 2 }
                    .map<T, T?> { it }
                    .catch { e ->
                        if (e is CancellationException) throw e
                        Timber.e(e, "Home data stream failed")
                        _errorEvents.tryEmit("Couldn't refresh dashboard data.")
                        emit(last)
                    }
            )
        }
    }

    private fun percentChange(current: Double, previous: Double?): Double? {
        if (previous == null || previous == 0.0) return null
        return (current - previous) / previous * 100.0
    }


    private fun previousPeriod(start: String?, end: String?): Pair<String, String>? {
        if (start == null || end == null) return null
        return try {
            val s = parseBound(start)
            val e = parseBound(end)
            val length = Duration.between(s, e)
            val fmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME
            s.minus(length).format(fmt) to s.format(fmt)
        } catch (e: Exception) {
            null
        }
    }

    /** Preset periods give date-only bounds; custom ranges give date-times. */
    private fun parseBound(bound: String): LocalDateTime =
        if ('T' in bound) LocalDateTime.parse(bound) else LocalDate.parse(bound).atStartOfDay()
}
