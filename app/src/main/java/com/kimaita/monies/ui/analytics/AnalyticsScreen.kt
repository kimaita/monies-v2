package com.kimaita.monies.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kimaita.monies.R
import com.kimaita.monies.data.dao.dtos.CategoryBudget
import com.kimaita.monies.data.dao.dtos.SubjectCount
import com.kimaita.monies.models.analytics.RecurringPayment
import com.kimaita.monies.models.export.NamedRef
import com.kimaita.monies.services.StreakSummary
import com.kimaita.monies.services.WeekdayBar
import com.kimaita.monies.services.activeSummary
import com.kimaita.monies.services.feeShare
import com.kimaita.monies.services.isSelected
import com.kimaita.monies.services.toggleGroup
import com.kimaita.monies.ui.charts.*
import com.kimaita.monies.ui.components.HighlightsFilter
import com.kimaita.monies.ui.components.Segmented
import com.kimaita.monies.ui.theme.MoniesTheme
import com.kimaita.monies.ui.util.dayMonthFmt
import com.kimaita.monies.ui.util.formatCompactKes
import com.kimaita.monies.ui.util.formatKes
import com.kimaita.monies.ui.util.formatSignedKes
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.Locale

enum class AnalyticsTab(val title: String) {
    OVERVIEW("Overview"), ACTIVITY("Activity"), SPENDING("Spending"), CASHFLOW("Cash flow")
}

