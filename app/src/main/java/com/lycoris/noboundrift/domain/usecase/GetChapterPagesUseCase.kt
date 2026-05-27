package com.lycoris.noboundrift.domain.usecase

import com.lycoris.noboundrift.domain.model.Page
import com.lycoris.noboundrift.domain.repository.MangaRepository
import javax.inject.Inject

/** Fetches the ordered list of image URLs for a chapter. Used by the reader screen. */
class GetChapterPagesUseCase @Inject constructor(
    private val repository: MangaRepository,
) {
    suspend operator fun invoke(sourceId: Long, chapterUrl: String): Result<List<Page>> =
        repository.fetchPageList(sourceId, chapterUrl)
}
