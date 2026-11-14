package com.kimaita.monies.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.kimaita.monies.models.ThemeMode

private val LightColors = lightColorScheme(
    primary = Emerald, onPrimary = Color.White,
    primaryContainer = Mint, onPrimaryContainer = EmeraldDeep,
    secondary = EmeraldDeep, onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDEBDA), onSecondaryContainer = EmeraldDeep,
    error = NegRed, onError = Color.White,
    errorContainer = Color(0xFFF8DAD3), onErrorContainer = Color(0xFF5C1A12),
    background = Paper, onBackground = Ink,
    surface = Paper, onSurface = Ink,
    surfaceVariant = LSurfaceVariant, onSurfaceVariant = LMuted,
    surfaceContainerLowest = Color.White, surfaceContainerLow = LCard,
    surfaceContainer = LContainer, surfaceContainerHigh = LContainerHigh,
    surfaceContainerHighest = Color(0xFFEFE8D9),
    outline = LOutline, outlineVariant = LOutlineVariant,
    inverseSurface = Ink, inverseOnSurface = Paper, inversePrimary = EmeraldBright,
)

private val DarkColors = darkColorScheme(
    primary = EmeraldBright, onPrimary = OnEmeraldDark,
    primaryContainer = DarkPrimaryContainer, onPrimaryContainer = OnDarkPrimaryContainer,
    secondary = EmeraldBright, onSecondary = OnEmeraldDark,
    secondaryContainer = Color(0xFF14271D), onSecondaryContainer = Color(0xFFBFEFD3),
    error = NegDark, onError = Color(0xFF2A0A05),
    errorContainer = Color(0xFF4A1810), onErrorContainer = Color(0xFFFFD9CF),
    background = Black, onBackground = DarkInk,
    surface = Black, onSurface = DarkInk,
    surfaceVariant = DSurfaceVariant, onSurfaceVariant = DarkMuted,
    surfaceContainerLowest = Black, surfaceContainerLow = DCard,
    surfaceContainer = DContainer, surfaceContainerHigh = DContainerHigh,
    surfaceContainerHighest = DContainerHighest,
    outline = DOutline, outlineVariant = DOutlineVariant,
    inverseSurface = DarkInk, inverseOnSurface = Color(0xFF1A1A1A), inversePrimary = Emerald,
)

@Composable
fun AppTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = if (dark) DarkColors else LightColors
    val extra = if (dark) DarkExtra else LightExtra

    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        val window = (view.context as Activity).window
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !dark
        controller.isAppearanceLightNavigationBars = !dark
    }

    CompositionLocalProvider(LocalExtraColors provides extra) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content,
        )
    }
}