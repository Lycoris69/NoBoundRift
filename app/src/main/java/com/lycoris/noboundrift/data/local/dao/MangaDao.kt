package com.lycoris.noboundrift.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.lycoris.noboundrift.data.local.entity.MangaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MangaDao {

    @Query("SELECT * FROM manga_library ORDER BY sortOrder ASC")
    fun observeAll(): Flow<List<MangaEntity>>

    @Query("SELECT * FROM manga_library WHERE id = :mangaId")
    suspend fun getById(mangaId: String): MangaEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM manga_library WHERE id = :mangaId)")
    fun observeExists(mangaId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MangaEntity)

    @Query("DELETE FROM manga_library WHERE id = :mangaId")
    suspend fun deleteById(mangaId: String)

    @Query("UPDATE manga_library SET latestChapterAt = :latestAt WHERE id = :mangaId")
    suspend fun updateLatestChapterAt(mangaId: String, latestAt: Long)

    @Query("UPDATE manga_library SET sortOrder = :order WHERE id = :id")
    suspend fun updateSortOrder(id: String, order: Long)

    @Transaction
    suspend fun reorderAll(orderedIds: List<String>) {
        orderedIds.forEachIndexed { index, id ->
            updateSortOrder(id, index.toLong() * 1_000L)
        }
    }
}
