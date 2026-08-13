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
}
