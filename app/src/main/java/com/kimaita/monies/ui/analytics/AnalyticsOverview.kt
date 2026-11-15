package com.kimaita.monies.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kimaita.monies.R
import com.kimaita.monies.data.dao.dtos.ActivityCell
import com.kimaita.monies.data.dao.dtos.IncExp
import com.kimaita.monies.models.TimeFilter
import com.kimaita.monies.services.FlowKind
import com.kimaita.monies.services.FlowNode
import com.kimaita.monies.services.PaceKind
import com.kimaita.monies.services.SliceValue
import com.kimaita.monies.services.activeSummary
import com.kimaita.monies.services.percentShares
import com.kimaita.monies.services.roughly
import com.kimaita.monies.services.shillingsPerFee
import com.kimaita.monies.services.slotShares
import com.kimaita.monies.ui.charts.*
import com.kimaita.monies.ui.theme.ExtraColors
import com.kimaita.monies.ui.theme.MoniesTheme
import com.kimaita.monies.ui.util.formatAmount

/** Every card shows its own placeholder until its own value arrives. */
@Composable
internal fun OverviewTab(vm: AnalyticsViewModel, listState: LazyListState, onJump: (AnalyticsTab) -> Unit) {
    val outlook by vm.outlook.collectAsStateWithLifecycle()
    val money by vm.money.collectAsStateWithLifecycle()
    val breakdown by vm.breakdown.collectAsStateWithLifecycle()
    val activity by vm.activityRaw.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val time by vm.timeFilter.collectAsStateWithLifecycle()
    val historyStart by vm.historyStart.collectAsStateWithLifecycle()
    val period = periodLabel(time, historyStart)

    TabList(listState) {
        item(key = "pace") { PaceCard(outlook, money, historyStart) { onJump(AnalyticsTab.ACTIVITY) } }
        item(key = "balance") { BalanceCard(money, period) { onJump(AnalyticsTab.CASHFLOW) } }
        item(key = "inout") { InOutCard(money, time, period) { onJump(AnalyticsTab.CASHFLOW) } }
        item(key = "where") { WhereItWentCard(breakdown, period) { onJump(AnalyticsTab.SPENDING) } }
        item(key = "active") { ActiveCard(activity, period) { onJump(AnalyticsTab.ACTIVITY) } }
        item(key = "fees") { FeesCard(stats, period) }
    }
}

/**
 * The pace card for the chip's period: progress through it, spending against the period before,
 * the figures, the running totals with a projection, and the regulars still due. For ALL, the
 * all-time total from [money].
 */
@Composable
private fun PaceCard(
    o: AnalyticsViewModel.Outlook?,
    money: AnalyticsViewModel.MoneyState?,
    historyStart: java.time.LocalDate?,
    onDue: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val on = cs.onPrimaryContainer
    BentoCard(containerColor = cs.primaryContainer) {
        val p = o?.period
        val pace = o?.pace
        if (o == null || p == null || pace == null) {
            CardHeader(o?.let { paceLabel(null, it.today, historyStart) } ?: "Spending", color = on)
            Spacer(Modifier.height(14.dp))
            val total = money?.walk?.spending
            if (o == null || total == null) EmptyHint(LOADING) else Say(allTimeSentence(total, historyStart))
            return@BentoCard
        }
        val change = pace.changePct
        val worse = change != null && Math.round(change) > 0
        CardHeader(paceLabel(p, o.today, historyStart), color = on)
        paceProgress(p, o.today)?.let { done ->
            Box(
                Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(on.copy(alpha = 0.18f), RoundedCornerShape(3.dp))
                    .semantics { contentDescription = "${(done * 100).toInt()}% of the period has passed" }
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(done)
                        .fillMaxHeight()
                        .background(on, RoundedCornerShape(3.dp))
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Say(
            paceSentence(p, change, pace.spent, o.mostly),
            toneColor = if (change != null && !worse) on else null, bad = worse,
        )
        Spacer(Modifier.height(12.dp))
        val before = pace.previous.lastOrNull()
        val endLabel = paceEndLabel(p)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniFigure("Spent", formatAmount(pace.spent), Modifier.weight(1f))
            if (endLabel != null) MiniFigure(
                endLabel,
                o.projected?.let { "~" + formatAmount(roughly(it)) } ?: paceFromLabel(p),
                Modifier.weight(1f), quiet = o.projected == null,
            )
            MiniFigure(previousLabel(p), before?.let { formatAmount(it) } ?: "None", Modifier.weight(1f), quiet = before == null)
        }
        if (p.kind != PaceKind.DAY) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                LegendLine(currentLabel(p), cs.primary, on, width = 3.dp)
                if (o.projected != null) LegendLine("Projected", cs.primary, on, dash = 1.dp to 5.dp)
                if (before != null) LegendLine(previousLabel(p), cs.onSurfaceVariant, on, dash = 4.dp to 3.dp)
            }
            Spacer(Modifier.height(8.dp))
            val axis = remember(p, o.today) { paceAxis(p, o.today) }
            PaceChart(pace, p.steps, o.projected, axis, gridColor = on.copy(alpha = 0.2f))
        }
        if (o.stillDue >= 1.0) {
            HorizontalDivider(Modifier.padding(top = 12.dp, bottom = 10.dp), color = on.copy(alpha = 0.2f))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onDue)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Say(
                    "**~${formatAmount(roughly(o.stillDue))}** in regular payments still due",
                    Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = on,
                )
                Icon(painterResource(R.drawable.keyboard_arrow_right), null, Modifier.size(16.dp), tint = on)
            }
        }
    }
}

