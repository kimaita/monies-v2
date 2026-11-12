package com.kimaita.monies.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class ExtraColors(
    val positive: Color,    // money in (text/figures)
    val negative: Color,    // money out / debt (Fuliza used, loans)
    val inflow: Color,      // chart inflow
    val outflow: Color,     // chart outflow
    val gold: Color,
    val chartPalette: List<Color>,
    val medals: List<Color>,
)

val LightExtra = ExtraColors(
    positive = PosGreen,
    negative = NegRed,
    inflow = Emerald,
    outflow = Coral,
    gold = Gold,
    chartPalette = listOf(
        Emerald,
        Gold,
        Coral,
        Color(0xFF4A90E2),
        Color(0xFF9B6DD6),
        Color(0xFF1E7F4F)
    ),
    medals = listOf(Color(0xFFE7C78A), Color(0xFFBFC4C9), Color(0xFFCB8B5E)),

    )

val DarkExtra = ExtraColors(
    positive = EmeraldBright,
    negative = NegDark,
    inflow = EmeraldBright,
    outflow = CoralDark,
    gold = Gold,
    chartPalette = listOf(
        EmeraldBright,
        Gold,
        CoralDark,
        Color(0xFF6AA9FF),
        Color(0xFFB98BF0),
        Color(0xFF45C77E)
    ),
    medals = listOf(Color(0xFFE7C78A), Color(0xFFBFC4C9), Color(0xFFCB8B5E)),

    )

val LocalExtraColors = staticCompositionLocalOf { LightExtra }

object MoniesTheme {
    val extra: ExtraColors
        @Composable @ReadOnlyComposable get() = LocalExtraColors.current
}