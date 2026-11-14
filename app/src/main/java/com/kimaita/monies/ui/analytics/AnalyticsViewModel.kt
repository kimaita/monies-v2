package com.kimaita.monies.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kimaita.monies.data.TransactionsRepository
import com.kimaita.monies.data.dao.dtos.CategoryBudget
import com.kimaita.monies.data.dao.dtos.ActivityCell
import com.kimaita.monies.data.dao.dtos.CashFlowBucket
import com.kimaita.monies.data.dao.dtos.DailyTotal
import com.kimaita.monies.data.dao.dtos.CategorySpending
import com.kimaita.monies.data.dao.dtos.IncExp
import com.kimaita.monies.data.dao.dtos.SourceAmount
import com.kimaita.monies.data.dao.dtos.SubjectCount
import com.kimaita.monies.data.dao.dtos.WeekdayCount
import com.kimaita.monies.models.TimeFilter
import com.kimaita.monies.models.TransactionFilters
import com.kimaita.monies.models.analytics.BalancePoint
import com.kimaita.monies.models.analytics.CashFlowWeek
import com.kimaita.monies.models.analytics.CostMonth
import com.kimaita.monies.models.analytics.HeatRow
import com.kimaita.monies.models.analytics.MerchantSpend
import com.kimaita.monies.models.analytics.RecurringPayment
import com.kimaita.monies.models.export.FilterOptions
import com.kimaita.monies.models.export.NamedRef
import com.kimaita.monies.models.getDatesForFilter
import com.kimaita.monies.services.PaceSummary
import com.kimaita.monies.services.WeekdayBar
import com.kimaita.monies.services.balanceBucketFormatFor
import com.kimaita.monies.services.bucketFormatFor
import com.kimaita.monies.services.bucketLabel
import com.kimaita.monies.services.daysForFilter
import com.kimaita.monies.services.daysFrom
import com.kimaita.monies.services.toHeatRows
import com.kimaita.monies.services.toWeekdayBars
import com.kimaita.monies.services.RhythmStats
import com.kimaita.monies.services.SliceValue
import com.kimaita.monies.services.StreakSummary
import com.kimaita.monies.services.computePace
import com.kimaita.monies.services.computeRhythm
import com.kimaita.monies.services.categorySlices
import com.kimaita.monies.services.computeStreaks
import com.kimaita.monies.services.sliceFilter
import com.kimaita.monies.services.toggleSlice
import com.kimaita.monies.services.typeSlices
import com.kimaita.monies.services.scopeLabel
import com.kimaita.monies.services.balancePoints
import com.kimaita.monies.services.yearStarts
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
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

