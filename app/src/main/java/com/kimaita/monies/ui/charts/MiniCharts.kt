package com.kimaita.monies.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

/**
 * [count] dots, twenty to a row, all of [color] except the last in [accent]: one in every [count]
 * shillings. Ten dp between dot centres.
 */
@Composable
fun DotGrid(count: Int, color: Color, accent: Color, modifier: Modifier = Modifier) {
    val rows = ceil(count / 20f).toInt().coerceAtLeast(1)
    Spacer(
        modifier
            .width(200.dp)
            .height((rows * 10 - 2).dp)
            .drawWithCache {
                val pitch = 10.dp.toPx()
                val r = 3.dp.toPx()
                val c = 4.dp.toPx()
                val centres = List(count) { Offset(c + (it % 20) * pitch, c + (it / 20) * pitch) }
                onDrawBehind {
                    for (i in 0 until count - 1) drawCircle(color, r, centres[i])
                    centres.lastOrNull()?.let { drawCircle(accent, 3.5.dp.toPx(), it) }
                }
            }
    )
}

/** Trailing-window contribution grid. One Canvas instead of ~91 Boxes. */
@Composable
fun ContributionGrid(
    intensities: Map<LocalDate, Double>,
    start: LocalDate,
    end: LocalDate,
) {
    val cell = 13.dp
    val gap = 3.dp
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

    Canvas(Modifier
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