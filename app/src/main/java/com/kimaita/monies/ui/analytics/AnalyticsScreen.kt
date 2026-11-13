package com.kimaita.monies.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import com.kimaita.monies.data.dao.dtos.SourceAmount
import com.kimaita.monies.data.dao.dtos.SubjectCount
import com.kimaita.monies.models.analytics.RecurringPayment
import com.kimaita.monies.models.export.NamedRef
import com.kimaita.monies.services.StreakSummary
import com.kimaita.monies.ui.charts.*
import com.kimaita.monies.ui.components.AutoSizeText
import com.kimaita.monies.ui.components.HighlightsFilter
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
            SegmentedTabs(tab) { tab = it }
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

@Composable
private fun SegmentedTabs(selected: AnalyticsTab, onSelect: (AnalyticsTab) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val outer = remember { RoundedCornerShape(14.dp) }
    val inner = remember { RoundedCornerShape(10.dp) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(cs.surfaceVariant.copy(alpha = 0.5f), outer)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AnalyticsTab.entries.forEach { t ->
            val active = t == selected
            Box(
                Modifier
                    .weight(1f)
                    .background(if (active) cs.surface else Color.Transparent, inner)
                    .clickable { onSelect(t) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    t.title,
                    maxLines = 1,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (active) cs.onSurface else cs.onSurfaceVariant,
                )
            }
        }
    }
}

/* ----------------------------- Overview (bento) ----------------------------- */

