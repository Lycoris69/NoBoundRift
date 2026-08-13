package com.lycoris.noboundrift.data.local

import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadPreferences @Inject constructor(private val prefs: SharedPreferences) {
    companion object {
        const val KEY_CONCURRENCY = "download_concurrency"
        const val DEFAULT_CONCURRENCY = 3
        const val KEY_WIFI_ONLY = "download_wifi_only"
        const val DEFAULT_WIFI_ONLY = false
    }

    fun getConcurrency(): Int = prefs.getInt(KEY_CONCURRENCY, DEFAULT_CONCURRENCY).coerceIn(1, 20)

    fun setConcurrency(value: Int) {
        prefs.edit().putInt(KEY_CONCURRENCY, value.coerceIn(1, 20)).apply()
    }

    fun observeConcurrency(): Flow<Int> = callbackFlow {
        trySend(getConcurrency())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_CONCURRENCY) trySend(getConcurrency())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    // ── Wi-Fi only ────────────────────────────────────────────────────────────

    fun isWifiOnly(): Boolean = prefs.getBoolean(KEY_WIFI_ONLY, DEFAULT_WIFI_ONLY)

    fun setWifiOnly(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WIFI_ONLY, enabled).apply()
    }

    fun observeWifiOnly(): Flow<Boolean> = callbackFlow {
        trySend(isWifiOnly())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_WIFI_ONLY) trySend(isWifiOnly())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()
}
