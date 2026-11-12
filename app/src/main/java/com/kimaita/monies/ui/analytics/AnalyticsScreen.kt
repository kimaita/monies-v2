package com.kimaita.monies.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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

enum class AnalyticsTab(val title: String) {
    OVERVIEW("Overview"), ACTIVITY("Activity"), SPENDING("Spending"), CASHFLOW("Cash flow")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(viewModel: AnalyticsViewModel = hiltViewModel()) {
    var tab by rememberSaveable { mutableStateOf(AnalyticsTab.OVERVIEW) }
    var showFilters by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Analytics", fontWeight = FontWeight.SemiBold) },
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
                    AnalyticsTab.OVERVIEW -> OverviewTab(viewModel) { tab = it }
                    AnalyticsTab.ACTIVITY -> ActivityTab(viewModel)
                    AnalyticsTab.SPENDING -> SpendingTab(viewModel)
                    AnalyticsTab.CASHFLOW -> CashFlowTab(viewModel)
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
    val timeFilter by flow.collectAsStateWithLifecycle(vm.filters.collectAsState().value.timeFilter)
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
private fun OverviewTab(vm: AnalyticsViewModel, onJump: (AnalyticsTab) -> Unit) {
    val stats by vm.stats.collectAsStateWithLifecycle()
    val donutValues by vm.donut.collectAsStateWithLifecycle()
    val donut = rememberSlices(donutValues)
    val cashFlow by vm.cashFlow.collectAsStateWithLifecycle()
    val balance by vm.balanceTrend.collectAsStateWithLifecycle()
    val heat by vm.heat.collectAsStateWithLifecycle()
    val top by vm.topSubjects.collectAsStateWithLifecycle()

    val net = remember(stats) { stats.income - stats.expense }
    val fees = remember(stats) { stats.incomeCost + stats.expenseCost }
    val extra = MoniesTheme.extra

    TabList {
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

        item(key = "donut_stats") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BentoCard(
                    Modifier
                        .weight(1f)
                        .height(168.dp),
                    onClick = { onJump(AnalyticsTab.SPENDING) }) {
                    TileLabel("Where it goes")
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (donut.isEmpty()) EmptyHint()
                        else DonutChart(
                            donut,
                            Modifier.fillMaxSize(),
                            thickness = 14.dp,
                            minSize = 0.dp
                        )
                    }
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

        item(key = "balance_fees") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BentoCard(
                    Modifier
                        .weight(1f)
                        .height(112.dp),
                    onClick = { onJump(AnalyticsTab.CASHFLOW) }) {
                    TileLabel("Balance")
                    Spacer(Modifier.height(8.dp))
                    if (balance.size < 2) EmptyHint()
                    else MiniSparkline(
                        balance,
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
                StatTile(
                    "Fees",
                    formatCompactKes(fees),
                    MaterialTheme.colorScheme.onSurface,
                    Modifier
                        .weight(1f)
                        .height(112.dp)
                )
            }
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
private fun ActivityTab(vm: AnalyticsViewModel) {
    val rhythm by vm.rhythm.collectAsStateWithLifecycle()
    val heat by vm.heat.collectAsStateWithLifecycle()
    val bars by vm.weekdayBars.collectAsStateWithLifecycle()
    val streak by vm.streak.collectAsStateWithLifecycle()
    val leaders by vm.leaderboard.collectAsStateWithLifecycle()
    val recurring by vm.recurring.collectAsStateWithLifecycle()

    val avgLabel = remember(rhythm.avgPerDay) { "%.1f txns".format(rhythm.avgPerDay) }

    TabList {
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
                if (recurring.isEmpty()) EmptyHint("Nothing regular yet")
                else recurring.forEach { RecurringRow(it) }
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
private fun SpendingTab(vm: AnalyticsViewModel) {
    val direction by vm.direction.collectAsStateWithLifecycle()
    val breakdown by vm.breakdown.collectAsStateWithLifecycle()
    val donutValues by vm.donut.collectAsStateWithLifecycle()
    val donut = rememberSlices(donutValues)
    val top by vm.topSubjects.collectAsStateWithLifecycle()
    val selected by vm.selectedSlice.collectAsStateWithLifecycle()
    val sources by vm.incomeSources.collectAsStateWithLifecycle()

    TabList {
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
            item(key = "breakdown") {
                SectionCard("Breakdown") {
                    ToggleRow(
                        options = remember {
                            listOf(
                                "Category" to Breakdown.CATEGORY,
                                "Type" to Breakdown.TYPE
                            )
                        },
                        selected = breakdown, onSelect = vm::setBreakdown,
                    )
                    Spacer(Modifier.height(12.dp))
                    if (donut.isEmpty()) EmptyHint()
                    else DonutChart(
                        donut,
                        Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        selectedLabel = selected, onSliceClick = vm::selectSlice,
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
                    if (top.isEmpty()) EmptyHint() else TopMerchantsChart(top)
                }
            }
        } else {
            item(key = "sources") {
                SectionCard("Income sources") {
                    if (sources.isEmpty()) EmptyHint() else IncomeSources(sources)
                }
            }
        }
    }
}

/* ----------------------------- Cash flow ----------------------------- */

@Composable
private fun CashFlowTab(vm: AnalyticsViewModel) {
    val balance by vm.balanceTrend.collectAsStateWithLifecycle()
    val cashFlow by vm.cashFlow.collectAsStateWithLifecycle()
    val costs by vm.costs.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val fees = remember(stats) { stats.incomeCost + stats.expenseCost }

    TabList {
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
            SectionCard("Amounts vs fees") {
                Text(
                    "You paid ${formatKes(fees)} in fees",
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
private fun TabList(content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
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
private fun IncomeSources(sources: List<SourceAmount>) {
    val total = remember(sources) { sources.sumOf { it.amount }.coerceAtLeast(1.0) }
    val barColor = MoniesTheme.extra.inflow
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val shape = remember { RoundedCornerShape(5.dp) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        sources.forEach { s ->
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        s.name,
                        Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(formatKes(s.amount), style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .background(trackColor, shape)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth((s.amount / total).toFloat())
                            .fillMaxHeight()
                            .background(barColor, shape),
                    )
                }
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
            .background(cs.surfaceVariant.copy(alpha = 0.5f), outer)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEach { (label, value) ->
            val active = value == selected
            Box(
                Modifier
                    .background(if (active) cs.surface else Color.Transparent, inner)
                    .clickable { onSelect(value) }
                    .padding(horizontal = 16.dp, vertical = 7.dp),
            ) {
                Text(
                    label, style = MaterialTheme.typography.labelLarge,
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