@Composable
private fun OverviewTab(vm: AnalyticsViewModel, listState: LazyListState, onJump: (AnalyticsTab) -> Unit) {
    val statsOrNull by vm.stats.collectAsStateWithLifecycle()
    val pace by vm.pace.collectAsStateWithLifecycle()
    val breakdownOrNull by vm.breakdown.collectAsStateWithLifecycle()
    val cashFlowOrNull by vm.cashFlow.collectAsStateWithLifecycle()
    val balanceOrNull by vm.balanceTrend.collectAsStateWithLifecycle()
    val heatOrNull by vm.heat.collectAsStateWithLifecycle()
    val topOrNull by vm.topMerchant.collectAsStateWithLifecycle()
    val extra = MoniesTheme.extra

    val stats = statsOrNull
    val breakdownRows = breakdownOrNull
    val cashFlow = cashFlowOrNull
    val balance = balanceOrNull
    val heat = heatOrNull
    val top = topOrNull
    if (stats == null || breakdownRows == null || cashFlow == null || balance == null || heat == null || top == null) {
        LoadingTab(listState)
        return
    }
    val whereItGoes = remember(breakdownRows) { breakdownRows.map { RankedRow(it.label, it.value.toDouble()) } }
    val net = remember(stats) { stats.income - stats.expense }
    val fees = remember(stats) { stats.incomeCost + stats.expenseCost }

    TabList(listState) {
        pace?.let { p ->
            item(key = "pace") {
                BentoCard {
                    TileLabel("Spending pace")
                    Spacer(Modifier.height(6.dp))
                    PaceChart(p, Modifier.fillMaxWidth())
                }
            }
        }
        item(key = "net") {
            BentoCard {
                TileLabel("Net this period")
                Spacer(Modifier.height(6.dp))
                AutoSizeText(
                    formatSignedKes(net),
                    Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (net >= 0) extra.positive else extra.negative,
                    minFontSize = 22.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "In ${formatCompactKes(stats.income)}  /  Out ${formatCompactKes(stats.expense)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item(key = "breakdown_stats") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BentoCard(
                    Modifier.weight(1f),
                    onClick = { onJump(AnalyticsTab.SPENDING) }) {
                    TileLabel("Where it goes")
                    Spacer(Modifier.height(8.dp))
                    if (whereItGoes.isEmpty()) EmptyHint()
                    else RankedBars(
                        whereItGoes, MoniesTheme.extra.outflow, limit = 3, showPercent = false
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Spent", formatCompactKes(stats.expense), extra.negative)
                    StatTile(
                        "Top", top.firstOrNull()?.name ?: "-",
                        MaterialTheme.colorScheme.onSurface,
                        sub = top.firstOrNull()?.let { formatCompactKes(it.amount) },
                        valueMaxLines = 2,
                    )
                }
            }
        }

        item(key = "cashflow") {
            BentoCard(Modifier.height(132.dp), onClick = { onJump(AnalyticsTab.CASHFLOW) }) {
                TileLabel("Cash flow")
                Spacer(Modifier.height(8.dp))
                if (cashFlow.isEmpty()) EmptyHint()
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
                if (balance.size < 2) EmptyHint()
                else BalanceTrendChart(balance, Modifier.fillMaxWidth(), plotHeight = 140.dp)
            }
        }

        item(key = "fees") {
            StatTile("Fees", formatCompactKes(fees), MaterialTheme.colorScheme.onSurface)
        }

        item(key = "heat") {
            BentoCard(onClick = { onJump(AnalyticsTab.ACTIVITY) }) {
                TileLabel("When you're active")
                Spacer(Modifier.height(8.dp))
                if (heat.isEmpty()) EmptyHint() else TimeOfDayHeatmap(heat, showLabels = false)
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
    if (rhythm == null || heat == null || bars == null || streak == null || leaders == null) {
        LoadingTab(listState)
        return
    }
    val avgLabel = remember(rhythm.avgPerDay) { "%.1f txns".format(rhythm.avgPerDay) }

    TabList(listState) {
        item(key = "rhythm1") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RhythmTile("Busiest day", weekdayName(rhythm.busiestWeekday), Modifier.weight(1f))
                RhythmTile("Most active", slotName(rhythm.mostActiveSlot), Modifier.weight(1f))
            }
        }
        item(key = "rhythm2") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RhythmTile("Avg / day", avgLabel, Modifier.weight(1f))
                RhythmTile("Longest quiet", "${rhythm.longestQuietDays}d", Modifier.weight(1f))
            }
        }
        item(key = "when") {
            SectionCard("When you spend") {
                if (heat.isEmpty()) EmptyHint() else TimeOfDayHeatmap(heat)
                Spacer(Modifier.height(16.dp))
                TileLabel("By day of week")
                Spacer(Modifier.height(8.dp))
                DayOfWeekBars(bars)
            }
        }
        item(key = "streak") {
            SectionCard("Spending streak") {
                StreakSection(streak, vm.streakWindow)
            }
        }
        item(key = "frequent") {
            SectionCard("Most frequent") {
                if (leaders.isEmpty()) EmptyHint()
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

@Composable
private fun SpendingTab(vm: AnalyticsViewModel, listState: LazyListState) {
    val direction by vm.direction.collectAsStateWithLifecycle()
    val mode by vm.breakdownMode.collectAsStateWithLifecycle()
    val breakdownOrNull by vm.breakdown.collectAsStateWithLifecycle()
    val topOrNull by vm.topSubjects.collectAsStateWithLifecycle()
    val selectedSlice by vm.selectedSlice.collectAsStateWithLifecycle()
    val sourcesOrNull by vm.incomeSources.collectAsStateWithLifecycle()
    val extra = MoniesTheme.extra
    val budgets by vm.budgets.collectAsStateWithLifecycle()

    val breakdownRows = breakdownOrNull
    val top = topOrNull
    val sources = sourcesOrNull
    if (breakdownRows == null || top == null || sources == null) {
        LoadingTab(listState)
        return
    }
    val slices = remember(breakdownRows) { breakdownRows.map { RankedRow(it.label, it.value.toDouble()) } }
    // A selection whose row has gone (the period changed, say) is no longer a filter.
    val selected = selectedSlice?.takeIf { s -> slices.any { it.label == s } }
    val merchants = remember(top) { top.map { RankedRow(it.name, it.amount) } }
    val sourceRows = remember(sources) { sources.map { RankedRow(it.name, it.amount) } }

    TabList(listState) {
        item(key = "direction") {
            ToggleRow(
                options = remember {
                    listOf(
                        "Out" to SpendDirection.OUT,
                        "In" to SpendDirection.IN
                    )
                },
                selected = direction, onSelect = vm::setDirection,
            )
        }

        if (direction == SpendDirection.OUT) {
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
                    ToggleRow(
                        options = remember {
                            listOf(
                                "Category" to Breakdown.CATEGORY,
                                "Type" to Breakdown.TYPE
                            )
                        },
                        selected = mode, onSelect = vm::setBreakdownMode,
                    )
                    Spacer(Modifier.height(12.dp))
                    if (slices.isEmpty()) EmptyHint()
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
                    if (merchants.isEmpty()) EmptyHint() else RankedBars(merchants, extra.outflow)
                }
            }
        } else {
            item(key = "sources") {
                SectionCard("Income sources") {
                    if (sourceRows.isEmpty()) EmptyHint() else RankedBars(sourceRows, extra.inflow)
                }
            }
        }
    }
}

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
    if (balance == null || cashFlow == null || costs == null || stats == null) {
        LoadingTab(listState)
        return
    }
    val feesHeader = remember(stats) {
        val fees = stats.incomeCost + stats.expenseCost
        "You paid ${formatKes(fees)} in fees" +
                if (stats.expense > 0.0) " · ${"%.1f".format(Locale.US, fees / stats.expense * 100)}% of spending" else ""
    }

    TabList(listState) {
        item(key = "trend") {
            SectionCard("Balance trend") {
                if (balance.isEmpty()) EmptyHint()
                else BalanceTrendChart(balance, Modifier.fillMaxWidth())
            }
        }
        item(key = "inout") {
            SectionCard("Money in vs out") {
                if (cashFlow.isEmpty()) EmptyHint() else CashFlowChart(
                    cashFlow,
                    Modifier.fillMaxWidth()
                )
            }
        }
        item(key = "fees") {
            SectionCard("Fees") {
                Text(
                    feesHeader,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                if (costs.isEmpty()) EmptyHint() else CostAnalyzerChart(
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
            ToggleRow(
                options = remember {
                    listOf(
                        "All" to null,
                        "Money in" to true,
                        "Money out" to false
                    )
                },
                selected = filters.natureFilter, onSelect = vm::setNature,
            )

            opts?.types?.takeIf { it.isNotEmpty() }?.let { types ->
                Text("Type", style = MaterialTheme.typography.labelLarge)
                MultiSelectChips(
                    types,
                    filters.typeFilters
                ) { vm.setTypeFilters(filters.typeFilters.toggle(it)) }
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

@Composable
private fun TileLabel(t: String) = Text(
    t, style = MaterialTheme.typography.labelMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)

@Composable
private fun StatTile(
    label: String, value: String, color: Color,
    modifier: Modifier = Modifier, sub: String? = null, valueMaxLines: Int = 1,
) {
    BentoCard(modifier) {
        TileLabel(label)
        Spacer(Modifier.height(6.dp))
        AutoSizeText(
            value, Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = valueMaxLines,
            minFontSize = 14.sp,
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

@Composable
private fun <T> ToggleRow(options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val outer = remember { RoundedCornerShape(12.dp) }
    val inner = remember { RoundedCornerShape(9.dp) }
    Row(
        Modifier
            .fillMaxWidth()
            .background(cs.surfaceVariant.copy(alpha = 0.5f), outer)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEach { (label, value) ->
            val active = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .background(if (active) cs.surface else Color.Transparent, inner)
                    .clickable { onSelect(value) }
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label, maxLines = 1, style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (active) cs.onSurface else cs.onSurfaceVariant,
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
private fun LoadingTab(listState: LazyListState) = TabList(listState) {
    item(key = "loading") { EmptyHint("Loading…") }
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

private val weekdayLabels =
    arrayOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

private fun weekdayName(i: Int?): String = i?.let { weekdayLabels.getOrNull(it) } ?: "-"
private fun slotName(i: Int?): String = i?.let { SLOT_LABELS.getOrNull(it) } ?: "-"
private fun List<Int>.toggle(id: Int): List<Int> = if (id in this) this - id else this + id