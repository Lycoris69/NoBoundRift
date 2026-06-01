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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MangaRepositoryImpl @Inject constructor(
    private val sourceManager: SourceManager,
    private val mangaDao: MangaDao,
    private val chapterDao: ChapterDao,
) : MangaRepository {

    private inline fun <T> resultCatching(block: () -> T): Result<T> =
        runCatching(block).also { result ->
            result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        }

    // ── Browse / search ──────────────────────────────────────────────────────

    override suspend fun fetchMangaList(sourceId: Long, page: Int, query: String): Result<List<MangaPreview>> =
        resultCatching {
            sourceManager.getSource(sourceId).fetchMangaList(page, query)
        }

    // ── Detail ───────────────────────────────────────────────────────────────

    override suspend fun fetchMangaDetail(sourceId: Long, url: String): Result<Manga> =
        resultCatching {
            sourceManager.getSource(sourceId).fetchMangaDetail(url)
        }

    override suspend fun fetchChapterList(sourceId: Long, mangaUrl: String): Result<List<Chapter>> =
        resultCatching {
            val chapters = sourceManager.getSource(sourceId).fetchChapterList(mangaUrl)
            // Normalize scraped URLs the same way Room stores them (trimEnd('/')) so the
            // batch lookup matches the primary keys in chapter_progress.
            val normalizedUrls = chapters.map { it.url.trimEnd('/') }
            val readUrlSet = chapterDao.getReadUrls(normalizedUrls).toHashSet()
            chapters.map { chapter ->
                if (chapter.url.trimEnd('/') in readUrlSet) chapter.copy(read = true) else chapter
            }
        }

    override suspend fun fetchPageList(sourceId: Long, chapterUrl: String): Result<List<Page>> =
        resultCatching {
            sourceManager.getSource(sourceId).fetchPageList(chapterUrl)
        }

    // ── Library (Room-backed) ─────────────────────────────────────────────────

    override fun getLibrary(): Flow<List<MangaPreview>> =
        mangaDao.observeAll().map { entities -> entities.map { it.toPreview() } }

    override suspend fun addToLibrary(manga: MangaPreview) {
        val now = System.currentTimeMillis()
        mangaDao.insert(
            MangaEntity(
                id = manga.id,
                title = manga.title,
                coverUrl = manga.coverUrl,
                sourceId = manga.sourceId,
                url = manga.url,
                addedAt = now,
                sortOrder = now,
            )
        )
    }

    override suspend fun removeFromLibrary(mangaId: String) {
        mangaDao.deleteById(mangaId)
    }

    override fun isInLibrary(mangaId: String): Flow<Boolean> =
        mangaDao.observeExists(mangaId)

    override suspend fun updateLatestChapterAt(mangaId: String, latestAt: Long) {
        mangaDao.updateLatestChapterAt(mangaId, latestAt)
    }

    override suspend fun reorderLibrary(orderedIds: List<String>) {
        mangaDao.reorderAll(orderedIds)
    }

    // ── Reading progress ──────────────────────────────────────────────────────

    override suspend fun markChapterRead(chapter: Chapter) {
        chapterDao.insertOrReplace(
            ChapterEntity(
                chapterUrl = chapter.url.trimEnd('/'),
                mangaId = chapter.mangaId,
                title = chapter.title,
                number = chapter.number,
                read = true,
                dateUpload = chapter.dateUpload,
                lastReadAt = System.currentTimeMillis(),
            )
        )
    }

    override suspend fun touchLastOpenedChapter(chapter: Chapter) {
        chapterDao.touchLastReadAt(
            chapterUrl = chapter.url.trimEnd('/'),
            mangaId = chapter.mangaId,
            title = chapter.title,
            number = chapter.number,
            dateUpload = chapter.dateUpload,
            timestamp = System.currentTimeMillis(),
        )
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
        latestChapterAt = latestChapterAt,
    )

}
