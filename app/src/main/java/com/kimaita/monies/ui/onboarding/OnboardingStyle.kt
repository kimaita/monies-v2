package com.kimaita.monies.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import com.kimaita.monies.ui.theme.MoniesTheme
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun onbMono(): FontFamily? = MaterialTheme.typography.labelSmall.fontFamily

@Composable
fun onbDisplay(): FontFamily? = MaterialTheme.typography.titleLarge.fontFamily

/** Fades and slides [content] in once [active], [index] x 120 ms after the page appears; hides it again when the page leaves. */
@Composable
fun Reveal(active: Boolean, index: Int, durationMillis: Int, content: @Composable () -> Unit) {
    var show by remember(active) { mutableStateOf(false) }
    LaunchedEffect(active) {
        if (active) {
            delay((120L * index).milliseconds); show = true
        } else show = false
    }
    AnimatedVisibility(
        show,
        enter = fadeIn(tween(durationMillis)) + slideInVertically(tween(durationMillis)) { it / 4 }) {
        content()
    }
}

@Composable
fun Modifier.onbCard(): Modifier = this
    .shadow(4.dp, RoundedCornerShape(18.dp), clip = false)
    .clip(RoundedCornerShape(18.dp))
    .background(MaterialTheme.colorScheme.surfaceContainerLow)
    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
    .padding(horizontal = 15.dp, vertical = 13.dp)

@Composable
fun Eyebrow(text: String, color: Color = MoniesTheme.extra.outflow) =
    Text(text, fontFamily = onbMono(), fontSize = 12.sp, letterSpacing = 1.7.sp, color = color)

@Composable
fun OnbHeadline(text: String, fontSize: TextUnit = 34.sp, modifier: Modifier = Modifier) =
    Text(
        text,
        modifier,
        fontFamily = onbDisplay(),
        fontWeight = FontWeight.Bold,
        fontSize = fontSize,
        lineHeight = fontSize * 1.05f,
        letterSpacing = (-1).sp,
        color = MaterialTheme.colorScheme.onSurface
    )

@Composable
fun OnbBody(text: String, modifier: Modifier = Modifier, fontSize: TextUnit = 15.5.sp) =
    Text(
        text,
        modifier,
        fontSize = fontSize,
        lineHeight = fontSize * 1.5f,
        color = MaterialTheme.colorScheme.onSurface
    )