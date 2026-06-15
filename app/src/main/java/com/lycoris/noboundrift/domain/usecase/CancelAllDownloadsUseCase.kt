package com.lycoris.noboundrift.domain.usecase

import com.lycoris.noboundrift.domain.repository.DownloadRepository
import javax.inject.Inject

class CancelAllDownloadsUseCase @Inject constructor(private val repository: DownloadRepository) {
    suspend operator fun invoke(mangaId: String) = repository.cancelAllDownloads(mangaId)
}
