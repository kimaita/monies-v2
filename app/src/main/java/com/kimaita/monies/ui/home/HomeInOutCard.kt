package com.kimaita.monies.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kimaita.monies.models.TimeFilter
import kotlin.math.abs

/* ---------------- change-in-balance pill ---------------- */

@Composable
internal fun ChangePill(change: Double?, filter: TimeFilter, onClick: () -> Unit) {
    if (change == null) return
    val up = change >= 0
    val fg = if (up) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val bg = fg.copy(alpha = 0.12f)
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(if (up) "▲" else "▼", color = fg, style = MaterialTheme.typography.labelSmall)
        Text(
            "${if (up) "+" else "−"}${money(abs(change))} ${periodWord(filter)}",
            color = fg,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text("›", color = fg.copy(alpha = 0.6f))
    }
}

/* ---------------- in & out card ---------------- */

@Composable
internal fun InOutCard(
    filter: TimeFilter, income: Double?, expense: Double?,
    onPeriod: (TimeFilter) -> Unit, onAnalytics: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val net = if (income != null && expense != null) income - expense else null
    val inFraction = if (income != null && expense != null) {
        (income / (income + expense).coerceAtLeast(1.0)).toFloat()
    } else null

    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text(
                    "IN & OUT",
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant
                )
                PeriodSegment(filter, onPeriod)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                IoColumn("IN", income, cs.primary, Modifier.weight(1f))
                IoColumn("OUT", expense, cs.onSurface, Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))
            if (inFraction == null) Spacer(Modifier.fillMaxWidth().height(7.dp))
            else Box(
                Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(cs.error.copy(alpha = 0.25f))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(inFraction)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(cs.primary)
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text(
                    buildString {
                        append("Net ")
                        if (net == null) append(DASH) else {
                            append(if (net >= 0) "+" else "−"); append(money(abs(net)))
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (net == null) cs.onSurfaceVariant else if (net >= 0) cs.primary else cs.error,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(
                    onClick = onAnalytics,
                    contentPadding = PaddingValues(0.dp)
                ) { Text("Analytics ›") }
            }
        }
    }
}

@Composable
private fun IoColumn(label: String, value: Double?, color: Color, modifier: Modifier = Modifier) =
    Column(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(color)
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            if (value == null) DASH else money(value),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(
                minFontSize = 14.sp,
                maxFontSize = MaterialTheme.typography.headlineSmall.fontSize
            ),
        )
    }

@Composable
private fun PeriodSegment(filter: TimeFilter, onPeriod: (TimeFilter) -> Unit) {
    val options =
        listOf("Day" to TimeFilter.DAY, "Week" to TimeFilter.WEEK, "Month" to TimeFilter.MONTH)
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(cs.surfaceVariant.copy(alpha = 0.6f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        options.forEach { (label, value) ->
            val on = filter == value
            Box(
                Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (on) cs.surface else Color.Transparent)
                    .clickable { onPeriod(value) }
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            ) {
                Text(
                    label, style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (on) cs.onSurface else cs.onSurfaceVariant
                )
            }
        }
    }
}
