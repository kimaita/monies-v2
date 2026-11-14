package com.kimaita.monies.ui.onboarding

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import com.kimaita.monies.ui.theme.MoniesTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* ---------- Page 2: live rows ---------- */

// Colours come from the theme, so they are read when the row draws, not held here.
private data class AutoRowData(
    val avatar: @Composable () -> Color,
    val glyph: String,
    val tag: String,
    val name: String,
    val sub: String,
    val amount: String,
    val amountColor: @Composable () -> Color
)

private val autoRows = listOf(
    AutoRowData(
        { MaterialTheme.colorScheme.primary },
        "↗",
        "RECV",
        "Salary",
        "Received · just now",
        "+48,000",
        { MoniesTheme.extra.positive }
    ),
    AutoRowData(
        { MoniesTheme.extra.outflow },
        "↘",
        "SEND",
        "Naivas",
        "Pay bill · 14:02",
        "−1,500",
        { MoniesTheme.extra.negative }
    ),
    AutoRowData(
        { MaterialTheme.colorScheme.onPrimaryContainer },
        "F",
        "FULIZA",
        "Available",
        "Updated 2:02 PM",
        "1,000",
        { MaterialTheme.colorScheme.onSurface }
    ),
)

@Composable
internal fun AutoPage(active: Boolean) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PulseDot(); Text(
            "LIVE",
            fontFamily = onbMono(),
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.primary
        )
        }
        Spacer(Modifier.height(12.dp))
        OnbHeadline("Nothing\nto type")
        Spacer(Modifier.height(14.dp))
        OnbBody(
            "Transactions, balances, Fuliza and M-Shwari stay current as messages arrive.",
            Modifier.widthIn(max = 300.dp)
        )
        Spacer(Modifier.height(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
            autoRows.forEachIndexed { i, r ->
                Reveal(active, i, 280) { AutoRow(r) }
            }
        }
    }
}

@Composable
private fun AutoRow(r: AutoRowData) {
    Row(
        Modifier
            .fillMaxWidth()
            .onbCard(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(r.avatar()),
            contentAlignment = Alignment.Center
        ) {
            Text(
                r.glyph,
                fontFamily = onbDisplay(),
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
        Column(Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    r.tag,
                    fontFamily = onbMono(),
                    fontSize = 9.5.sp,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                )
                Text(
                    r.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.5.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(r.sub, fontFamily = onbMono(), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            r.amount,
            fontFamily = onbDisplay(),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = r.amountColor()
        )
    }
}

@Composable
private fun PulseDot() {
    val tr = rememberInfiniteTransition(label = "pulse")
    val a by tr.animateFloat(
        1f,
        0.3f,
        infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "a"
    )
    Box(Modifier
        .size(7.dp)
        .graphicsLayer { alpha = a }
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.primary))
}