private val tabOptions = AnalyticsTab.entries.map { it.title to it }
private val directionOptions = listOf("Out" to SpendDirection.OUT, "In" to SpendDirection.IN)
private val breakdownOptions = listOf("Category" to Breakdown.CATEGORY, "Type" to Breakdown.TYPE)
private val natureOptions: List<Pair<String, Boolean?>> =
    listOf("All" to null, "Money in" to true, "Money out" to false)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(viewModel: AnalyticsViewModel = hiltViewModel()) {
    var tab by rememberSaveable { mutableStateOf(AnalyticsTab.OVERVIEW) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val listStates = AnalyticsTab.entries.associateWith { rememberLazyListState() }
    val scope by viewModel.scopeLabel.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Analytics", fontWeight = FontWeight.SemiBold)
                        scope?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                actions = {
                    TextButton(onClick = { showFilters = true }) {
                        Icon(painterResource(R.drawable.filter), null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Filters")
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
        ) {
            TimeChips(viewModel)
            Spacer(Modifier.height(8.dp))
            Segmented(
                tabOptions, tab, { tab = it }, Modifier.padding(horizontal = 16.dp),
                radius = 14.dp, inset = 4.dp, itemPadding = PaddingValues(vertical = 8.dp),
            )
            Box(Modifier.weight(1f)) {
                when (tab) {
                    AnalyticsTab.OVERVIEW -> OverviewTab(viewModel, listStates.getValue(tab)) { tab = it }
                    AnalyticsTab.ACTIVITY -> ActivityTab(viewModel, listStates.getValue(tab))
                    AnalyticsTab.SPENDING -> SpendingTab(viewModel, listStates.getValue(tab))
                    AnalyticsTab.CASHFLOW -> CashFlowTab(viewModel, listStates.getValue(tab))
                }
            }
        }
    }

    if (showFilters) FiltersSheet(viewModel) { showFilters = false }
}

/* ----------------------------- chrome ----------------------------- */

// Collects only timeFilter so unrelated filter edits don't recompose the chips.
@Composable
private fun TimeChips(vm: AnalyticsViewModel) {
    val flow = remember(vm) { vm.filters.map { it.timeFilter }.distinctUntilChanged() }
    val timeFilter by flow.collectAsStateWithLifecycle(vm.filters.value.timeFilter)
    HighlightsFilter(timeFilter, onFilterChange = vm::setTimeFilter)
}

/* ----------------------------- Overview (bento) ----------------------------- */

// Every card shows its own placeholder until its own value arrives.
@Composable
private fun OverviewTab(vm: AnalyticsViewModel, listState: LazyListState, onJump: (AnalyticsTab) -> Unit) {
    val statsOrNull by vm.stats.collectAsStateWithLifecycle()
    val paceOrNull by vm.pace.collectAsStateWithLifecycle()
    val breakdownOrNull by vm.breakdown.collectAsStateWithLifecycle()
    val cashFlowOrNull by vm.cashFlow.collectAsStateWithLifecycle()
    val balanceOrNull by vm.balanceTrend.collectAsStateWithLifecycle()
    val activityOrNull by vm.activityRaw.collectAsStateWithLifecycle()
    val topOrNull by vm.topMerchant.collectAsStateWithLifecycle()
    val sourcesOrNull by vm.incomeSources.collectAsStateWithLifecycle()
    val extra = MoniesTheme.extra

    val stats = statsOrNull
    val pace = paceOrNull
    val breakdownRows = breakdownOrNull
    val cashFlow = cashFlowOrNull
    val balance = balanceOrNull
    val activity = activityOrNull
    val top = topOrNull
    val sources = sourcesOrNull
    val whereItGoes = remember(breakdownRows) { breakdownRows?.map { RankedRow(it.label, it.value.toDouble()) } }
    val net = remember(stats) { stats?.let { it.income - it.expense } }
    val fees = remember(stats) { stats?.let { it.incomeCost + it.expenseCost } }
    val activeText = remember(activity) { activity?.let { activeSummary(it) ?: "Not enough activity yet" } }

    TabList(listState) {
        item(key = "pace") {
            BentoCard {
                TileLabel("Spending pace")
                Spacer(Modifier.height(6.dp))
                if (pace == null) EmptyHint(LOADING) else PaceChart(pace, Modifier.fillMaxWidth())
            }
        }
        item(key = "net") {
            BentoCard {
                TileLabel("Net this period")
                Spacer(Modifier.height(6.dp))
                Text(
                    net?.let { formatSignedKes(it) } ?: PENDING,
                    Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        net == null -> MaterialTheme.colorScheme.onSurfaceVariant
                        net >= 0 -> extra.positive
                        else -> extra.negative
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = 22.sp,
                        maxFontSize = MaterialTheme.typography.headlineMedium.fontSize,
                    ),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stats?.let { "In ${formatCompactKes(it.income)}  /  Out ${formatCompactKes(it.expense)}" }
                        ?: PENDING,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TopLine("Top in", sources != null, sources?.firstOrNull()?.let { it.name to it.amount })
                TopLine("Top out", top != null, top?.firstOrNull()?.let { it.name to it.amount })
            }
        }

        item(key = "breakdown") {
            BentoCard(onClick = { onJump(AnalyticsTab.SPENDING) }) {
                TileLabel("Where it goes")
                Spacer(Modifier.height(8.dp))
                if (whereItGoes == null) EmptyHint(LOADING)
                else if (whereItGoes.isEmpty()) EmptyHint()
                else RankedBars(
                    whereItGoes, MoniesTheme.extra.outflow, limit = 3, showPercent = false
                )
            }
        }

        item(key = "fees") {
            StatTile(
                "Fees", fees?.let { formatCompactKes(it) } ?: PENDING, MaterialTheme.colorScheme.onSurface,
                sub = stats?.let { feeShareText(it.incomeCost + it.expenseCost, it.expense) },
            )
        }

        item(key = "cashflow") {
            BentoCard(Modifier.height(132.dp), onClick = { onJump(AnalyticsTab.CASHFLOW) }) {
                TileLabel("Cash flow")
                Spacer(Modifier.height(8.dp))
                if (cashFlow == null) EmptyHint(LOADING)
                else if (cashFlow.isEmpty()) EmptyHint()
                else MiniInOutBars(
                    cashFlow, Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }

        item(key = "balance") {
            BentoCard(onClick = { onJump(AnalyticsTab.CASHFLOW) }) {
                TileLabel("Balance")
                Spacer(Modifier.height(8.dp))
                if (balance == null) EmptyHint(LOADING)
                else if (balance.size < 2) EmptyHint()
                else BalanceTrendChart(balance, Modifier.fillMaxWidth(), plotHeight = 140.dp)
            }
        }

        item(key = "heat") {
            BentoCard(onClick = { onJump(AnalyticsTab.ACTIVITY) }) {
                TileLabel("When you're active")
                Spacer(Modifier.height(8.dp))
                Text(activeText ?: LOADING, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/* ----------------------------- Activity ----------------------------- */

@Composable
private fun ActivityTab(vm: AnalyticsViewModel, listState: LazyListState) {
    val rhythmOrNull by vm.rhythm.collectAsStateWithLifecycle()
    val heatOrNull by vm.heat.collectAsStateWithLifecycle()
    val barsOrNull by vm.weekdayBars.collectAsStateWithLifecycle()
    val streakOrNull by vm.streak.collectAsStateWithLifecycle()
    val leadersOrNull by vm.leaderboard.collectAsStateWithLifecycle()
    val recurring by vm.recurring.collectAsStateWithLifecycle()

    val rhythm = rhythmOrNull
    val heat = heatOrNull
    val bars = barsOrNull
    val streak = streakOrNull
    val leaders = leadersOrNull
    val avgLabel = remember(rhythm?.avgPerDay) { rhythm?.let { "%.1f txns".format(it.avgPerDay) } }

    TabList(listState) {
        item(key = "rhythm1") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RhythmTile("Busiest day", rhythm?.let { weekdayName(it.busiestWeekday) } ?: PENDING, Modifier.weight(1f))
                RhythmTile("Most active", rhythm?.let { slotName(it.mostActiveSlot) } ?: PENDING, Modifier.weight(1f))
            }
        }
        item(key = "rhythm2") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RhythmTile("Avg / day", avgLabel ?: PENDING, Modifier.weight(1f))
                RhythmTile("Longest quiet", rhythm?.let { "${it.longestQuietDays}d" } ?: PENDING, Modifier.weight(1f))
            }
        }
        item(key = "when") {
            SectionCard("When you spend") {
                if (heat == null) EmptyHint(LOADING)
                else if (heat.isEmpty()) EmptyHint() else TimeOfDayHeatmap(heat)
                Spacer(Modifier.height(16.dp))
                TileLabel("By day of week")
                Spacer(Modifier.height(8.dp))
                if (bars == null) EmptyHint(LOADING) else DayOfWeekBars(bars)
            }
        }
        item(key = "streak") {
            SectionCard("Spending streak") {
                if (streak == null) EmptyHint(LOADING) else StreakSection(streak, vm.streakWindow)
            }
        }
        item(key = "frequent") {
            SectionCard("Most frequent") {
                if (leaders == null) EmptyHint(LOADING)
                else if (leaders.isEmpty()) EmptyHint()
                else leaders.forEachIndexed { i, s -> LeaderRow(i, s) }
            }
        }
        item(key = "recurring") {
            SectionCard("Recurring payments") {
                val rows = recurring
                if (rows == null) EmptyHint("Checking…")
                else if (rows.isEmpty()) EmptyHint("Nothing regular yet")
                else rows.forEach { RecurringRow(it) }
            }
        }
    }
}

@Composable
private fun StreakSection(streak: StreakSummary, window: ClosedRange<LocalDate>) {
    ContributionGrid(streak.intensities, window.start, window.endInclusive)
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        MiniStat("Current", "${streak.currentStreak}d")
        MiniStat("Longest", "${streak.longestActive}d")
        MiniStat("Longest gap", "${streak.longestNoSpend}d")
    }
}

/* ----------------------------- Spending ----------------------------- */

// One side at a time, so each collects (and queries for) only what it shows.
@Composable
private fun SpendingTab(vm: AnalyticsViewModel, listState: LazyListState) {
    val direction by vm.direction.collectAsStateWithLifecycle()
    if (direction == SpendDirection.OUT) SpendingOut(vm, listState) else SpendingIn(vm, listState)
}

@Composable
private fun SpendingOut(vm: AnalyticsViewModel, listState: LazyListState) {
    val mode by vm.breakdownMode.collectAsStateWithLifecycle()
    val breakdownOrNull by vm.breakdown.collectAsStateWithLifecycle()
    val topOrNull by vm.topSubjects.collectAsStateWithLifecycle()
    val selectedSlice by vm.selectedSlice.collectAsStateWithLifecycle()
    val budgets by vm.budgets.collectAsStateWithLifecycle()
    val extra = MoniesTheme.extra

    val breakdownRows = breakdownOrNull
    val top = topOrNull
    val slices = remember(breakdownRows) { breakdownRows?.map { RankedRow(it.label, it.value.toDouble()) } }
    // A selection whose row has gone (the period changed, say) is no longer a filter.
    val selected = slices?.let { rows -> selectedSlice?.takeIf { s -> rows.any { it.label == s } } }
    val merchants = remember(top) { top?.map { RankedRow(it.name, it.amount) } }

    TabList(listState) {
        item(key = "direction") { DirectionToggle(SpendDirection.OUT, vm::setDirection) }
        if (budgets.isNotEmpty()) item(key = "budgets") {
            SectionCard("Budgets this month") {
                budgets.forEachIndexed { i, b ->
                    if (i > 0) Spacer(Modifier.height(12.dp))
                    BudgetRow(b)
                }
            }
        }
        item(key = "breakdown") {
            SectionCard("Breakdown") {
                Segmented(breakdownOptions, mode, vm::setBreakdownMode)
                Spacer(Modifier.height(12.dp))
                if (slices == null) EmptyHint(LOADING)
                else if (slices.isEmpty()) EmptyHint()
                else RankedBars(
                    slices, extra.outflow,
                    selected = selected, onRowClick = vm::selectSlice,
                )
            }
        }
        item(key = "top") {
            SectionCard(if (selected != null) "Top in \"$selected\"" else "Top merchants") {
                if (selected != null) {
                    AssistChip(
                        onClick = { vm.selectSlice(null) },
                        label = { Text("Clear filter") },
                        leadingIcon = {
                            Icon(
                                painterResource(R.drawable.close_small_24dp),
                                null,
                                Modifier.size(16.dp)
                            )
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                }
                if (merchants == null) EmptyHint(LOADING)
                else if (merchants.isEmpty()) EmptyHint() else RankedBars(merchants, extra.outflow)
            }
        }
    }
}

@Composable
private fun SpendingIn(vm: AnalyticsViewModel, listState: LazyListState) {
    val sourcesOrNull by vm.incomeSources.collectAsStateWithLifecycle()
    val sources = sourcesOrNull
    val sourceRows = remember(sources) { sources?.map { RankedRow(it.name, it.amount) } }

    TabList(listState) {
        item(key = "direction") { DirectionToggle(SpendDirection.IN, vm::setDirection) }
        item(key = "sources") {
            SectionCard("Income sources") {
                if (sourceRows == null) EmptyHint(LOADING)
                else if (sourceRows.isEmpty()) EmptyHint() else RankedBars(sourceRows, MoniesTheme.extra.inflow)
            }
        }
    }
}

@Composable
private fun DirectionToggle(direction: SpendDirection, onSelect: (SpendDirection) -> Unit) =
    Segmented(directionOptions, direction, onSelect)

/* ----------------------------- Cash flow ----------------------------- */

@Composable
private fun CashFlowTab(vm: AnalyticsViewModel, listState: LazyListState) {
    val balanceOrNull by vm.balanceTrend.collectAsStateWithLifecycle()
    val cashFlowOrNull by vm.cashFlow.collectAsStateWithLifecycle()
    val costsOrNull by vm.costs.collectAsStateWithLifecycle()
    val statsOrNull by vm.stats.collectAsStateWithLifecycle()
    val balance = balanceOrNull
    val cashFlow = cashFlowOrNull
    val costs = costsOrNull
    val stats = statsOrNull
    val feesHeader = remember(stats) {
        stats?.let {
            val fees = it.incomeCost + it.expenseCost
            "You paid ${formatKes(fees)} in fees" +
                    (feeShareText(fees, it.expense)?.let { share -> " · $share" } ?: "")
        }
    }

    TabList(listState) {
        item(key = "trend") {
            SectionCard("Balance trend") {
                if (balance == null) EmptyHint(LOADING)
                else if (balance.isEmpty()) EmptyHint()
                else BalanceTrendChart(balance, Modifier.fillMaxWidth())
            }
        }
        item(key = "inout") {
            SectionCard("Money in vs out") {
                if (cashFlow == null) EmptyHint(LOADING)
                else if (cashFlow.isEmpty()) EmptyHint() else CashFlowChart(
                    cashFlow,
                    Modifier.fillMaxWidth()
                )
            }
        }
        item(key = "fees") {
            SectionCard("Fees") {
                Text(
                    feesHeader ?: LOADING,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                if (costs == null) EmptyHint(LOADING)
                else if (costs.isEmpty()) EmptyHint() else CostAnalyzerChart(
                    costs,
                    Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/* ----------------------------- filter sheet ----------------------------- */

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FiltersSheet(vm: AnalyticsViewModel, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val filters by vm.filters.collectAsStateWithLifecycle()
    val opts by vm.filterOptions.collectAsStateWithLifecycle()
    val query by vm.subjectQuery.collectAsStateWithLifecycle()
    val results by vm.subjectResults.collectAsStateWithLifecycle()
    val chosen by vm.selectedSubjects.collectAsStateWithLifecycle()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Filters",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            Text("Direction", style = MaterialTheme.typography.labelLarge)
            Segmented(natureOptions, filters.natureFilter, vm::setNature)

            opts?.types?.takeIf { it.isNotEmpty() }?.let { types ->
                Text("Type", style = MaterialTheme.typography.labelLarge)
                val picked = filters.typeFilters.toSet()
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    types.forEach { group ->
                        FilterChip(
                            selected = group.isSelected(picked),
                            onClick = { vm.setTypeFilters(picked.toggleGroup(group).toList()) },
                            label = { Text(group.label) })
                    }
                }
            }
            opts?.categories?.takeIf { it.isNotEmpty() }?.let { cats ->
                Text("Category", style = MaterialTheme.typography.labelLarge)
                MultiSelectChips(
                    cats,
                    filters.categoryFilters
                ) { vm.setCategoryFilters(filters.categoryFilters.toggle(it)) }
            }

            Text("Subjects", style = MaterialTheme.typography.labelLarge)
            if (chosen.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                chosen.forEach { ref ->
                    InputChip(
                        selected = true,
                        onClick = { vm.removeSubject(ref) },
                        label = { Text(ref.name) },
                        trailingIcon = {
                            Icon(
                                painterResource(R.drawable.close_small_24dp),
                                null,
                                Modifier.size(16.dp)
                            )
                        },
                    )
                }
            }
            OutlinedTextField(
                value = query, onValueChange = vm::setSubjectQuery,
                placeholder = { Text("Search subjects") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions.Default,
            )
            if (results.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                results.take(12).forEach { ref ->
                    val on = chosen.any { it.id == ref.id }
                    FilterChip(
                        selected = on,
                        onClick = { if (on) vm.removeSubject(ref) else vm.addSubject(ref) },
                        label = { Text(ref.name) },
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = vm::clearFilters,
                    modifier = Modifier.weight(1f)
                ) { Text("Clear") }
                Button(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Done") }
            }
        }
    }
}

/* ----------------------------- small components ----------------------------- */

@Composable
private fun TabList(state: LazyListState, content: LazyListScope.() -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        state = state,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun BentoCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = remember { RoundedCornerShape(20.dp) }
    val colors =
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    if (onClick != null) {
        Card(onClick, modifier.fillMaxWidth(), shape = shape, colors = colors) {
            Column(Modifier.padding(16.dp), content = content)
        }
    } else {
        Card(modifier.fillMaxWidth(), shape = shape, colors = colors) {
            Column(Modifier.padding(16.dp), content = content)
        }
    }
}

@Composable
private fun BudgetRow(b: CategoryBudget) {
    val color = if (b.isOver) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(b.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Text(
            "${formatKes(b.spent)} / ${formatKes(b.budget)}",
            style = MaterialTheme.typography.bodyMedium,
            color = if (b.isOver) color else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(6.dp))
    LinearProgressIndicator(
        progress = { b.progress },
        modifier = Modifier.fillMaxWidth(),
        color = color,
    )
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    BentoCard {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

/** "Top in  Name  KES 12K": the name takes the room left and ellipsizes; "–" when there is none, "…" while [loaded] is false. */
@Composable
private fun TopLine(label: String, loaded: Boolean, top: Pair<String, Double>?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            if (loaded) top?.first ?: "–" else PENDING, Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        top?.let {
            Text(
                formatCompactKes(it.second), style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun TileLabel(t: String) = Text(
    t, style = MaterialTheme.typography.labelMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)

@Composable
private fun StatTile(
    label: String, value: String, color: Color,
    modifier: Modifier = Modifier, sub: String? = null,
) {
    BentoCard(modifier) {
        TileLabel(label)
        Spacer(Modifier.height(6.dp))
        Text(
            value, Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            autoSize = TextAutoSize.StepBased(
                minFontSize = 14.sp,
                maxFontSize = MaterialTheme.typography.titleLarge.fontSize,
            ),
        )
        sub?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RhythmTile(label: String, value: String, modifier: Modifier = Modifier) =
    StatTile(label, value, MaterialTheme.colorScheme.onSurface, modifier)

@Composable
private fun MiniStat(label: String, value: String) =
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

@Composable
private fun LeaderRow(index: Int, s: SubjectCount) {
    val medals = MoniesTheme.extra.medals
    val accent = medals.getOrNull(index) ?: MaterialTheme.colorScheme.surfaceVariant
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(26.dp)
                .background(accent, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${index + 1}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (index < medals.size) MaterialTheme.colorScheme.scrim else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(s.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            "${s.count}x", style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RecurringRow(r: RecurringPayment) {
    val next = remember(r.nextEstimate) { r.nextEstimate.format(dayMonthFmt) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                r.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium
            )
            Text(
                "Next ~$next", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatKes(r.typicalAmount), fontWeight = FontWeight.SemiBold)
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    r.cadenceLabel, Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun DayOfWeekBars(bars: List<WeekdayBar>) {
    val max = remember(bars) { (bars.maxOfOrNull { it.count } ?: 0).coerceAtLeast(1) }
    val barColor = MoniesTheme.extra.inflow
    val shape = remember { RoundedCornerShape(6.dp) }
    Row(
        Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        bars.forEach { b ->
            Column(
                Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(0.55f)
                        .height((4 + 80 * b.count / max).dp)
                        .background(barColor, shape)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    b.label, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MultiSelectChips(
    options: List<NamedRef<Int>>,
    selected: List<Int>,
    onToggle: (Int) -> Unit
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { ref ->
            FilterChip(
                selected = ref.id in selected,
                onClick = { onToggle(ref.id) },
                label = { Text(ref.name) })
        }
    }
}

@Composable
private fun EmptyHint(text: String = "Nothing here yet") =
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp), contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

/* ----------------------------- helpers ----------------------------- */

private const val LOADING = "Loading…"

/** Stands in for a number or a name until its value arrives; never a zero. */
private const val PENDING = "…"

private val weekdayLabels =
    arrayOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

private fun feeShareText(fees: Double, expense: Double): String? =
    feeShare(fees, expense)?.let { "${"%.1f".format(Locale.US, it)}% of spend" }

private fun weekdayName(i: Int?): String = i?.let { weekdayLabels.getOrNull(it) } ?: "-"
private fun slotName(i: Int?): String = i?.let { SLOT_LABELS.getOrNull(it) } ?: "-"
private fun List<Int>.toggle(id: Int): List<Int> = if (id in this) this - id else this + id