enum class SpendDirection { OUT, IN }
enum class Breakdown { CATEGORY, TYPE }

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val repository: TransactionsRepository,
) : ViewModel() {

    private val started = SharingStarted.WhileSubscribed(5_000)
    private val onceStarted = SharingStarted.Lazily

    /** The calendar day every date range is built from; the screen refreshes it on each start. */
    private val today = MutableStateFlow(LocalDate.now())

    /** Called when the screen starts: a process kept alive past midnight moves to the new day. */
    fun refreshToday() {
        today.value = LocalDate.now()
    }

    private val _filters = MutableStateFlow(TransactionFilters(timeFilter = TimeFilter.MONTH))
    val filters: StateFlow<TransactionFilters> = _filters.asStateFlow()

    private val _direction = MutableStateFlow(SpendDirection.OUT)
    val direction: StateFlow<SpendDirection> = _direction.asStateFlow()

    private val _breakdownMode = MutableStateFlow(Breakdown.CATEGORY)
    val breakdownMode: StateFlow<Breakdown> = _breakdownMode.asStateFlow()

    private val _selectedSlice = MutableStateFlow<String?>(null)
    val selectedSlice: StateFlow<String?> = _selectedSlice.asStateFlow()

    /**
     * Applies a change to the filters that feed the breakdown. The selected slice goes first: a slice
     * whose row is gone from the new rows must not narrow the entries again if the row returns.
     */
    private fun updateFilters(change: (TransactionFilters) -> TransactionFilters) {
        val before = _filters.value
        val after = change(before)
        if (after == before) return
        _selectedSlice.value = null
        _filters.value = after
    }

    fun setTimeFilter(f: TimeFilter) = updateFilters { it.copy(timeFilter = f) }

    // Nature reaches only the activity queries, not the breakdown, so a slice stays valid.
    fun setNature(n: Boolean?) = _filters.update { it.copy(natureFilter = n) }
    fun setTypeFilters(ids: List<Int>) = updateFilters { it.copy(typeFilters = ids) }
    fun setCategoryFilters(ids: List<Int>) = updateFilters { it.copy(categoryFilters = ids) }
    fun setSubjectFilters(ids: List<Long>) = updateFilters { it.copy(subjectFilters = ids) }
    fun setDirection(d: SpendDirection) {
        _direction.value = d
    }

    fun setBreakdownMode(b: Breakdown) {
        _breakdownMode.value = b; _selectedSlice.value = null
    }

    fun selectSlice(label: String?) {
        _selectedSlice.value = toggleSlice(_selectedSlice.value, label)
    }

    fun clearFilters() {
        updateFilters { TransactionFilters(timeFilter = it.timeFilter) }
        _selectedSubjects.value = emptyList()
        _subjectQuery.value = ""
    }

    // ---------------- derived keys ----------------
    /** What most queries restart on. [time] is here, not only its dates, so labels follow their rows. */
    private data class FilterKey(
        val start: String?, val end: String?, val time: TimeFilter,
        val subjects: List<Long>?, val categories: List<Int>?, val types: List<Int>?,
    )

    private fun keyOf(f: TransactionFilters, day: LocalDate): FilterKey {
        val (s, e) = getDatesForFilter(f.timeFilter, day)
        return FilterKey(
            s, e, f.timeFilter,
            f.subjectFilters.ifEmpty { null },
            f.categoryFilters.ifEmpty { null },
            f.typeFilters.ifEmpty { null },
        )
    }

    private val key: Flow<FilterKey> = combine(_filters, today, ::keyOf).distinctUntilChanged()

    /**
     * [key] with the direction filter, for the three queries that use it. Built from the same
     * emission, so a change to both (Clear) restarts them once, not twice.
     */
    private val natureKey: Flow<Pair<FilterKey, Boolean?>> =
        combine(_filters, today) { f, day -> keyOf(f, day) to f.natureFilter }.distinctUntilChanged()

    // ---------------- Cash flow tab: balance, flow, costs ----------------
    /** The two bar series of one query, labelled for the time filter the rows were queried for. */
    private class CashFlowRows(val flow: List<CashFlowWeek>, val costs: List<CostMonth>)

    private val cashFlowRows: StateFlow<CashFlowRows?> = key.flatMapLatest { k ->
        repository.getCashFlow(bucketFormatFor(k.time), k.start, k.end, k.subjects, k.categories, k.types)
            .map { rows ->
                val years = yearStarts(rows.map { it.bucket })
                val labels = rows.map { bucketLabel(it.bucket, k.time) }
                CashFlowRows(
                    rows.mapIndexed { i, r ->
                        CashFlowWeek(labels[i], r.income.toFloat(), r.expense.toFloat(), years[i])
                    },
                    rows.mapIndexed { i, r ->
                        CostMonth(labels[i], r.expense.toFloat(), r.cost.toFloat(), years[i])
                    },
                )
            }
    }.stateIn(viewModelScope, started, null)

    val cashFlow: StateFlow<List<CashFlowWeek>?> =
        cashFlowRows.map { it?.flow }.stateIn(viewModelScope, started, null)

    val costs: StateFlow<List<CostMonth>?> =
        cashFlowRows.map { it?.costs }.stateIn(viewModelScope, started, null)

    // The balance reads only the time range, so it restarts on that alone.
    val balanceTrend: StateFlow<List<BalancePoint>?> =
        combine(_filters.map { it.timeFilter }.distinctUntilChanged(), today) { tf, day ->
            val (s, e) = getDatesForFilter(tf, day)
            Triple(tf, s, e)
        }.distinctUntilChanged().flatMapLatest { (tf, s, e) ->
            repository.getBalanceTrend(balanceBucketFormatFor(tf), s, e)
                .map { rows -> balancePoints(rows) }
        }.stateIn(viewModelScope, started, null)

    // ---------------- headline stats (net / in / out / fees) ----------------
    val stats: StateFlow<IncExp?> = key.flatMapLatest { k ->
        repository.getIncomeVsExpense(k.start, k.end, k.subjects, k.categories, k.types)
    }.stateIn(viewModelScope, started, null)

    // ---------------- Spending tab: breakdown, subjects (cross-filtered), income tree ----------------
    /** The rows the breakdown shows, and whether they are by type (chosen, or because there are no categories). */
    private class BreakdownRows(val byType: Boolean, val rows: List<CategorySpending>)

    private fun typeRows(k: FilterKey) = repository.getTypeSpending(
        k.start, k.end, k.subjects, k.categories, k.types, natureFilter = false
    ).map { BreakdownRows(true, it) }

    // One query for the selected mode; the type query runs only when there are no categories.
    private val breakdownRows: Flow<BreakdownRows> =
        combine(key, _breakdownMode, ::Pair).flatMapLatest { (k, mode) ->
            if (mode == Breakdown.TYPE) typeRows(k)
            else repository.getCategorySpending(
                k.start, k.end, k.subjects, k.categories, k.types, natureFilter = false
            ).flatMapLatest { cat ->
                if (cat.isEmpty()) typeRows(k) else flowOf(BreakdownRows(false, cat))
            }
        }

    val breakdown: StateFlow<List<SliceValue>?> = combine(
        breakdownRows, repository.getCategories(), repository.getTransactionTypes(),
    ) { b, categories, types ->
        if (b.byType) typeSlices(b.rows, types)
        else categorySlices(b.rows, categories.map { NamedRef(it.id, it.name) })
    }.stateIn(viewModelScope, started, null)

    /** Budgeted categories with this calendar month's spending (not affected by the filters). */
    val budgets: StateFlow<List<CategoryBudget>> =
        repository.getBudgets().stateIn(viewModelScope, started, emptyList())

    val filterOptions: StateFlow<FilterOptions?> =
        repository.getFilterOptions().stateIn(viewModelScope, started, null)

    /** Top merchants, narrowed to the breakdown row that is selected, in Category and Type mode alike. */
    val topSubjects: StateFlow<List<MerchantSpend>?> =
        combine(key, breakdown, _selectedSlice) { fk, rows, slice -> Triple(fk, rows, slice) }
            .flatMapLatest { (k, rows, slice) ->
                if (rows == null) return@flatMapLatest emptyFlow()
                val (categories, types) = sliceFilter(rows, slice, k.categories, k.types)
                repository.getTopSubjectsByAmount(
                    k.start, k.end, k.subjects, categories, types,
                    natureFilter = false,
                    limit = 8,
                ).map { list -> list.map { MerchantSpend(it.name, it.amount) } }
            }.stateIn(viewModelScope, started, null)

    /** The Overview "Top out" line: the biggest merchant for the filters, not affected by the selected slice. */
    val topMerchant: StateFlow<List<MerchantSpend>?> = key.flatMapLatest { k ->
        repository.getTopSubjectsByAmount(
            k.start, k.end, k.subjects, k.categories, k.types, natureFilter = false, limit = 1,
        ).map { list -> list.map { MerchantSpend(it.name, it.amount) } }
    }.stateIn(viewModelScope, started, null)

    val incomeSources: StateFlow<List<SourceAmount>?> = key.flatMapLatest { k ->
        repository.getIncomeBySource(k.start, k.end, k.subjects, k.categories, k.types, 8)
    }.stateIn(viewModelScope, started, null)

    // ---------------- Activity tab ----------------
    val activityRaw: StateFlow<List<ActivityCell>?> =
        natureKey.flatMapLatest { (k, nature) ->
            repository.getActivityByTimeOfDay(
                k.start, k.end, k.subjects, k.categories, k.types, nature
            )
        }.stateIn(viewModelScope, started, null)

    /** Weekday counts with the time filter they were queried for, so the daily average divides by that range. */
    private class WeekdayRows(val time: TimeFilter, val counts: List<WeekdayCount>)

    private val weekdayRaw: StateFlow<WeekdayRows?> =
        natureKey.flatMapLatest { (k, nature) ->
            repository.getWeekdayCounts(
                k.start, k.end, k.subjects, k.categories, k.types, nature
            ).map { WeekdayRows(k.time, it) }
        }.stateIn(viewModelScope, started, null)

    val heat: StateFlow<List<HeatRow>?> = activityRaw.map { it?.let(::toHeatRows) }
        .stateIn(viewModelScope, started, null)

    val weekdayBars: StateFlow<List<WeekdayBar>?> =
        weekdayRaw.map { it?.let { w -> toWeekdayBars(w.counts) } }
            .stateIn(viewModelScope, started, null)

    val leaderboard: StateFlow<List<SubjectCount>?> = natureKey.flatMapLatest { (k, nature) ->
        repository.getTopSubjectsByCount(
            k.start, k.end, k.subjects, k.categories, k.types, nature, 10
        )
    }.stateIn(viewModelScope, started, null)

    /**
     * One day-totals query wide enough for both summaries that read it, ending tomorrow: the streak
     * (its own 13 weeks, independent of the time chip) and the pace (last month's first day on).
     * Each summary keeps its own days in Kotlin. Both ignore the filters; expense days only.
     */
    private class DailyWindow(val today: LocalDate) {
        val streakStart: LocalDate = today.minusWeeks(13)
        val paceStart: LocalDate = today.minusMonths(1).withDayOfMonth(1)
        val from: String = minOf(streakStart, paceStart).atStartOfDay().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val to: String = today.plusDays(1).atStartOfDay().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    }

    private class DailyRows(val window: DailyWindow, val days: List<DailyTotal>)

    private val dailyWindow: Flow<DailyWindow> = today.map { DailyWindow(it) }

    private val dailyTotals: StateFlow<DailyRows?> = dailyWindow.flatMapLatest { w ->
        repository.getDailyTotals(w.from, w.to, natureFilter = false).map { DailyRows(w, it) }
    }.stateIn(viewModelScope, started, null)

    /** The streak and the days it covers, from one emission so the grid never mixes two days. */
    class StreakState(val summary: StreakSummary, val window: ClosedRange<LocalDate>)

    val streak: StateFlow<StreakState?> = dailyTotals
        .map { r ->
            r?.let {
                val w = it.window
                StreakState(
                    computeStreaks(daysFrom(it.days, w.streakStart), w.streakStart, w.today),
                    w.streakStart..w.today,
                )
            }
        }
        .stateIn(viewModelScope, started, null)

    val pace: StateFlow<PaceSummary?> = dailyTotals
        .map { r -> r?.let { computePace(daysFrom(it.days, it.window.paceStart), it.window.today) } }
        .stateIn(viewModelScope, started, null)

    /** Real span of recorded history, for the ALL filter. Null until loaded. */
    private val dataSpanDays: StateFlow<Int?> =
        combine(repository.selectedAccount, today) { _, day -> day }.mapLatest { day ->
            val first = repository.getEarliestTransactionDate()
            first?.let { (ChronoUnit.DAYS.between(it.toLocalDate(), day) + 1).toInt() }
        }.stateIn(viewModelScope, onceStarted, null)

    /** Which line these numbers are for: its name, or "All accounts"; null when there is only one line. */
    val scopeLabel: StateFlow<String?> =
        combine(repository.selectedAccount, repository.getAccountBalances()) { selected, lines ->
            scopeLabel(selected, lines.map { it.accountId to it.name })
        }.stateIn(viewModelScope, started, null)

    val rhythm: StateFlow<RhythmStats?> =
        combine(weekdayRaw, activityRaw, streak, dataSpanDays, today) { wk, act, st, spanDays, day ->
            if (wk == null || act == null || st == null) return@combine null
            computeRhythm(
                wk.counts,
                act,
                totalCount = wk.counts.sumOf { it.count },
                rangeDays = daysForFilter(wk.time, spanDays, day),
                longestNoSpend = st.summary.longestNoSpend
            )
        }.stateIn(viewModelScope, started, null)


    val recurring: StateFlow<List<RecurringPayment>?> =
        repository.selectedAccount.mapLatest { repository.getRecurringPayments() }.stateIn(
            viewModelScope,
            onceStarted,
            null
        )

    private val _subjectQuery = MutableStateFlow("")
    val subjectQuery: StateFlow<String> = _subjectQuery.asStateFlow()
    fun setSubjectQuery(q: String) {
        _subjectQuery.value = q
    }

    private val _selectedSubjects = MutableStateFlow<List<NamedRef<Long>>>(emptyList())
    val selectedSubjects: StateFlow<List<NamedRef<Long>>> = _selectedSubjects.asStateFlow()

    fun addSubject(ref: NamedRef<Long>) {
        if (_selectedSubjects.value.none { it.id == ref.id }) {
            _selectedSubjects.value += ref
            setSubjectFilters(_selectedSubjects.value.map { it.id })
        }
    }

    fun removeSubject(ref: NamedRef<Long>) {
        _selectedSubjects.value = _selectedSubjects.value.filterNot { it.id == ref.id }
        setSubjectFilters(_selectedSubjects.value.map { it.id })
    }

    val subjectResults: StateFlow<List<NamedRef<Long>>> = _subjectQuery
        .debounce(250.milliseconds).distinctUntilChanged()
        .mapLatest { q -> if (q.isBlank()) emptyList() else repository.searchSubjects(q.trim()) }
        .stateIn(viewModelScope, started, emptyList())
}
