package com.cuentasclaras.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.cuentasclaras.app.R

private val googleFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private val outfitFont = GoogleFont("Outfit")
private val sourceSans3Font = GoogleFont("Source Sans 3")

private val OutfitFontFamily = FontFamily(
    Font(googleFont = outfitFont, fontProvider = googleFontProvider, weight = FontWeight.Normal),
    Font(googleFont = outfitFont, fontProvider = googleFontProvider, weight = FontWeight.Medium),
    Font(googleFont = outfitFont, fontProvider = googleFontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = outfitFont, fontProvider = googleFontProvider, weight = FontWeight.Bold),
)

private val SourceSans3FontFamily = FontFamily(
    Font(googleFont = sourceSans3Font, fontProvider = googleFontProvider, weight = FontWeight.Normal),
    Font(googleFont = sourceSans3Font, fontProvider = googleFontProvider, weight = FontWeight.Medium),
    Font(googleFont = sourceSans3Font, fontProvider = googleFontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = sourceSans3Font, fontProvider = googleFontProvider, weight = FontWeight.Bold),
)

private val baseline = Typography()

val CuentasClarasTypography = Typography(
    displayLarge = baseline.displayLarge.copy(fontFamily = OutfitFontFamily),
    displayMedium = baseline.displayMedium.copy(fontFamily = OutfitFontFamily),
    displaySmall = baseline.displaySmall.copy(fontFamily = OutfitFontFamily),
    headlineLarge = baseline.headlineLarge.copy(fontFamily = OutfitFontFamily),
    headlineMedium = baseline.headlineMedium.copy(fontFamily = OutfitFontFamily),
    headlineSmall = baseline.headlineSmall.copy(fontFamily = OutfitFontFamily),
    titleLarge = baseline.titleLarge.copy(fontFamily = OutfitFontFamily),
    titleMedium = baseline.titleMedium.copy(fontFamily = OutfitFontFamily),
    titleSmall = baseline.titleSmall.copy(fontFamily = OutfitFontFamily),
    bodyLarge = baseline.bodyLarge.copy(fontFamily = SourceSans3FontFamily),
    bodyMedium = baseline.bodyMedium.copy(fontFamily = SourceSans3FontFamily),
    bodySmall = baseline.bodySmall.copy(fontFamily = SourceSans3FontFamily),
    labelLarge = baseline.labelLarge.copy(fontFamily = SourceSans3FontFamily),
    labelMedium = baseline.labelMedium.copy(fontFamily = SourceSans3FontFamily),
    labelSmall = baseline.labelSmall.copy(fontFamily = SourceSans3FontFamily),
)
