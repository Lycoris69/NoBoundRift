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
     * Returns the URL of the most recently read chapter for a manga, or null if none.
     * Used to resume reading from the correct chapter.
     */
    @Query(
        """
        SELECT chapterUrl FROM chapter_progress
        WHERE mangaId = :mangaId AND read = 1
        ORDER BY lastReadAt DESC
        LIMIT 1
        """
    )
    fun observeLastRead(mangaId: String): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(entity: ChapterEntity)

    @Query("UPDATE chapter_progress SET read = 1, lastReadAt = :timestamp WHERE chapterUrl = :url")
    suspend fun markRead(url: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE chapter_progress SET read = 0 WHERE chapterUrl = :url")
    suspend fun markUnread(url: String)
}
