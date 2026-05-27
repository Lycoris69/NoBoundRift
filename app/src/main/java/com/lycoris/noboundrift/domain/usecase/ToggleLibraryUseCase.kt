package com.lycoris.noboundrift.domain.usecase

import com.lycoris.noboundrift.domain.model.MangaPreview
import com.lycoris.noboundrift.domain.repository.MangaRepository
import javax.inject.Inject

/**
 * Adds or removes a manga from the user's library depending on whether it is
 * currently saved. Callers do not need to know the current state — this use case
 * handles the toggle logic atomically.
 */
class ToggleLibraryUseCase @Inject constructor(
    private val repository: MangaRepository,
) {
    suspend operator fun invoke(manga: MangaPreview, currentlyInLibrary: Boolean) {
        if (currentlyInLibrary) {
            repository.removeFromLibrary(manga.id)
        } else {
            repository.addToLibrary(manga)
        }
    }
}
