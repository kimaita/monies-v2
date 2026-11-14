package com.kimaita.monies.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A full-width row of equal choices in a rounded track; the selected one sits on a raised pill.
 * [inset] is the track's padding and the gap between choices, so the pill's corner is [radius] less
 * [inset] and stays concentric with the track. The defaults are the compact one (filters, breakdown);
 * the tab row passes a larger radius, inset and item padding.
 */
@Composable
fun <T> Segmented(
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    radius: Dp = 12.dp,
    inset: Dp = 3.dp,
    itemPadding: PaddingValues = PaddingValues(horizontal = 8.dp, vertical = 7.dp),
) {
    val cs = MaterialTheme.colorScheme
    val outer = remember(radius) { RoundedCornerShape(radius) }
    val inner = remember(radius, inset) { RoundedCornerShape(radius - inset) }
    Row(
        modifier
            .fillMaxWidth()
            .background(cs.surfaceVariant.copy(alpha = 0.5f), outer)
            .padding(inset),
        horizontalArrangement = Arrangement.spacedBy(inset),
    ) {
        options.forEach { (label, value) ->
            val active = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .background(if (active) cs.surface else Color.Transparent, inner)
                    .clickable { onSelect(value) }
                    .padding(itemPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label, maxLines = 1, style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (active) cs.onSurface else cs.onSurfaceVariant,
                )
            }
        }
    }
}
