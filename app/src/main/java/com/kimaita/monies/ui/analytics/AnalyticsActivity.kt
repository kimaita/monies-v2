package com.kimaita.monies.ui.analytics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kimaita.monies.services.StreakSummary
import com.kimaita.monies.ui.charts.*
import java.time.LocalDate

@Composable
internal fun ActivityTab(vm: AnalyticsViewModel, listState: LazyListState) {
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
                if (streak == null) EmptyHint(LOADING) else StreakSection(streak.summary, streak.window)
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
