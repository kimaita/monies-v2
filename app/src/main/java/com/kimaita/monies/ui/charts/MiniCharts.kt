package com.kimaita.monies.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kimaita.monies.models.analytics.BalancePoint
import com.kimaita.monies.models.analytics.CashFlowWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

/** Non-interactive in/out bars for the Overview bento */
@Composable
fun MiniInOutBars(data: List<CashFlowWeek>, modifier: Modifier = Modifier, buckets: Int = 8) {
    val colors = chartColors()
    val slice = remember(data, buckets) { data.takeLast(buckets) }
    val max = remember(slice) {
        slice.maxOfOrNull { maxOf(it.inflow, it.outflow) }?.coerceAtLeast(1f) ?: 1f
    }
    Canvas(modifier) {
        if (slice.isEmpty()) return@Canvas
        val groupW = size.width / slice.size
        val barW = (groupW * 0.34f).coerceAtMost(10f)
        val r = CornerRadius(barW / 3f)
        slice.forEachIndexed { i, w ->
            val cx = i * groupW + groupW / 2f
            val hIn = size.height * (w.inflow / max)
            val hOut = size.height * (w.outflow / max)
            drawRoundRect(
                colors.inflow,
                Offset(cx - barW - 1f, size.height - hIn),
                Size(barW, hIn),
                r
            )
            drawRoundRect(colors.outflow, Offset(cx + 1f, size.height - hOut), Size(barW, hOut), r)
        }
    }
}

/** Non-interactive balance sparkline for the Overview bento. */
@Composable
fun MiniSparkline(points: List<BalancePoint>, modifier: Modifier = Modifier) {
    val colors = chartColors()
    Canvas(modifier) {
        if (points.size < 2) return@Canvas
        val min = points.minOf { it.balance }
        val max = points.maxOf { it.balance }
        val span = (max - min).takeIf { it > 0f } ?: 1f
        val stepX = size.width / (points.size - 1)
        val path = Path()
        points.forEachIndexed { i, p ->
            val x = i * stepX
            val y = size.height - (p.balance - min) / span * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path,
            colors.inflow,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f)
        )
    }
}

/** Trailing-window contribution grid. One Canvas instead of ~91 Boxes. */
@Composable
fun ContributionGrid(
    intensities: Map<LocalDate, Double>,
    start: LocalDate,
    end: LocalDate,
    modifier: Modifier = Modifier,
    cell: Dp = 13.dp,
    gap: Dp = 3.dp,
) {
    val colors = chartColors()
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant
    val levels = remember(intensities, start, end) {
        val max = intensities.values.maxOrNull() ?: 0.0
        val days = (ChronoUnit.DAYS.between(start, end) + 1).toInt().coerceAtLeast(0)
        FloatArray(days) { i ->
            val v = intensities[start.plusDays(i.toLong())] ?: 0.0
            if (v <= 0.0 || max <= 0.0) 0f
            else (0.25f + 0.75f * (v / max)).toFloat().coerceIn(0.25f, 1f)
        }
    }
    val density = LocalDensity.current
    val cellPx = with(density) { cell.toPx() }
    val gapPx = with(density) { gap.toPx() }
    val radius = with(density) { CornerRadius(3.dp.toPx()) }
    val weeks = ceil(levels.size / 7f).toInt().coerceAtLeast(1)

    Canvas(modifier
        .fillMaxWidth()
        .height(cell * 7 + gap * 6)) {
        val step = cellPx + gapPx
        val fitted =
            if (weeks * step > size.width) (size.width - gapPx * (weeks - 1)) / weeks else cellPx
        val stepF = fitted + gapPx
        for (i in levels.indices) {
            val a = levels[i]
            drawRoundRect(
                color = if (a == 0f) emptyColor else colors.inflow.copy(alpha = a),
                topLeft = Offset((i / 7) * stepF, (i % 7) * stepF),
                size = Size(fitted, fitted),
                cornerRadius = radius,
            )
        }
    }
}