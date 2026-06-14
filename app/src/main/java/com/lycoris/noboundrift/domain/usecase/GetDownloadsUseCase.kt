package com.lycoris.noboundrift.domain.usecase

import com.lycoris.noboundrift.data.local.entity.DownloadEntity
import com.lycoris.noboundrift.domain.repository.DownloadRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetDownloadsUseCase @Inject constructor(private val repository: DownloadRepository) {
    operator fun invoke(mangaId: String): Flow<List<DownloadEntity>> =
        repository.observeDownloadsForManga(mangaId)

    fun all(): Flow<List<DownloadEntity>> = repository.observeAllDownloads()

    suspend fun forMangaUrl(mangaUrl: String): List<DownloadEntity> =
        repository.getCompletedDownloadsForMangaUrl(mangaUrl)
}
