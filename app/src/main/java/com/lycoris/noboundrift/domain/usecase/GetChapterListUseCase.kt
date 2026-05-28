package com.lycoris.noboundrift.domain.usecase

import com.lycoris.noboundrift.domain.model.Chapter
import com.lycoris.noboundrift.domain.repository.MangaRepository
import javax.inject.Inject

/** Fetches the ordered list of chapters for a manga. Used to resolve next/previous chapters. */
class GetChapterListUseCase @Inject constructor(
    private val repository: MangaRepository,
) {
    suspend operator fun invoke(sourceId: Long, mangaUrl: String): Result<List<Chapter>> =
        repository.fetchChapterList(sourceId, mangaUrl)
}
