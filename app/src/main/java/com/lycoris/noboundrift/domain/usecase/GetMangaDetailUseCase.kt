package com.lycoris.noboundrift.domain.usecase

import com.lycoris.noboundrift.domain.model.Manga
import com.lycoris.noboundrift.domain.repository.MangaRepository
import javax.inject.Inject

/** Fetches full manga metadata + chapter list, merging with local read state. */
class GetMangaDetailUseCase @Inject constructor(
    private val repository: MangaRepository,
) {
    suspend operator fun invoke(sourceId: Long, url: String): Result<Manga> =
        repository.fetchMangaDetail(sourceId, url)
}
