package com.kimaita.monies.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.em
import com.kimaita.monies.R

val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)
private val bricolageFont = GoogleFont("Bricolage Grotesque")
private val spaceMonoFont = GoogleFont("Space Mono")
private val interFont = GoogleFont("Inter")
val Bricolage = FontFamily(
    Font(googleFont = bricolageFont, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = bricolageFont, fontProvider = provider, weight = FontWeight.Bold),
    Font(googleFont = bricolageFont, fontProvider = provider, weight = FontWeight.ExtraBold),
)

val SpaceMono = FontFamily(
    Font(googleFont = spaceMonoFont, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = spaceMonoFont, fontProvider = provider, weight = FontWeight.Bold),
)

val Inter = FontFamily(
    Font(googleFont = interFont, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = interFont, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = interFont, fontProvider = provider, weight = FontWeight.SemiBold),
)

private val displayFontFamily = Bricolage
private val monoFontFamily = SpaceMono
private val bodyFontFamily = Inter

val baseline = Typography()

val AppTypography = Typography(
    // Headings + big numbers — Bricolage
    displayLarge = baseline.displayLarge.copy(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.ExtraBold
    ),
    displayMedium = baseline.displayMedium.copy(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.ExtraBold
    ),
    displaySmall = baseline.displaySmall.copy(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.ExtraBold
    ),
    headlineLarge = baseline.headlineLarge.copy(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.Bold
    ),
    headlineMedium = baseline.headlineMedium.copy(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.Bold
    ),
    headlineSmall = baseline.headlineSmall.copy(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.Bold
    ),
    titleLarge = baseline.titleLarge.copy(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.Bold
    ),

    // Small titles live in rows/cards — body font reads better at this size
    titleMedium = baseline.titleMedium.copy(
        fontFamily = bodyFontFamily,
        fontWeight = FontWeight.SemiBold
    ),
    titleSmall = baseline.titleSmall.copy(
        fontFamily = bodyFontFamily,
        fontWeight = FontWeight.SemiBold
    ),

    bodyLarge = baseline.bodyLarge.copy(fontFamily = bodyFontFamily),
    bodyMedium = baseline.bodyMedium.copy(fontFamily = bodyFontFamily),
    bodySmall = baseline.bodySmall.copy(fontFamily = bodyFontFamily),

    // labelLarge is M3 button text — keep it in the body font
    labelLarge = baseline.labelLarge.copy(
        fontFamily = bodyFontFamily,
        fontWeight = FontWeight.SemiBold
    ),

    // Data labels, pill values, eyebrows — Space Mono with wide tracking
    labelMedium = baseline.labelMedium.copy(fontFamily = monoFontFamily, letterSpacing = 0.08.em),
    labelSmall = baseline.labelSmall.copy(fontFamily = monoFontFamily, letterSpacing = 0.14.em),
)