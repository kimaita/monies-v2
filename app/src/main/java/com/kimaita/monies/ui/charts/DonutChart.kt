package com.kimaita.monies.ui.charts

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kimaita.monies.models.analytics.CategorySlice
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

@Composable
fun DonutChart(
    slices: List<CategorySlice>,
    modifier: Modifier = Modifier,
    thickness: Dp = 26.dp,
    gapDegrees: Float = 3f,
    minSize: Dp = 200.dp,
    selectedLabel: String? = null,
    onSliceClick: ((String) -> Unit)? = null,
    center: @Composable () -> Unit = {},
) {
    val total =
        remember(slices) { slices.sumOf { it.value.toDouble() }.toFloat().coerceAtLeast(1f) }
    var visible by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        if (visible) 1f else 0f, tween(900, easing = FastOutSlowInEasing), label = "donut",
    )
    LaunchedEffect(Unit) { visible = true }

    val popoutPx = with(LocalDensity.current) { 8.dp.toPx() }

    Box(modifier.defaultMinSize(minSize, minSize), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .fillMaxSize()
                .then(
                    if (onSliceClick == null) Modifier
                    else Modifier.pointerInput(slices, total, thickness) {
                        detectTapGestures { tap ->
                            val strokePx = thickness.toPx()
                            val dx = tap.x - size.width / 2f
                            val dy = tap.y - size.height / 2f
                            val outer = min(size.width, size.height) / 2f + popoutPx
                            if (hypot(dx, dy) > outer) return@detectTapGestures
                            val raw = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                            val ang = ((raw % 360f) + 360f) % 360f
                            var start = -90f
                            for (slice in slices) {
                                val full = slice.value / total * 360f
                                val s0 = ((start % 360f) + 360f) % 360f
                                val s1 = (((start + full) % 360f) + 360f) % 360f
                                val hit = if (s0 <= s1) ang in s0..s1 else (ang >= s0 || ang <= s1)
                                if (hit) {
                                    onSliceClick(slice.label); break
                                }
                                start += full
                            }
                        }
                    },
                ),
        ) {
            val strokePx = thickness.toPx()
            val d = min(size.width, size.height) - strokePx - popoutPx * 2
            val anySelected = selectedLabel != null
            var start = -90f
            slices.forEach { slice ->
                val full = slice.value / total * 360f
                val sweep = (full - gapDegrees).coerceAtLeast(0f) * progress
                val isSelected = anySelected && slice.label == selectedLabel
                val mid = Math.toRadians((start + full / 2f).toDouble())
                val shiftX = if (isSelected) (cos(mid) * popoutPx).toFloat() else 0f
                val shiftY = if (isSelected) (sin(mid) * popoutPx).toFloat() else 0f
                drawArc(
                    color = if (anySelected && !isSelected) slice.color.copy(alpha = 0.35f) else slice.color,
                    startAngle = start + gapDegrees / 2f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(
                        (size.width - d) / 2f + shiftX,
                        (size.height - d) / 2f + shiftY
                    ),
                    size = Size(d, d),
                    style = Stroke(
                        width = if (isSelected) strokePx * 1.3f else strokePx,
                        cap = StrokeCap.Butt
                    ),
                )
                start += full
            }
        }
        center()
    }
}