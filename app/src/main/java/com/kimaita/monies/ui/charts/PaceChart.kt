package com.kimaita.monies.ui.charts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kimaita.monies.services.PaceSummary
import com.kimaita.monies.ui.theme.MoniesTheme
import com.kimaita.monies.ui.util.formatKes
import java.util.Locale

/** Cumulative spending by day of month: this month against last month. */
@Composable
fun PaceChart(pace: PaceSummary, modifier: Modifier = Modifier, plotHeight: Dp = 140.dp) {
    val cs = MaterialTheme.colorScheme
    val extra = MoniesTheme.extra
    val thisColor = cs.primary
    val lastColor = cs.onSurfaceVariant

    val series = remember(pace, thisColor, lastColor) {
        listOfNotNull(
            if (pace.thisMonth.isEmpty()) null else LineSeries(
                FloatArray(pace.thisMonth.size) { it + 1f },
                FloatArray(pace.thisMonth.size) { pace.thisMonth[it].toFloat() },
                thisColor, 3.dp,
            ),
            if (pace.lastMonth.isEmpty()) null else LineSeries(
                FloatArray(pace.lastMonth.size) { it + 1f },
                FloatArray(pace.lastMonth.size) { pace.lastMonth[it].toFloat() },
                lastColor, 2.dp, dashed = true,
            ),
        )
    }
    val lastDay = maxOf(pace.thisMonth.size, pace.lastMonth.size)
    val xLabels = remember(lastDay) {
        (listOf(1) + (5..lastDay step 5) + lastDay.coerceAtLeast(1)).distinct()
            .map { it.toFloat() to it.toString() }
    }

    val change = pace.changePct
    val header = formatKes(pace.spent) + " spent so far" +
            (change?.let { " · ${"%+.0f".format(Locale.US, it)}% vs this point last month" } ?: "")
    Column(modifier) {
        Text(
            header,
            style = MaterialTheme.typography.bodyMedium,
            color = when {
                change == null -> cs.onSurfaceVariant
                change > 0 -> extra.negative
                else -> extra.positive
            },
        )
        Spacer(Modifier.height(12.dp))
        LineChartCanvas(
            series, 1f, lastDay.toFloat(), xLabels,
            plotHeight = plotHeight, zeroBased = true,
        )
    }
}
