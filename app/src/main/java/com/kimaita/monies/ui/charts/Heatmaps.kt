package com.kimaita.monies.ui.charts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kimaita.monies.models.analytics.HabitWeek
import com.kimaita.monies.models.analytics.HeatRow

val SLOT_LABELS = arrayOf("Morning", "Midday", "Evening", "Night")

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
    val cellShape = remember { RoundedCornerShape(6.dp) }

    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(600), label = "heat")
    LaunchedEffect(Unit) { visible = true }

    Column(modifier
        .fillMaxWidth()
        .alpha(alpha), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (showLabels) Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(40.dp))
            SLOT_LABELS.forEach {
                Text(
                    it, Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall, color = labelColor,
                )
            }
        }
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (showLabels) Text(
                    row.day, Modifier.width(40.dp),
                    style = MaterialTheme.typography.labelSmall, color = labelColor,
                )
                row.values.forEach { v ->
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(if (showLabels) 1.6f else 2.4f)
                            .padding(3.dp)
                            .background(
                                lerp(emptyColor, fullColor, v.toFloat() / maxVal),
                                cellShape
                            ),
                    )
                }
            }
        }
    }
}

/** GitHub-style 12-week habit graph. Currently unused; kept for the subject-profile page. */
@Composable
fun HabitGraph(weeks: List<HabitWeek>, modifier: Modifier = Modifier) {
    val colors = chartColors()
    val emptyColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val shape = remember { RoundedCornerShape(3.dp) }
    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(700), label = "habit")
    LaunchedEffect(Unit) { visible = true }

    Row(modifier
        .fillMaxWidth()
        .alpha(alpha), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        weeks.forEach { week ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                week.days.forEach { count ->
                    val c = when {
                        count <= 0 -> emptyColor
                        count == 1 -> colors.inflow.copy(alpha = 0.35f)
                        count == 2 -> colors.inflow.copy(alpha = 0.6f)
                        else -> colors.inflow
                    }
                    Box(Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .background(c, shape))
                }
            }
        }
    }
}