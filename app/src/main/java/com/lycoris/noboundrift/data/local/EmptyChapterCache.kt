package com.lycoris.noboundrift.data.local

import android.content.SharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the set of manga IDs for which we have confirmed zero readable chapters.
 * Backed by SharedPreferences so the knowledge survives app restarts.
 * Thread-safe: all mutations are @Synchronized.
 */
@Singleton
class EmptyChapterCache @Inject constructor(prefs: SharedPreferences) {

    private val ids: MutableSet<String> =
        prefs.getStringSet(KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
    private val store = prefs

    @Synchronized
    fun mark(mangaId: String) {
        if (ids.add(mangaId)) {
            store.edit().putStringSet(KEY, ids.toSet()).apply()
        }
    }

    @Synchronized
    fun getAll(): Set<String> = ids.toSet()

    companion object {
        private const val KEY = "empty_chapter_manga_ids"
    }
}
