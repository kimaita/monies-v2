package com.kimaita.monies.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kimaita.monies.R
import com.kimaita.monies.data.dao.dtos.IncExp
import com.kimaita.monies.data.dao.dtos.TypeFees
import com.kimaita.monies.models.TimeFilter
import com.kimaita.monies.services.LedgerKind
import com.kimaita.monies.services.ledger
import com.kimaita.monies.ui.charts.*
import com.kimaita.monies.ui.theme.MoniesTheme
import com.kimaita.monies.ui.util.formatAmount

/** What one bar stands for under each chip: "week" for 1M. */
private fun bucketWord(tf: TimeFilter): String = when (tf) {
    TimeFilter.DAY -> "hour"
    TimeFilter.WEEK, is TimeFilter.CustomRange -> "day"
    TimeFilter.MONTH -> "week"
    else -> "month"
}

@Composable
internal fun CashFlowTab(vm: AnalyticsViewModel, listState: LazyListState) {
    val money by vm.money.collectAsStateWithLifecycle()
    val cashFlow by vm.cashFlow.collectAsStateWithLifecycle()
    val costs by vm.costs.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val time by vm.timeFilter.collectAsStateWithLifecycle()
    val historyStart by vm.historyStart.collectAsStateWithLifecycle()
    val typeFees by vm.typeFees.collectAsStateWithLifecycle()
    val period = periodLabel(time, historyStart)

    TabList(listState) {
        item(key = "balance") { BalanceWalkCard(money, period) }
        item(key = "inout") { InVsOutCard(cashFlow, stats, time, period, money) }
        item(key = "fees") { FeesRow(costs, stats, period, typeFees) }
    }
}

@Composable
private fun Hero(text: String) = Text(
    text, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp, lineHeight = 32.sp),
    fontWeight = FontWeight.Bold, maxLines = 1,
)

@Composable
private fun CardTitle(title: String, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        trailing()
    }
}

@Composable
private fun BalanceWalkCard(m: AnalyticsViewModel.MoneyState?, period: String) {
    val extra = MoniesTheme.extra
    val cs = MaterialTheme.colorScheme
    var open by rememberSaveable { mutableStateOf(false) }
    BentoCard {
        CardTitle("Balance") { TileLabel(period.replaceFirstChar { it.uppercase() }) }
        if (m == null) {
            EmptyHint(LOADING)
            return@BentoCard
        }
        val w = m.walk
        val change = Math.round(w.change)
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Hero("KES ${formatAmount(w.closing)}")
            Text(
                "${signedAmount(w.change)} ${sinceLabel(m.time, m.start)}",
                Modifier.padding(bottom = 5.dp),
                style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold,
                color = if (change >= 0) extra.positive else extra.negative,
            )
        }
        if (m.points.size >= 2) {
            Spacer(Modifier.height(12.dp))
            BalanceTrendChart(m.points, Modifier.fillMaxWidth(), plotHeight = 150.dp, highLow = true, paydayAt = m.paydayAt)
        }
        HorizontalDivider(Modifier.padding(top = 12.dp), color = cs.outlineVariant)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clickable(role = Role.Button, onClickLabel = if (open) "Close" else "Open") { open = !open }
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Why it moved", fontWeight = FontWeight.Bold)
                Note(whyItMovedSummary(w))
            }
            Icon(
                painterResource(R.drawable.keyboard_arrow_right), null,
                Modifier
                    .size(20.dp)
                    .rotate(if (open) -90f else 90f),
                tint = if (open) cs.onSurface else cs.onSurfaceVariant,
            )
        }
        if (open) {
            Spacer(Modifier.height(6.dp))
            Statement(m)
            Spacer(Modifier.height(10.dp))
            Note("Fees and transfers aren't spending, so they sit below Kept.")
        }
    }
}

@Composable
private fun LedgerRow(
    name: String, change: String?, balance: String?,
    changeColor: Color = Color.Unspecified, bold: Boolean = false, muted: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val mono = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, lineHeight = 20.sp)
    val color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    val weight = if (bold) FontWeight.Bold else null
    Row(modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium.copy(fontSize = if (muted) 12.sp else 14.sp), color = color, fontWeight = weight)
        LedgerFigure(change.orEmpty(), mono, if (changeColor != Color.Unspecified) changeColor else color, weight)
        LedgerFigure(balance.orEmpty(), mono, color, weight)
    }
}

