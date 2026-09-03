package com.lycoris.noboundrift.domain.repository

import com.lycoris.noboundrift.domain.model.Chapter
import com.lycoris.noboundrift.domain.model.Manga
import com.lycoris.noboundrift.domain.model.MangaPreview
import com.lycoris.noboundrift.domain.model.Page
import kotlinx.coroutines.flow.Flow

interface MangaRepository {

    // ── Browse / search ──────────────────────────────────────────────────────

    suspend fun fetchMangaList(sourceId: Long, page: Int, query: String = ""): Result<List<MangaPreview>>

    // ── Detail ───────────────────────────────────────────────────────────────

    suspend fun fetchMangaDetail(sourceId: Long, url: String): Result<Manga>

    suspend fun fetchChapterList(sourceId: Long, mangaUrl: String): Result<List<Chapter>>

    suspend fun fetchPageList(sourceId: Long, chapterUrl: String): Result<List<Page>>

    // ── Library (Room-backed) ─────────────────────────────────────────────────

    fun getLibrary(): Flow<List<MangaPreview>>

    /** Returns the single library entry for [mangaId], or null if not saved. */
    suspend fun getLibraryEntry(mangaId: String): MangaPreview?

    suspend fun addToLibrary(manga: MangaPreview)

    suspend fun removeFromLibrary(mangaId: String)

    /** Bulk-removes every library entry whose source matches [sourceId]. */
    suspend fun removeBySourceId(sourceId: Long)

    fun isInLibrary(mangaId: String): Flow<Boolean>

    suspend fun updateLatestChapterAt(mangaId: String, latestAt: Long, latestChapterUrl: String)

    /**
     * Returns a map of manga ID → latestChapterAt for all [ids] that have a stored
     * date in the library DB. IDs not in the library are absent from the result.
     */
    suspend fun getLatestChapterDates(ids: List<String>): Map<String, Long>

    suspend fun reorderLibrary(orderedIds: List<String>)

    /** Sets a 1–5 star rating for a library entry. Pass 0 to clear. */
    suspend fun setRating(mangaId: String, rating: Int)

    // ── Reading progress ──────────────────────────────────────────────────────

    suspend fun markChapterRead(chapter: Chapter)

    suspend fun touchLastOpenedChapter(chapter: Chapter)

    suspend fun markChapterUnread(chapterUrl: String)

    /** Emits the last-read chapter URL for a given manga, or null if unread. */
    fun getLastReadChapter(mangaId: String): Flow<String?>

    fun observeReadChapterUrls(mangaId: String): Flow<Set<String>>

    /** Emits the set of all chapter URLs (normalized) that have been marked read. */
    fun observeAllReadChapterUrls(): Flow<Set<String>>
}
