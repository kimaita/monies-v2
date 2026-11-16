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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kimaita.monies.data.dao.dtos.ActivityCell
import com.kimaita.monies.data.dao.dtos.DayPayment
import com.kimaita.monies.data.dao.dtos.SubjectCount
import com.kimaita.monies.models.analytics.RecurringPayment
import com.kimaita.monies.services.CalendarDay
import com.kimaita.monies.services.MonthCalendar
import com.kimaita.monies.services.Streak
import com.kimaita.monies.services.dueDates
import com.kimaita.monies.services.initials
import com.kimaita.monies.services.noSpendDayNote
import com.kimaita.monies.services.noSpendSentence
import com.kimaita.monies.services.pastMonthSentence
import com.kimaita.monies.services.paymentsPerDaySentence
import com.kimaita.monies.services.recurringSummary
import com.kimaita.monies.services.streakSentence
import com.kimaita.monies.services.timeOfDaySentence
import com.kimaita.monies.services.weekdaySentence
import com.kimaita.monies.ui.charts.ChartFont
import com.kimaita.monies.ui.theme.MoniesTheme
import com.kimaita.monies.ui.util.formatAmount
import com.kimaita.monies.ui.util.formatKes
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayLong = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
private val clock = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
private val monthName = DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH)
private val monthYearName = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
private val weekHeads = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val CellGap = 4.dp

@Composable
internal fun ActivityTab(vm: AnalyticsViewModel, listState: LazyListState, onSubject: (Long) -> Unit) {
    val calendar by vm.calendar.collectAsStateWithLifecycle()
    val streak by vm.currentStreak.collectAsStateWithLifecycle()
    val selected by vm.selectedDay.collectAsStateWithLifecycle()
    val payments by vm.dayPayments.collectAsStateWithLifecycle()
    val canGoBack by vm.canGoBack.collectAsStateWithLifecycle()
    val weekdays by vm.weekdays.collectAsStateWithLifecycle()
    val cells by vm.activityRaw.collectAsStateWithLifecycle()
    val leaders by vm.leaderboard.collectAsStateWithLifecycle()
    val nature by vm.nature.collectAsStateWithLifecycle()
    val recurring by vm.recurring.collectAsStateWithLifecycle()
    val monthlyOut by vm.monthlyOut.collectAsStateWithLifecycle()
    val time by vm.timeFilter.collectAsStateWithLifecycle()
    val historyStart by vm.historyStart.collectAsStateWithLifecycle()
    val period = periodLabel(time, historyStart)

    TabList(listState) {
        item(key = "calendar") {
            CalendarCard(
                calendar, streak, selected, payments, recurring.orEmpty(), canGoBack,
                onShift = vm::shiftMonth, onSelect = vm::selectDay,
            )
        }
        item(key = "days") { DaysCard(weekdays, period) }
        item(key = "time") { TimeOfDayCard(cells, period) }
        item(key = "regulars") { RegularsCard(leaders, nature, period, onSubject) }
        item(key = "recurring") { RecurringCard(recurring, monthlyOut) }
    }
}

