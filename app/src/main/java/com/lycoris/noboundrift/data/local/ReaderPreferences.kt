package com.lycoris.noboundrift.data.local

import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

enum class PreloadMode { ALWAYS, WIFI_ONLY }

enum class ReadingDirection { LTR, RTL }

@Singleton
class ReaderPreferences @Inject constructor(private val prefs: SharedPreferences) {

    companion object {
        const val KEY_PRELOAD_MODE = "preload_mode"
        val DEFAULT = PreloadMode.ALWAYS

        const val KEY_READING_DIRECTION = "reading_direction"
        const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        val DEFAULT_READING_DIRECTION = ReadingDirection.LTR
        const val DEFAULT_KEEP_SCREEN_ON = false

        const val KEY_NOVEL_FONT_SIZE = "novel_font_size"
        const val DEFAULT_NOVEL_FONT_SIZE = 18
        const val MIN_NOVEL_FONT_SIZE = 12
        const val MAX_NOVEL_FONT_SIZE = 32
    }

    fun getPreloadMode(): PreloadMode =
        prefs.getString(KEY_PRELOAD_MODE, DEFAULT.name)
            ?.let { runCatching { PreloadMode.valueOf(it) }.getOrDefault(DEFAULT) }
            ?: DEFAULT

    fun setPreloadMode(mode: PreloadMode) {
        prefs.edit().putString(KEY_PRELOAD_MODE, mode.name).apply()
    }

    fun observePreloadMode(): Flow<PreloadMode> = callbackFlow {
        trySend(getPreloadMode())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_PRELOAD_MODE) trySend(getPreloadMode())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    fun getReadingDirection(): ReadingDirection =
        prefs.getString(KEY_READING_DIRECTION, DEFAULT_READING_DIRECTION.name)
            ?.let { runCatching { ReadingDirection.valueOf(it) }.getOrDefault(DEFAULT_READING_DIRECTION) }
            ?: DEFAULT_READING_DIRECTION

    fun setReadingDirection(dir: ReadingDirection) {
        prefs.edit().putString(KEY_READING_DIRECTION, dir.name).apply()
    }

    fun observeReadingDirection(): Flow<ReadingDirection> = callbackFlow {
        trySend(getReadingDirection())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_READING_DIRECTION) trySend(getReadingDirection())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    fun getKeepScreenOn(): Boolean =
        prefs.getBoolean(KEY_KEEP_SCREEN_ON, DEFAULT_KEEP_SCREEN_ON)

    fun setKeepScreenOn(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, enabled).apply()
    }

    fun observeKeepScreenOn(): Flow<Boolean> = callbackFlow {
        trySend(getKeepScreenOn())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_KEEP_SCREEN_ON) trySend(getKeepScreenOn())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    /** Text size (sp) used by the novel reader. */
    fun getNovelFontSize(): Int =
        prefs.getInt(KEY_NOVEL_FONT_SIZE, DEFAULT_NOVEL_FONT_SIZE)
            .coerceIn(MIN_NOVEL_FONT_SIZE, MAX_NOVEL_FONT_SIZE)

    fun setNovelFontSize(sizeSp: Int) {
        prefs.edit().putInt(KEY_NOVEL_FONT_SIZE, sizeSp.coerceIn(MIN_NOVEL_FONT_SIZE, MAX_NOVEL_FONT_SIZE)).apply()
    }

    fun observeNovelFontSize(): Flow<Int> = callbackFlow {
        trySend(getNovelFontSize())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_NOVEL_FONT_SIZE) trySend(getNovelFontSize())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()
}
