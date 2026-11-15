package com.kimaita.monies.ui.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kimaita.monies.models.analytics.BalancePoint
import com.kimaita.monies.ui.util.formatShortAmount
import com.kimaita.monies.ui.util.formatKes
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * One line of a [LineChartCanvas]. [xs] are in the chart's x domain and must be increasing. [dash] is
 * (on, off) for a dashed line.
 */
internal class LineSeries(
    val xs: FloatArray,
    val ys: FloatArray,
    val color: Color,
    val width: Dp,
    val dash: Pair<Dp, Dp>? = null,
    val fill: Boolean = false,
)

enum class MarkerStyle { DOT, RING, NONE }

/** Where a marker's label sits against its point. */
enum class LabelPlace { RIGHT, ABOVE_LEFT, BELOW_LEFT, BELOW_RIGHT }

/**
 * A point drawn over a [LineChartCanvas], in the chart's units: a filled dot, a ring filled with
 * [fill] (the card behind it), or nothing; with a bold [label] and a lighter [note] under it.
 */
internal class ChartMarker(
    val x: Float,
    val y: Float,
    val color: Color,
    val style: MarkerStyle = MarkerStyle.DOT,
    val fill: Color = Color.Transparent,
    val label: String? = null,
    val note: String? = null,
    val place: LabelPlace = LabelPlace.ABOVE_LEFT,
    val labelColor: Color? = null,
)

/**
 * Adds a monotone cubic (Fritsch-Carlson) curve through the points: the curve never overshoots
 * between two points. Control points sit at one third of each interval.
 */
internal fun Path.monotoneCurve(xs: FloatArray, ys: FloatArray) {
    val n = xs.size
    if (n == 0) return
    moveTo(xs[0], ys[0])
    if (n == 1) return
    val d = FloatArray(n - 1) { (ys[it + 1] - ys[it]) / (xs[it + 1] - xs[it]) }
    val m = FloatArray(n)
    m[0] = d[0]
    m[n - 1] = d[n - 2]
    for (k in 1 until n - 1) m[k] = if (d[k - 1] * d[k] <= 0f) 0f else (d[k - 1] + d[k]) / 2f
    for (k in 0 until n - 1) {
        if (d[k] == 0f) {
            m[k] = 0f; m[k + 1] = 0f
        } else {
            val a = m[k] / d[k]
            val b = m[k + 1] / d[k]
            val s = a * a + b * b
            if (s > 9f) {
                val t = 3f / kotlin.math.sqrt(s)
                m[k] = t * a * d[k]; m[k + 1] = t * b * d[k]
            }
        }
    }
    for (k in 0 until n - 1) {
        val h = xs[k + 1] - xs[k]
        cubicTo(
            xs[k] + h / 3f, ys[k] + m[k] * h / 3f,
            xs[k + 1] - h / 3f, ys[k + 1] - m[k + 1] * h / 3f,
            xs[k + 1], ys[k + 1],
        )
    }
}

/**
 * Gridlines, y labels, x labels, grow-in animation, [markers] and (when [tooltip] is set) scrubbing
 * for one or more lines. The x label at [strongX] is bold and wins over neighbours it would touch.
 * Callers must remember [series], [markers] and [tooltip] against their data: a new instance
 * rebuilds the cached geometry and restarts the animation.
 */
