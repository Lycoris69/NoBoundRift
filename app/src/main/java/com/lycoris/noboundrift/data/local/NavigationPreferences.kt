package com.lycoris.noboundrift.data.local

import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NavigationPreferences @Inject constructor(private val prefs: SharedPreferences) {

    companion object {
        const val KEY_SHOW_DISCOVER = "show_discover_tab"
        const val DEFAULT_SHOW_DISCOVER = true
    }

    fun getShowDiscover(): Boolean =
        prefs.getBoolean(KEY_SHOW_DISCOVER, DEFAULT_SHOW_DISCOVER)

    fun setShowDiscover(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_DISCOVER, show).apply()
    }

    fun observeShowDiscover(): Flow<Boolean> = callbackFlow {
        trySend(getShowDiscover())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_SHOW_DISCOVER) trySend(getShowDiscover())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()
}
