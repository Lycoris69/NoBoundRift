package com.lycoris.noboundrift.presentation.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.lycoris.noboundrift.data.local.AccentColor
import com.lycoris.noboundrift.data.local.AppFont
import com.lycoris.noboundrift.data.local.AppTheme

/**
 * Root theme composable. Wraps the entire app in [MainActivity].
 *
 * [appTheme]    — dark / AMOLED / light / follow-system
 * [accentColor] — one of six preset accent palettes (violet by default)
 * [appFont]     — default sans-serif or serif
 *
 * A [SideEffect] keeps the system-bar icon colours in sync so they are always
 * readable against the current background.
 */
@Composable
fun NoBoundRiftTheme(
    appTheme: AppTheme = AppTheme.DARK,
    accentColor: AccentColor = AccentColor.VIOLET,
    appFont: AppFont = AppFont.DEFAULT,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (appTheme) {
        AppTheme.DARK, AppTheme.AMOLED -> true
        AppTheme.LIGHT -> false
        AppTheme.SYSTEM -> systemDark
    }

    val palette = accentColor.toPalette()

    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = palette.primary,
            onPrimary = OnPrimary,
            primaryContainer = palette.darkPrimary,
            onPrimaryContainer = OnPrimary,
            secondary = palette.secondary,
            onSecondary = OnSecondary,
            background = if (appTheme == AppTheme.AMOLED) AmoledBackground else Background,
            onBackground = OnBackground,
            surface = if (appTheme == AppTheme.AMOLED) AmoledSurface else Surface,
            onSurface = OnSurface,
            surfaceVariant = SurfaceVariant,
            onSurfaceVariant = OnSurfaceVariant,
            error = Error,
            onError = OnError,
        )
    } else {
        // Light mode: use the darker hue variant as primary so it reads on white.
        lightColorScheme(
            primary = palette.darkPrimary,
            onPrimary = Color.White,
            primaryContainer = palette.primary.copy(alpha = 0.15f),
            onPrimaryContainer = palette.darkPrimary,
            secondary = palette.secondary,
            onSecondary = Color.Black,
            background = LightBackground,
            onBackground = LightOnBackground,
            surface = LightSurface,
            onSurface = LightOnSurface,
            surfaceVariant = LightSurfaceVariant,
            onSurfaceVariant = LightOnSurfaceVariant,
            error = Error,
            onError = OnError,
        )
    }

    // Sync system-bar icon tint so they stay readable after a theme switch.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Edge-to-edge: keep the bar itself transparent; just flip icon contrast.
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !isDark
            controller.isAppearanceLightNavigationBars = !isDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typographyFor(appFont),
        content = content,
    )
}
