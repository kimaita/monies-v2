package com.kimaita.monies.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.kimaita.monies.R

/** The one app font: regular and a real bold are bundled; SemiBold and ExtraBold resolve to the bold file. */
val AtkinsonNext = FontFamily(
    Font(R.font.atkinson_next_regular, FontWeight.Normal),
    Font(R.font.atkinson_next_bold, FontWeight.Bold),
)

/** Tabular figures, so amounts in a column line up digit for digit. */
const val TabularFigures = "tnum"

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
    labelMedium = baseline.labelMedium.copy(fontFamily = AtkinsonNext, fontFeatureSettings = TabularFigures),
    labelSmall = baseline.labelSmall.copy(fontFamily = AtkinsonNext, fontFeatureSettings = TabularFigures),
)
