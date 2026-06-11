package com.lycoris.noboundrift.data.local

import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

enum class PreloadMode { ALWAYS, WIFI_ONLY }

@Singleton
class ReaderPreferences @Inject constructor(private val prefs: SharedPreferences) {

    companion object {
        const val KEY_PRELOAD_MODE = "preload_mode"
        val DEFAULT = PreloadMode.ALWAYS
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
}
