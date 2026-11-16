package com.kimaita.monies.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kimaita.monies.R
import com.kimaita.monies.data.dao.dtos.CategoryBudget
import com.kimaita.monies.data.dao.dtos.SubjectAmount
import com.kimaita.monies.models.TimeFilter
import com.kimaita.monies.services.Change
import com.kimaita.monies.services.History
import com.kimaita.monies.services.Move
import com.kimaita.monies.services.changes
import com.kimaita.monies.services.compareLabels
import com.kimaita.monies.services.historyNote
import com.kimaita.monies.services.percentShares
import com.kimaita.monies.services.previousPhrase
import com.kimaita.monies.services.squarify
import com.kimaita.monies.services.totalChangeSentence
import com.kimaita.monies.ui.charts.ChartFont
import com.kimaita.monies.ui.components.Segmented
import com.kimaita.monies.ui.theme.MoniesTheme
import com.kimaita.monies.ui.util.formatAmount
import com.kimaita.monies.ui.util.formatKes

private val directionOptions = listOf("Out" to SpendDirection.OUT, "In" to SpendDirection.IN)
private val breakdownOptions = listOf("Category" to Breakdown.CATEGORY, "Type" to Breakdown.TYPE)

/** Tile shades, strongest first: the money colour mixed into the card. */
private val tileMix = listOf(0.70f, 0.60f, 0.48f, 0.40f, 0.34f, 0.28f, 0.22f)

/** One box of the treemap or row of the comparison list. [id] is a subject for In, else null. */
private class Part(val label: String, val amount: Double, val count: Int, val change: Change?, val id: Long? = null)

/** One side at a time, so each collects (and queries for) only what it shows. */
@Composable
internal fun SpendingTab(vm: AnalyticsViewModel, listState: LazyListState, onSubject: (Long) -> Unit) {
    val direction by vm.direction.collectAsStateWithLifecycle()
    if (direction == SpendDirection.OUT) SpendingOut(vm, listState, onSubject) else SpendingIn(vm, listState, onSubject)
}

@Composable
private fun Toggles(vm: AnalyticsViewModel, direction: SpendDirection) {
    val mode by vm.breakdownMode.collectAsStateWithLifecycle()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Segmented(directionOptions, direction, vm::setDirection, Modifier.width(120.dp))
        if (direction == SpendDirection.OUT) Segmented(breakdownOptions, mode, vm::setBreakdownMode, Modifier.weight(1f))
        else Note("By who paid you")
    }
}

/** The comparison is shown only with a period before it and the history to fill it. */
private fun comparable(time: TimeFilter, history: History?) = time != TimeFilter.ALL && history?.enough == true

@Composable
private fun SpendingOut(vm: AnalyticsViewModel, listState: LazyListState, onSubject: (Long) -> Unit) {
    val slices by vm.breakdown.collectAsStateWithLifecycle()
    val before by vm.breakdownBefore.collectAsStateWithLifecycle()
    val byType by vm.breakdownByType.collectAsStateWithLifecycle()
    val top by vm.topSubjects.collectAsStateWithLifecycle()
    val selectedSlice by vm.selectedSlice.collectAsStateWithLifecycle()
    val budgets by vm.budgets.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val statsBefore by vm.statsBefore.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val time by vm.timeFilter.collectAsStateWithLifecycle()
    val historyStart by vm.historyStart.collectAsStateWithLifecycle()

    val compare = comparable(time, history)
    val parts = remember(slices, before, compare) {
        slices?.map { s ->
            Part(s.label, s.value.toDouble(), s.count, if (compare) Change(s.value.toDouble(), before?.get(s.label) ?: 0.0) else null)
        }
    }
    val selected = parts?.firstOrNull { it.label == selectedSlice }
    val total = stats?.expense
    val totalChange = if (compare && total != null) statsBefore?.let { Change(total, it.expense) } else null

    TabList(listState) {
        item(key = "toggles") { Toggles(vm, SpendDirection.OUT) }
        item(key = "total") {
            TotalCard(
                "Out · ${periodLabel(time, historyStart)}", total, totalChange, time, parts,
                selected?.label, out = true, onTap = { vm.selectSlice(it.label) },
            )
        }
        item(key = "selected") {
            SelectedCard(selected, parts, total, top, compare, time, onClear = { vm.selectSlice(null) }, onSubject)
        }
        if (time != TimeFilter.ALL) item(key = "changes") {
            BiggestChanges(parts, before, history, time, out = true)
        }
        item(key = "rows") {
            CompareList(
                if (byType) "By type" else "By category", parts, compare, selected?.label, out = true,
                footer = if (compare) "Grey: moved less than 10%." else null,
                onTap = { vm.selectSlice(it.label) },
            )
        }
        if (budgets.isNotEmpty()) item(key = "budgets") {
            SectionCard("Budgets this month") {
                budgets.forEachIndexed { i, b ->
                    if (i > 0) Spacer(Modifier.height(12.dp))
                    BudgetRow(b)
                }
            }
        }
    }
}

