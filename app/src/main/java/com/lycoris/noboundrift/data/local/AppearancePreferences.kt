package com.lycoris.noboundrift.data.local

import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

enum class AppTheme(val displayName: String) {
    DARK("Dark"),
    AMOLED("AMOLED"),
    LIGHT("Light"),
    SYSTEM("System"),
}

/** Named accent presets — actual Color values are resolved in the theme layer. */
enum class AccentColor(val displayName: String) {
    VIOLET("Violet"),
    BLUE("Blue"),
    TEAL("Teal"),
    GREEN("Green"),
    ROSE("Rose"),
    ORANGE("Orange"),
}

enum class AppFont(val displayName: String) {
    DEFAULT("Default"),
    SERIF("Serif"),
}

@Singleton
class AppearancePreferences @Inject constructor(private val prefs: SharedPreferences) {

    companion object {
        const val KEY_APP_THEME = "app_theme"
        const val KEY_ACCENT_COLOR = "accent_color"
        const val KEY_APP_FONT = "app_font"
        val DEFAULT_THEME = AppTheme.DARK
        val DEFAULT_ACCENT = AccentColor.VIOLET
        val DEFAULT_FONT = AppFont.DEFAULT
    }

    // ── Theme ─────────────────────────────────────────────────────────────────

    fun getAppTheme(): AppTheme =
        prefs.getString(KEY_APP_THEME, DEFAULT_THEME.name)
            ?.let { runCatching { AppTheme.valueOf(it) }.getOrDefault(DEFAULT_THEME) }
            ?: DEFAULT_THEME

    fun setAppTheme(theme: AppTheme) {
        prefs.edit().putString(KEY_APP_THEME, theme.name).apply()
    }

    fun observeAppTheme(): Flow<AppTheme> = callbackFlow {
        trySend(getAppTheme())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_APP_THEME) trySend(getAppTheme())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    // ── Accent colour ─────────────────────────────────────────────────────────

    fun getAccentColor(): AccentColor =
        prefs.getString(KEY_ACCENT_COLOR, DEFAULT_ACCENT.name)
            ?.let { runCatching { AccentColor.valueOf(it) }.getOrDefault(DEFAULT_ACCENT) }
            ?: DEFAULT_ACCENT

    fun setAccentColor(color: AccentColor) {
        prefs.edit().putString(KEY_ACCENT_COLOR, color.name).apply()
    }

    fun observeAccentColor(): Flow<AccentColor> = callbackFlow {
        trySend(getAccentColor())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_ACCENT_COLOR) trySend(getAccentColor())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    // ── Font ──────────────────────────────────────────────────────────────────

    fun getAppFont(): AppFont =
        prefs.getString(KEY_APP_FONT, DEFAULT_FONT.name)
            ?.let { runCatching { AppFont.valueOf(it) }.getOrDefault(DEFAULT_FONT) }
            ?: DEFAULT_FONT

    fun setAppFont(font: AppFont) {
        prefs.edit().putString(KEY_APP_FONT, font.name).apply()
    }

    fun observeAppFont(): Flow<AppFont> = callbackFlow {
        trySend(getAppFont())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_APP_FONT) trySend(getAppFont())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()
}