private val FigureWidth = 88.dp

/** One figure column of the statement: always one line, shrinking to fit a seven-figure amount. */
@Composable
private fun LedgerFigure(text: String, style: androidx.compose.ui.text.TextStyle, color: Color, weight: FontWeight?) = Text(
    text, Modifier.width(FigureWidth), style = style.copy(textAlign = TextAlign.End), color = color, fontWeight = weight,
    maxLines = 1, softWrap = false,
    autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = style.fontSize),
)

/** "Why it moved", opened: the walk as a bank statement, with the change and the balance after each line. */
@Composable
private fun Statement(m: AnalyticsViewModel.MoneyState) {
    val cs = MaterialTheme.colorScheme
    val extra = MoniesTheme.extra
    val w = m.walk
    val lines = remember(w) { w.ledger() }
    Column {
        Row {
            Spacer(Modifier.weight(1f))
            listOf("Change", "Balance").forEach {
                Text(it, Modifier.width(FigureWidth), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, textAlign = TextAlign.End)
            }
        }
        val opening = m.start?.let { "Balance on ${dayMonthLabel(it)}" } ?: "Balance at the start"
        LedgerRow(opening, null, formatAmount(w.opening))
        HorizontalDivider(color = cs.outlineVariant)
        lines.forEach { l ->
            val tone = when {
                l.kind == LedgerKind.IN || (l.kind == LedgerKind.KEPT && l.change >= 0) -> extra.positive
                l.kind == LedgerKind.KEPT -> extra.negative
                else -> Color.Unspecified
            }
            if (l.kind == LedgerKind.KEPT) {
                LedgerRow(
                    ledgerName(l), signedAmount(l.change), null, tone, muted = true,
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .background(cs.surface, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp),
                )
            } else {
                LedgerRow(ledgerName(l), signedAmount(l.change), l.balance?.let { formatAmount(it) }, tone)
                HorizontalDivider(color = cs.outlineVariant)
            }
        }
        HorizontalDivider(Modifier.padding(top = 2.dp), thickness = 3.dp, color = cs.onSurface)
        val c = Math.round(w.change)
        LedgerRow(
            "Balance today", signedAmount(w.change), formatAmount(w.closing),
            if (c > 0) extra.positive else if (c < 0) extra.negative else Color.Unspecified, bold = true,
        )
    }
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier, dot: Color? = null, valueColor: Color = Color.Unspecified) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier
            .background(cs.surface, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            dot?.let { Box(Modifier.size(10.dp).background(it, RoundedCornerShape(3.dp))) }
            Text(label, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        }
        Text(value, style = MaterialTheme.typography.titleMedium.copy(lineHeight = 22.sp), color = valueColor, maxLines = 1)
    }
}

@Composable
private fun InVsOutCard(buckets: List<BarBucket>?, stats: IncExp?, time: TimeFilter, period: String, m: AnalyticsViewModel.MoneyState?) {
    val extra = MoniesTheme.extra
    var picked by remember(buckets) { mutableStateOf<Int?>(null) }
    BentoCard {
        val pick = picked?.let { buckets?.getOrNull(it) }
        CardTitle("Money in vs out") {
            if (pick == null) TileLabel(period.replaceFirstChar { it.uppercase() })
            else InputChip(
                selected = true, onClick = { picked = null },
                label = { Text(pick.days?.let { "${pick.label} · $it" } ?: pick.label) },
                trailingIcon = { Icon(painterResource(R.drawable.close_small_24dp), "Back to ${period}", Modifier.size(16.dp)) },
            )
        }
        if (buckets == null || stats == null) {
            EmptyHint(LOADING)
            return@BentoCard
        }
        val inn = pick?.up?.toDouble() ?: stats.income
        val out = pick?.down?.toDouble() ?: stats.expense
        val kept = inn - out
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatBox("In", formatAmount(inn), Modifier.weight(1f), extra.inflow)
            StatBox("Out", formatAmount(out), Modifier.weight(1f), extra.outflow)
            StatBox(
                if (kept >= 0) "Kept" else "Short", signedAmount(kept), Modifier.weight(1f),
                valueColor = if (kept >= 0) extra.positive else extra.negative,
            )
        }
        if (kept < 0) {
            Spacer(Modifier.height(10.dp))
            Note("${formatAmount(-kept)} more out than in ${if (pick != null) "that ${bucketWord(time)}" else inPeriod(time)}.")
        }
        if (buckets.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            BarChart(
                buckets, Modifier.fillMaxWidth(), extra.inflow, extra.outflow, showNet = true,
                picked = picked, onPick = { picked = it.takeIf { i -> i >= 0 } },
                notes = remember(buckets, m) { weekNotes(buckets, m) },
            )
            Spacer(Modifier.height(8.dp))
            val word = bucketWord(time)
            Note("The line is what you kept each $word: in minus out. Tap a $word to see its figures.")
        }
    }
}

