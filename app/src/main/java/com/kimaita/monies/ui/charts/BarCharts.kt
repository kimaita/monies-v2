package com.kimaita.monies.ui.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kimaita.monies.ui.theme.MoniesTheme
import com.kimaita.monies.ui.util.formatCompactAmount
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

/** Rounds only the end of the bar away from [base]; [tip] is where the bar ends. */
internal fun Path.addBar(left: Float, right: Float, base: Float, tip: Float, radius: Float) {
    val r = CornerRadius(radius)
    val z = CornerRadius.Zero
    if (tip <= base) addRoundRect(RoundRect(left, tip, right, base, r, r, z, z))
    else addRoundRect(RoundRect(left, base, right, tip, z, z, r, r))
}

/**
 * One bar per bucket: [BarBucket.up] rises from the baseline, [BarBucket.down] hangs below it.
 * Bars are 12-28 dp wide and fill the width; the chart scrolls only when they would be narrower.
 * A value label shows only where it fits its slot; tapping a bar still shows its tooltip.
 */
@Composable
fun BarChart(
    buckets: List<BarBucket>,
    modifier: Modifier = Modifier,
    upColor: Color = MoniesTheme.extra.inflow,
    downColor: Color = MoniesTheme.extra.outflow,
    plotHeight: Dp = 180.dp,
    showNet: Boolean = false,
    tooltip: (BarBucket) -> String = { it.label },
    picked: Int? = null,
    onPick: ((Int) -> Unit)? = null,
    valueLabels: ((BarBucket) -> String)? = null,
    notes: Map<Int, Pair<String?, String?>> = emptyMap(),
) {
    if (buckets.isEmpty()) return
    val measurer = rememberTextMeasurer()
    val cs = MaterialTheme.colorScheme
    val chart = chartColors()
    val extra = MoniesTheme.extra
    val scale = remember(buckets) { barScale(buckets.maxOf { it.up }, buckets.maxOf { it.down }) }

    var tapped by remember(buckets) { mutableIntStateOf(-1) }
    val selected = if (onPick != null) picked ?: -1 else tapped
    val grow = remember(buckets) { Animatable(0f) }
    LaunchedEffect(buckets) {
        grow.snapTo(0f); grow.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
    }
    val scroll = rememberScrollState()
    val bottomPad = if (buckets.any { it.year != null }) PlotBottomPad + YearRowHeight else PlotBottomPad
    val height = plotHeight + PlotTopPad + bottomPad

    BoxWithConstraints(modifier) {
        val n = buckets.size
        val slotW = maxOf((maxWidth - YAxisWidth) / n, 24.dp)
        val barW = (slotW / 2).coerceIn(12.dp, 28.dp)
        val contentWidth = slotW * n
        LaunchedEffect(contentWidth) { scroll.scrollTo(scroll.maxValue) }

        Row {
            Spacer(
                Modifier
                    .width(YAxisWidth)
                    .height(height)
                    .drawWithCache {
                        val style = TextStyle(fontSize = 10.sp, color = chart.axisLabel)
                        val top = PlotTopPad.toPx()
                        val bottom = size.height - bottomPad.toPx()
                        val labels = Array(scale.values.size) {
                            measurer.measure(
                                formatCompactAmount(abs(scale.values[it]).toDouble()), style
                            )
                        }
                        val pos = Array(labels.size) {
                            Offset(
                                size.width - 6.dp.toPx() - labels[it].size.width,
                                (scale.y(scale.values[it], top, bottom) - labels[it].size.height / 2f)
                                    .coerceIn(0f, size.height - labels[it].size.height)
                            )
                        }
                        onDrawBehind { labels.forEachIndexed { i, l -> drawText(l, topLeft = pos[i]) } }
                    }
            )
            Box(
                Modifier
                    .weight(1f)
                    .horizontalScroll(scroll)
            ) {
                Spacer(
                    Modifier
                        .width(contentWidth)
                        .height(height)
                        .pointerInput(buckets, slotW, barW, selected) {
                            val slot = slotW.toPx()
                            val reach = max(barW.toPx(), 24.dp.toPx()) / 2f
                            detectTapGestures { off ->
                                val idx = (off.x / slot).toInt()
                                val onBar = idx in buckets.indices &&
                                        abs(off.x - (idx + 0.5f) * slot) <= reach
                                val next = if (onBar && idx != selected) idx else -1
                                if (onPick != null) onPick(next) else tapped = next
                            }
                        }
                        .drawWithCache {
                            val labelStyle = TextStyle(fontSize = 10.sp, color = chart.axisLabel)
                            val tipStyle = TextStyle(fontSize = 11.sp, color = chart.tooltipText)
                            val top = PlotTopPad.toPx()
                            val bottom = size.height - bottomPad.toPx()
                            val slot = slotW.toPx()
                            val bw = barW.toPx()
                            val minH = 2.dp.toPx()
                            val radius = bw * 0.35f
                            val y0 = scale.y(0f, top, bottom)

                            val gridPx = 1.dp.toPx()
                            val gridYs = FloatArray(scale.values.size) { scale.y(scale.values[it], top, bottom) }
                            val baselinePx = 1.5.dp.toPx()

                            val upPaths = arrayOfNulls<Path>(n)
                            val downPaths = arrayOfNulls<Path>(n)
                            for (i in 0 until n) {
                                val l = i * slot + (slot - bw) / 2f
                                val b = buckets[i]
                                if (b.up > 0f) {
                                    val tip = minOf(scale.y(b.up, top, bottom), y0 - minH)
                                    upPaths[i] = Path().apply { addBar(l, l + bw, y0, tip, minOf(radius, y0 - tip)) }
                                }
                                if (b.down > 0f) {
                                    val tip = maxOf(scale.y(-b.down, top, bottom), y0 + minH)
                                    downPaths[i] = Path().apply { addBar(l, l + bw, y0, tip, minOf(radius, tip - y0)) }
                                }
                            }
                            val pivot = Offset(0f, y0)
                            val dotR = 4.dp.toPx()
                            val dotX = FloatArray(n) { (it + 0.5f) * slot }
                            val dotDy = FloatArray(n) { scale.y(buckets[it].up - buckets[it].down, top, bottom) - y0 }
                            val dotColors = Array(n) {
                                if (buckets[it].up - buckets[it].down >= 0f) extra.positive else extra.negative
                            }

                            val xLabels = Array<TextLayoutResult>(n) { measurer.measure(buckets[it].label, labelStyle) }
                            val widest = xLabels.maxOf { it.size.width } + 6.dp.toPx()
                            val stride = ceil(widest / slot).toInt().coerceAtLeast(1)
                            val xPos = Array(n) {
                                Offset(
                                    (dotX[it] - xLabels[it].size.width / 2f)
                                        .coerceIn(0f, (size.width - xLabels[it].size.width).coerceAtLeast(0f)),
                                    bottom + 6.dp.toPx()
                                )
                            }

                            val yearStyle = TextStyle(
                                fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurface
                            )
                            val yearAt = (0 until n).filter { buckets[it].year != null }
                            val yearLabels = yearAt.map { measurer.measure(buckets[it].year!!, yearStyle) }
                            val yearX = yearAt.map { it * slot + 3.dp.toPx() }
                            val yearShown = yearAt.indices.filter { k ->
                                k == yearAt.lastIndex || yearX[k] + yearLabels[k].size.width + 4.dp.toPx() <= yearX[k + 1]
                            }
                            val yearY = bottom + 6.dp.toPx() + xLabels.maxOf { it.size.height }
                            val dividerColor = chart.axisLabel.copy(alpha = 0.4f)

                            val tips = TooltipCache(measurer, tipStyle)
                            val valueStyle = TextStyle(fontSize = 10.sp, fontFamily = ChartFont, fontWeight = FontWeight.Bold, color = cs.onSurface)
                            val values = valueLabels?.let { f -> Array(n) { measurer.measure(f(buckets[it]), valueStyle) } }
                            val valuePos = values?.let { v ->
                                Array(n) {
                                    val b = buckets[it]
                                    val up = b.up >= b.down
                                    val tip = if (up) scale.y(b.up, top, bottom) - 4.dp.toPx() - v[it].size.height
                                    else scale.y(-b.down, top, bottom) + 4.dp.toPx()
                                    Offset(
                                        (dotX[it] - v[it].size.width / 2f).coerceIn(0f, (size.width - v[it].size.width).coerceAtLeast(0f)),
                                        tip.coerceIn(0f, size.height - v[it].size.height),
                                    )
                                }
                            }
                            val noteStyle = TextStyle(fontSize = 10.sp, fontFamily = ChartFont, color = chart.axisLabel)
                            val notePlaced = notes.flatMap { (i, pair) ->
                                if (i !in 0 until n) return@flatMap emptyList()
                                val x = dotX[i] + barW.toPx() / 2 + 6.dp.toPx()
                                listOfNotNull(
                                    pair.first?.let { t ->
                                        val l = measurer.measure(t, noteStyle)
                                        l to Offset(x.coerceAtMost(size.width - l.size.width), (scale.y(buckets[i].up, top, bottom)).coerceIn(0f, size.height - l.size.height))
                                    },
                                    pair.second?.let { t ->
                                        val l = measurer.measure(t, noteStyle)
                                        l to Offset(x.coerceAtMost(size.width - l.size.width), (scale.y(-buckets[i].down, top, bottom) - l.size.height).coerceIn(0f, size.height - l.size.height))
                                    },
                                )
                            }
                            val band = chart.axisLabel.copy(alpha = 0.08f)
                            val bandW = maxOf(barW.toPx() + 12.dp.toPx(), slot * 0.7f)
                            val pad = 8.dp.toPx()
                            val tipRadius = CornerRadius(8.dp.toPx())

                            onDrawBehind {
                                gridYs.forEach { y ->
                                    drawLine(chart.grid, Offset(0f, y), Offset(size.width, y), gridPx)
                                }
                                for (k in yearAt.indices) if (yearAt[k] > 0) {
                                    val x = yearAt[k] * slot
                                    drawLine(dividerColor, Offset(x, top), Offset(x, size.height), gridPx)
                                }
                                drawLine(cs.onSurfaceVariant, Offset(0f, y0), Offset(size.width, y0), baselinePx)

                                val sel = selected
                                val g = grow.value
                                if (onPick != null && sel in 0 until n) drawRoundRect(
                                    band, Offset(dotX[sel] - bandW / 2, 0f), androidx.compose.ui.geometry.Size(bandW, bottom + 2.dp.toPx()),
                                    CornerRadius(8.dp.toPx()),
                                )
                                scale(1f, g, pivot) {
                                    for (i in 0 until n) {
                                        val a = if (sel != -1 && sel != i) 0.3f else 1f
                                        val a2 = if (onPick != null && a < 1f) 0.35f else a
                                        upPaths[i]?.let { drawPath(it, upColor, a2) }
                                        downPaths[i]?.let { drawPath(it, downColor, a2) }
                                    }
                                }
                                if (showNet) for (i in 0 until n) {
                                    val a = if (sel != -1 && sel != i) 0.3f else 1f
                                    drawCircle(dotColors[i], dotR, Offset(dotX[i], y0 + dotDy[i] * g), a)
                                }
                                for (i in 0 until n) {
                                    if ((n - 1 - i) % stride == 0) drawText(xLabels[i], topLeft = xPos[i])
                                }
                                for (k in yearShown) drawText(yearLabels[k], topLeft = Offset(yearX[k], yearY))
                                if (g >= 1f) notePlaced.forEach { (l, at) -> drawText(l, topLeft = at) }
                                if (values != null && g >= 1f) values.forEachIndexed { i, l ->
                                    if (l.size.width <= slot - 2.dp.toPx()) drawText(l, topLeft = valuePos!![i])
                                }
                                if (onPick == null && sel in 0 until n) {
                                    drawTooltip(
                                        tips.layoutFor(sel) { tooltip(buckets[sel]) },
                                        dotX[sel], chart.tooltipBg, pad, tipRadius,
                                    )
                                }
                            }
                        }
                )
            }
        }
    }
}
