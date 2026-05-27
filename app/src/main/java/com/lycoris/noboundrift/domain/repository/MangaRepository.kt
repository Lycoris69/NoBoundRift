package com.lycoris.noboundrift.domain.repository

import com.lycoris.noboundrift.domain.model.Chapter
import com.lycoris.noboundrift.domain.model.Manga
import com.lycoris.noboundrift.domain.model.MangaPreview
import com.lycoris.noboundrift.domain.model.Page
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface in the domain layer — the ViewModel and use-case layer depend
 * on this abstraction, not on the concrete data-layer implementation. This keeps the
 * domain pure and makes the data layer swappable / testable.
 */
interface MangaRepository {

    // ── Browse / search ──────────────────────────────────────────────────────

    /**
     * Returns a page of manga previews from the given source.
     * Results may be cached locally but the remote source is the authority.
     */
    suspend fun fetchMangaList(sourceId: Long, page: Int): Result<List<MangaPreview>>

    // ── Detail ───────────────────────────────────────────────────────────────

    /**
     * Fetches full manga metadata from the remote source and merges it with
     * any locally stored reading state (e.g. chapter read flags).
     */
    suspend fun fetchMangaDetail(sourceId: Long, url: String): Result<Manga>

    suspend fun fetchChapterList(sourceId: Long, mangaUrl: String): Result<List<Chapter>>

    suspend fun fetchPageList(sourceId: Long, chapterUrl: String): Result<List<Page>>

    // ── Library (Room-backed) ─────────────────────────────────────────────────

    /** Emits the current saved library, updated whenever the DB changes. */
    fun getLibrary(): Flow<List<MangaPreview>>

    suspend fun addToLibrary(manga: MangaPreview)

    suspend fun removeFromLibrary(mangaId: String)

    fun isInLibrary(mangaId: String): Flow<Boolean>

    // ── Reading progress ──────────────────────────────────────────────────────

    suspend fun markChapterRead(chapterUrl: String)

    suspend fun markChapterUnread(chapterUrl: String)

    /** Emits the last-read chapter URL for a given manga, or null if unread. */
    fun getLastReadChapter(mangaId: String): Flow<String?>
}
