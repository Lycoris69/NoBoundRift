package com.lycoris.noboundrift.data.local

import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SourcePreferences @Inject constructor(private val prefs: SharedPreferences) {

    companion object {
        private const val KEY = "selected_source_id"
    }

    fun getSelectedSourceId(): Long = prefs.getLong(KEY, 2L)

    fun setSelectedSourceId(id: Long) {
        prefs.edit().putLong(KEY, id).apply()
    }

    fun observeSelectedSourceId(): Flow<Long> = callbackFlow {
        trySend(getSelectedSourceId())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY) trySend(getSelectedSourceId())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()
}
