package com.lycoris.noboundrift.domain.usecase

import com.lycoris.noboundrift.domain.model.Chapter
import com.lycoris.noboundrift.domain.repository.DownloadRepository
import javax.inject.Inject

class QueueDownloadUseCase @Inject constructor(private val repository: DownloadRepository) {

    suspend operator fun invoke(
        sourceId: Long,
        mangaId: String,
        mangaUrl: String,
        mangaTitle: String,
        mangaCoverUrl: String,
        chapter: Chapter,
    ) = repository.queueChapter(sourceId, mangaId, mangaUrl, mangaTitle, mangaCoverUrl, chapter)

    suspend fun all(
        sourceId: Long,
        mangaId: String,
        mangaUrl: String,
        mangaTitle: String,
        mangaCoverUrl: String,
        chapters: List<Chapter>,
    ) = repository.queueAllChapters(sourceId, mangaId, mangaUrl, mangaTitle, mangaCoverUrl, chapters)
}
