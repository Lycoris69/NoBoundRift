package com.lycoris.noboundrift.data.local

import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CachePreferences @Inject constructor(private val prefs: SharedPreferences) {

    companion object {
        const val KEY_CACHE_SIZE = "cache_size_bytes"
        val DEFAULT = 128L * 1024 * 1024
    }

    fun getCacheSizeBytes(): Long = prefs.getLong(KEY_CACHE_SIZE, DEFAULT)

    fun setCacheSizeBytes(bytes: Long) {
        prefs.edit().putLong(KEY_CACHE_SIZE, bytes).apply()
    }

    fun observeCacheSizeBytes(): Flow<Long> = callbackFlow {
        trySend(getCacheSizeBytes())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_CACHE_SIZE) trySend(getCacheSizeBytes())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()
}
