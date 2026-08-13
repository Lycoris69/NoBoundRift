package com.lycoris.noboundrift.data.local

import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BrowsePreferences @Inject constructor(private val prefs: SharedPreferences) {

    companion object {
        const val KEY_GRID_COLUMNS = "browse_grid_columns"
        const val KEY_BLUR_COVERS = "blur_covers"
        const val DEFAULT_GRID_COLUMNS = 3
        const val DEFAULT_BLUR = false
    }

    fun getGridColumns(): Int = prefs.getInt(KEY_GRID_COLUMNS, DEFAULT_GRID_COLUMNS).coerceIn(2, 5)
    fun setGridColumns(value: Int) { prefs.edit().putInt(KEY_GRID_COLUMNS, value.coerceIn(2, 5)).apply() }
    fun observeGridColumns(): Flow<Int> = callbackFlow {
        trySend(getGridColumns())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_GRID_COLUMNS) trySend(getGridColumns())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    fun isBlurCovers(): Boolean = prefs.getBoolean(KEY_BLUR_COVERS, DEFAULT_BLUR)
    fun setBlurCovers(enabled: Boolean) { prefs.edit().putBoolean(KEY_BLUR_COVERS, enabled).apply() }
    fun observeBlurCovers(): Flow<Boolean> = callbackFlow {
        trySend(isBlurCovers())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_BLUR_COVERS) trySend(isBlurCovers())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()
}
