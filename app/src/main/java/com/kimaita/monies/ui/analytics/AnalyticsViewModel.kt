package com.kimaita.monies.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kimaita.monies.data.TransactionsRepository
import com.kimaita.monies.data.dao.dtos.CategoryBudget
import com.kimaita.monies.data.dao.dtos.ActivityCell
import com.kimaita.monies.data.dao.dtos.BalanceBucket
import com.kimaita.monies.data.dao.dtos.CashFlowBucket
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
import kotlinx.coroutines.flow.flow
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

data class WeekdayBar(val label: String, val count: Int)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val repository: TransactionsRepository,
) : ViewModel() {

    private val started = SharingStarted.WhileSubscribed(5_000)
    private val onceStarted = SharingStarted.Lazily

    private val _filters = MutableStateFlow(TransactionFilters(timeFilter = TimeFilter.MONTH))
    val filters: StateFlow<TransactionFilters> = _filters.asStateFlow()

    private val _direction = MutableStateFlow(SpendDirection.OUT)
    val direction: StateFlow<SpendDirection> = _direction.asStateFlow()

    private val _breakdownMode = MutableStateFlow(Breakdown.CATEGORY)
    val breakdownMode: StateFlow<Breakdown> = _breakdownMode.asStateFlow()

    private val _selectedSlice = MutableStateFlow<String?>(null)
    val selectedSlice: StateFlow<String?> = _selectedSlice.asStateFlow()

    fun setTimeFilter(f: TimeFilter) = _filters.update { it.copy(timeFilter = f) }
    fun setNature(n: Boolean?) = _filters.update { it.copy(natureFilter = n) }
    fun setTypeFilters(ids: List<Int>) = _filters.update { it.copy(typeFilters = ids) }
    fun setCategoryFilters(ids: List<Int>) = _filters.update { it.copy(categoryFilters = ids) }
    fun setSubjectFilters(ids: List<Long>) = _filters.update { it.copy(subjectFilters = ids) }
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
        _filters.update { TransactionFilters(timeFilter = it.timeFilter) }
        _selectedSubjects.value = emptyList()
        _subjectQuery.value = ""
    }

    // ---------------- derived keys ----------------
    private data class FilterKey(
        val start: String?, val end: String?, val bucket: String,
        val subjects: List<Long>?, val categories: List<Int>?, val types: List<Int>?,
        val nature: Boolean?,
    )

    private val key: Flow<FilterKey> = _filters.map { f ->
        val (s, e) = getDatesForFilter(f.timeFilter)
        FilterKey(
            s, e, bucketFormatFor(f.timeFilter),
            f.subjectFilters.ifEmpty { null },
            f.categoryFilters.ifEmpty { null },
            f.typeFilters.ifEmpty { null },
            f.natureFilter
        )
    }.distinctUntilChanged()

    // ---------------- Cash flow tab: balance, flow, costs ----------------
    private val cashFlowRaw: StateFlow<List<CashFlowBucket>?> = key.flatMapLatest { k ->
        repository.getCashFlow(k.bucket, k.start, k.end, k.subjects, k.categories, k.types)
    }.stateIn(viewModelScope, started, null)

    val cashFlow: StateFlow<List<CashFlowWeek>?> = combine(cashFlowRaw, _filters) { rows, f ->
        rows?.map {
            CashFlowWeek(
                bucketLabel(it.bucket, f.timeFilter),
                it.income.toFloat(),
                it.expense.toFloat()
            )
        }
    }.stateIn(viewModelScope, started, null)

    val costs: StateFlow<List<CostMonth>?> = combine(cashFlowRaw, _filters) { rows, f ->
        rows?.map {
            CostMonth(
                bucketLabel(it.bucket, f.timeFilter),
                it.expense.toFloat(),
                it.cost.toFloat()
            )
        }
    }.stateIn(viewModelScope, started, null)

    val balanceTrend: StateFlow<List<BalancePoint>?> = _filters.flatMapLatest { f ->
        val (s, e) = getDatesForFilter(f.timeFilter)
        repository.getBalanceTrend(bucketFormatFor(f.timeFilter), s, e)
            .map { rows -> rows.map { it.toPoint(f.timeFilter) } }
    }.stateIn(viewModelScope, started, null)

    private fun BalanceBucket.toPoint(tf: TimeFilter) =
        BalancePoint(bucketLabel(bucket, tf), balance)

    // ---------------- headline stats (net / in / out / fees) ----------------
    val stats: StateFlow<IncExp?> = key.flatMapLatest { k ->
        repository.getIncomeVsExpense(k.start, k.end, k.subjects, k.categories, k.types)
    }.stateIn(viewModelScope, started, null)

    // ---------------- Spending tab: breakdown, subjects (cross-filtered), income tree ----------------
    private val categorySpending =
        key.flatMapLatest { k ->
            repository.getCategorySpending(
                k.start, k.end, k.subjects, k.categories, k.types, natureFilter = false
            )
        }.stateIn(viewModelScope, started, null)
    private val typeSpending = key.flatMapLatest { k ->
        repository.getTypeSpending(
            k.start, k.end, k.subjects, k.categories, k.types, natureFilter = false
        )
    }.stateIn(viewModelScope, started, null)

    val breakdown: StateFlow<List<SliceValue>?> = combine(
        _breakdownMode, categorySpending, typeSpending,
        repository.getCategories(), repository.getTransactionTypes(),
    ) { mode, cat, type, categories, types ->
        if (cat == null || type == null) return@combine null
        val byType = typeSlices(type, types)
        if (mode == Breakdown.TYPE) byType
        else categorySlices(cat, categories.map { NamedRef(it.id, it.name) }).ifEmpty { byType }
    }.stateIn(viewModelScope, started, null)

    /** Budgeted categories with this calendar month's spending (not affected by the filters). */
    val budgets: StateFlow<List<CategoryBudget>> =
        repository.getBudgets().stateIn(viewModelScope, started, emptyList())

    val filterOptions: StateFlow<FilterOptions?> =
        flow { emit(repository.getFilterOptions()) }.stateIn(viewModelScope, onceStarted, null)

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

    /** The Overview "Top" tile: the biggest merchant for the filters, not affected by the selected slice. */
    val topMerchant: StateFlow<List<MerchantSpend>?> = key.flatMapLatest { k ->
        repository.getTopSubjectsByAmount(
            k.start, k.end, k.subjects, k.categories, k.types, natureFilter = false, limit = 1,
        ).map { list -> list.map { MerchantSpend(it.name, it.amount) } }
    }.stateIn(viewModelScope, started, null)

    val incomeSources: StateFlow<List<SourceAmount>?> = key.flatMapLatest { k ->
        repository.getIncomeBySource(k.start, k.end, k.subjects, k.categories, k.types, 8)
    }.stateIn(viewModelScope, started, null)

    // ---------------- Activity tab ----------------
    private val activityRaw: StateFlow<List<ActivityCell>?> =
        key.flatMapLatest { k ->
            repository.getActivityByTimeOfDay(
                k.start, k.end, k.subjects, k.categories, k.types, k.nature
            )
        }.stateIn(viewModelScope, started, null)

    private val weekdayRaw: StateFlow<List<WeekdayCount>?> =
        key.flatMapLatest { k ->
            repository.getWeekdayCounts(
                k.start, k.end, k.subjects, k.categories, k.types, k.nature
            )
        }.stateIn(viewModelScope, started, null)

    val heat: StateFlow<List<HeatRow>?> = activityRaw.map { it?.let(::toHeatRows) }
        .stateIn(viewModelScope, started, null)

    val weekdayBars: StateFlow<List<WeekdayBar>?> = weekdayRaw.map { it?.let(::toWeekdayBars) }
        .stateIn(viewModelScope, started, null)

    val leaderboard: StateFlow<List<SubjectCount>?> = key.flatMapLatest { k ->
        repository.getTopSubjectsByCount(
            k.start, k.end, k.subjects, k.categories, k.types, k.nature, 10
        )
    }.stateIn(viewModelScope, started, null)

    // Streak is its own fixed 13-week window (independent of the time chip), expense days only.
    private val streakEnd: LocalDate = LocalDate.now()
    private val streakStart: LocalDate = streakEnd.minusWeeks(13)
    val streakWindow: ClosedRange<LocalDate> = streakStart..streakEnd
    private val streakDaily = repository
        .getDailyTotals(
            streakStart.atStartOfDay().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            streakEnd.plusDays(1).atStartOfDay().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            natureFilter = false
        )
        .stateIn(viewModelScope, started, null)

    val streak: StateFlow<StreakSummary?> = streakDaily
        .map { it?.let { days -> computeStreaks(days, streakStart, streakEnd) } }
        .stateIn(viewModelScope, started, null)

    // Spending pace: last month's first day to tomorrow; ignores the time chip and filters.
    val pace: StateFlow<PaceSummary?> = repository
        .getDailyTotals(
            LocalDate.now().minusMonths(1).withDayOfMonth(1).atStartOfDay()
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            LocalDate.now().plusDays(1).atStartOfDay().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            natureFilter = false
        )
        .map<_, PaceSummary?> { computePace(it, LocalDate.now()) }
        .stateIn(viewModelScope, started, null)

    /** Real span of recorded history, for the ALL filter. Null until loaded. */
    private val dataSpanDays: StateFlow<Int?> =
        repository.selectedAccount.mapLatest {
            val first = repository.getEarliestTransactionDate()
            first?.let { (ChronoUnit.DAYS.between(it.toLocalDate(), LocalDate.now()) + 1).toInt() }
        }.stateIn(viewModelScope, onceStarted, null)

    /** Which line these numbers are for: its name, or "All accounts"; null when there is only one line. */
    val scopeLabel: StateFlow<String?> =
        combine(repository.selectedAccount, repository.getAccountBalances()) { selected, lines ->
            scopeLabel(selected, lines.map { it.accountId to it.name })
        }.stateIn(viewModelScope, started, null)

    val rhythm: StateFlow<RhythmStats?> =
        combine(
            weekdayRaw,
            activityRaw,
            streak,
            _filters,
            dataSpanDays
        ) { wk, act, st, f, spanDays ->
            if (wk == null || act == null || st == null) return@combine null
            computeRhythm(
                wk,
                act,
                totalCount = wk.sumOf { it.count },
                rangeDays = daysForFilter(f.timeFilter, spanDays),
                longestNoSpend = st.longestNoSpend
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
        .mapLatest { q -> if (q.isBlank()) repository.topSubjects() else repository.searchSubjects(q.trim()) }
        .stateIn(viewModelScope, started, emptyList())

    // ---------------- helpers ----------------
    private fun bucketFormatFor(tf: TimeFilter): String = when (tf) {
        TimeFilter.DAY -> "%Y-%m-%d %H"
        TimeFilter.WEEK -> "%Y-%m-%d"
        TimeFilter.MONTH, TimeFilter.QUARTER -> "%Y-%W"
        TimeFilter.SIX_MONTHS, TimeFilter.YTD, TimeFilter.ALL -> "%Y-%m"
        is TimeFilter.CustomRange -> "%Y-%m-%d"
    }

    private val monthAbbrev =
        arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    private fun bucketLabel(bucket: String, tf: TimeFilter): String = when (tf) {
        TimeFilter.DAY -> bucket.substringAfter(" ", bucket) + "h"
        TimeFilter.WEEK, is TimeFilter.CustomRange -> bucket.substringAfterLast("-")
        TimeFilter.MONTH, TimeFilter.QUARTER -> "Wk " + bucket.substringAfterLast("-")
        else -> bucket.substringAfterLast("-").toIntOrNull()
            ?.let { monthAbbrev.getOrElse(it - 1) { bucket } } ?: bucket
    }

    private fun daysForFilter(tf: TimeFilter, spanDays: Int?): Int = when (tf) {
        TimeFilter.DAY -> 1
        TimeFilter.WEEK -> 7
        TimeFilter.MONTH -> 30
        TimeFilter.QUARTER -> 90
        TimeFilter.SIX_MONTHS -> 182
        TimeFilter.YTD -> LocalDate.now().dayOfYear
        TimeFilter.ALL -> spanDays ?: 1
        is TimeFilter.CustomRange ->
            (ChronoUnit.DAYS.between(
                tf.startDate.toLocalDate(),
                tf.endDate.toLocalDate()
            ) + 1).toInt()
                .coerceAtLeast(1)
    }

    private val weekdayOrder = listOf(1, 2, 3, 4, 5, 6, 0)
    private val weekdayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    private fun toHeatRows(cells: List<ActivityCell>): List<HeatRow> {
        val map = cells.associate { (it.weekday to it.slot) to it.count }
        return weekdayOrder.mapIndexed { i, wd ->
            HeatRow(
                weekdayNames[i],
                (0..3).map { slot -> map[wd to slot] ?: 0 })
        }
    }

    private fun toWeekdayBars(counts: List<WeekdayCount>): List<WeekdayBar> {
        val map = counts.associate { it.weekday to it.count }
        return weekdayOrder.mapIndexed { i, wd -> WeekdayBar(weekdayNames[i], map[wd] ?: 0) }
    }

}