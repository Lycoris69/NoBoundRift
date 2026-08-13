package com.lycoris.noboundrift.presentation.theme

import androidx.compose.ui.graphics.Color
import com.lycoris.noboundrift.data.local.AccentColor

// ── Dark palette ──────────────────────────────────────────────────────────────
// Deep ink-black background — reduces eye strain during long reading sessions.
val Background = Color(0xFF0D0D0D)
val Surface = Color(0xFF1A1A1A)
val SurfaceVariant = Color(0xFF252525)

// Accent — a muted violet-blue evocative of manga ink (default; overridden by AccentColor pref)
val Primary = Color(0xFF7B68EE)          // medium slate blue
val PrimaryVariant = Color(0xFF5A4FCF)
val OnPrimary = Color(0xFFFFFFFF)

val Secondary = Color(0xFF03DAC6)        // teal accent
val OnSecondary = Color(0xFF000000)

val OnBackground = Color(0xFFE8E8E8)
val OnSurface = Color(0xFFD0D0D0)
val OnSurfaceVariant = Color(0xFF9E9E9E)

// Status / semantic
val Error = Color(0xFFCF6679)
val OnError = Color(0xFFFFFFFF)
val ReadIndicator = Color(0xFF4CAF50)    // green checkmark for read chapters
val UnreadIndicator = Color(0xFFFFB74D) // amber for unread

// ── AMOLED ────────────────────────────────────────────────────────────────────
val AmoledBackground = Color(0xFF000000)
val AmoledSurface = Color(0xFF0A0A0A)

// ── Light palette ─────────────────────────────────────────────────────────────
val LightBackground = Color(0xFFFAFAFA)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF0F0F0)
val LightOnBackground = Color(0xFF1A1A1A)
val LightOnSurface = Color(0xFF2C2C2C)
val LightOnSurfaceVariant = Color(0xFF707070)

// ── Accent palettes ───────────────────────────────────────────────────────────

/**
 * Resolved colors for a given [AccentColor] preset.
 * [primary] is the brighter shade used in dark mode.
 * [darkPrimary] is the same hue darkened — used as the light-mode primary so it
 * reads clearly against the white background.
 * [secondary] is the complementary accent.
 */
data class AccentPalette(
    val primary: Color,
    val darkPrimary: Color,
    val secondary: Color,
)

fun AccentColor.toPalette(): AccentPalette = when (this) {
    AccentColor.VIOLET -> AccentPalette(Color(0xFF7B68EE), Color(0xFF5A4FCF), Color(0xFF03DAC6))
    AccentColor.BLUE   -> AccentPalette(Color(0xFF4FC3F7), Color(0xFF0288D1), Color(0xFF80CBC4))
    AccentColor.TEAL   -> AccentPalette(Color(0xFF4DB6AC), Color(0xFF00897B), Color(0xFFB39DDB))
    AccentColor.GREEN  -> AccentPalette(Color(0xFF81C784), Color(0xFF388E3C), Color(0xFFFFB74D))
    AccentColor.ROSE   -> AccentPalette(Color(0xFFE91E63), Color(0xFFC2185B), Color(0xFFFF8A65))
    AccentColor.ORANGE -> AccentPalette(Color(0xFFFF8A65), Color(0xFFE64A19), Color(0xFF80DEEA))
}
