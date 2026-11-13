package com.kimaita.monies.ui.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kimaita.monies.ui.util.formatAmount
import kotlin.math.roundToInt

data class RankedRow(val label: String, val amount: Double)

/** Rows ranked by amount: label, amount, share of the total, and a bar scaled to the largest row. */
@Composable
fun RankedBars(
    rows: List<RankedRow>,
    color: Color,
    selected: String? = null,
    onRowClick: ((String) -> Unit)? = null,
    limit: Int = 8,
    showPercent: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val ranked = remember(rows, limit) { rows.sortedByDescending { it.amount }.take(limit) }
    val total = remember(rows) { rows.sumOf { it.amount }.coerceAtLeast(1.0) }
    val max = remember(ranked) { (ranked.firstOrNull()?.amount ?: 1.0).coerceAtLeast(1.0) }
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val grow = remember(ranked) { Animatable(0f) }
    LaunchedEffect(ranked) { grow.snapTo(0f); grow.animateTo(1f, tween(600, easing = FastOutSlowInEasing)) }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ranked.forEach { r ->
            val dim = selected != null && selected != r.label
            Column(
                Modifier
                    .fillMaxWidth()
                    .alpha(if (dim) 0.35f else 1f)
                    .then(if (onRowClick != null) Modifier.clickable { onRowClick(r.label) } else Modifier)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        r.label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        formatAmount(r.amount), style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (showPercent) Text(
                        "${(r.amount / total * 100).roundToInt()}%", Modifier.width(40.dp),
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(6.dp))
                val frac = (r.amount / max).toFloat().coerceIn(0f, 1f)
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .drawWithCache {
                            val radius = CornerRadius(size.height / 2f)
                            onDrawBehind {
                                drawRoundRect(track, Offset.Zero, size, radius)
                                drawRoundRect(
                                    color, Offset.Zero,
                                    Size(size.width * frac * grow.value, size.height), radius
                                )
                            }
                        }
                )
            }
        }
    }
}