@Composable
private fun SpendingIn(vm: AnalyticsViewModel, listState: LazyListState, onSubject: (Long) -> Unit) {
    val sources by vm.incomeSources.collectAsStateWithLifecycle()
    val sourcesBefore by vm.incomeSourcesBefore.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val statsBefore by vm.statsBefore.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val time by vm.timeFilter.collectAsStateWithLifecycle()
    val historyStart by vm.historyStart.collectAsStateWithLifecycle()

    val compare = comparable(time, history)
    val beforeMap = remember(sourcesBefore) { sourcesBefore?.associate { it.name to it.amount } }
    val parts = remember(sources, beforeMap, compare) {
        sources?.map { s ->
            Part(s.name, s.amount, s.count, if (compare) Change(s.amount, beforeMap?.get(s.name) ?: 0.0) else null, s.subjectId)
        }
    }
    val total = stats?.income
    val totalChange = if (compare && total != null) statsBefore?.let { Change(total, it.income) } else null

    TabList(listState) {
        item(key = "toggles") { Toggles(vm, SpendDirection.IN) }
        item(key = "total") {
            TotalCard(
                "In · ${periodLabel(time, historyStart)}", total, totalChange, time, parts, null, out = false,
                onTap = { p -> p.id?.let(onSubject) },
            )
        }
        if (time != TimeFilter.ALL) item(key = "changes") {
            BiggestChanges(parts, beforeMap, history, time, out = false)
        }
        item(key = "rows") {
            CompareList(
                "By source", parts, compare, null, out = false, footer = "Tap a source for its payments.",
                onTap = { p -> p.id?.let(onSubject) },
            )
        }
    }
}

/** Pill colours for a move: spending more is bad news, money coming in is good. */
@Composable
private fun pillColors(move: Move, out: Boolean): Pair<Color, Color> {
    val cs = MaterialTheme.colorScheme
    val bad = cs.errorContainer to cs.onErrorContainer
    val good = cs.secondaryContainer to cs.onSecondaryContainer
    return when (move) {
        Move.UP, Move.NEW -> if (out) bad else good
        Move.DOWN -> if (out) good else bad
        Move.FLAT -> cs.surfaceContainerHighest to cs.onSurfaceVariant
    }
}

@Composable
private fun MovePill(c: Change, out: Boolean, small: Boolean = false) {
    val (bg, fg) = pillColors(c.move, out)
    Text(
        c.pill,
        Modifier
            .background(bg, RoundedCornerShape(if (small) 6.dp else 8.dp))
            .padding(horizontal = if (small) 5.dp else 7.dp, vertical = if (small) 1.dp else 2.dp),
        fontFamily = ChartFont, fontSize = if (small) 10.sp else 11.sp, lineHeight = if (small) 14.sp else 16.sp,
        fontWeight = if (c.move == Move.FLAT) FontWeight.Normal else FontWeight.Bold, color = fg, maxLines = 1,
    )
}

