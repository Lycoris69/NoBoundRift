package com.lycoris.noboundrift.data.repository

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.lycoris.noboundrift.data.download.ChapterDownloadWorker
import com.lycoris.noboundrift.data.local.dao.DownloadDao
import com.lycoris.noboundrift.data.local.entity.DownloadEntity
import com.lycoris.noboundrift.data.local.entity.DownloadStatus
import com.lycoris.noboundrift.domain.model.Chapter
import com.lycoris.noboundrift.domain.model.Page
import com.lycoris.noboundrift.domain.repository.DownloadRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadDao: DownloadDao,
) : DownloadRepository {

    private val workManager = WorkManager.getInstance(context)

    override suspend fun queueChapter(
        sourceId: Long,
        mangaId: String,
        mangaUrl: String,
        mangaTitle: String,
        mangaCoverUrl: String,
        chapter: Chapter,
    ) {
        val normalizedUrl = chapter.url.trimEnd('/')
        // Skip if already queued, downloading, or completed — only retry FAILED entries.
        val existing = downloadDao.getByChapterUrl(normalizedUrl)
        if (existing != null && existing.status != DownloadStatus.FAILED) return

        val localDir = buildLocalDir(mangaId, chapter)
        val entity = DownloadEntity(
            chapterUrl = normalizedUrl,
            mangaId = mangaId,
            mangaUrl = mangaUrl,
            sourceId = sourceId,
            mangaTitle = mangaTitle,
            mangaCoverUrl = mangaCoverUrl,
            chapterTitle = chapter.title.ifBlank { "Chapter ${chapter.number.toInt()}" },
            chapterNumber = chapter.number,
            status = DownloadStatus.QUEUED,
            localDir = localDir,
            queuedAt = System.currentTimeMillis(),
        )
        downloadDao.insert(entity)
        enqueueWork(sourceId, normalizedUrl, localDir)
    }

    override suspend fun queueAllChapters(
        sourceId: Long,
        mangaId: String,
        mangaUrl: String,
        mangaTitle: String,
        mangaCoverUrl: String,
        chapters: List<Chapter>,
    ) {
        chapters.forEach { chapter ->
            queueChapter(sourceId, mangaId, mangaUrl, mangaTitle, mangaCoverUrl, chapter)
        }
    }

    override suspend fun cancelDownload(chapterUrl: String) {
        val normalized = chapterUrl.trimEnd('/')
        workManager.cancelUniqueWork(workTag(normalized))
        downloadDao.updateStatus(normalized, DownloadStatus.FAILED)
    }

    override suspend fun deleteDownload(chapterUrl: String) {
        val normalized = chapterUrl.trimEnd('/')
        workManager.cancelUniqueWork(workTag(normalized))
        downloadDao.getByChapterUrl(normalized)?.let { entity ->
            File(entity.localDir).deleteRecursively()
        }
        downloadDao.deleteByChapterUrl(normalized)
    }

    override fun observeDownloadsForManga(mangaId: String): Flow<List<DownloadEntity>> =
        downloadDao.observeForManga(mangaId)

    override fun observeAllDownloads(): Flow<List<DownloadEntity>> =
        downloadDao.observeAll()

    override suspend fun getLocalPages(chapterUrl: String): List<Page>? {
        val normalized = chapterUrl.trimEnd('/')
        val entity = downloadDao.getByChapterUrl(normalized) ?: return null
        if (entity.status != DownloadStatus.COMPLETED) return null
        val dir = File(entity.localDir)
        if (!dir.exists()) return null
        return dir.listFiles()
            ?.filter { it.extension == "jpg" }
            ?.sortedBy { it.nameWithoutExtension.toIntOrNull() ?: 0 }
            ?.mapIndexed { index, file ->
                Page(index = index, imageUrl = file.toURI().toString())
            }
            ?.takeIf { it.isNotEmpty() }
    }

    private fun buildLocalDir(mangaId: String, chapter: Chapter): String {
        val mangaHash = mangaId.hashCode().toString(16)
        val urlHash = chapter.url.trimEnd('/').hashCode().toString(16)
        val chapterDir = "downloads/$mangaHash/${chapter.number}_$urlHash"
        return File(context.filesDir, chapterDir).absolutePath
    }

    private fun workTag(normalizedUrl: String) = "download_${normalizedUrl.hashCode()}"

    private fun enqueueWork(sourceId: Long, normalizedUrl: String, localDir: String) {
        val inputData = Data.Builder()
            .putLong(ChapterDownloadWorker.KEY_SOURCE_ID, sourceId)
            .putString(ChapterDownloadWorker.KEY_CHAPTER_URL, normalizedUrl)
            .putString(ChapterDownloadWorker.KEY_LOCAL_DIR, localDir)
            .build()
        val request = OneTimeWorkRequestBuilder<ChapterDownloadWorker>()
            .setInputData(inputData)
            .addTag(workTag(normalizedUrl))
            .build()
        workManager.enqueueUniqueWork(
            workTag(normalizedUrl),
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}
