package com.lycoris.noboundrift.domain.usecase

import com.lycoris.noboundrift.domain.model.MangaPreview
import com.lycoris.noboundrift.domain.repository.MangaRepository
import javax.inject.Inject

/**
 * Fetches a page of [MangaPreview] entries from the specified source.
 * When [query] is non-blank the source's search endpoint is used instead of the catalogue.
 * Thin use-case — its value is providing a named, testable unit with a clear
 * single responsibility that the ViewModel can depend on.
 */
class GetMangaListUseCase @Inject constructor(
    private val repository: MangaRepository,
) {
    suspend operator fun invoke(
        sourceId: Long,
        page: Int,
        query: String = "",
    ): Result<List<MangaPreview>> = repository.fetchMangaList(sourceId, page, query)
}