@Composable
private fun TotalCard(
    label: String,
    total: Double?,
    change: Change?,
    time: TimeFilter,
    parts: List<Part>?,
    selected: String?,
    out: Boolean,
    onTap: (Part) -> Unit,
) {
    BentoCard {
        CardHeader(label)
        if (total == null || parts == null) {
            EmptyHint(LOADING)
            return@BentoCard
        }
        Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(formatKes(total), fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold)
            if (change != null && change.move != Move.FLAT) MovePill(change, out)
        }
        when {
            time == TimeFilter.ALL -> Note("Nothing to compare with. Pick 1M, 6M or YTD.", Modifier.padding(top = 2.dp))
            change != null -> Note(totalChangeSentence(change, previousPhrase(time), ::formatAmount), Modifier.padding(top = 2.dp))
        }
        if (parts.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Say(if (out) "Nothing went out in this period." else "Nothing came in in this period.")
            return@BentoCard
        }
        Spacer(Modifier.height(12.dp))
        val hidden = Treemap(parts, total, selected, out, onTap)
        val pills = parts.mapNotNull { it.change }.filter { it.move != Move.FLAT }
        val notes = buildList {
            if (pills.isNotEmpty()) add("Shown for moves of 10% or more.")
            if (hidden.isNotEmpty()) add(
                "Not labelled: " + hidden.joinToString("; ") { p ->
                    "${p.label}, ${formatAmount(p.amount)}" + if (p.change?.move == Move.NEW) " (new)" else ""
                } + "."
            )
        }
        if (pills.isNotEmpty() || notes.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (pills.isNotEmpty()) {
                    val more = if (out) "spent more" else "more came in"
                    val less = if (out) "spent less" else "less came in"
                    pills.firstOrNull { it.move == Move.UP }?.let { KeyPill(it, out, more) }
                    pills.firstOrNull { it.move == Move.DOWN }?.let { KeyPill(it, out, less) }
                }
                notes.forEach { Note(it) }
            }
        }
    }
}

@Composable
private fun KeyPill(c: Change, out: Boolean, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        MovePill(c, out, small = true)
        Note(label)
    }
}

/**
 * The parts as a squarified treemap, at most 8 named boxes and the rest as "Others". Returns the
 * parts whose boxes were too small to name.
 */
@Composable
private fun Treemap(parts: List<Part>, total: Double, selected: String?, out: Boolean, onTap: (Part) -> Unit): List<Part> {
    val cs = MaterialTheme.colorScheme
    val extra = MoniesTheme.extra
    val base = if (out) extra.outflow else extra.inflow
    val shown = remember(parts, total) {
        val top = parts.take(8)
        val rest = total - top.sumOf { it.amount }
        if (parts.size > 8 && rest > 0) top + Part("Others", rest, parts.drop(8).sumOf { it.count }, null) else top
    }
    val shares = remember(shown) { percentShares(shown.map { it.amount }) }
    val hidden = mutableListOf<Part>()
    BoxWithConstraints(Modifier.fillMaxWidth().height(210.dp)) {
        val w = maxWidth
        val h = maxHeight
        val tiles = remember(shown, w, h) { squarify(shown.map { it.amount }, w.value, h.value) }
        val gap = 2.dp
        tiles.forEach { t ->
            val p = shown[t.index]
            val tw = (t.w.dp - gap).coerceAtLeast(1.dp)
            val th = (t.h.dp - gap).coerceAtLeast(1.dp)
            val small = tw < 90.dp || th < 56.dp
            val named = tw >= 44.dp && th >= 30.dp
            if (!named) hidden += p
            val isSelected = p.label == selected
            val ink = if (t.index == 0) extra.onTile else cs.onSurface
            val shape = RoundedCornerShape(10.dp)
            Column(
                Modifier
                    .offset(t.x.dp, t.y.dp)
                    .size(tw, th)
                    .background(lerp(cs.surfaceContainer, base, tileMix.getOrElse(t.index) { 0.18f }), shape)
                    .then(if (isSelected) Modifier.border(3.dp, cs.onSurface, shape) else Modifier)
                    .clickable(role = Role.Button, enabled = p.label != "Others") { onTap(p) }
                    .semantics { contentDescription = "${p.label}, ${shares[t.index]}%" + (p.change?.let { ", ${it.pill}" } ?: "") }
                    .padding(if (small) 6.dp else 8.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                if (!named) return@Column
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            p.label, fontWeight = FontWeight.Bold, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            fontSize = if (small) 12.sp else 14.sp, lineHeight = if (small) 14.sp else 18.sp,
                        )
                        if (small) p.change?.takeIf { it.move != Move.FLAT && th >= 56.dp }?.let { MovePill(it, out, small = true) }
                    }
                    if (!small) p.change?.takeIf { it.move != Move.FLAT }?.let { MovePill(it, out, small = true) }
                    if (isSelected) {
                        Spacer(Modifier.weight(1f))
                        Icon(painterResource(R.drawable.check_24px), "Selected", Modifier.size(16.dp), tint = ink)
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    if (!small && tw >= 120.dp && th >= 80.dp) {
                        Text(formatAmount(p.amount), Modifier.weight(1f), fontFamily = ChartFont, fontSize = 12.sp, color = ink)
                    } else Spacer(Modifier.weight(1f))
                    Text("${shares[t.index]}%", fontWeight = FontWeight.Bold, color = ink, fontSize = if (small) 12.sp else 14.sp)
                }
            }
        }
    }
    return hidden
}

