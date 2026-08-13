package com.lycoris.noboundrift.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.lycoris.noboundrift.data.local.AppFont

val NoBoundRiftTypography = Typography(
    // Large title — manga title on detail screen
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    // Chapter titles, source names
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    // Body text — synopsis, metadata
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    // Chip labels, badges
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)

/**
 * Returns a [Typography] with every text style's [FontFamily] replaced by the
 * one corresponding to [font]. When [font] is [AppFont.DEFAULT] the base
 * [NoBoundRiftTypography] is returned as-is to avoid creating a new object.
 */
fun typographyFor(font: AppFont): Typography {
    val family = when (font) {
        AppFont.DEFAULT -> return NoBoundRiftTypography
        AppFont.SERIF -> FontFamily.Serif
    }
    // Replace family on all 15 M3 type roles so custom fonts apply everywhere.
    return Typography(
        displayLarge    = NoBoundRiftTypography.displayLarge.copy(fontFamily = family),
        displayMedium   = NoBoundRiftTypography.displayMedium.copy(fontFamily = family),
        displaySmall    = NoBoundRiftTypography.displaySmall.copy(fontFamily = family),
        headlineLarge   = NoBoundRiftTypography.headlineLarge.copy(fontFamily = family),
        headlineMedium  = NoBoundRiftTypography.headlineMedium.copy(fontFamily = family),
        headlineSmall   = NoBoundRiftTypography.headlineSmall.copy(fontFamily = family),
        titleLarge      = NoBoundRiftTypography.titleLarge.copy(fontFamily = family),
        titleMedium     = NoBoundRiftTypography.titleMedium.copy(fontFamily = family),
        titleSmall      = NoBoundRiftTypography.titleSmall.copy(fontFamily = family),
        bodyLarge       = NoBoundRiftTypography.bodyLarge.copy(fontFamily = family),
        bodyMedium      = NoBoundRiftTypography.bodyMedium.copy(fontFamily = family),
        bodySmall       = NoBoundRiftTypography.bodySmall.copy(fontFamily = family),
        labelLarge      = NoBoundRiftTypography.labelLarge.copy(fontFamily = family),
        labelMedium     = NoBoundRiftTypography.labelMedium.copy(fontFamily = family),
        labelSmall      = NoBoundRiftTypography.labelSmall.copy(fontFamily = family),
    )
}
