package com.kimaita.monies.ui.analytics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kimaita.monies.services.activeSummary
import com.kimaita.monies.ui.charts.*
import com.kimaita.monies.ui.theme.MoniesTheme
import com.kimaita.monies.ui.util.formatCompactKes
import com.kimaita.monies.ui.util.formatSignedKes
import kotlinx.coroutines.flow.map

/** Every card shows its own placeholder until its own value arrives. */
@Composable
internal fun OverviewTab(vm: AnalyticsViewModel, listState: LazyListState, onJump: (AnalyticsTab) -> Unit) {
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
                TopLine("Top in", sources != null, sources?.firstOrNull()?.let { it.label to it.amount })
                TopLine("Top out", top != null, top?.firstOrNull()?.let { it.label to it.amount })
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
