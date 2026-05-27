package com.lycoris.noboundrift.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.lycoris.noboundrift.data.local.dao.ChapterDao
import com.lycoris.noboundrift.data.local.dao.MangaDao
import com.lycoris.noboundrift.data.local.entity.ChapterEntity
import com.lycoris.noboundrift.data.local.entity.MangaEntity

/**
 * Room database. Version bumps require a migration strategy — use
 * [androidx.room.migration.Migration] objects or [RoomDatabase.Builder.fallbackToDestructiveMigration]
 * (acceptable for personal use during early development).
 */
@Database(
    entities = [MangaEntity::class, ChapterEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class NoBoundRiftDatabase : RoomDatabase() {
    abstract fun mangaDao(): MangaDao
    abstract fun chapterDao(): ChapterDao
}
