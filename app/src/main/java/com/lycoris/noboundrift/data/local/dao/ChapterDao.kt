package com.lycoris.noboundrift.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lycoris.noboundrift.data.local.entity.ChapterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterDao {

    @Query("SELECT * FROM chapter_progress WHERE mangaId = :mangaId ORDER BY number ASC")
    fun observeByManga(mangaId: String): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapter_progress WHERE chapterUrl = :url")
    suspend fun getByUrl(url: String): ChapterEntity?

    /**
     * Returns the set of normalized chapter URLs (from [urls]) that have a read=true
     * entry in the database. Used for batch read-state hydration to avoid N+1 queries.
     */
    @Query("SELECT chapterUrl FROM chapter_progress WHERE chapterUrl IN (:urls) AND read = 1")
    suspend fun getReadUrls(urls: List<String>): List<String>

    /**
     * Returns the URL of the most recently opened chapter for a manga, or null if none.
     * Used to resume reading from the correct chapter.
     */
    @Query(
        """
        SELECT chapterUrl FROM chapter_progress
        WHERE mangaId = :mangaId
        ORDER BY lastReadAt DESC
        LIMIT 1
        """
    )
    fun observeLastRead(mangaId: String): Flow<String?>

    @Query(
        """
        INSERT INTO chapter_progress (chapterUrl, mangaId, title, number, read, dateUpload, lastReadAt)
        VALUES (:chapterUrl, :mangaId, :title, :number, 0, :dateUpload, :timestamp)
        ON CONFLICT(chapterUrl) DO UPDATE SET lastReadAt = :timestamp
        """
    )
    suspend fun touchLastReadAt(
        chapterUrl: String,
        mangaId: String,
        title: String,
        number: Float,
        dateUpload: Long,
        timestamp: Long,
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(entity: ChapterEntity)

    @Query("""
        INSERT INTO chapter_progress (chapterUrl, mangaId, title, number, read, dateUpload, lastReadAt)
        VALUES (:chapterUrl, :mangaId, :title, :number, 1, :dateUpload, :timestamp)
        ON CONFLICT(chapterUrl) DO UPDATE SET read = 1, lastReadAt = :timestamp
    """)
    suspend fun markReadUpsert(
        chapterUrl: String,
        mangaId: String,
        title: String,
        number: Float,
        dateUpload: Long,
        timestamp: Long,
    )

    @Query("UPDATE chapter_progress SET read = 1, lastReadAt = :timestamp WHERE chapterUrl = :url")
    suspend fun markRead(url: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE chapter_progress SET read = 0 WHERE chapterUrl = :url")
    suspend fun markUnread(url: String)
}
