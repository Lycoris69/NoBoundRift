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

    suspend fun addToLibrary(manga: MangaPreview)

    suspend fun removeFromLibrary(mangaId: String)

    fun isInLibrary(mangaId: String): Flow<Boolean>

    suspend fun updateLatestChapterAt(mangaId: String, latestAt: Long)

    // ── Reading progress ──────────────────────────────────────────────────────

    suspend fun markChapterRead(chapter: Chapter)

    suspend fun touchLastOpenedChapter(chapter: Chapter)

    suspend fun markChapterUnread(chapterUrl: String)

    /** Emits the last-read chapter URL for a given manga, or null if unread. */
    fun getLastReadChapter(mangaId: String): Flow<String?>
}
