package com.kimaita.monies.ui.charts

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kimaita.monies.models.analytics.MerchantSpend
import com.kimaita.monies.ui.util.formatAmount

@Composable
fun TopMerchantsChart(items: List<MerchantSpend>, modifier: Modifier = Modifier, limit: Int = 5) {
    val top = remember(items, limit) { items.sortedByDescending { it.amount }.take(limit) }
    val max = remember(top) { top.maxOfOrNull { it.amount } ?: 1.0 }
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val barColor = MaterialTheme.colorScheme.primary

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        top.forEachIndexed { i, m ->
            var visible by remember(m.name) { mutableStateOf(false) }
            val frac by animateFloatAsState(
                if (visible) (m.amount / max).toFloat() else 0f,
                tween(700, delayMillis = i * 80, easing = FastOutSlowInEasing), label = "bar$i",
            )
            LaunchedEffect(m.name) { visible = true }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        m.name, Modifier.weight(1f, false), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        formatAmount(m.amount), style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(Modifier.fillMaxWidth().height(10.dp).background(trackColor, CircleShape)) {
                    Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(barColor, CircleShape))
                }
            }
        }
    }
}