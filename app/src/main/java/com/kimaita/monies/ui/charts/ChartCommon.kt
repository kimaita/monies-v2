package com.kimaita.monies.ui.charts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kimaita.monies.ui.theme.MoniesTheme
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/** One bar slot: [up] rises from the baseline, [down] hangs below it. [year] marks where a year begins. */
data class BarBucket(val label: String, val up: Float, val down: Float = 0f, val year: String? = null)

// Plot geometry shared by the line and bar charts.
internal val YAxisWidth = 40.dp
internal val PlotTopPad = 8.dp
internal val PlotBottomPad = 22.dp   // room for the x-axis labels
internal val YearRowHeight = 16.dp   // extra room, when a chart marks years, for a second label row

/** Returns (axisMax, step) giving ~[lines] evenly-spaced, round gridlines. */
fun niceAxis(max: Float, lines: Int = 4): Pair<Float, Float> {
    if (max <= 0f) return 1f to 1f
    val raw = max / lines
    val mag = 10f.pow(floor(log10(raw)))
    val norm = raw / mag
    val nice = when {
        norm <= 1f -> 1f; norm <= 2f -> 2f; norm <= 5f -> 5f; else -> 10f
    }
    val step = nice * mag
    return (ceil(max / step) * step) to step
}

/** Vertical value range [lo, hi] with gridlines every [step]. */
internal class YScale(val lo: Float, val hi: Float, val step: Float) {
    val values = FloatArray(((hi - lo) / step).roundToInt() + 1) { lo + it * step }
    fun y(v: Float, top: Float, bottom: Float) = bottom - (v - lo) / (hi - lo) * (bottom - top)
}

/**
 * Starts at 0, unless [zeroBased] is false and the minimum is above half the maximum: then it starts
 * at the nice floor of the minimum so small changes stay visible.
 */
internal fun lineScale(min: Float, max: Float, zeroBased: Boolean = false): YScale {
    val lo0 = if (!zeroBased && min > 0f && min > max / 2f) min else minOf(min, 0f)
    val step = niceAxis(max - lo0).second
    val lo = floor(lo0 / step) * step
    var hi = ceil(max / step - 1e-4f) * step
    if (hi <= lo) hi = lo + step
    return YScale(lo, hi, step)
}

/** Runs from the nice ceiling of [maxDown] (below the baseline) to the nice ceiling of [maxUp]. */
internal fun barScale(maxUp: Float, maxDown: Float): YScale {
    val step = niceAxis(maxOf(maxUp, maxDown)).second
    var hi = ceil(maxUp / step - 1e-4f) * step
    val lo = -ceil(maxDown / step - 1e-4f) * step
    if (hi - lo <= 0f) hi = step
    return YScale(lo, hi, step)
}

@Immutable
data class ChartColors(
    val inflow: Color,
    val outflow: Color,
    val grid: Color,
    val axisLabel: Color,
    val tooltipBg: Color,
    val tooltipText: Color,
)

@Composable
@ReadOnlyComposable
fun chartColors(): ChartColors {
    val cs = MaterialTheme.colorScheme
    val extra = MoniesTheme.extra
    return ChartColors(
        inflow = extra.inflow,
        outflow = extra.outflow,
        grid = cs.outlineVariant.copy(alpha = 0.5f),
        axisLabel = cs.onSurfaceVariant,
        tooltipBg = cs.surfaceContainerHighest,
        tooltipText = cs.onSurface,
    )
}