@Composable
private fun BalanceCard(m: AnalyticsViewModel.MoneyState?, period: String, onLink: () -> Unit) {
    val extra = MoniesTheme.extra
    BentoCard {
        CardHeader("Balance · $period", "Cash flow", onLink)
        Spacer(Modifier.height(6.dp))
        if (m == null) {
            EmptyHint(LOADING)
            return@BentoCard
        }
        val change = Math.round(m.walk.change)
        Say(
            balanceSentence(m.walk.change, m.walk.closing, sinceLabel(m.time, m.start)),
            toneColor = when {
                change > 0 -> extra.positive
                change < 0 -> extra.negative
                else -> null
            },
        )
        if (m.points.size >= 2) {
            Spacer(Modifier.height(12.dp))
            BalanceTrendChart(m.points, Modifier.fillMaxWidth(), plotHeight = 130.dp, paydayAt = m.paydayAt)
        }
    }
}

/** How much of the money-in colour each payer box gets, biggest first; the card shows through the rest. */
private val payerShades = listOf(1f, 0.7f, 0.52f, 0.36f)

/** The flow box colours for everything but the payers, which take [payerShades]. */
private fun flowColor(kind: FlowKind, extra: ExtraColors, primary: Color, muted: Color): Color = when (kind) {
    FlowKind.SOURCE, FlowKind.OTHERS -> extra.inflow
    FlowKind.BALANCE, FlowKind.STAYED -> primary
    FlowKind.MSHWARI -> muted
    FlowKind.FULIZA -> extra.negative
    FlowKind.OTHER -> extra.track2
    FlowKind.SPENDING -> extra.outflow
    FlowKind.FEES -> extra.gold
}

@Composable
private fun InOutCard(m: AnalyticsViewModel.MoneyState?, time: TimeFilter, period: String, onLink: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val extra = MoniesTheme.extra
    BentoCard {
        CardHeader("In and out · $period", "Cash flow", onLink)
        Spacer(Modifier.height(6.dp))
        if (m == null) {
            EmptyHint(LOADING)
            return@BentoCard
        }
        val empty = m.flow.left.isEmpty() && m.flow.right.isEmpty()
        Say(
            inOutHeadline(m.walk, time, empty),
            toneColor = extra.positive.takeIf { m.walk.kept >= 0 }, bad = !empty && m.walk.kept < 0,
        )
        if (!empty) flowNote(m.walk)?.let {
            Spacer(Modifier.height(4.dp))
            Note(it)
        }
        Spacer(Modifier.height(12.dp))
        val card = cs.surfaceContainer
        val others = m.flow.others.size
        fun boxes(nodes: List<FlowNode>): List<FlowBox> {
            var payer = 0
            return nodes.map {
                val color = if (it.kind == FlowKind.SOURCE || it.kind == FlowKind.OTHERS)
                    lerp(card, extra.inflow, payerShades[minOf(payer++, payerShades.lastIndex)])
                else flowColor(it.kind, extra, cs.primary, cs.onSurfaceVariant)
                FlowBox(flowName(it, others), it.amount, color)
            }
        }
        val left = remember(m.flow, extra, cs.primary) { boxes(m.flow.left) }
        val right = remember(m.flow, extra, cs.primary) { boxes(m.flow.right) }
        val description = remember(left, right, period) {
            if (empty) "Nothing has come in or gone out yet"
            else "${period.replaceFirstChar { it.uppercase() }}: " +
                    left.joinToString { "${it.name} ${formatAmount(it.amount)}" } + " into your M-PESA; out: " +
                    right.joinToString { "${it.name} ${formatAmount(it.amount)}" }
        }
        FlowChart(left, right, "Your M-PESA", description)
        othersLine(m.flow.others)?.let {
            Spacer(Modifier.height(10.dp))
            Note(it)
        }
    }
}