@Composable
private fun CalendarCard(
    c: MonthCalendar?,
    streak: Streak?,
    selected: LocalDate?,
    payments: List<DayPayment>?,
    recurring: List<RecurringPayment>,
    canGoBack: Boolean,
    onShift: (Long) -> Unit,
    onSelect: (LocalDate) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    BentoCard {
        if (c == null) {
            EmptyHint(LOADING)
            return@BentoCard
        }
        Row(Modifier.fillMaxWidth().offset(y = (-6).dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ onShift(-1) }, enabled = canGoBack) { Text("‹", fontSize = 22.sp) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                val sameYear = c.month.year == LocalDate.now().year
                Text(
                    c.month.atDay(1).format(if (sameYear) monthName else monthYearName),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                )
                Note("${formatKes(c.spent)} spent")
            }
            IconButton({ onShift(1) }, enabled = !c.current) { Text("›", fontSize = 22.sp) }
        }
        if (c.current) {
            streak?.let { Say(streakSentence(it)) }
            Spacer(Modifier.height(4.dp))
            Note(noSpendSentence(c.noSpendDays))
        } else {
            Say(pastMonthSentence(c))
        }
        Spacer(Modifier.height(12.dp))
        MonthGrid(c, selected, onSelect)
        Spacer(Modifier.height(12.dp))
        if (c.current) {
            ShadeLegend()
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                KeyItem("Streak") { Box(Modifier.size(16.dp, 3.dp).background(cs.onSurface, RoundedCornerShape(1.5.dp))) }
                KeyItem("Payment due") { Box(Modifier.size(9.dp).border(2.dp, cs.primary, CircleShape)) }
                KeyItem("Today") { Box(Modifier.size(6.dp).background(cs.onSurface, CircleShape)) }
            }
        } else if (c.run != null) {
            KeyItem("Longest run") { Box(Modifier.size(16.dp, 3.dp).background(cs.onSurface, RoundedCornerShape(1.5.dp))) }
        }
        val day = selected?.takeIf { d -> c.weeks.any { w -> w.any { it.date == d && it.inMonth } } }
        if (day != null) {
            Spacer(Modifier.height(14.dp))
            DayBox(day, c, payments, recurring)
        }
    }
}

@Composable
private fun MonthGrid(c: MonthCalendar, selected: LocalDate?, onSelect: (LocalDate) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val today = LocalDate.now()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CellGap)) {
        weekHeads.forEach {
            Text(
                it, Modifier.weight(1f), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = cs.onSurfaceVariant,
            )
        }
    }
    Spacer(Modifier.height(6.dp))
    val run = c.run
    val ink = cs.onSurface
    Column(verticalArrangement = Arrangement.spacedBy(CellGap)) {
        c.weeks.forEach { week ->
            val first = run?.let { r -> week.indexOfFirst { it.date in r } }?.takeIf { it >= 0 }
            val last = run?.let { r -> week.indexOfLast { it.date in r } }?.takeIf { it >= 0 }
            Row(
                Modifier
                    .fillMaxWidth()
                    .drawWithContent {
                        drawContent()
                        if (first != null && last != null) {
                            val gap = CellGap.toPx()
                            val cell = (size.width - gap * 6) / 7
                            val inset = 4.dp.toPx()
                            val x0 = first * (cell + gap) + inset
                            val x1 = last * (cell + gap) + cell - inset
                            val h = 3.dp.toPx()
                            drawRoundRect(
                                ink, Offset(x0, size.height + 0.5.dp.toPx()), Size(x1 - x0, h),
                                CornerRadius(h / 2),
                            )
                        }
                    },
                horizontalArrangement = Arrangement.spacedBy(CellGap),
            ) {
                week.forEach { d ->
                    if (!d.inMonth && d.date.isAfter(c.month.atEndOfMonth())) {
                        Spacer(Modifier.weight(1f))
                        return@forEach
                    }
                    DayCell(
                        d, d.date == selected, d.date == today && c.current, d.date in c.dueDays,
                        Modifier.weight(1f), onSelect,
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    d: CalendarDay,
    selected: Boolean,
    today: Boolean,
    due: Boolean,
    modifier: Modifier,
    onSelect: (LocalDate) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)
    val track = cs.surfaceContainerHighest
    val fill = when {
        d.future -> Color.Transparent
        d.shade == 0f -> track
        else -> lerp(track, cs.primary, d.shade)
    }
    val dark = d.shade >= 0.7f
    val numberColor = when {
        d.future || !d.inMonth -> cs.onSurfaceVariant
        dark -> cs.onPrimary
        else -> cs.onSurface
    }
    val label = buildString {
        append(d.date.format(dayLong))
        if (!d.future) append(if (d.spent > 0) ", ${formatKes(d.spent)} spent" else ", nothing spent")
        if (due) append(", payment due")
        if (today) append(", today")
    }
    Box(
        modifier
            .height(40.dp)
            .alpha(if (d.inMonth) 1f else 0.45f)
            .clip(shape)
            .background(fill)
            .then(
                when {
                    selected -> Modifier.border(2.dp, cs.onSurface, shape)
                    d.future -> Modifier.border(1.dp, cs.outlineVariant, shape)
                    else -> Modifier
                }
            )
            .then(
                if (d.inMonth) Modifier.clickable(role = Role.Button, onClickLabel = "Show the day") { onSelect(d.date) }
                else Modifier
            )
            .semantics { contentDescription = label },
    ) {
        Text(
            d.date.dayOfMonth.toString(),
            Modifier.padding(start = 6.dp, top = 4.dp),
            fontFamily = ChartFont, fontSize = 11.sp, lineHeight = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = numberColor,
        )
        d.note?.let {
            Text(
                it, Modifier.align(Alignment.BottomEnd).padding(end = 4.dp, bottom = 3.dp),
                fontFamily = ChartFont, fontSize = 9.sp, lineHeight = 10.sp, maxLines = 1,
                overflow = TextOverflow.Clip, color = if (dark) cs.onPrimary else cs.onSurface,
            )
        }
        when {
            today -> Box(
                Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = 6.dp).size(6.dp)
                    .background(cs.onSurface, CircleShape)
            )
            due -> Box(
                Modifier.align(Alignment.BottomEnd).padding(end = 5.dp, bottom = 5.dp).size(9.dp)
                    .border(2.dp, cs.primary, CircleShape)
            )
        }
    }
}

@Composable
private fun ShadeLegend() {
    val cs = MaterialTheme.colorScheme
    val track = cs.surfaceContainerHighest
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Note("Less")
        listOf(0f, 0.35f, 0.65f, 1f).forEach { f ->
            Box(Modifier.size(14.dp, 10.dp).background(lerp(track, cs.primary, f), RoundedCornerShape(3.dp)))
        }
        Note("More")
    }
}

@Composable
private fun KeyItem(label: String, key: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        key()
        Note(label)
    }
}

