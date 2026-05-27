package com.lycoris.noboundrift.domain.usecase

import com.lycoris.noboundrift.domain.repository.MangaRepository
import javax.inject.Inject

/** Persists the read state for a chapter. Called automatically by the reader on exit. */
class MarkChapterReadUseCase @Inject constructor(
    private val repository: MangaRepository,
) {
    suspend operator fun invoke(chapterUrl: String) {
        repository.markChapterRead(chapterUrl)
    }
}
