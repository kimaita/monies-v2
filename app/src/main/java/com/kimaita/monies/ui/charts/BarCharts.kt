package com.kimaita.monies.ui.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kimaita.monies.models.analytics.CashFlowWeek
import com.kimaita.monies.models.analytics.CostMonth

@Composable
fun CashFlowChart(data: List<CashFlowWeek>, modifier: Modifier = Modifier, plotHeight: Dp = 180.dp) {
    val colors = chartColors()
    val groups = remember(data, colors) {
        data.map {
            BarGroup(it.label, listOf(
                BarSegment(it.inflow, colors.inflow),
                BarSegment(it.outflow, colors.outflow),
            ))
        }
    }
    ScrollableBarChart(groups, stacked = false, modifier=modifier, plotHeight = plotHeight, colors = colors)
}

@Composable
fun CostAnalyzerChart(data: List<CostMonth>, modifier: Modifier = Modifier) {
    val colors = chartColors()
    val groups = remember(data, colors) {
        data.map {
            BarGroup(it.month, listOf(
                BarSegment(it.principal, colors.outflow),
                BarSegment(it.fee, colors.inflow),
            ))
        }
    }
    ScrollableBarChart(groups, stacked = true, modifier = modifier, colors = colors)
}

@Composable
fun ScrollableBarChart(
    groups: List<BarGroup>,
    stacked: Boolean,
    modifier: Modifier = Modifier,
    barWidth: Dp = 16.dp,
    barGap: Dp = 6.dp,
    groupGap: Dp = 26.dp,
    plotHeight: Dp = 180.dp,
    tooltip: (BarGroup) -> String = { it.label },
    colors: ChartColors = chartColors(),
) {
    if (groups.isEmpty()) return
    val maxValue = remember(groups, stacked) {
        groups.maxOf { g ->
            if (stacked) g.segments.sumOf { it.value.toDouble() }
                .toFloat() else (g.segments.maxOfOrNull { it.value } ?: 0f)
        }
    }
    val (axisMax, step) = remember(maxValue) { niceAxis(maxValue) }
    val labels = remember(axisMax, step) { axisLabels(axisMax, step) }
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val gridColor = onSurfaceVariant.copy(alpha = 0.15f)
    val surface = MaterialTheme.colorScheme.surfaceContainerHighest

    var selected by remember(groups) { mutableIntStateOf(-1) }
    val grow = remember(groups) { Animatable(0f) }
    LaunchedEffect(groups) {
        grow.snapTo(0f); grow.animateTo(
        1f,
        tween(700, easing = FastOutSlowInEasing)
    )
    }

    val segCount = if (stacked) 1 else (groups.firstOrNull()?.segments?.size ?: 1)
    val groupWidth = if (stacked) barWidth else barWidth * segCount + barGap * (segCount - 1)
    val contentWidth = (groupWidth + groupGap) * groups.size + groupGap
    val scroll = rememberScrollState()
    LaunchedEffect(contentWidth) { scroll.scrollTo(scroll.maxValue) }   // start at most recent

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
        Box(
            Modifier
                .weight(1f)
                .horizontalScroll(scroll)
        ) {
            val labelStyle = TextStyle(fontSize = 10.sp, color = onSurfaceVariant)
            Canvas(
                Modifier
                    .width(contentWidth)
                    .height(plotHeight + 22.dp)
                    .pointerInput(groups) {
                        detectTapGestures { off ->
                            val gw = with(density) { (groupWidth + groupGap).toPx() }
                            val start = with(density) { groupGap.toPx() }
                            val idx = ((off.x - start) / gw).toInt()
                            selected =
                                if (idx in groups.indices && idx == selected) -1 else idx.coerceIn(
                                    0,
                                    groups.lastIndex
                                )
                        }
                    }
            ) {
                val plotH = plotHeight.toPx()
                val gw = (groupWidth + groupGap).toPx()
                val bw = barWidth.toPx()
                val bg = barGap.toPx()
                val gwOnly = groupWidth.toPx()
                val start = groupGap.toPx()
                val radius = CornerRadius(bw * 0.35f, bw * 0.35f)

                labels.forEach { v ->
                    val y = plotH * (1f - v / axisMax)
                    drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1f)
                }

                groups.forEachIndexed { gi, group ->
                    val gx = start + gi * gw
                    val dim = selected != -1 && selected != gi
                    if (stacked) {
                        var yBottom = plotH
                        group.segments.forEach { seg ->
                            val h = plotH * (seg.value / axisMax) * grow.value
                            drawRoundRect(
                                seg.color.copy(alpha = if (dim) 0.3f else 1f),
                                Offset(gx, yBottom - h), Size(bw, h), radius
                            )
                            yBottom -= h
                        }
                    } else {
                        group.segments.forEachIndexed { si, seg ->
                            val x = gx + si * (bw + bg)
                            val h = plotH * (seg.value / axisMax) * grow.value
                            drawRoundRect(
                                seg.color.copy(alpha = if (dim) 0.3f else 1f),
                                Offset(x, plotH - h), Size(bw, h), radius
                            )
                        }
                    }
                    val lbl = measurer.measure(group.label, labelStyle)
                    drawText(
                        lbl,
                        topLeft = Offset(gx + gwOnly / 2 - lbl.size.width / 2, plotH + 4.dp.toPx())
                    )
                }

                if (selected in groups.indices) {
                    val gx = start + selected * gw
                    val text = measurer.measure(
                        tooltip(groups[selected]),
                        TextStyle(fontSize = 11.sp, color = onSurface)
                    )
                    val pad = 8.dp.toPx()
                    val w = text.size.width + pad * 2
                    val h = text.size.height + pad
                    val cx = (gx + gwOnly / 2 - w / 2).coerceIn(0f, size.width - w)
                    drawRoundRect(
                        surface,
                        Offset(cx, 0f),
                        Size(w, h),
                        CornerRadius(8.dp.toPx(), 8.dp.toPx())
                    )
                    drawText(text, topLeft = Offset(cx + pad, pad / 2))
                }
            }
        }
    }
}