package com.kimaita.monies.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kimaita.monies.data.dao.dtos.SubjectCount
import com.kimaita.monies.models.analytics.RecurringPayment
import com.kimaita.monies.services.WeekdayBar
import com.kimaita.monies.services.feeShare
import com.kimaita.monies.ui.charts.*
import com.kimaita.monies.ui.theme.MoniesTheme
import com.kimaita.monies.ui.util.dayMonthFmt
import com.kimaita.monies.ui.util.formatCompactKes
import com.kimaita.monies.ui.util.formatKes
import java.util.Locale

/* ----------------------------- small components ----------------------------- */

@Composable
internal fun TabList(state: LazyListState, content: LazyListScope.() -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        state = state,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
internal fun BentoCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = remember { RoundedCornerShape(20.dp) }
    val colors =
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    if (onClick != null) {
        Card(onClick, modifier.fillMaxWidth(), shape = shape, colors = colors) {
            Column(Modifier.padding(16.dp), content = content)
        }
    } else {
        Card(modifier.fillMaxWidth(), shape = shape, colors = colors) {
            Column(Modifier.padding(16.dp), content = content)
        }
    }
}

@Composable
internal fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    BentoCard {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

/** "Top in  Name  KES 12K": the name takes the room left and ellipsizes; "–" when there is none, "…" while [loaded] is false. */
@Composable
internal fun TopLine(label: String, loaded: Boolean, top: Pair<String, Double>?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            if (loaded) top?.first ?: "–" else PENDING, Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        top?.let {
            Text(
                formatCompactKes(it.second), style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
internal fun TileLabel(t: String) = Text(
    t, style = MaterialTheme.typography.labelMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)

@Composable
internal fun StatTile(
    label: String, value: String, color: Color,
    modifier: Modifier = Modifier, sub: String? = null,
) {
    BentoCard(modifier) {
        TileLabel(label)
        Spacer(Modifier.height(6.dp))
        Text(
            value, Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            autoSize = TextAutoSize.StepBased(
                minFontSize = 14.sp,
                maxFontSize = MaterialTheme.typography.titleLarge.fontSize,
            ),
        )
        sub?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun RhythmTile(label: String, value: String, modifier: Modifier = Modifier) =
    StatTile(label, value, MaterialTheme.colorScheme.onSurface, modifier)

@Composable
internal fun MiniStat(label: String, value: String) =
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

@Composable
internal fun LeaderRow(index: Int, s: SubjectCount) {
    val medals = MoniesTheme.extra.medals
    val accent = medals.getOrNull(index) ?: MaterialTheme.colorScheme.surfaceVariant
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(26.dp)
                .background(accent, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${index + 1}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (index < medals.size) MaterialTheme.colorScheme.scrim else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(s.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            "${s.count}x", style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun RecurringRow(r: RecurringPayment) {
    val next = remember(r.nextEstimate) { r.nextEstimate.format(dayMonthFmt) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                r.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium
            )
            Text(
                "Next ~$next", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatKes(r.typicalAmount), fontWeight = FontWeight.SemiBold)
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    r.cadenceLabel, Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
internal fun DayOfWeekBars(bars: List<WeekdayBar>) {
    val max = remember(bars) { (bars.maxOfOrNull { it.count } ?: 0).coerceAtLeast(1) }
    val barColor = MoniesTheme.extra.inflow
    val shape = remember { RoundedCornerShape(6.dp) }
    Row(
        Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        bars.forEach { b ->
            Column(
                Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(0.55f)
                        .height((4 + 80 * b.count / max).dp)
                        .background(barColor, shape)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    b.label, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun EmptyHint(text: String = "Nothing here yet") =
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp), contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

/* ----------------------------- helpers ----------------------------- */

internal const val LOADING = "Loading…"

/** Stands in for a number or a name until its value arrives; never a zero. */
internal const val PENDING = "…"

private val weekdayLabels =
    arrayOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

internal fun feeShareText(fees: Double, expense: Double): String? =
    feeShare(fees, expense)?.let { "${"%.1f".format(Locale.US, it)}% of spend" }

internal fun weekdayName(i: Int?): String = i?.let { weekdayLabels.getOrNull(it) } ?: "-"
internal fun slotName(i: Int?): String = i?.let { SLOT_LABELS.getOrNull(it) } ?: "-"
