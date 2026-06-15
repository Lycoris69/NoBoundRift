package com.lycoris.noboundrift.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lycoris.noboundrift.data.local.entity.DownloadEntity
import com.lycoris.noboundrift.data.local.entity.DownloadStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: DownloadEntity)

    @Update
    suspend fun update(entity: DownloadEntity)

    @Query("SELECT * FROM downloads WHERE chapterUrl = :chapterUrl")
    suspend fun getByChapterUrl(chapterUrl: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE mangaId = :mangaId ORDER BY chapterNumber ASC")
    fun observeForManga(mangaId: String): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads ORDER BY queuedAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("DELETE FROM downloads WHERE chapterUrl = :chapterUrl")
    suspend fun deleteByChapterUrl(chapterUrl: String)

    @Query("UPDATE downloads SET status = :status WHERE chapterUrl = :chapterUrl")
    suspend fun updateStatus(chapterUrl: String, status: DownloadStatus)

    @Query("UPDATE downloads SET status = :status, totalPages = :total, downloadedPages = :downloaded WHERE chapterUrl = :chapterUrl")
    suspend fun updateProgress(chapterUrl: String, status: DownloadStatus, total: Int, downloaded: Int)

    @Query("SELECT * FROM downloads WHERE (mangaUrl = :mangaUrl OR mangaUrl = :mangaUrlTrailing) AND status = 'COMPLETED' ORDER BY chapterNumber ASC")
    suspend fun getCompletedByMangaUrl(mangaUrl: String, mangaUrlTrailing: String): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE mangaId = :mangaId AND status = 'QUEUED' ORDER BY chapterNumber ASC")
    suspend fun getQueuedForManga(mangaId: String): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE mangaId = :mangaId AND (status = 'QUEUED' OR status = 'DOWNLOADING') ORDER BY chapterNumber ASC")
    suspend fun getActiveForManga(mangaId: String): List<DownloadEntity>

    @Query("SELECT COUNT(*) FROM downloads WHERE mangaId = :mangaId AND status = 'DOWNLOADING'")
    suspend fun getDownloadingCountForManga(mangaId: String): Int

    @Query("UPDATE downloads SET status = 'FAILED' WHERE mangaId = :mangaId AND (status = 'QUEUED' OR status = 'DOWNLOADING')")
    suspend fun cancelAllActive(mangaId: String)
}