@Composable
private fun SelectedCard(
    selected: Part?,
    parts: List<Part>?,
    total: Double?,
    top: List<SubjectAmount>?,
    compare: Boolean,
    time: TimeFilter,
    onClear: () -> Unit,
    onSubject: (Long) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    BentoCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(selected?.label ?: "Top merchants", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                val count = selected?.count ?: parts?.sumOf { it.count }
                val payments = count?.let { " · $it ${if (it == 1) "payment" else "payments"}" } ?: ""
                if (selected != null && total != null && total > 0) {
                    Note("${(selected.amount / total * 100).toInt()}% of spending$payments")
                } else if (selected == null) Note("All spending$payments")
            }
            if (selected != null) {
                AssistChip(
                    onClick = onClear,
                    label = { Text("Clear") },
                    leadingIcon = { Icon(painterResource(R.drawable.close_small_24dp), null, Modifier.size(16.dp)) },
                    modifier = Modifier.semantics { contentDescription = "Clear the ${selected.label} selection" },
                )
            }
        }
        val change = selected?.change
        if (selected != null && compare && change != null) {
            val (nowLabel, beforeLabel) = compareLabels(time)
            val max = maxOf(change.now, change.before).coerceAtLeast(1.0)
            Column(
                Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .background(cs.surface, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CompareBar(nowLabel, change.now, (change.now / max).toFloat(), MoniesTheme.extra.outflow, 10.dp, strong = true)
                CompareBar(beforeLabel, change.before, (change.before / max).toFloat(), MoniesTheme.extra.track2, 10.dp, strong = false)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    if (change.move != Move.FLAT) MovePill(change, out = true)
                    Spacer(Modifier.width(6.dp))
                    Note(
                        when {
                            change.delta > 0 -> "${formatAmount(change.delta)} more"
                            change.delta < 0 -> "${formatAmount(-change.delta)} less"
                            else -> "No change"
                        }
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        when {
            top == null -> EmptyHint(LOADING)
            top.isEmpty() -> EmptyHint()
            else -> {
                val max = top.maxOf { it.amount }.coerceAtLeast(1.0)
                top.forEachIndexed { i, m ->
                    if (i > 0) HorizontalDivider(color = cs.outlineVariant)
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button, onClickLabel = "Open ${m.name}") { onSubject(m.subjectId) }
                            .padding(vertical = 9.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(m.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (m.count > 0) Note(
                                    "${m.count} ${if (m.count == 1) "payment" else "payments"} · ~${formatAmount(m.amount / m.count)} each"
                                )
                            }
                            Text(formatAmount(m.amount), fontWeight = FontWeight.Bold)
                        }
                        Bar((m.amount / max).toFloat(), MoniesTheme.extra.outflow, 6.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CompareBar(label: String, value: Double, fraction: Float, color: Color, height: Dp, strong: Boolean) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Note(label, Modifier.width(100.dp))
        Box(Modifier.weight(1f)) { Bar(fraction, color, height, track = Color.Transparent) }
        Text(
            formatAmount(value), Modifier.width(56.dp), fontFamily = ChartFont, fontSize = 12.sp,
            fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal,
            color = if (strong) cs.onSurface else cs.onSurfaceVariant,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun Bar(fraction: Float, color: Color, height: Dp, track: Color = MaterialTheme.colorScheme.surfaceContainerHighest) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .background(track, RoundedCornerShape(height / 2))
    ) {
        if (fraction > 0f) Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                .fillMaxHeight()
                .background(color, RoundedCornerShape(height / 2))
        )
    }
}

@Composable
private fun BiggestChanges(parts: List<Part>?, before: Map<String, Double>?, history: History?, time: TimeFilter, out: Boolean) {
    val cs = MaterialTheme.colorScheme
    BentoCard {
        Text("Biggest changes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (parts == null || history == null) {
            Spacer(Modifier.height(8.dp))
            EmptyHint(LOADING)
            return@BentoCard
        }
        if (!history.enough) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Glyph("⋯", cs.surfaceContainerHighest, cs.onSurfaceVariant)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Say("Not enough history yet.")
                    Note(historyNote(history))
                }
            }
            return@BentoCard
        }
        val all = remember(parts, before) { changes(parts.associate { it.label to it.amount }, before.orEmpty()) }
        val big = all.filter { it.second.big }.take(5)
        Note(
            "Vs ${previousPhrase(time)}." + if (out) " Moves of 10% and KES 500 or more." else "",
            Modifier.padding(top = 2.dp),
        )
        if (big.isEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Glyph("–", cs.secondaryContainer, cs.onSecondaryContainer)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Say("Nothing changed much.")
                    Note("All within 10% or KES 500.")
                }
            }
            all.firstOrNull()?.takeIf { it.second.delta != 0.0 }?.let { (name, c) ->
                HorizontalDivider(Modifier.padding(top = 12.dp, bottom = 10.dp), color = cs.outlineVariant)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Note("Largest move", Modifier.weight(1f))
                    Text(name, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(6.dp))
                    Text("${signedAmount(c.delta)} · ${c.pill}", fontFamily = ChartFont, fontSize = 12.sp, color = cs.onSurfaceVariant)
                }
            }
            return@BentoCard
        }
        Spacer(Modifier.height(8.dp))
        val extra = MoniesTheme.extra
        big.forEach { (name, c) ->
            val up = c.delta > 0
            val (bg, fg) = pillColors(if (up) Move.UP else Move.DOWN, out)
            val good = up != out
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Glyph(if (up) "↑" else "↓", bg, fg)
                Column(Modifier.weight(1f)) {
                    Text(name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Note("${formatAmount(c.before)} → ${formatAmount(c.now)}")
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(signedAmount(c.delta), fontWeight = FontWeight.Bold, color = if (good) extra.positive else extra.negative)
                    Text(c.pill, fontFamily = ChartFont, fontSize = 12.sp, color = cs.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Glyph(text: String, bg: Color, fg: Color) {
    Box(Modifier.size(32.dp).background(bg, CircleShape), contentAlignment = Alignment.Center) {
        Text(text, color = fg, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
private fun CompareList(
    title: String,
    parts: List<Part>?,
    compare: Boolean,
    selected: String?,
    out: Boolean,
    footer: String?,
    onTap: (Part) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val extra = MoniesTheme.extra
    val color = if (out) extra.outflow else extra.inflow
    BentoCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (compare) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp, 8.dp).background(color, RoundedCornerShape(3.dp))); Note("Now")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp, 4.dp).background(extra.track2, RoundedCornerShape(2.dp))); Note("Before")
                    }
                }
            }
        }
        if (parts == null) {
            EmptyHint(LOADING)
            return@BentoCard
        }
        if (parts.isEmpty()) {
            EmptyHint()
            return@BentoCard
        }
        Spacer(Modifier.height(6.dp))
        val max = parts.maxOf { maxOf(it.amount, it.change?.before ?: 0.0) }.coerceAtLeast(1.0)
        parts.forEach { p ->
            val on = p.label == selected
            val c = p.change
            val moved = c != null && c.move != Move.FLAT
            Column(
                Modifier
                    .fillMaxWidth()
                    .then(if (on) Modifier.background(cs.surface, RoundedCornerShape(12.dp)) else Modifier)
                    .clickable(role = Role.Button, enabled = !out || p.label != "Others") { onTap(p) }
                    .padding(horizontal = if (on) 8.dp else 0.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        p.label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    )
                    Text(
                        formatAmount(p.amount), fontWeight = FontWeight.Bold,
                        color = if (on || moved || c == null) cs.onSurface else cs.onSurfaceVariant,
                    )
                    c?.let { MovePill(it, out) }
                }
                Bar((p.amount / max).toFloat(), color, 10.dp, track = Color.Transparent)
                if (c != null && c.before > 0) Bar((c.before / max).toFloat(), extra.track2, 4.dp, track = Color.Transparent)
            }
        }
        footer?.let { Note(it, Modifier.padding(top = 6.dp)) }
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
