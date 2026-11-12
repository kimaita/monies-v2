package com.kimaita.monies.ui.charts

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import com.kimaita.monies.models.analytics.CategorySlice
import com.kimaita.monies.ui.analytics.SliceValue
import com.kimaita.monies.ui.theme.AppTheme
import com.kimaita.monies.ui.theme.MoniesTheme

data class BarSegment(val value: Float, val color: Color)
data class BarGroup(val label: String, val segments: List<BarSegment>)

fun kesCompact(v: Float): String = when {
    v >= 1_000_000f -> "%.1fM".format(v / 1_000_000f)
    v >= 1_000f -> "%.1fk".format(v / 1_000f)
    else -> v.roundToInt().toString()
}

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

fun axisLabels(axisMax: Float, step: Float): List<Float> =
    buildList {
        var v = axisMax; while (v >= -0.001f) {
        add(v); v -= step
    }
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
        grid = cs.outlineVariant,
        axisLabel = cs.onSurfaceVariant,
        tooltipBg = cs.inverseSurface,
        tooltipText = cs.inverseOnSurface,
    )
}

/** Assigns theme palette colours to slices, cycling if there are more slices than colours. */
@Composable
fun rememberSlices(values: List<SliceValue>): List<CategorySlice> {
        val palette = MoniesTheme.extra.chartPalette
        return remember(values, palette) {
                values.mapIndexed { i, v -> CategorySlice(v.label, v.value, palette[i % palette.size]) }
            }
    }