package com.kimaita.monies.ui.charts

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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

