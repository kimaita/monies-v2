package com.kimaita.monies.ui.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kimaita.monies.models.analytics.BalancePoint
import kotlin.math.roundToInt

@Composable
fun BalanceTrendChart(
    data: List<BalancePoint>,
    modifier: Modifier = Modifier,
    plotHeight: Dp = 180.dp
) {
    if (data.size < 2) return
    val maxV = remember(data) { data.maxOf { it.balance } }
    val (axisMax, step) = remember(maxV) { niceAxis(maxV) }
    val labels = remember(axisMax, step) { axisLabels(axisMax, step) }
    val measurer = rememberTextMeasurer()
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val line = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surfaceContainerHighest
    val gridColor = onSurfaceVariant.copy(alpha = 0.15f)

    var scrub by remember(data) { mutableStateOf<Int?>(null) }
    val grow = remember(data) { Animatable(0f) }
    LaunchedEffect(data) { grow.snapTo(0f); grow.animateTo(1f, tween(800)) }

    Row(modifier) {
        Column(
            Modifier.height(plotHeight),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End
        ) {
            labels.forEach {
                Text(
                    kesCompact(it),
                    style = MaterialTheme.typography.labelSmall,
                    color = onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Canvas(
            Modifier
                .weight(1f)
                .height(plotHeight + 22.dp)
                .pointerInput(data) {
                    val dx = size.width / (data.size - 1)
                    fun idx(x: Float) = (x / dx).roundToInt().coerceIn(0, data.lastIndex)
                    detectDragGestures(
                        onDragStart = { scrub = idx(it.x) },
                        onDrag = { change, _ -> scrub = idx(change.position.x); change.consume() },
                        onDragEnd = { scrub = null },
                        onDragCancel = { scrub = null },
                    )
                }
        ) {
            val plotH = plotHeight.toPx()
            val dx = size.width / (data.size - 1)
            fun py(v: Float) = plotH * (1f - v / axisMax)

            labels.forEach { v ->
                val y = py(v); drawLine(
                gridColor,
                Offset(0f, y),
                Offset(size.width, y),
                1f
            )
            }

            val linePath = Path().apply {
                data.forEachIndexed { i, p ->
                    val x = i * dx;
                    val y = py(p.balance); if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
            }
            val areaPath = Path().apply {
                addPath(linePath); lineTo((data.size - 1) * dx, plotH); lineTo(0f, plotH); close()
            }
            clipRect(right = size.width * grow.value) {
                drawPath(
                    areaPath,
                    Brush.verticalGradient(listOf(line.copy(alpha = 0.25f), line.copy(alpha = 0f)))
                )
                drawPath(linePath, line, style = Stroke(width = 2.5.dp.toPx()))
            }

            scrub?.let { i ->
                val x = i * dx;
                val y = py(data[i].balance)
                drawLine(
                    onSurfaceVariant.copy(alpha = 0.4f),
                    Offset(x, 0f),
                    Offset(x, plotH),
                    1.dp.toPx()
                )
                drawCircle(line, 5.dp.toPx(), Offset(x, y))
                drawCircle(surface, 2.5.dp.toPx(), Offset(x, y))
                val text = measurer.measure(
                    "${data[i].balance}: ${kesCompact(data[i].balance)}",
                    TextStyle(fontSize = 11.sp, color = onSurface)
                )
                val pad = 8.dp.toPx();
                val w = text.size.width + pad * 2;
                val h = text.size.height + pad
                val cx = (x - w / 2).coerceIn(0f, size.width - w)
                drawRoundRect(
                    surface, Offset(cx, 0f), androidx.compose.ui.geometry.Size(w, h),
                    androidx.compose.ui.geometry.CornerRadius(8.dp.toPx(), 8.dp.toPx())
                )
                drawText(text, topLeft = Offset(cx + pad, pad / 2))
            }
        }
    }
}