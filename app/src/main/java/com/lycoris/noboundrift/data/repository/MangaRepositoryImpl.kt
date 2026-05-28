package com.lycoris.noboundrift.data.repository

import com.lycoris.noboundrift.data.local.dao.ChapterDao
import com.lycoris.noboundrift.data.local.dao.MangaDao
import com.lycoris.noboundrift.data.local.entity.ChapterEntity
import com.lycoris.noboundrift.data.local.entity.MangaEntity
import com.lycoris.noboundrift.data.remote.source.SourceManager
import com.lycoris.noboundrift.domain.model.Chapter
import com.lycoris.noboundrift.domain.model.Manga
import com.lycoris.noboundrift.domain.model.MangaPreview
import com.lycoris.noboundrift.domain.model.Page
import com.lycoris.noboundrift.domain.repository.MangaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Concrete repository that coordinates between:
 * - [SourceManager] (network/scraper layer)
 * - [MangaDao] / [ChapterDao] (Room persistence layer)
 *
 * All network calls are delegated to the appropriate [Source] fetched via [SourceManager].
 * Results are wrapped in [Result] so the ViewModel can handle errors without try/catch.
 */
@Singleton
class MangaRepositoryImpl @Inject constructor(
    private val sourceManager: SourceManager,
    private val mangaDao: MangaDao,
    private val chapterDao: ChapterDao,
) : MangaRepository {

    // ── Browse / search ──────────────────────────────────────────────────────

    override suspend fun fetchMangaList(sourceId: Long, page: Int, query: String): Result<List<MangaPreview>> =
        runCatching {
            sourceManager.getSource(sourceId).fetchMangaList(page, query)
        }

    // ── Detail ───────────────────────────────────────────────────────────────

    override suspend fun fetchMangaDetail(sourceId: Long, url: String): Result<Manga> =
        runCatching {
            sourceManager.getSource(sourceId).fetchMangaDetail(url)
        }

    override suspend fun fetchChapterList(sourceId: Long, mangaUrl: String): Result<List<Chapter>> =
        runCatching {
            val chapters = sourceManager.getSource(sourceId).fetchChapterList(mangaUrl)
            // Merge with locally stored read flags so the UI reflects progress
            chapters.map { chapter ->
                val local = chapterDao.getByUrl(chapter.url)
                if (local != null) chapter.copy(read = local.read) else chapter
            }
        }

    override suspend fun fetchPageList(sourceId: Long, chapterUrl: String): Result<List<Page>> =
        runCatching {
            sourceManager.getSource(sourceId).fetchPageList(chapterUrl)
        }

    // ── Library (Room-backed) ─────────────────────────────────────────────────

    override fun getLibrary(): Flow<List<MangaPreview>> =
        mangaDao.observeAll().map { entities -> entities.map { it.toPreview() } }

    override suspend fun addToLibrary(manga: MangaPreview) {
        mangaDao.insert(manga.toEntity())
    }

    override suspend fun removeFromLibrary(mangaId: String) {
        mangaDao.deleteById(mangaId)
    }

    override fun isInLibrary(mangaId: String): Flow<Boolean> =
        mangaDao.observeExists(mangaId)

    // ── Reading progress ──────────────────────────────────────────────────────

    override suspend fun markChapterRead(chapterUrl: String) {
        chapterDao.markRead(chapterUrl)
    }

    override suspend fun markChapterUnread(chapterUrl: String) {
        chapterDao.markUnread(chapterUrl)
    }

    override fun getLastReadChapter(mangaId: String): Flow<String?> =
        chapterDao.observeLastRead(mangaId)

    // ── Mappers ───────────────────────────────────────────────────────────────

    private fun MangaEntity.toPreview() = MangaPreview(
        id = id,
        title = title,
        coverUrl = coverUrl,
        sourceId = sourceId,
        url = url,
    )

    private fun MangaPreview.toEntity() = MangaEntity(
        id = id,
        title = title,
        coverUrl = coverUrl,
        sourceId = sourceId,
        url = url,
    )
}
