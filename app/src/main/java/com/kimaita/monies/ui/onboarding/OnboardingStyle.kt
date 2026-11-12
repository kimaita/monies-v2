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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object OnbColors {
    val Paper = Color(0xFFFFFDF6)
    val Ink = Color(0xFF15130D)
    val BodyInk = Color(0xFF3F3A30)
    val Emerald = Color(0xFF0E8C5A)
    val EmeraldDeep = Color(0xFF0A5C3C)
    val Coral = Color(0xFFFF6A45)
    val Mint = Color(0xFFDBF3E5)
    val Pos = Color(0xFF1E7F4F)
    val Neg = Color(0xFFC2453A)
    val Muted = Color(0xFF7A7263)
    val Rule = Color(0xFFECE4D4)
    val Gold = Color(0xFFE7C78A)
    val Card = Color(0xFFFFFFFF)
    val SmsBg = Color(0xFFF4F0E6)
    val DotIdle = Color(0xFFDED7C6)
    val ChipOpt = Color(0xFFEFE8D8)
}


fun Modifier.onbCard(): Modifier = this
    .shadow(4.dp, RoundedCornerShape(18.dp), clip = false)
    .clip(RoundedCornerShape(18.dp))
    .background(OnbColors.Card)
    .border(1.dp, OnbColors.Rule, RoundedCornerShape(18.dp))
    .padding(horizontal = 15.dp, vertical = 13.dp)

@Composable
fun Eyebrow(text: String, color: Color = OnbColors.Coral) =
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
        color = OnbColors.Ink
    )

@Composable
fun OnbBody(text: String, modifier: Modifier = Modifier, fontSize: TextUnit = 15.5.sp) =
    Text(
        text,
        modifier,
        fontSize = fontSize,
        lineHeight = fontSize * 1.5f,
        color = OnbColors.BodyInk
    )