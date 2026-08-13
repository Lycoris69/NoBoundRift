package com.lycoris.noboundrift.data.local

import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccessibilityPreferences @Inject constructor(private val prefs: SharedPreferences) {

    companion object {
        const val KEY_HAPTIC_FEEDBACK = "haptic_feedback"
        const val DEFAULT_HAPTIC = true
    }

    fun isHapticFeedback(): Boolean = prefs.getBoolean(KEY_HAPTIC_FEEDBACK, DEFAULT_HAPTIC)

    fun setHapticFeedback(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HAPTIC_FEEDBACK, enabled).apply()
    }

    fun observeHapticFeedback(): Flow<Boolean> = callbackFlow {
        trySend(isHapticFeedback())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_HAPTIC_FEEDBACK) trySend(isHapticFeedback())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()
}
