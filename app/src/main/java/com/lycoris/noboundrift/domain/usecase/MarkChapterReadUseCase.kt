package com.lycoris.noboundrift.domain.usecase

import com.lycoris.noboundrift.domain.model.Chapter
import com.lycoris.noboundrift.domain.repository.MangaRepository
import javax.inject.Inject

class MarkChapterReadUseCase @Inject constructor(
    private val repository: MangaRepository,
) {
    suspend operator fun invoke(chapter: Chapter) {
        repository.markChapterRead(chapter)
    }
}