/** The tapped day: its payments, why nothing was spent, or what is expected. */
@Composable
private fun DayBox(day: LocalDate, c: MonthCalendar, payments: List<DayPayment>?, recurring: List<RecurringPayment>) {
    val cs = MaterialTheme.colorScheme
    val today = LocalDate.now()
    Column(
        Modifier
            .fillMaxWidth()
            .background(cs.surface, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val title = day.format(dayLong)
        when {
            day.isAfter(today) -> {
                val due = remember(recurring, day) { dueDates(recurring, day, day) }
                BoxHeader(title) { Note("Expected") }
                if (due.isEmpty()) Note("Nothing regular is due.")
                due.forEach { p ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(p.payment.name, Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.width(8.dp))
                        CadencePill(p.payment.cadenceLabel)
                        Spacer(Modifier.weight(1f))
                        Text("~${formatAmount(p.payment.typicalAmount)}", fontWeight = FontWeight.Bold)
                    }
                    Note("Last paid ${dayMonthLabel(p.payment.lastDate.toLocalDate())}. May shift a day or two.")
                }
            }
            payments == null -> BoxHeader(title) {}
            payments.isEmpty() && day == today -> {
                BoxHeader(title) {}
                Note("Nothing spent yet today.")
            }
            payments.isEmpty() -> {
                BoxHeader(title) { Text("No spending", fontWeight = FontWeight.Bold, color = cs.onSurfaceVariant) }
                noSpendDayNote(day, c)?.let { Note(it) }
            }
            else -> {
                BoxHeader(title) { Text(formatKes(payments.sumOf { it.amount }), fontWeight = FontWeight.Bold) }
                val shown = payments.take(8)
                shown.forEach { p ->
                    Row(Modifier.fillMaxWidth()) {
                        Note(
                            "${p.name ?: "Unnamed"} · ${timeOf(p.time)}",
                            Modifier.weight(1f),
                        )
                        Note(formatAmount(p.amount))
                    }
                }
                if (payments.size > shown.size) Note("+${payments.size - shown.size} more")
            }
        }
    }
}

@Composable
private fun BoxHeader(title: String, end: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontWeight = FontWeight.Bold)
        end()
    }
}

private fun timeOf(iso: String): String =
    runCatching { LocalDateTime.parse(iso).format(clock).lowercase() }.getOrDefault("")