@Composable
private fun FeesRow(costs: List<BarBucket>?, stats: IncExp?, period: String, byType: List<TypeFees>?) {
    val gold = MoniesTheme.extra.gold
    BentoCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                TileLabel("Fees · $period")
                if (stats == null || costs == null) {
                    EmptyHint(LOADING)
                    return@Row
                }
                val fees = stats.incomeCost + stats.expenseCost
                Text("KES ${formatAmount(fees)}", style = MaterialTheme.typography.titleLarge.copy(lineHeight = 28.sp))
                val most = costs.maxByOrNull { it.up }?.takeIf { it.up > 0f }
                val parts = listOfNotNull(feeShareText(fees, stats.expense), most?.let { "most in ${it.label}" })
                if (parts.isNotEmpty()) Note(parts.joinToString(" · "))
            }
            val bars = costs?.takeLast(8).orEmpty()
            val max = bars.maxOfOrNull { it.up }?.takeIf { it > 0f }
            if (max != null) Row(Modifier.height(44.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                bars.forEach {
                    Box(
                        Modifier
                            .width(12.dp)
                            .height((44 * it.up / max).coerceAtLeast(2f).dp)
                            .background(gold, RoundedCornerShape(3.dp))
                    )
                }
            }
        }
        val types = byType.orEmpty()
        if (types.isNotEmpty()) {
            val total = types.sumOf { it.fees }
            HorizontalDivider(Modifier.padding(top = 12.dp, bottom = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Say(
                "Most of it on **${types[0].name}**: KES ${formatAmount(types[0].fees)} of ${formatAmount(total)}.",
                style = MaterialTheme.typography.bodyMedium,
            )
            types.forEach { t ->
                Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t.name, fontWeight = FontWeight.Bold, maxLines = 1)
                            Note("${t.count} ${if (t.count == 1) "transaction" else "transactions"} · ~${formatAmount(t.fees / t.count)} each")
                        }
                        Text(formatAmount(t.fees), fontWeight = FontWeight.Bold)
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(3.dp))
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth((t.fees / types[0].fees).toFloat().coerceIn(0.02f, 1f))
                                .fillMaxHeight()
                                .background(gold, RoundedCornerShape(3.dp))
                        )
                    }
                }
            }
        }
    }
}

/**
 * "payday" over the week the biggest payment in landed in and the category of the biggest payment
 * out under its week. Only weekly bars carry their dates, so other periods get none.
 */
private fun weekNotes(buckets: List<BarBucket>, m: AnalyticsViewModel.MoneyState?): Map<Int, Pair<String?, String?>> {
    if (m == null || buckets.none { it.days != null }) return emptyMap()
    fun weekOf(d: java.time.LocalDate) = buckets.indexOfFirst { b ->
        b.days != null && b.label.removePrefix("Wk ").toIntOrNull()?.let { w ->
            com.kimaita.monies.services.weekDays("${d.year}-%02d".format(w))?.contains(d)
        } == true
    }
    val out = HashMap<Int, Pair<String?, String?>>()
    m.payday?.let { d -> weekOf(d).takeIf { it >= 0 }?.let { out[it] = "payday" to null } }
    m.bigOut?.let { (d, cat) -> weekOf(d).takeIf { it >= 0 }?.let { out[it] = (out[it]?.first) to cat } }
    return out
}

