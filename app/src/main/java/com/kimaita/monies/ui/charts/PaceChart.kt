package com.kimaita.monies.ui.charts

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kimaita.monies.services.PaceSummary
import com.kimaita.monies.ui.util.formatShortAmount

/**
 * Spending added up over a period, one point per step counted from 1: the current period in the
 * primary colour up to today, the period before dashed, and a dotted line from today to [projected]
 * at the last of [steps]. Today's figure is labelled when there is no projection. [xLabels] are
 * (step, text); today's is bold. [card] fills the projection's end ring; [labelColor] is for its label.
 */
@Composable
fun PaceChart(
    pace: PaceSummary,
    steps: Int,
    projected: Double?,
    xLabels: List<Pair<Int, String>>,
    modifier: Modifier = Modifier,
    plotHeight: Dp = 132.dp,
    card: Color = MaterialTheme.colorScheme.primaryContainer,
    labelColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    gridColor: Color? = null,
) {
    val cs = MaterialTheme.colorScheme
    val thisColor = cs.primary
    val lastColor = cs.onSurfaceVariant
    val now = pace.current.size

    val series = remember(pace, projected, steps, thisColor, lastColor) {
        listOfNotNull(
            if (pace.current.isEmpty()) null else LineSeries(
                FloatArray(now) { it + 1f },
                FloatArray(now) { pace.current[it].toFloat() },
                thisColor, 3.dp,
            ),
            if (pace.previous.isEmpty()) null else LineSeries(
                FloatArray(pace.previous.size) { it + 1f },
                FloatArray(pace.previous.size) { pace.previous[it].toFloat() },
                lastColor, 2.dp, dash = 5.dp to 4.dp,
            ),
            if (projected == null || now == 0 || now >= steps) null else LineSeries(
                floatArrayOf(now.toFloat(), steps.toFloat()),
                floatArrayOf(pace.spent.toFloat(), projected.toFloat()),
                thisColor, 2.dp, dash = 1.dp to 6.dp,
            ),
        )
    }
    val markers = remember(pace, projected, steps, thisColor, card, labelColor) {
        if (now == 0) emptyList()
        else listOfNotNull(
            ChartMarker(
                now.toFloat(), pace.spent.toFloat(), thisColor,
                label = if (projected == null) formatShortAmount(pace.spent) else null,
                place = LabelPlace.BELOW_RIGHT,
            ),
            projected?.takeIf { now < steps }?.let {
                ChartMarker(
                    steps.toFloat(), it.toFloat(), thisColor, MarkerStyle.RING, card,
                    label = "~" + formatShortAmount(it), place = LabelPlace.BELOW_LEFT, labelColor = labelColor,
                )
            },
        )
    }
    val labels = remember(xLabels) { xLabels.map { it.first.toFloat() to it.second } }
    LineChartCanvas(
        series, 1f, maxOf(steps, pace.previous.size, 2).toFloat(), labels, modifier,
        plotHeight = plotHeight, zeroBased = true, markers = markers,
        strongX = now.toFloat(), gridColor = gridColor,
    )
}
