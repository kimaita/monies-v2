package com.kimaita.monies.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import com.kimaita.monies.ui.theme.MoniesTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Modifier.onbCard(): Modifier = this
    .shadow(4.dp, RoundedCornerShape(18.dp), clip = false)
    .clip(RoundedCornerShape(18.dp))
    .background(MaterialTheme.colorScheme.surfaceContainerLow)
    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
    .padding(horizontal = 15.dp, vertical = 13.dp)

@Composable
fun Eyebrow(text: String, color: Color = MoniesTheme.extra.outflow) =
    Text(text, fontFamily = MaterialTheme.typography.labelSmall.fontFamily, fontSize = 12.sp, letterSpacing = 1.7.sp, color = color)

@Composable
fun OnbHeadline(text: String, fontSize: TextUnit = 34.sp, modifier: Modifier = Modifier) =
    Text(
        text,
        modifier,
        fontFamily = MaterialTheme.typography.titleLarge.fontFamily,
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