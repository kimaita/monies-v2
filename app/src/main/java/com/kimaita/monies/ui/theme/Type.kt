package com.kimaita.monies.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.kimaita.monies.R

private val AtkinsonNext = FontFamily(
    Font(R.font.atkinson_next_regular, FontWeight.Normal),
    Font(R.font.atkinson_next_bold, FontWeight.Bold),
)

/** Figures and small labels. Bold is synthesised: only the regular weight is bundled. */
val AtkinsonMono = FontFamily(
    Font(R.font.atkinson_mono_regular, FontWeight.Normal),
)

private val baseline = Typography()

val AppTypography = Typography(
    displayLarge = baseline.displayLarge.copy(fontFamily = AtkinsonNext, fontWeight = FontWeight.Bold),
    displayMedium = baseline.displayMedium.copy(fontFamily = AtkinsonNext, fontWeight = FontWeight.Bold),
    displaySmall = baseline.displaySmall.copy(fontFamily = AtkinsonNext, fontWeight = FontWeight.Bold),
    headlineLarge = baseline.headlineLarge.copy(fontFamily = AtkinsonNext, fontWeight = FontWeight.Bold),
    headlineMedium = baseline.headlineMedium.copy(fontFamily = AtkinsonNext, fontWeight = FontWeight.Bold),
    headlineSmall = baseline.headlineSmall.copy(fontFamily = AtkinsonNext, fontWeight = FontWeight.Bold),
    titleLarge = baseline.titleLarge.copy(fontFamily = AtkinsonNext, fontWeight = FontWeight.Bold),
    titleMedium = baseline.titleMedium.copy(fontFamily = AtkinsonNext, fontWeight = FontWeight.Bold),
    titleSmall = baseline.titleSmall.copy(fontFamily = AtkinsonNext, fontWeight = FontWeight.Bold),
    bodyLarge = baseline.bodyLarge.copy(fontFamily = AtkinsonNext),
    bodyMedium = baseline.bodyMedium.copy(fontFamily = AtkinsonNext),
    bodySmall = baseline.bodySmall.copy(fontFamily = AtkinsonNext),
    labelLarge = baseline.labelLarge.copy(fontFamily = AtkinsonNext, fontWeight = FontWeight.Bold),
    labelMedium = baseline.labelMedium.copy(fontFamily = AtkinsonMono),
    labelSmall = baseline.labelSmall.copy(fontFamily = AtkinsonMono),
)