@Composable
private fun CadencePill(text: String) {
    val cs = MaterialTheme.colorScheme
    Text(
        text,
        Modifier
            .background(cs.secondaryContainer, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
        color = cs.onSecondaryContainer, maxLines = 1,
    )
}

@Composable
private fun NotEnough() {
    Say("Not enough payments to show a pattern.")
    Spacer(Modifier.height(4.dp))
    Note("Needs 5 or more. Try a longer period.")
}

@Composable
private fun DaysCard(w: AnalyticsViewModel.Weekdays?, period: String) {
    val cs = MaterialTheme.colorScheme
    BentoCard {
        CardHeader("Days · $period")
        Spacer(Modifier.height(6.dp))
        if (w == null) {
            EmptyHint(LOADING)
            return@BentoCard
        }
        // Monday first; WeekdayCount numbers Sunday 0.
        val counts = (1..7).map { i -> w.counts.firstOrNull { it.weekday == i % 7 }?.count ?: 0 }
        val total = counts.sum()
        val sentence = remember(w) { weekdaySentence(w.counts) }
        if (sentence == null || total < 5) {
            NotEnough()
            return@BentoCard
        }
        Say(sentence)
        Spacer(Modifier.height(4.dp))
        Note(paymentsPerDaySentence(total, w.days))
        Spacer(Modifier.height(14.dp))
        val max = counts.max().coerceAtLeast(1)
        val busiest = counts.indices.maxBy { counts[it] }
        Row(
            Modifier
                .fillMaxWidth()
                .height(112.dp)
                .semantics {
                    contentDescription = "Payments by weekday: " +
                            weekHeads.indices.joinToString { "${weekHeads[it]} ${counts[it]}" }
                },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            counts.forEachIndexed { i, n ->
                val on = i == busiest
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Bottom),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.6f)
                            .height((80f * n / max).coerceAtLeast(4f).dp)
                            .background(if (on) cs.primary else MoniesTheme.extra.track2, RoundedCornerShape(6.dp))
                    )
                    Text(
                        weekHeads[i], style = MaterialTheme.typography.labelMedium,
                        color = if (on) cs.onSurface else cs.onSurfaceVariant,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

private val slotWeights = listOf(7f, 5f, 5f, 7f)

@Composable
private fun TimeOfDayCard(cells: List<ActivityCell>?, period: String) {
    val cs = MaterialTheme.colorScheme
    BentoCard {
        CardHeader("Time of day · $period")
        Spacer(Modifier.height(6.dp))
        if (cells == null) {
            EmptyHint(LOADING)
            return@BentoCard
        }
        val counts = remember(cells) { (0..3).map { s -> cells.filter { it.slot == s }.sumOf { it.count } } }
        val sentence = timeOfDaySentence(counts)
        if (sentence == null) {
            NotEnough()
            return@BentoCard
        }
        Say(sentence)
        Spacer(Modifier.height(14.dp))
        val max = counts.max().coerceAtLeast(1)
        val busiest = counts.indices.maxBy { counts[it] }
        val track = cs.surfaceContainerHighest
        Row(
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .semantics {
                    contentDescription = "Payments across the day: morning ${counts[0]}, midday ${counts[1]}, " +
                            "evening ${counts[2]}, night ${counts[3]}"
                },
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            counts.forEachIndexed { i, n ->
                val on = i == busiest
                val shape = when (i) {
                    0 -> RoundedCornerShape(8.dp, 4.dp, 4.dp, 8.dp)
                    3 -> RoundedCornerShape(4.dp, 8.dp, 8.dp, 4.dp)
                    else -> RoundedCornerShape(4.dp)
                }
                Box(
                    Modifier
                        .weight(slotWeights[i])
                        .fillMaxHeight()
                        .background(if (on) cs.primary else lerp(track, cs.primary, 0.18f + 0.52f * n / max), shape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        n.toString(), fontFamily = ChartFont, fontSize = 12.sp,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                        color = if (on) cs.onPrimary else cs.onSurface,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        val axis = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
        Row(Modifier.fillMaxWidth()) {
            Text("5 am", Modifier.weight(7f), style = axis, color = cs.onSurfaceVariant)
            Text("12 pm", Modifier.weight(5f), style = axis, color = cs.onSurfaceVariant)
            Text("5 pm", Modifier.weight(5f), style = axis, color = cs.onSurfaceVariant)
            Row(Modifier.weight(7f), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("10 pm", style = axis, color = cs.onSurfaceVariant)
                Text("5 am", style = axis, color = cs.onSurfaceVariant)
            }
        }
    }
}

/** "You paid **Baraka Mart** most: 23 times.", worded for money in, out or both. */
private fun regularsSentence(top: SubjectCount, nature: Boolean?): String {
    val times = if (top.count == 1) "once" else "${top.count} times"
    return when (nature) {
        true -> "**${top.name}** paid you most: $times."
        false -> "You paid **${top.name}** most: $times."
        null -> "**${top.name}** comes up most: $times."
    }
}

@Composable
private fun RegularsCard(leaders: List<SubjectCount>?, nature: Boolean?, period: String, onSubject: (Long) -> Unit) {
    BentoCard {
        CardHeader("Your regulars · $period")
        Spacer(Modifier.height(6.dp))
        when {
            leaders == null -> EmptyHint(LOADING)
            leaders.isEmpty() -> Say("No payments in this period.")
            else -> {
                var expanded by rememberSaveable { mutableStateOf(false) }
                Say(regularsSentence(leaders[0], nature))
                Spacer(Modifier.height(14.dp))
                val rows = if (expanded || leaders.size <= 5) leaders.chunked(5) else listOf(leaders.take(4))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    rows.forEachIndexed { r, row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            row.forEachIndexed { i, s -> Avatar(s, r == 0 && i == 0) { onSubject(s.subjectId) } }
                            if (!expanded && leaders.size > 5 && r == 0) {
                                MoreAvatar(leaders.size - 4) { expanded = true }
                            }
                            repeat(5 - row.size - (if (!expanded && leaders.size > 5 && r == 0) 1 else 0)) {
                                Spacer(Modifier.width(60.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Note("Tap a name for their profile.")
            }
        }
    }
}

@Composable
private fun Avatar(s: SubjectCount, top: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .width(60.dp)
            .clickable(role = Role.Button, onClickLabel = "Open ${s.name}", onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier
                .size(48.dp)
                .background(if (top) cs.primary else cs.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(initials(s.name), fontWeight = FontWeight.Bold, color = if (top) cs.onPrimary else cs.onSecondaryContainer)
        }
        Text("${s.count}×", fontFamily = ChartFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(
            s.name, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MoreAvatar(more: Int, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .width(60.dp)
            .clickable(role = Role.Button, onClickLabel = "See all regulars", onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier.size(48.dp).background(cs.surfaceContainerHighest, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text("+$more", fontWeight = FontWeight.Bold, color = cs.onSurfaceVariant) }
        Text("See all", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun RecurringCard(recurring: List<RecurringPayment>?, monthlyOut: Double?) {
    val cs = MaterialTheme.colorScheme
    BentoCard {
        Text("Recurring payments", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        when {
            recurring == null -> {
                Spacer(Modifier.height(8.dp))
                EmptyHint("Checking…")
            }
            recurring.isEmpty() -> {
                Spacer(Modifier.height(8.dp))
                Say("Nothing regular yet.")
                Spacer(Modifier.height(4.dp))
                Note("Shows up after payments on 4+ days in 4 months to one name, like rent.")
            }
            else -> {
                Spacer(Modifier.height(4.dp))
                Note("Paid on 4+ days in the last 4 months. Next date is the last one plus the usual gap.")
                Spacer(Modifier.height(12.dp))
                Say(
                    recurringSummary(recurring, monthlyOut, ::formatAmount),
                    Modifier
                        .fillMaxWidth()
                        .background(cs.surface, RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(6.dp))
                recurring.forEachIndexed { i, r ->
                    if (i > 0) HorizontalDivider(color = cs.outlineVariant)
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(r.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Note("Next ~${dayMonthLabel(r.nextEstimate.toLocalDate())} · paid ${r.occurrences}×")
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(formatAmount(r.typicalAmount), fontWeight = FontWeight.Bold)
                            CadencePill(r.cadenceLabel)
                        }
                    }
                }
            }
        }
    }
}
