package com.lycoris.noboundrift.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lycoris.noboundrift.data.local.entity.MangaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MangaDao {

    @Query("SELECT * FROM manga_library ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<MangaEntity>>

    @Query("SELECT * FROM manga_library WHERE id = :mangaId")
    suspend fun getById(mangaId: String): MangaEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM manga_library WHERE id = :mangaId)")
    fun observeExists(mangaId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MangaEntity)

    @Query("DELETE FROM manga_library WHERE id = :mangaId")
    suspend fun deleteById(mangaId: String)
}
