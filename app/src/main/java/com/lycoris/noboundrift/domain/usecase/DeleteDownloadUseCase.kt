package com.lycoris.noboundrift.domain.usecase

import com.lycoris.noboundrift.domain.repository.DownloadRepository
import javax.inject.Inject

class DeleteDownloadUseCase @Inject constructor(private val repository: DownloadRepository) {
    suspend operator fun invoke(chapterUrl: String) = repository.deleteDownload(chapterUrl)
}
