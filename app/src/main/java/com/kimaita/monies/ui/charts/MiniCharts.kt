package com.kimaita.monies.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kimaita.monies.models.analytics.BalancePoint
import com.kimaita.monies.models.analytics.CashFlowWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

/** Non-interactive diverging in/out bars for the Overview bento. */
@Composable
fun MiniInOutBars(data: List<CashFlowWeek>, modifier: Modifier = Modifier, buckets: Int = 8) {
    val colors = chartColors()
    val slice = remember(data, buckets) { data.takeLast(buckets) }
    Spacer(modifier.drawWithCache {
        if (slice.isEmpty()) return@drawWithCache onDrawBehind { }
        val maxUp = slice.maxOf { it.inflow }
        val maxDown = slice.maxOf { it.outflow }
        val total = (maxUp + maxDown).coerceAtLeast(1f)
        val y0 = size.height * maxUp / total
        val slot = size.width / slice.size
        val bw = (slot * 0.6f).coerceIn(4.dp.toPx(), 12.dp.toPx())
        val minH = 2.dp.toPx()
        val radius = bw * 0.35f
        val upPath = Path()
        val downPath = Path()
        slice.forEachIndexed { i, w ->
            val l = i * slot + (slot - bw) / 2f
            if (w.inflow > 0f) {
                val tip = minOf(y0 - size.height * w.inflow / total, y0 - minH)
                upPath.addBar(l, l + bw, y0, tip, minOf(radius, y0 - tip))
            }
            if (w.outflow > 0f) {
                val tip = maxOf(y0 + size.height * w.outflow / total, y0 + minH)
                downPath.addBar(l, l + bw, y0, tip, minOf(radius, tip - y0))
            }
        }
        onDrawBehind {
            drawPath(upPath, colors.inflow)
            drawPath(downPath, colors.outflow)
        }
    })
}

/** Non-interactive balance sparkline for the Overview bento. */
@Composable
fun MiniSparkline(points: List<BalancePoint>, modifier: Modifier = Modifier) {
    val color = chartColors().inflow
    Spacer(modifier.drawWithCache {
        if (points.size < 2) return@drawWithCache onDrawBehind { }
        val strokeW = 2.dp.toPx()
        val scale = lineScale(points.minOf { it.balance }, points.maxOf { it.balance })
        val top = strokeW
        val bottom = size.height - strokeW
        val stepX = size.width / (points.size - 1)
        val path = Path().apply {
            monotoneCurve(
                FloatArray(points.size) { it * stepX },
                FloatArray(points.size) { scale.y(points[it].balance, top, bottom) },
            )
        }
        val stroke = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
        onDrawBehind { drawPath(path, color, style = stroke) }
    })
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