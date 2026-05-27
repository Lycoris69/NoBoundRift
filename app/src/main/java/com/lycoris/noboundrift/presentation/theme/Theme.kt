package com.lycoris.noboundrift.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Dark-first color scheme. A light theme is intentionally omitted — the app is
 * designed for low-light reading sessions and a light mode would require
 * significant redesign work for minimal gain.
 */
private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryVariant,
    onPrimaryContainer = OnPrimary,

    secondary = Secondary,
    onSecondary = OnSecondary,

    background = Background,
    onBackground = OnBackground,

    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,

    error = Error,
    onError = OnError,
)

/**
 * Root theme composable. Wrap the entire app in this inside [MainActivity].
 * Always uses the dark scheme — no dynamic color or system dark-mode switching.
 */
@Composable
fun NoBoundRiftTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = NoBoundRiftTypography,
        content = content,
    )
}
