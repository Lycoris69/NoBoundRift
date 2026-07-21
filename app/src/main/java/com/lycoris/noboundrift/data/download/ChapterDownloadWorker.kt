package com.lycoris.noboundrift.data.download

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lycoris.noboundrift.data.local.dao.DownloadDao
import com.lycoris.noboundrift.data.local.entity.DownloadStatus
import com.lycoris.noboundrift.data.remote.source.SourceManager
import com.lycoris.noboundrift.domain.repository.DownloadRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

@HiltWorker
class ChapterDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sourceManager: SourceManager,
    private val downloadDao: DownloadDao,
    private val okHttpClient: OkHttpClient,
    private val downloadRepository: DownloadRepository,
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_SOURCE_ID = "sourceId"
        const val KEY_CHAPTER_URL = "chapterUrl"
        const val KEY_LOCAL_DIR = "localDir"
        const val KEY_MANGA_ID = "mangaId"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val sourceId = inputData.getLong(KEY_SOURCE_ID, -1L)
        val chapterUrl = inputData.getString(KEY_CHAPTER_URL) ?: return@withContext Result.success()
        val localDir = inputData.getString(KEY_LOCAL_DIR) ?: return@withContext Result.success()
        val mangaId = inputData.getString(KEY_MANGA_ID) ?: ""

        downloadDao.updateStatus(chapterUrl, DownloadStatus.DOWNLOADING)

        try {
            val pages = sourceManager.getSource(sourceId).fetchPageList(chapterUrl)
            if (pages.isEmpty()) {
                downloadDao.updateStatus(chapterUrl, DownloadStatus.FAILED)
                return@withContext Result.success()
            }

            val dir = File(localDir).also { it.mkdirs() }
            downloadDao.updateProgress(chapterUrl, DownloadStatus.DOWNLOADING, pages.size, 0)

            pages.forEachIndexed { i, page ->
                val file = File(dir, "$i.jpg")
                if (!file.exists()) {
                    val request = Request.Builder()
                        .url(page.imageUrl)
                        .apply { page.refererUrl?.let { addHeader("Referer", it) } }
                        .build()
                    okHttpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw IllegalStateException("HTTP ${response.code} fetching ${page.imageUrl}")
                        }
                        val body = response.body
                            ?: throw IllegalStateException("Empty body for ${page.imageUrl}")
                        body.byteStream().use { input ->
                            file.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                }
                downloadDao.updateProgress(chapterUrl, DownloadStatus.DOWNLOADING, pages.size, i + 1)
            }

            downloadDao.updateStatus(chapterUrl, DownloadStatus.COMPLETED)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            downloadDao.updateStatus(chapterUrl, DownloadStatus.FAILED)
        } finally {
            withContext(NonCancellable) {
                if (mangaId.isNotEmpty()) downloadRepository.scheduleNextForManga(mangaId)
            }
        }
        Result.success()
    }
}
