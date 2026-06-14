package com.lycoris.noboundrift.data.local

import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

enum class LibraryLayout { GRID, LIST }

@Singleton
class LibraryPreferences @Inject constructor(private val prefs: SharedPreferences) {

    companion object {
        const val KEY_LIBRARY_LAYOUT = "library_layout"
        val DEFAULT = LibraryLayout.GRID
    }

    fun getLibraryLayout(): LibraryLayout =
        prefs.getString(KEY_LIBRARY_LAYOUT, DEFAULT.name)
            ?.let { runCatching { LibraryLayout.valueOf(it) }.getOrDefault(DEFAULT) }
            ?: DEFAULT

    fun setLibraryLayout(layout: LibraryLayout) {
        prefs.edit().putString(KEY_LIBRARY_LAYOUT, layout.name).apply()
    }

    fun observeLibraryLayout(): Flow<LibraryLayout> = callbackFlow {
        trySend(getLibraryLayout())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_LIBRARY_LAYOUT) trySend(getLibraryLayout())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()
}