@Composable
private fun WhereItWentCard(rows: List<SliceValue>?, period: String, onLink: () -> Unit) {
    val extra = MoniesTheme.extra
    val card = MaterialTheme.colorScheme.surfaceContainer
    BentoCard {
        CardHeader("Where it went · $period", "Spending", onLink)
        Spacer(Modifier.height(6.dp))
        if (rows == null) {
            EmptyHint(LOADING)
            return@BentoCard
        }
        if (rows.isEmpty()) {
            EmptyHint()
            return@BentoCard
        }
        val top = rows.take(4)
        val rest = rows.drop(4)
        val values = top.map { it.value.toDouble() } + listOfNotNull(rest.sumOf { it.value.toDouble() }.takeIf { rest.isNotEmpty() })
        val pcts = remember(rows) { percentShares(values) }
        val shades = listOf(1f, 0.72f, 0.5f, 0.34f).map { lerp(card, extra.outflow, it) }
        val colors = top.indices.map { shades[it] } + listOfNotNull(extra.track2.takeIf { rest.isNotEmpty() })
        val names = top.map { it.label } + listOfNotNull(
            when (rest.size) {
                0 -> null
                1 -> rest[0].label
                else -> "${rest.size} others"
            }
        )
        Say(whereItWentSentence(top[0].label, pcts[0]))
        Spacer(Modifier.height(12.dp))
        ShareBar(
            values.indices.map { values[it].toFloat() to colors[it] },
            Modifier.semantics {
                contentDescription = "Share of spending: " + names.indices.joinToString { "${names[it]} ${pcts[it]}%" }
            },
        )
        Spacer(Modifier.height(10.dp))
        names.indices.chunked(2).forEach { pair ->
            Row(Modifier.padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                pair.forEach { i -> LegendShare(names[i], pcts[i], colors[i], Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LegendShare(name: String, pct: Int, color: Color, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .size(10.dp)
                .background(color, RoundedCornerShape(3.dp))
        )
        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        Text("$pct%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val slotLabels = listOf("Morning", "Midday", "Evening", "Night")

@Composable
private fun ActiveCard(cells: List<ActivityCell>?, period: String, onLink: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val extra = MoniesTheme.extra
    BentoCard {
        CardHeader("When you're active · $period", "Activity", onLink)
        Spacer(Modifier.height(6.dp))
        if (cells == null) {
            EmptyHint(LOADING)
            return@BentoCard
        }
        val summary = remember(cells) { activeSummary(cells) }
        if (summary == null) {
            Say("Not enough payments to show a pattern.")
            Spacer(Modifier.height(4.dp))
            Note("Needs 5 or more. Try a longer period.")
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(4) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(14.dp)
                            .background(cs.surfaceContainerHighest, RoundedCornerShape(4.dp))
                    )
                }
            }
            return@BentoCard
        }
        val shares = remember(cells) { slotShares(cells) }
        val busiest = shares.indices.maxBy { shares[it] }
        Say(summary)
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.semantics {
                contentDescription = "Payments by time of day: " +
                        slotLabels.indices.joinToString { "${slotLabels[it].lowercase()} ${shares[it]}%" }
            },
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            shares.forEachIndexed { i, pct ->
                val on = i == busiest
                Column(Modifier.weight(maxOf(pct, 11).toFloat()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .background(if (on) cs.primary else extra.track2, RoundedCornerShape(4.dp))
                    )
                    Text(
                        "$pct%", style = MaterialTheme.typography.labelMedium,
                        color = if (on) cs.onSurface else cs.onSurfaceVariant,
                        fontWeight = if (on) FontWeight.Bold else null,
                    )
                    Text(
                        slotLabels[i], style = MaterialTheme.typography.bodySmall,
                        color = if (on) cs.onSurface else cs.onSurfaceVariant, maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun FeesCard(stats: IncExp?, period: String) {
    val extra = MoniesTheme.extra
    BentoCard {
        CardHeader("Fees · $period")
        Spacer(Modifier.height(6.dp))
        if (stats == null) {
            EmptyHint(LOADING)
            return@BentoCard
        }
        val fees = stats.incomeCost + stats.expenseCost
        val perFee = shillingsPerFee(fees, stats.expense)
        Say(feesSentence(fees, perFee))
        if (perFee != null && perFee in 2..200) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.semantics {
                    contentDescription = "$perFee dots, one of them gold: fees are about one in every $perFee shillings spent"
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DotGrid(perFee, extra.track2, extra.gold)
                Column(Modifier.padding(start = 14.dp)) {
                    val small = MaterialTheme.typography.labelSmall
                    val muted = MaterialTheme.colorScheme.onSurfaceVariant
                    Text("1 dot ≈ KES ${formatAmount(stats.expense / perFee)}", style = small, color = muted)
                    Text("of spending", style = small, color = muted)
                    Spacer(Modifier.height(4.dp))
                    Text("● fees", style = small, color = extra.gold, fontWeight = FontWeight.Bold)
                }
            }
        }
        feeShareText(fees, stats.expense)?.let {
            Spacer(Modifier.height(8.dp))
            Note("$it.")
        }
    }
}
