package com.kimaita.monies.ui.analytics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kimaita.monies.R
import com.kimaita.monies.data.dao.dtos.CategoryBudget
import com.kimaita.monies.ui.charts.*
import com.kimaita.monies.ui.components.Segmented
import com.kimaita.monies.ui.theme.MoniesTheme
import com.kimaita.monies.ui.util.formatKes
import kotlinx.coroutines.flow.map

private val directionOptions = listOf("Out" to SpendDirection.OUT, "In" to SpendDirection.IN)
private val breakdownOptions = listOf("Category" to Breakdown.CATEGORY, "Type" to Breakdown.TYPE)

/** One side at a time, so each collects (and queries for) only what it shows. */
@Composable
internal fun SpendingTab(vm: AnalyticsViewModel, listState: LazyListState) {
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
    val selected = slices?.let { rows -> selectedSlice?.takeIf { s -> rows.any { it.label == s } } }

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
                if (top == null) EmptyHint(LOADING)
                else if (top.isEmpty()) EmptyHint() else RankedBars(top, extra.outflow)
            }
        }
    }
}

@Composable
private fun SpendingIn(vm: AnalyticsViewModel, listState: LazyListState) {
    val sourcesOrNull by vm.incomeSources.collectAsStateWithLifecycle()
    val sources = sourcesOrNull

    TabList(listState) {
        item(key = "direction") { DirectionToggle(SpendDirection.IN, vm::setDirection) }
        item(key = "sources") {
            SectionCard("Income sources") {
                if (sources == null) EmptyHint(LOADING)
                else if (sources.isEmpty()) EmptyHint() else RankedBars(sources, MoniesTheme.extra.inflow)
            }
        }
    }
}

@Composable
private fun DirectionToggle(direction: SpendDirection, onSelect: (SpendDirection) -> Unit) =
    Segmented(directionOptions, direction, onSelect)

@Composable
internal fun CashFlowTab(vm: AnalyticsViewModel, listState: LazyListState) {
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
