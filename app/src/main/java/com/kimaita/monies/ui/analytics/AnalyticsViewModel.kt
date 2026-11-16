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
import com.kimaita.monies.data.dao.dtos.SubjectCount
import com.kimaita.monies.data.dao.dtos.WeekdayCount
import com.kimaita.monies.models.StatsFilter
import com.kimaita.monies.models.TimeFilter
import com.kimaita.monies.models.TransactionFilters
import com.kimaita.monies.models.analytics.BalancePoint
import com.kimaita.monies.models.analytics.RecurringPayment
import com.kimaita.monies.models.export.FilterOptions
import com.kimaita.monies.models.export.NamedRef
import com.kimaita.monies.models.getDatesForFilter
import com.kimaita.monies.services.PaceSummary
import com.kimaita.monies.services.feesByType
import com.kimaita.monies.data.dao.dtos.TypeFees
import com.kimaita.monies.services.weekDays
import com.kimaita.monies.services.dayRangeLabel
import com.kimaita.monies.services.PacePeriod
import com.kimaita.monies.services.pacePeriod
import com.kimaita.monies.services.computePace
import com.kimaita.monies.services.projectPeriodEnd
import com.kimaita.monies.services.stillDue
import com.kimaita.monies.services.early
import kotlinx.coroutines.flow.filterNotNull
import com.kimaita.monies.services.mostlyCategory
import com.kimaita.monies.services.paydayDate
import com.kimaita.monies.services.flowSides
import com.kimaita.monies.services.moneyWalk
import com.kimaita.monies.services.FlowSides
import com.kimaita.monies.services.MoneyWalk
import com.kimaita.monies.services.balanceBucketFormatFor
import com.kimaita.monies.services.bucketFormatFor
import com.kimaita.monies.services.bucketLabel
import com.kimaita.monies.services.daysForFilter
import com.kimaita.monies.services.daysFrom
import com.kimaita.monies.services.SliceValue
import com.kimaita.monies.services.StreakSummary
import com.kimaita.monies.services.categorySlices
import com.kimaita.monies.services.computeStreaks
import com.kimaita.monies.services.sliceFilter
import com.kimaita.monies.services.toggleSlice
import com.kimaita.monies.services.typeSlices
import com.kimaita.monies.services.scopeLabel
import com.kimaita.monies.services.balancePoints
import com.kimaita.monies.services.yearStarts
import com.kimaita.monies.ui.charts.BarBucket
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
import com.kimaita.monies.data.dao.dtos.DayPayment
import com.kimaita.monies.data.dao.dtos.SourceAmount
import com.kimaita.monies.data.dao.dtos.SubjectAmount
import com.kimaita.monies.data.database.models.TransactionType
import com.kimaita.monies.services.History
import com.kimaita.monies.services.historyFor
import com.kimaita.monies.services.previousRange
import com.kimaita.monies.services.DAYS_PER_MONTH
import com.kimaita.monies.services.MonthCalendar
import com.kimaita.monies.services.Streak
import com.kimaita.monies.services.calendarRange
import com.kimaita.monies.services.dueDates
import com.kimaita.monies.services.monthCalendar
import com.kimaita.monies.services.streakNow
import java.time.LocalDate
import java.time.YearMonth
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

    val timeFilter: StateFlow<TimeFilter> = _filters.map { it.timeFilter }.distinctUntilChanged()
        .stateIn(viewModelScope, started, _filters.value.timeFilter)

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

    /** What most queries restart on. [time] is here, not only its dates, so labels follow their rows. */
    private data class FilterKey(val stats: StatsFilter, val time: TimeFilter)

    private fun keyOf(f: TransactionFilters, day: LocalDate): FilterKey {
        val (s, e) = getDatesForFilter(f.timeFilter, day)
        return FilterKey(
            StatsFilter(
                s, e,
                f.subjectFilters.ifEmpty { null },
                f.categoryFilters.ifEmpty { null },
                f.typeFilters.ifEmpty { null },
            ),
            f.timeFilter,
        )
    }

    private val key: Flow<FilterKey> = combine(_filters, today, ::keyOf).distinctUntilChanged()

    /**
     * [key] with the direction filter, for the three queries that use it. Built from the same
     * emission, so a change to both (Clear) restarts them once, not twice.
     */
    private val natureKey: Flow<Pair<FilterKey, Boolean?>> =
        combine(_filters, today) { f, day -> keyOf(f, day) to f.natureFilter }.distinctUntilChanged()

    /** The two bar series of one query, labelled for the time filter the rows were queried for. */
    private class CashFlowRows(val flow: List<BarBucket>, val costs: List<BarBucket>)

    private val cashFlowRows: StateFlow<CashFlowRows?> = key.flatMapLatest { k ->
        repository.getCashFlow(bucketFormatFor(k.time), k.stats)
            .map { rows ->
                val years = yearStarts(rows.map { it.bucket })
                val labels = rows.map { bucketLabel(it.bucket, k.time) }
                CashFlowRows(
                    rows.mapIndexed { i, r ->
                        BarBucket(
                            labels[i], r.income.toFloat(), r.expense.toFloat(), years[i],
                            if (k.time == TimeFilter.MONTH) weekDays(r.bucket)?.let(::dayRangeLabel) else null,
                        )
                    },
                    rows.mapIndexed { i, r ->
                        BarBucket(labels[i], r.cost.toFloat(), year = years[i])
                    },
                )
            }
    }.stateIn(viewModelScope, started, null)

    val cashFlow: StateFlow<List<BarBucket>?> =
        cashFlowRows.map { it?.flow }.stateIn(viewModelScope, started, null)

    val costs: StateFlow<List<BarBucket>?> =
        cashFlowRows.map { it?.costs }.stateIn(viewModelScope, started, null)

    /** The period's dates alone: what the balance, and the money that moved it, are counted over. */
    private data class Period(val time: TimeFilter, val start: String?, val end: String?)

    private val period: Flow<Period> =
        combine(_filters.map { it.timeFilter }.distinctUntilChanged(), today) { tf, day ->
            val (s, e) = getDatesForFilter(tf, day)
            Period(tf, s, e)
        }.distinctUntilChanged()

    private class BalanceRows(val period: Period, val points: List<BalancePoint>)

    private val balanceRows: StateFlow<BalanceRows?> = period.flatMapLatest { p ->
        repository.getBalanceTrend(balanceBucketFormatFor(p.time), p.start, p.end)
            .map { rows -> BalanceRows(p, balancePoints(rows)) }
    }.stateIn(viewModelScope, started, null)

    val balanceTrend: StateFlow<List<BalancePoint>?> =
        balanceRows.map { it?.points }.stateIn(viewModelScope, started, null)

    /**
     * The balance over the period and what moved it, filters aside. [start] is the period's first day
     * (the first point's for ALL); [paydayAt] indexes [points].
     */
    class MoneyState(
        val time: TimeFilter,
        val start: LocalDate?,
        val points: List<BalancePoint>,
        val walk: MoneyWalk,
        val flow: FlowSides,
        val paydayAt: Int?,
        /** The day payday fell on, and the biggest payment out (a quarter or more of spending) with its category. */
        val payday: LocalDate?,
        val bigOut: Pair<LocalDate, String>?,
    )

    val money: StateFlow<MoneyState?> = balanceRows.filterNotNull().flatMapLatest { b ->
        val p = b.period
        combine(
            repository.getWalkTotals(p.start, p.end),
            repository.getOpeningBalance(p.start),
            repository.getIncomeBySource(StatsFilter(p.start, p.end), 50),
            repository.getLargestPayments(p.start, p.end),
        ) { totals, opening, sources, largest ->
            val points = b.points
            val closing = points.lastOrNull()?.balance?.toDouble() ?: opening ?: 0.0
            val walk = moneyWalk(totals, opening ?: points.firstOrNull()?.balance?.toDouble() ?: closing, closing)
            val payday = paydayDate(largest, totals.income)
            MoneyState(
                p.time,
                p.start?.let { LocalDate.parse(it.take(10)) } ?: points.firstOrNull()?.date,
                points,
                walk,
                flowSides(walk, sources.map { it.name to it.amount }),
                payday?.let { d -> points.indices.filter { points[it].date == d }.maxByOrNull { points[it].balance } },
                payday,
                largest.firstOrNull { !it.isInc && it.amount >= totals.expense / 4 && it.category != null }
                    ?.let { LocalDate.parse(it.time.take(10)) to it.category!!.lowercase() },
            )
        }
    }.stateIn(viewModelScope, started, null)

    val stats: StateFlow<IncExp?> = key.flatMapLatest { k ->
        repository.getIncomeVsExpense(k.stats)
    }.stateIn(viewModelScope, started, null)

    /** Fees by transaction type for the filters, with how many transactions paid them. */
    val typeFees: StateFlow<List<TypeFees>?> =
        combine(key.flatMapLatest { repository.getTypeFees(it.stats) }, repository.getTransactionTypes()) { rows, types ->
            feesByType(rows, types)
        }.stateIn(viewModelScope, started, null)

    /** The rows the breakdown shows, and whether they are by type (chosen, or because there are no categories). */
    private class BreakdownRows(val byType: Boolean, val rows: List<CategorySpending>)

    private fun typeRows(k: FilterKey) = repository.getTypeSpending(k.stats, natureFilter = false).map { BreakdownRows(true, it) }

    private val breakdownRows: StateFlow<BreakdownRows?> =
        combine(key, _breakdownMode, ::Pair).flatMapLatest { (k, mode) ->
            if (mode == Breakdown.TYPE) typeRows(k)
            else repository.getCategorySpending(k.stats, natureFilter = false).flatMapLatest { cat ->
                if (cat.isEmpty()) typeRows(k) else flowOf(BreakdownRows(false, cat))
            }
        }.stateIn(viewModelScope, started, null)

    private fun slices(b: BreakdownRows, categories: List<NamedRef<Int>>, types: List<TransactionType>) =
        if (b.byType) typeSlices(b.rows, types) else categorySlices(b.rows, categories)

    private val categoryRefs = repository.getCategories().map { list -> list.map { NamedRef(it.id, it.name) } }

    val breakdown: StateFlow<List<SliceValue>?> = combine(
        breakdownRows, categoryRefs, repository.getTransactionTypes(),
    ) { b, categories, types -> b?.let { slices(it, categories, types) } }
        .stateIn(viewModelScope, started, null)

    /** Whether the breakdown is by type, chosen or because nothing has a category. */
    val breakdownByType: StateFlow<Boolean> = breakdownRows.map { it?.byType == true }
        .stateIn(viewModelScope, started, false)

    /** The filters' range moved back to the period before, of the same length; null for ALL. */
    private val previousKey: Flow<StatsFilter?> = combine(_filters, today) { f, day ->
        previousRange(f.timeFilter, day)?.let { (s, e) -> keyOf(f, day).stats.copy(start = s.toString(), end = e.toString()) }
    }.distinctUntilChanged()

    /**
     * The breakdown's rows over the period before, grouped the same way as now (by type when now is),
     * label to amount. Null for ALL; empty while that period has nothing.
     */
    val breakdownBefore: StateFlow<Map<String, Double>?> =
        combine(breakdownRows, previousKey, ::Pair)
            .flatMapLatest { (b, prev) ->
                if (b == null || prev == null) flowOf(null)
                else {
                    val rows = if (b.byType) repository.getTypeSpending(prev, natureFilter = false).map { BreakdownRows(true, it) }
                    else repository.getCategorySpending(prev, natureFilter = false).map { BreakdownRows(false, it) }
                    combine(rows, categoryRefs, repository.getTransactionTypes()) { r, categories, types ->
                        slices(r, categories, types).associate { it.label to it.value.toDouble() }
                    }
                }
            }.stateIn(viewModelScope, started, null)

    /** Money in and out over the period before; null for ALL. */
    val statsBefore: StateFlow<IncExp?> = previousKey.flatMapLatest { prev ->
        if (prev == null) flowOf(null) else repository.getIncomeVsExpense(prev)
    }.stateIn(viewModelScope, started, null)


    /** Budgeted categories with this calendar month's spending (not affected by the filters). */
    val budgets: StateFlow<List<CategoryBudget>> =
        repository.getBudgets().stateIn(viewModelScope, started, emptyList())

    val filterOptions: StateFlow<FilterOptions?> =
        repository.getFilterOptions().stateIn(viewModelScope, started, null)

    /** Top merchants, narrowed to the breakdown row that is selected, in Category and Type mode alike. */
    val topSubjects: StateFlow<List<SubjectAmount>?> =
        combine(key, breakdown, _selectedSlice) { fk, rows, slice -> Triple(fk, rows, slice) }
            .flatMapLatest { (k, rows, slice) ->
                if (rows == null) return@flatMapLatest emptyFlow()
                val (categories, types) = sliceFilter(rows, slice, k.stats.categories, k.stats.types)
                repository.getTopSubjectsByAmount(
                    k.stats.copy(categories = categories, types = types),
                    natureFilter = false,
                    limit = 8,
                )
            }.stateIn(viewModelScope, started, null)

    /** Who paid in, biggest first, now and over the period before (null for ALL). */
    val incomeSources: StateFlow<List<SourceAmount>?> = key.flatMapLatest { k ->
        repository.getIncomeBySource(k.stats, 30)
    }.stateIn(viewModelScope, started, null)

    val incomeSourcesBefore: StateFlow<List<SourceAmount>?> = previousKey.flatMapLatest { prev ->
        if (prev == null) flowOf(null) else repository.getIncomeBySource(prev, 30)
    }.stateIn(viewModelScope, started, null)

    val activityRaw: StateFlow<List<ActivityCell>?> =
        natureKey.flatMapLatest { (k, nature) ->
            repository.getActivityByTimeOfDay(k.stats, nature)
        }.stateIn(viewModelScope, started, null)

    /** Weekday counts with the time filter they were queried for, so the daily average divides by that range. */
    private class WeekdayRows(val time: TimeFilter, val counts: List<WeekdayCount>)

    private val weekdayRaw: StateFlow<WeekdayRows?> =
        natureKey.flatMapLatest { (k, nature) ->
            repository.getWeekdayCounts(k.stats, nature).map { WeekdayRows(k.time, it) }
        }.stateIn(viewModelScope, started, null)

    val leaderboard: StateFlow<List<SubjectCount>?> = natureKey.flatMapLatest { (k, nature) ->
        repository.getTopSubjectsByCount(k.stats, nature, 10)
    }.stateIn(viewModelScope, started, null)

    /** The pace card's period for the chip (null for ALL), with the day it was worked out on. */
    private val chipPeriod: Flow<Pair<PacePeriod?, LocalDate>> =
        combine(_filters.map { it.timeFilter }.distinctUntilChanged(), today) { tf, day -> pacePeriod(tf, day) to day }
            .distinctUntilChanged()

    /**
     * One day-totals query wide enough for both summaries that read it, ending tomorrow: the streak
     * (its own 13 weeks, independent of the time chip) and the pace card ([period] and the period it
     * is compared with). Each summary keeps its own days in Kotlin. Both ignore the filters; expense
     * days only.
     */
    private class DailyWindow(val today: LocalDate, val period: PacePeriod?) {
        val streakStart: LocalDate = today.minusWeeks(13)
        val from: String = minOf(streakStart, period?.compareStart ?: streakStart).atStartOfDay()
            .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val to: String = today.plusDays(1).atStartOfDay().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    }

    private class DailyRows(val window: DailyWindow, val days: List<DailyTotal>)

    private val dailyWindow: Flow<DailyWindow> = chipPeriod.map { (p, day) -> DailyWindow(day, p) }

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

    /** The day recorded history starts, for the ALL filter's label and span. Null until loaded or with none. */
    val historyStart: StateFlow<LocalDate?> =
        repository.selectedAccount.mapLatest { repository.getEarliestTransactionDate()?.toLocalDate() }
            .stateIn(viewModelScope, onceStarted, null)

    /** Whether history reaches back over the whole period before. Null for ALL or until loaded. */
    val history: StateFlow<History?> = combine(timeFilter, today, historyStart) { tf, day, first ->
        historyFor(tf, day, first)
    }.stateIn(viewModelScope, started, null)

    /** Real span of recorded history, for the ALL filter. Null until loaded. */
    private val dataSpanDays: StateFlow<Int?> =
        combine(historyStart, today) { first, day ->
            first?.let { (ChronoUnit.DAYS.between(it, day) + 1).toInt() }
        }.stateIn(viewModelScope, onceStarted, null)

    /** Which line these numbers are for: its name, or "All accounts"; null when there is only one line. */
    val scopeLabel: StateFlow<String?> =
        combine(repository.selectedAccount, repository.getAccountBalances()) { selected, lines ->
            scopeLabel(selected, lines.map { it.accountId to it.name })
        }.stateIn(viewModelScope, started, null)


    val recurring: StateFlow<List<RecurringPayment>?> =
        repository.selectedAccount.mapLatest { repository.getRecurringPayments() }.stateIn(
            viewModelScope,
            onceStarted,
            null
        )

    /** The month the Activity calendar shows; null for the current one. */
    private val _shownMonth = MutableStateFlow<YearMonth?>(null)
    private val _selectedDay = MutableStateFlow<LocalDate?>(null)

    /** Step the calendar a month back or forward, within recorded history and not past this month. */
    fun shiftMonth(by: Long) {
        val now = YearMonth.from(today.value)
        val first = historyStart.value?.let(YearMonth::from) ?: now
        val next = (_shownMonth.value ?: now).plusMonths(by)
        if (next.isAfter(now) || next.isBefore(first)) return
        _shownMonth.value = next.takeIf { it != now }
        _selectedDay.value = null
    }

    fun selectDay(day: LocalDate) {
        _selectedDay.value = day
    }

    val canGoBack: StateFlow<Boolean> =
        combine(_shownMonth, historyStart, today) { m, first, day ->
            first != null && (m ?: YearMonth.from(day)).isAfter(YearMonth.from(first))
        }.stateIn(viewModelScope, started, false)

    /**
     * The Activity calendar: one day-totals query for the shown month's weeks and its biggest payment
     * (filters aside, spending only, like the streak), with regular payments due later this month.
     */
    val calendar: StateFlow<MonthCalendar?> =
        combine(_shownMonth, today) { m, day -> (m ?: YearMonth.from(day)) to day }
            .distinctUntilChanged()
            .flatMapLatest { (month, day) ->
                val range = calendarRange(month)
                val to = range.endInclusive.plusDays(1).toString()
                combine(
                    repository.getDailyTotals(range.start.toString(), to, natureFilter = false),
                    repository.getLargestPayments(month.atDay(1).toString(), month.atEndOfMonth().plusDays(1).toString()),
                    streak,
                    recurring,
                ) { days, largest, st, regulars ->
                    val biggest = largest.firstOrNull { !it.isInc }?.let {
                        Triple(LocalDate.parse(it.time.take(10)), it.amount, it.category)
                    }
                    val due = regulars.orEmpty().let { dueDates(it, day.plusDays(1), month.atEndOfMonth()) }
                        .map { it.date }.toSet()
                    monthCalendar(month, days, day, st?.let { streakNow(it.summary.intensities, day) }, due, biggest)
                }
            }.stateIn(viewModelScope, started, null)

    /** The streak and record above the calendar, from the 13 weeks the streak query covers. */
    val currentStreak: StateFlow<Streak?> = combine(streak, today) { st, day ->
        st?.let { streakNow(it.summary.intensities, day) }
    }.stateIn(viewModelScope, started, null)

    /** The tapped day: today in the current month until another is picked, none in a past month. */
    val selectedDay: StateFlow<LocalDate?> =
        combine(_selectedDay, _shownMonth, today) { picked, month, day -> picked ?: day.takeIf { month == null } }
            .stateIn(viewModelScope, started, null)

    /** The selected day's payments out; empty for a day still to come. */
    val dayPayments: StateFlow<List<DayPayment>?> =
        combine(selectedDay, today) { d, day -> d?.takeIf { !it.isAfter(day) } }
            .distinctUntilChanged()
            .flatMapLatest { d ->
                if (d == null) flowOf(emptyList())
                else repository.getPaymentsOut(d.toString(), d.plusDays(1).toString())
            }.stateIn(viewModelScope, started, null)

    /** Payment counts by weekday for the filters, with the days of the range they fall in. */
    class Weekdays(val counts: List<WeekdayCount>, val days: Int)

    val weekdays: StateFlow<Weekdays?> =
        combine(weekdayRaw, dataSpanDays, today) { w, span, day ->
            w?.let { Weekdays(it.counts, daysForFilter(it.time, span, day)) }
        }.stateIn(viewModelScope, started, null)

    /** The nature filter alone, for wording that depends on money in or out. */
    val nature: StateFlow<Boolean?> = _filters.map { it.natureFilter }.distinctUntilChanged()
        .stateIn(viewModelScope, started, null)

    /** What goes out in an average month at the period's rate, for the Recurring card's share. */
    val monthlyOut: StateFlow<Double?> =
        combine(stats, timeFilter, dataSpanDays, today) { s, tf, span, day ->
            s?.let { it.expense * DAYS_PER_MONTH / daysForFilter(tf, span, day) }
        }.stateIn(viewModelScope, started, null)

    /**
     * The period's spending by category so far, filters aside, while it is too early to project:
     * for "Mostly rent.". Empty, without a query, the rest of the time.
     */
    private val earlyCategories: Flow<List<CategorySpending>> = chipPeriod.flatMapLatest { (p, day) ->
        if (p == null || !p.early(day)) flowOf(emptyList())
        else repository.getCategorySpending(
            StatsFilter(p.start.toString(), day.plusDays(1).toString()), natureFilter = false,
        )
    }

    /**
     * The Overview pace card for the chip's period: spending so far against the period before, where
     * it is heading, and the regulars still due. [period] and [pace] are null for ALL, whose card
     * shows the all-time total instead.
     */
    class Outlook(
        val period: PacePeriod?,
        val today: LocalDate,
        val pace: PaceSummary?,
        val projected: Double?,
        val stillDue: Double,
        val mostly: String?,
    )

    val outlook: StateFlow<Outlook?> =
        combine(dailyTotals, recurring, earlyCategories) { rows, rec, cats ->
            if (rows == null || rec == null) return@combine null
            val day = rows.window.today
            val p = rows.window.period ?: return@combine Outlook(null, day, null, null, 0.0, null)
            val days = daysFrom(rows.days, p.compareStart)
            val pace = computePace(days, p, day)
            val due = stillDue(rec, p, day)
            Outlook(
                p, day, pace, projectPeriodEnd(days, p, day, pace.spent, due), due,
                if (p.early(day)) mostlyCategory(cats) else null,
            )
        }.stateIn(viewModelScope, started, null)

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
