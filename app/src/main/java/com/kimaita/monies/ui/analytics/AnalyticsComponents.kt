package com.kimaita.monies.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
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
import com.kimaita.monies.services.feeShare
import com.kimaita.monies.ui.charts.*
import com.kimaita.monies.ui.theme.MoniesTheme
import com.kimaita.monies.ui.util.dayMonthFmt
import com.kimaita.monies.ui.util.formatKes
import java.util.Locale

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
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = remember { RoundedCornerShape(20.dp) }
    val colors = CardDefaults.cardColors(containerColor = containerColor)
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

/** A card's small label, "Balance · last 30 days", with an optional "Cash flow ›" link on the right. */
@Composable
internal fun CardHeader(
    label: String,
    link: String? = null,
    onLink: (() -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = color,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (link != null && onLink != null) {
            Text(
                "$link ›",
                Modifier
                    .clickable(role = Role.Button, onClick = onLink)
                    .padding(start = 8.dp, top = 2.dp, bottom = 2.dp),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** The 18/24 sentence each card leads with. */
internal val SayStyle: TextStyle
    @Composable @ReadOnlyComposable get() = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 24.sp)

/**
 * [text] with its `**` runs bold. The first run takes [toneColor] when given, or, when [bad], the
 * error container behind it so colour isn't the only cue.
 */
internal fun boldRuns(text: String, toneColor: Color? = null, bad: Boolean = false, badBg: Color = Color.Unspecified, badFg: Color = Color.Unspecified): AnnotatedString =
    buildAnnotatedString {
        text.split("**").forEachIndexed { i, part ->
            if (i % 2 == 0) append(part)
            else {
                val first = i == 1
                val style = when {
                    first && bad -> SpanStyle(fontWeight = FontWeight.Bold, color = badFg, background = badBg)
                    first && toneColor != null -> SpanStyle(fontWeight = FontWeight.Bold, color = toneColor)
                    else -> SpanStyle(fontWeight = FontWeight.Bold)
                }
                withStyle(style) { append(if (first && bad) "\u2009$part\u2009" else part) }
            }
        }
    }

/** A card's lead sentence; see [boldRuns]. */
@Composable
internal fun Say(
    text: String,
    modifier: Modifier = Modifier,
    toneColor: Color? = null,
    bad: Boolean = false,
    style: TextStyle = SayStyle,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val cs = MaterialTheme.colorScheme
    val annotated = remember(text, toneColor, bad, cs.errorContainer) {
        boldRuns(text, toneColor, bad, cs.errorContainer, cs.onErrorContainer)
    }
    Text(annotated, modifier, style = style, color = color)
}

/** A small note under a sentence or chart. */
@Composable
internal fun Note(text: String, modifier: Modifier = Modifier) = Text(
    text, modifier, style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)

/** "Spent / 31,400" on a pale box; [quiet] greys and shrinks a value that stands in for a figure. */
@Composable
internal fun MiniFigure(label: String, value: String, modifier: Modifier = Modifier, quiet: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier
            .background(cs.surface.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, maxLines = 1)
        Text(
            value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = if (quiet) 13.sp else 16.sp, lineHeight = 22.sp,
                fontWeight = if (quiet) FontWeight.Normal else FontWeight.Bold,
            ),
            color = if (quiet) cs.onSurfaceVariant else cs.onSurface,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = if (quiet) 13.sp else 16.sp),
        )
    }
}

/** A legend key: a short line, dashed when [dash] is (on, off), then its label. */
@Composable
internal fun LegendLine(label: String, color: Color, textColor: Color, width: Dp = 2.dp, dash: Pair<Dp, Dp>? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Spacer(
            Modifier
                .size(18.dp, 6.dp)
                .drawWithCache {
                    val stroke = Stroke(
                        width.toPx(), cap = StrokeCap.Round,
                        pathEffect = dash?.let { (on, off) -> PathEffect.dashPathEffect(floatArrayOf(on.toPx(), off.toPx())) },
                    )
                    val y = size.height / 2
                    val path = Path().apply { moveTo(1.dp.toPx(), y); lineTo(size.width - 1.dp.toPx(), y) }
                    onDrawBehind { drawPath(path, color, style = stroke) }
                }
        )
        Text(label, style = MaterialTheme.typography.labelMedium, color = textColor)
    }
}

/** One bar split into [parts] by weight, 14 dp tall with 2 dp between parts. */
@Composable
internal fun ShareBar(parts: List<Pair<Float, Color>>, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(7.dp)),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        parts.filter { it.first > 0f }.forEach { (w, c) ->
            Box(
                Modifier
                    .weight(w)
                    .fillMaxHeight()
                    .background(c)
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

internal const val LOADING = "Loading…"

/** Stands in for a number or a name until its value arrives; never a zero. */
internal const val PENDING = "…"

/** "0.7% of spending" */
internal fun feeShareText(fees: Double, expense: Double): String? =
    feeShare(fees, expense)?.let { "${"%.1f".format(Locale.US, it)}% of spending" }


