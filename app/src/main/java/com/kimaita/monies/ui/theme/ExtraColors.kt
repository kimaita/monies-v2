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
    val medals: List<Color>,
)

val LightExtra = ExtraColors(
    positive = PosGreen,
    negative = NegRed,
    inflow = Emerald,
    outflow = Coral,
    gold = Gold,
    medals = listOf(Color(0xFFE7C78A), Color(0xFFBFC4C9), Color(0xFFCB8B5E)),

    )

val DarkExtra = ExtraColors(
    positive = EmeraldBright,
    negative = NegDark,
    inflow = EmeraldBright,
    outflow = CoralDark,
    gold = Gold,
    medals = listOf(Color(0xFFE7C78A), Color(0xFFBFC4C9), Color(0xFFCB8B5E)),

    )

val LocalExtraColors = staticCompositionLocalOf { LightExtra }

object MoniesTheme {
    val extra: ExtraColors
        @Composable @ReadOnlyComposable get() = LocalExtraColors.current
}