@Composable
internal fun LineChartCanvas(
    series: List<LineSeries>,
    xMin: Float,
    xMax: Float,
    xLabels: List<Pair<Float, String>>,
    modifier: Modifier = Modifier,
    plotHeight: Dp = 180.dp,
    zeroBased: Boolean = false,
    tooltip: ((Int) -> String)? = null,
    markers: List<ChartMarker> = emptyList(),
    strongX: Float? = null,
    gridColor: Color? = null,
) {
    val measurer = rememberTextMeasurer()
    val haptic = LocalHapticFeedback.current
    val cs = MaterialTheme.colorScheme
    val chart = chartColors()
    val labelColor = cs.onSurfaceVariant
    val pointColor = cs.surfaceContainerHighest

    val inkColor = cs.onSurface
    var scrub by remember(series) { mutableIntStateOf(-1) }
    val grow = remember(series) { Animatable(0f) }
    LaunchedEffect(series) { grow.snapTo(0f); grow.animateTo(1f, tween(800)) }

    Spacer(
        modifier
            .fillMaxWidth()
            .height(plotHeight + PlotTopPad + PlotBottomPad)
            .pointerInput(series, tooltip) {
                if (tooltip == null) return@pointerInput
                val left = YAxisWidth.toPx()
                val right = size.width - 6.dp.toPx()
                fun nearest(px: Float): Int {
                    val f = ((px - left) / (right - left)).coerceIn(0f, 1f)
                    val x = xMin + f * (xMax - xMin)
                    val xs = series[0].xs
                    var best = 0
                    for (i in xs.indices) if (abs(xs[i] - x) < abs(xs[best] - x)) best = i
                    return best
                }

                fun move(px: Float) {
                    val i = nearest(px)
                    if (i != scrub) {
                        scrub = i
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                }
                detectHorizontalDragGestures(
                    onDragStart = { move(it.x) },
                    onHorizontalDrag = { change, _ -> move(change.position.x); change.consume() },
                    onDragEnd = { scrub = -1 },
                    onDragCancel = { scrub = -1 },
                )
            }
            .drawWithCache {
                val axisStyle = TextStyle(fontSize = 10.sp, color = labelColor, fontFamily = ChartFont)
                val strongStyle = axisStyle.copy(color = inkColor, fontWeight = FontWeight.Bold)
                val tipStyle = TextStyle(fontSize = 11.sp, color = cs.onSurface)
                var mn = Float.MAX_VALUE
                var mx = -Float.MAX_VALUE
                for (s in series) for (y in s.ys) {
                    if (y < mn) mn = y; if (y > mx) mx = y
                }
                for (m in markers) {
                    if (m.y < mn) mn = m.y; if (m.y > mx) mx = m.y
                }
                if (mn > mx) { mn = 0f; mx = 0f }
                val scale = lineScale(mn, mx, zeroBased)

                val left = YAxisWidth.toPx()
                val right = size.width - 6.dp.toPx()
                val top = PlotTopPad.toPx()
                val bottom = size.height - PlotBottomPad.toPx()
                val span = (xMax - xMin).coerceAtLeast(1e-6f)
                fun px(x: Float) = left + (x - xMin) / span * (right - left)

                val gridPx = 1.dp.toPx()
                val gridYs = FloatArray(scale.values.size) { scale.y(scale.values[it], top, bottom) }
                val yLabels = Array(scale.values.size) {
                    measurer.measure(formatShortAmount(scale.values[it].toDouble()), axisStyle)
                }
                val yLabelPos = Array(yLabels.size) {
                    Offset(
                        left - 6.dp.toPx() - yLabels[it].size.width,
                        (gridYs[it] - yLabels[it].size.height / 2f)
                            .coerceIn(0f, size.height - yLabels[it].size.height)
                    )
                }
                val strongAt = xLabels.indexOfFirst { it.first == strongX }
                val xLabelLayouts = Array(xLabels.size) {
                    measurer.measure(xLabels[it].second, if (it == strongAt) strongStyle else axisStyle)
                }
                val xLabelPos = Array(xLabels.size) {
                    Offset(
                        (px(xLabels[it].first) - xLabelLayouts[it].size.width / 2f)
                            .coerceIn(0f, size.width - xLabelLayouts[it].size.width),
                        bottom + 6.dp.toPx()
                    )
                }
                val labelGap = 6.dp.toPx()
                fun touches(a: Int, b: Int) = xLabelPos[a].x + xLabelLayouts[a].size.width + labelGap > xLabelPos[b].x
                val shown = ArrayList<Int>()
                for (i in xLabels.indices) {
                    val wins = i == strongAt || (i == xLabels.lastIndex && shown.lastOrNull() != strongAt)
                    if (wins) while (shown.isNotEmpty() && touches(shown.last(), i)) shown.removeAt(shown.lastIndex)
                    if (shown.isEmpty() || !touches(shown.last(), i)) shown.add(i)
                }

                val paths = Array(series.size) { i ->
                    val s = series[i]
                    Path().apply {
                        monotoneCurve(
                            FloatArray(s.xs.size) { px(s.xs[it]) },
                            FloatArray(s.ys.size) { scale.y(s.ys[it], top, bottom) },
                        )
                    }
                }
                val areas = Array(series.size) { i ->
                    val s = series[i]
                    if (!s.fill || s.xs.size < 2) null else Path().apply {
                        addPath(paths[i])
                        lineTo(px(s.xs.last()), bottom)
                        lineTo(px(s.xs.first()), bottom)
                        close()
                    }
                }
                val strokes = Array(series.size) { i ->
                    Stroke(
                        width = series[i].width.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                        pathEffect = series[i].dash?.let { (on, off) ->
                            PathEffect.dashPathEffect(floatArrayOf(on.toPx(), off.toPx()))
                        },
                    )
                }
                val brushes = Array(series.size) { i ->
                    Brush.verticalGradient(
                        listOf(series[i].color.copy(alpha = 0.25f), series[i].color.copy(alpha = 0f)),
                        startY = top, endY = bottom,
                    )
                }
                val lonePoints = Array(series.size) { i ->
                    val s = series[i]
                    if (s.xs.size == 1) Offset(px(s.xs[0]), scale.y(s.ys[0], top, bottom)) else null
                }

                val first = series.firstOrNull()
                val pointX = FloatArray(first?.xs?.size ?: 0) { px(first!!.xs[it]) }
                val pointY = FloatArray(pointX.size) { scale.y(first!!.ys[it], top, bottom) }
                val tips = TooltipCache(measurer, tipStyle)
                val pad = 8.dp.toPx()
                val tipRadius = CornerRadius(8.dp.toPx())
                val markerStroke = 1.dp.toPx()
                val outerR = 5.dp.toPx()
                val innerR = 2.5.dp.toPx()

                val markStyle = strongStyle
                val noteStyle = axisStyle
                val dotR = 4.dp.toPx()
                val ringStroke = Stroke(2.dp.toPx())
                val gap = 8.dp.toPx()
                val marks = markers.map { m ->
                    val at = Offset(px(m.x), scale.y(m.y, top, bottom))
                    val label = m.label?.let { measurer.measure(it, markStyle.copy(color = m.labelColor ?: inkColor)) }
                    val note = m.note?.let { measurer.measure(it, noteStyle) }
                    val w = maxOf(label?.size?.width ?: 0, note?.size?.width ?: 0).toFloat()
                    val h = ((label?.size?.height ?: 0) + (note?.size?.height ?: 0)).toFloat()
                    val lh = (label?.size?.height ?: 0).toFloat()
                    val raw = when (m.place) {
                        LabelPlace.RIGHT -> Offset(at.x + gap, at.y - lh * 0.75f)
                        LabelPlace.ABOVE_LEFT -> Offset(at.x - gap / 2 - w, at.y - gap - h)
                        LabelPlace.BELOW_LEFT -> Offset(at.x - gap - w, at.y + gap / 2)
                        LabelPlace.BELOW_RIGHT -> Offset(at.x + gap, at.y + gap / 2)
                    }
                    val pos = Offset(
                        raw.x.coerceIn(0f, (size.width - w).coerceAtLeast(0f)),
                        raw.y.coerceIn(0f, (size.height - h).coerceAtLeast(0f)),
                    )
                    PlacedMarker(m, at, label, note, pos, pos.copy(y = pos.y + lh))
                }

                onDrawBehind {
                    gridYs.forEach { y ->
                        drawLine(gridColor ?: chart.grid, Offset(left, y), Offset(right, y), gridPx)
                    }
                    yLabels.forEachIndexed { i, l -> drawText(l, topLeft = yLabelPos[i]) }
                    for (i in shown) drawText(xLabelLayouts[i], topLeft = xLabelPos[i])

                    val g = grow.value
                    val clipRight = if (g >= 1f) size.width else left + (right - left) * g
                    clipRect(0f, 0f, clipRight, size.height) {
                        for (i in paths.indices) {
                            areas[i]?.let { drawPath(it, brushes[i]) }
                            drawPath(paths[i], series[i].color, style = strokes[i])
                            lonePoints[i]?.let { drawCircle(series[i].color, series[i].width.toPx(), it) }
                        }
                    }

                    for (pm in marks) {
                        val m = pm.marker
                        when (m.style) {
                            MarkerStyle.DOT -> drawCircle(m.color, dotR, pm.at, alpha = g)
                            MarkerStyle.RING -> {
                                drawCircle(m.fill, dotR, pm.at, alpha = g)
                                drawCircle(m.color, dotR - ringStroke.width / 2, pm.at, alpha = g, style = ringStroke)
                            }
                            MarkerStyle.NONE -> Unit
                        }
                        if (g >= 1f) {
                            pm.label?.let { drawText(it, topLeft = pm.labelAt) }
                            pm.note?.let { drawText(it, topLeft = pm.noteAt) }
                        }
                    }

                    val i = scrub
                    if (tooltip != null && i in pointX.indices) {
                        val x = pointX[i]
                        val y = pointY[i]
                        drawLine(
                            labelColor.copy(alpha = 0.4f), Offset(x, top), Offset(x, bottom), markerStroke
                        )
                        drawCircle(first!!.color, outerR, Offset(x, y))
                        drawCircle(pointColor, innerR, Offset(x, y))
                        drawTooltip(tips.layoutFor(i) { tooltip(i) }, x, chart.tooltipBg, pad, tipRadius)
                    }
                }
            }
    )
}

private class PlacedMarker(
    val marker: ChartMarker,
    val at: Offset,
    val label: TextLayoutResult?,
    val note: TextLayoutResult?,
    val labelAt: Offset,
    val noteAt: Offset,
)

/**
 * The balance by point: a ring where it started and a dot with its figure where it ends. With
 * [highLow], rings and labels at the highest and lowest points. [paydayAt] notes "payday" at that
 * point, under the High label when they meet.
 */
@Composable
fun BalanceTrendChart(
    data: List<BalancePoint>,
    modifier: Modifier = Modifier,
    plotHeight: Dp = 180.dp,
    highLow: Boolean = false,
    paydayAt: Int? = null,
) {
    if (data.size < 2) return
    val cs = MaterialTheme.colorScheme
    val color = cs.primary
    val card = cs.surfaceContainer
    val series = remember(data, color) {
        listOf(
            LineSeries(
                FloatArray(data.size) { it.toFloat() },
                FloatArray(data.size) { data[it].balance },
                color, 3.dp, fill = true,
            )
        )
    }
    val xLabels = remember(data) {
        listOf(0, data.lastIndex / 2, data.lastIndex).distinct().map { it.toFloat() to data[it].axisLabel }
    }
    val markers = remember(data, color, card, highLow, paydayAt) {
        balanceMarkers(data, color, card, highLow, paydayAt)
    }
    val tooltip = remember(data) {
        { i: Int -> "${data[i].label} · ${formatKes(data[i].balance.toDouble())}" }
    }
    LineChartCanvas(
        series, 0f, data.lastIndex.toFloat(), xLabels, modifier, plotHeight,
        tooltip = tooltip, markers = markers,
    )
}

private val markerDate = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

private fun balanceMarkers(
    data: List<BalancePoint>, color: Color, card: Color, highLow: Boolean, paydayAt: Int?,
): List<ChartMarker> {
    val last = data.lastIndex
    fun ring(i: Int, label: String? = null, note: String? = null, place: LabelPlace = LabelPlace.RIGHT) =
        ChartMarker(i.toFloat(), data[i].balance, color, MarkerStyle.RING, card, label, note, place)

    val out = mutableListOf(ring(0))
    val payday = paydayAt?.takeIf { it in data.indices }
    var paydayNoted = false
    if (highLow) {
        val hi = data.indices.maxBy { data[it].balance }
        val lo = data.indices.minBy { data[it].balance }
        if (hi != 0 && hi != last) {
            paydayNoted = payday == hi
            out += ring(
                hi, "High ${formatShortAmount(data[hi].balance.toDouble())} · ${data[hi].date.format(markerDate)}",
                if (paydayNoted) "payday" else null,
                if (hi > last * 0.6f) LabelPlace.BELOW_LEFT else LabelPlace.RIGHT,
            )
        }
        if (lo != 0 && lo != last && lo != hi) {
            out += ring(
                lo, "Low ${formatShortAmount(data[lo].balance.toDouble())} · ${data[lo].date.format(markerDate)}",
                place = if (lo < last * 0.3f) LabelPlace.BELOW_RIGHT else LabelPlace.BELOW_LEFT,
            )
        }
    }
    if (payday != null && !paydayNoted) {
        out += ChartMarker(payday.toFloat(), data[payday].balance, color, MarkerStyle.NONE, note = "payday", place = LabelPlace.RIGHT)
    }
    out += ChartMarker(
        last.toFloat(), data[last].balance, color, MarkerStyle.DOT,
        label = formatShortAmount(data[last].balance.toDouble()), place = LabelPlace.ABOVE_LEFT,
    )
    return out
}
