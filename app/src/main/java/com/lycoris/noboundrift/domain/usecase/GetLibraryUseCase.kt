package com.lycoris.noboundrift.domain.usecase

import com.lycoris.noboundrift.domain.model.MangaPreview
import com.lycoris.noboundrift.domain.repository.MangaRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Exposes the user's saved library as a reactive [Flow]. */
class GetLibraryUseCase @Inject constructor(
    private val repository: MangaRepository,
) {
    operator fun invoke(): Flow<List<MangaPreview>> = repository.getLibrary()
}
