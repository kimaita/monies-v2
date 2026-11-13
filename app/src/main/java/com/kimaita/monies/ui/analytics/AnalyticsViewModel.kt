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
import com.kimaita.monies.services.RhythmStats
import com.kimaita.monies.services.StreakSummary
import com.kimaita.monies.services.computeRhythm
import com.kimaita.monies.services.computeStreaks
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
data class SliceValue(val label: String, val value: Float)

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

    private val _breakdown = MutableStateFlow(Breakdown.CATEGORY)
    val breakdown: StateFlow<Breakdown> = _breakdown.asStateFlow()

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

    fun setBreakdown(b: Breakdown) {
        _breakdown.value = b; _selectedSlice.value = null
    }

    fun selectSlice(label: String?) {
        _selectedSlice.value = if (_selectedSlice.value == label) null else label
    }

    fun clearFilters() = _filters.update {
        TransactionFilters(timeFilter = it.timeFilter)
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
    private val cashFlowRaw: StateFlow<List<CashFlowBucket>> = key.flatMapLatest { k ->
        repository.getCashFlow(k.bucket, k.start, k.end, k.subjects, k.categories, k.types)
    }.stateIn(viewModelScope, started, emptyList())

    val cashFlow: StateFlow<List<CashFlowWeek>> = combine(cashFlowRaw, _filters) { rows, f ->
        rows.map {
            CashFlowWeek(
                bucketLabel(it.bucket, f.timeFilter),
                it.income.toFloat(),
                it.expense.toFloat()
            )
        }
    }.stateIn(viewModelScope, started, emptyList())

    val costs: StateFlow<List<CostMonth>> = combine(cashFlowRaw, _filters) { rows, f ->
        rows.map {
            CostMonth(
                bucketLabel(it.bucket, f.timeFilter),
                it.expense.toFloat(),
                it.cost.toFloat()
            )
        }
    }.stateIn(viewModelScope, started, emptyList())

    val balanceTrend: StateFlow<List<BalancePoint>> = _filters.flatMapLatest { f ->
        val (s, e) = getDatesForFilter(f.timeFilter)
        repository.getBalanceTrend(bucketFormatFor(f.timeFilter), s, e)
            .map { rows -> rows.map { it.toPoint(f.timeFilter) } }
    }.stateIn(viewModelScope, started, emptyList())

    private fun BalanceBucket.toPoint(tf: TimeFilter) =
        BalancePoint(bucketLabel(bucket, tf), balance)

    // ---------------- headline stats (net / in / out / fees) ----------------
    val stats: StateFlow<IncExp> = key.flatMapLatest { k ->
        repository.getIncomeVsExpense(k.start, k.end, k.subjects, k.categories, k.types)
    }.stateIn(viewModelScope, started, IncExp())

    // ---------------- Spending tab: donut, subjects (cross-filtered), income tree ----------------
    private val categorySpending =
        key.flatMapLatest { k ->
            repository.getCategorySpending(
                k.start, k.end, k.subjects, k.categories, k.types, natureFilter = false
            )
        }.stateIn(viewModelScope, started, emptyList())
    private val typeSpending = key.flatMapLatest { k ->
        repository.getTypeSpending(
            k.start, k.end, k.subjects, k.categories, k.types, natureFilter = false
        )
    }.stateIn(viewModelScope, started, emptyList())

    val donut: StateFlow<List<SliceValue>> =
        combine(_breakdown, categorySpending, typeSpending) { mode, cat, type ->
            val chosen = if (mode == Breakdown.CATEGORY) cat else type
            (chosen.ifEmpty { type })
                .sortedByDescending { it.total }
                .map { SliceValue(it.name, it.total.toFloat()) }
        }.stateIn(viewModelScope, started, emptyList())

    /** Budgeted categories with this calendar month's spending (not affected by the filters). */
    val budgets: StateFlow<List<CategoryBudget>> =
        repository.getBudgets().stateIn(viewModelScope, started, emptyList())

    val filterOptions: StateFlow<FilterOptions?> =
        flow { emit(repository.getFilterOptions()) }.stateIn(viewModelScope, onceStarted, null)

    private data class SpendKey(
        val key: FilterKey,
        val mode: Breakdown,
        val slice: String?,
        val opts: FilterOptions?
    )

    val topSubjects: StateFlow<List<MerchantSpend>> =
        combine(
            key,
            _breakdown,
            _selectedSlice,
            filterOptions
        ) { fk, mode, slice, opts -> SpendKey(fk, mode, slice, opts) }
            .flatMapLatest { k ->
                val catId =
                    if (k.mode == Breakdown.CATEGORY) k.opts?.categories?.firstOrNull { it.name == k.slice }?.id else null
                val typeId =
                    if (k.mode == Breakdown.TYPE) k.opts?.types?.firstOrNull { it.name == k.slice }?.id else null
                repository.getTopSubjectsByAmount(
                    k.key.start,
                    k.key.end,
                    k.key.subjects,
                    catId?.let { listOf(it) } ?: k.key.categories,
                    typeId?.let { listOf(it) } ?: k.key.types,
                    natureFilter = false,
                    limit = 8,
                ).map { list -> list.map { MerchantSpend(it.name, it.amount) } }
            }.stateIn(viewModelScope, started, emptyList())

    val incomeSources: StateFlow<List<SourceAmount>> = key.flatMapLatest { k ->
        repository.getIncomeBySource(k.start, k.end, k.subjects, k.categories, k.types, 8)
    }.stateIn(viewModelScope, started, emptyList())

    // ---------------- Activity tab ----------------
    private val activityRaw: StateFlow<List<ActivityCell>> =
        key.flatMapLatest { k ->
            repository.getActivityByTimeOfDay(
                k.start, k.end, k.subjects, k.categories, k.types, k.nature
            )
        }.stateIn(viewModelScope, started, emptyList())

    private val weekdayRaw: StateFlow<List<WeekdayCount>> =
        key.flatMapLatest { k ->
            repository.getWeekdayCounts(
                k.start, k.end, k.subjects, k.categories, k.types, k.nature
            )
        }.stateIn(viewModelScope, started, emptyList())

    val heat: StateFlow<List<HeatRow>> = activityRaw.map { toHeatRows(it) }
        .stateIn(viewModelScope, started, emptyList())

    val weekdayBars: StateFlow<List<WeekdayBar>> = weekdayRaw.map { toWeekdayBars(it) }
        .stateIn(viewModelScope, started, emptyList())

    val leaderboard: StateFlow<List<SubjectCount>> = key.flatMapLatest { k ->
        repository.getTopSubjectsByCount(
            k.start, k.end, k.subjects, k.categories, k.types, k.nature, 10
        )
    }.stateIn(viewModelScope, started, emptyList())

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
        .stateIn(viewModelScope, started, emptyList())

    val streak: StateFlow<StreakSummary> = streakDaily
        .map { computeStreaks(it, streakStart, streakEnd) }
        .stateIn(viewModelScope, started, StreakSummary(0, 0, 0, emptyMap()))

    /** Real span of recorded history, for the ALL filter. Null until loaded. */
    private val dataSpanDays: StateFlow<Int?> =
        flow {
            val first = repository.getEarliestTransactionDate()
            emit(first?.let {
                (ChronoUnit.DAYS.between(it.toLocalDate(), LocalDate.now()) + 1).toInt()
            })
        }.stateIn(viewModelScope, onceStarted, null)

    val rhythm: StateFlow<RhythmStats> =
        combine(
            weekdayRaw,
            activityRaw,
            streak,
            _filters,
            dataSpanDays
        ) { wk, act, st, f, spanDays ->
            computeRhythm(
                wk,
                act,
                totalCount = wk.sumOf { it.count },
                rangeDays = daysForFilter(f.timeFilter, spanDays),
                longestNoSpend = st.longestNoSpend
            )
        }.stateIn(viewModelScope, started, RhythmStats(null, null, 0.0, 0))


    val recurring: StateFlow<List<RecurringPayment>> =
        repository.selectedAccount.mapLatest { repository.getRecurringPayments() }.stateIn(
            viewModelScope,
            onceStarted,
            emptyList()
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