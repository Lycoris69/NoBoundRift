package com.lycoris.noboundrift.data.local

import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

enum class LibraryLayout { GRID, LIST }

enum class LibrarySortOrder(val displayName: String) {
    CUSTOM("Custom"),
    TITLE("Title"),
    UPDATED("Last updated"),
}

@Singleton
class LibraryPreferences @Inject constructor(private val prefs: SharedPreferences) {

    companion object {
        const val KEY_LIBRARY_LAYOUT = "library_layout"
        val DEFAULT = LibraryLayout.GRID

        const val KEY_GRID_COLUMNS = "library_grid_columns"
        const val KEY_SORT_ORDER = "library_sort_order"
        const val KEY_ROUNDED_COVERS = "library_rounded_covers"
        const val DEFAULT_GRID_COLUMNS = 3
        val DEFAULT_SORT = LibrarySortOrder.CUSTOM
        const val DEFAULT_ROUNDED = true
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

    fun getSortOrder(): LibrarySortOrder =
        prefs.getString(KEY_SORT_ORDER, DEFAULT_SORT.name)
            ?.let { runCatching { LibrarySortOrder.valueOf(it) }.getOrDefault(DEFAULT_SORT) }
            ?: DEFAULT_SORT

    fun setSortOrder(order: LibrarySortOrder) {
        prefs.edit().putString(KEY_SORT_ORDER, order.name).apply()
    }

    fun observeSortOrder(): Flow<LibrarySortOrder> = callbackFlow {
        trySend(getSortOrder())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_SORT_ORDER) trySend(getSortOrder())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    fun isRoundedCovers(): Boolean = prefs.getBoolean(KEY_ROUNDED_COVERS, DEFAULT_ROUNDED)
    fun setRoundedCovers(rounded: Boolean) { prefs.edit().putBoolean(KEY_ROUNDED_COVERS, rounded).apply() }
    fun observeRoundedCovers(): Flow<Boolean> = callbackFlow {
        trySend(isRoundedCovers())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_ROUNDED_COVERS) trySend(isRoundedCovers())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()
}
