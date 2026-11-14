package com.kimaita.monies.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import com.kimaita.monies.ui.theme.MoniesTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp

/* ---------- Page 1: SMS → record ---------- */

@Composable
internal fun HeroPage(active: Boolean, parallax: () -> Float) {
    val t = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (active) {
            t.snapTo(0f); t.animateTo(1f, tween(750, easing = FastOutSlowInEasing))
        } else t.snapTo(0f)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.graphicsLayer {
            val p1 = (t.value * 1.7f).coerceIn(0f, 1f)
            alpha = p1; translationX = (1f - p1) * -30f + parallax() * 50f
        }) { SmsBubble() }
        Spacer(Modifier.height(11.dp))
        ArrowBadge(Modifier.graphicsLayer { alpha = (t.value * 1.7f).coerceIn(0f, 1f) })
        Spacer(Modifier.height(12.dp))
        Box(Modifier.graphicsLayer {
            val p2 = ((t.value - 0.35f) / 0.65f).coerceIn(0f, 1f)
            alpha = p2; translationY = (1f - p2) * 28f; scaleX = lerp(0.96f, 1f, p2); scaleY =
            lerp(0.96f, 1f, p2)
        }) { RecordCard() }
        Spacer(Modifier.height(30.dp))
        Eyebrow("WELCOME TO MONIES")
        Spacer(Modifier.height(12.dp))
        OnbHeadline("Sorted the\nmoment it lands")
        Spacer(Modifier.height(14.dp))
        OnbBody(
            "Every M-PESA message becomes a clean record — automatically, no typing.",
            Modifier.widthIn(max = 300.dp)
        )
    }
}

@Composable
private fun SmsBubble() {
    val shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 5.dp)
    Column(
        Modifier
            .widthIn(max = 290.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Box(Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary))
            Text(
                "M-PESA · TODAY 2:02 PM",
                fontFamily = onbMono(),
                fontSize = 9.5.sp,
                letterSpacing = 1.2.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = MoniesTheme.extra.outflow)) { append("WEF6X3K9LM") }
                append(" Confirmed. Ksh1,500.00 paid to NAIVAS LIMITED. New M-PESA balance is Ksh8,420.00.")
            },
            fontFamily = onbMono(), fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ArrowBadge(modifier: Modifier = Modifier) {
    Row(
        modifier.width(290.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Text(
            "Monies reads it",
            fontFamily = onbMono(),
            fontWeight = FontWeight.Bold,
            fontSize = 10.5.sp,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .clip(RoundedCornerShape(7.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 8.dp, vertical = 3.dp)
        )
        val dashColor = MaterialTheme.colorScheme.primaryContainer
        Canvas(Modifier
            .weight(1f)
            .height(2.dp)) {
            drawLine(
                dashColor,
                Offset(0f, size.height / 2),
                Offset(size.width, size.height / 2),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 6.dp.toPx()))
            )
        }
        Text("↓", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
    }
}

@Composable
private fun RecordCard() {
    Row(
        Modifier
            .widthIn(max = 300.dp)
            .onbCard(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "N",
                fontFamily = onbDisplay(),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                "Naivas Limited",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "Pay bill · 14:02",
                fontFamily = onbMono(),
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            "−1,500",
            fontFamily = onbDisplay(),
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MoniesTheme.extra.negative
        )
    }
}
