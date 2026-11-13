package com.kimaita.monies.ui.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kimaita.monies.models.analytics.HeatRow

val SLOT_LABELS = arrayOf("Morning", "Midday", "Evening", "Night")

/** Day-by-time-slot grid drawn in one Canvas. */
@Composable
fun TimeOfDayHeatmap(
    rows: List<HeatRow>,
    modifier: Modifier = Modifier,
    showLabels: Boolean = true,
) {
    val maxVal = remember(rows) { rows.flatMap { it.values }.maxOrNull()?.coerceAtLeast(1) ?: 1 }
    val emptyColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val fullColor = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()

    val fade = remember { Animatable(0f) }
    LaunchedEffect(Unit) { fade.animateTo(1f, tween(600)) }

    val rowHeight = if (showLabels) 36.dp else 22.dp
    val headerHeight = if (showLabels) 18.dp else 0.dp
    Spacer(
        modifier
            .fillMaxWidth()
            .height(headerHeight + rowHeight * rows.size)
            .graphicsLayer { alpha = fade.value }
            .drawWithCache {
                val style = TextStyle(fontSize = 11.sp, color = labelColor)
                val gutter = if (showLabels) 40.dp.toPx() else 0f
                val header = headerHeight.toPx()
                val rowH = rowHeight.toPx()
                val cols = rows.maxOfOrNull { it.values.size } ?: 0
                val colW = if (cols > 0) (size.width - gutter) / cols else 0f
                val inset = 3.dp.toPx()
                val radius = CornerRadius(6.dp.toPx())
                val cellSize = Size((colW - inset * 2).coerceAtLeast(0f), (rowH - inset * 2).coerceAtLeast(0f))

                val slotLabels = if (showLabels) Array(minOf(cols, SLOT_LABELS.size)) {
                    measurer.measure(SLOT_LABELS[it], style)
                } else arrayOf<TextLayoutResult>()
                val slotPos = Array(slotLabels.size) {
                    Offset(gutter + colW * it + (colW - slotLabels[it].size.width) / 2f, 0f)
                }
                val dayLabels = if (showLabels) Array(rows.size) { measurer.measure(rows[it].day, style) } else arrayOf<TextLayoutResult>()
                val dayPos = Array(dayLabels.size) {
                    Offset(0f, header + rowH * it + (rowH - dayLabels[it].size.height) / 2f)
                }
                val cellPos = ArrayList<Offset>()
                val cellColor = ArrayList<androidx.compose.ui.graphics.Color>()
                rows.forEachIndexed { r, row ->
                    row.values.forEachIndexed { c, v ->
                        cellPos.add(Offset(gutter + colW * c + inset, header + rowH * r + inset))
                        cellColor.add(lerp(emptyColor, fullColor, v.toFloat() / maxVal))
                    }
                }
                onDrawBehind {
                    slotLabels.forEachIndexed { i, l -> drawText(l, topLeft = slotPos[i]) }
                    dayLabels.forEachIndexed { i, l -> drawText(l, topLeft = dayPos[i]) }
                    for (i in cellPos.indices) drawRoundRect(cellColor[i], cellPos[i], cellSize, radius)
                }
            }
    )
}
