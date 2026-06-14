package com.lycoris.noboundrift.domain.repository

import com.lycoris.noboundrift.data.local.entity.DownloadEntity
import com.lycoris.noboundrift.domain.model.Chapter
import com.lycoris.noboundrift.domain.model.Page
import kotlinx.coroutines.flow.Flow

interface DownloadRepository {

    suspend fun queueChapter(
        sourceId: Long,
        mangaId: String,
        mangaUrl: String,
        mangaTitle: String,
        mangaCoverUrl: String,
        chapter: Chapter,
    )

    suspend fun queueAllChapters(
        sourceId: Long,
        mangaId: String,
        mangaUrl: String,
        mangaTitle: String,
        mangaCoverUrl: String,
        chapters: List<Chapter>,
    )

    suspend fun cancelDownload(chapterUrl: String)

    suspend fun deleteDownload(chapterUrl: String)

    fun observeDownloadsForManga(mangaId: String): Flow<List<DownloadEntity>>

    fun observeAllDownloads(): Flow<List<DownloadEntity>>

    suspend fun getLocalPages(chapterUrl: String): List<Page>?

    suspend fun getCompletedDownloadsForMangaUrl(mangaUrl: String): List<DownloadEntity>
}
