package com.kimaita.monies.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class ExtraColors(
    /** money in (text/figures) */
    val positive: Color,
    /** money out / debt (Fuliza used, loans) */
    val negative: Color,
    /** chart inflow */
    val inflow: Color,
    /** chart outflow */
    val outflow: Color,
    val gold: Color,
    val medals: List<Color>,
    /** "before" bars, quiet segments and the "others" share */
    val track2: Color,
    /** text on the strongest treemap tile */
    val onTile: Color,
)

val LightExtra = ExtraColors(
    positive = PosGreen,
    negative = NegRed,
    inflow = Emerald,
    outflow = Coral,
    gold = Gold,
    medals = listOf(Color(0xFFE7C78A), Color(0xFFBFC4C9), Color(0xFFCB8B5E)),
    track2 = LTrack2,
    onTile = Ink,
)

val DarkExtra = ExtraColors(
    positive = EmeraldBright,
    negative = NegDark,
    inflow = EmeraldBright,
    outflow = CoralDark,
    gold = GoldLight,
    medals = listOf(Color(0xFFE7C78A), Color(0xFFBFC4C9), Color(0xFFCB8B5E)),
    track2 = DOutline,
    onTile = OnEmeraldDark,
)

val LocalExtraColors = staticCompositionLocalOf { LightExtra }

object MoniesTheme {
    val extra: ExtraColors
        @Composable @ReadOnlyComposable get() = LocalExtraColors.current
}