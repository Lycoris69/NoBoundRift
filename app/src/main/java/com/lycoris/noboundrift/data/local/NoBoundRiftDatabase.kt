package com.lycoris.noboundrift.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.lycoris.noboundrift.data.local.dao.ChapterDao
import com.lycoris.noboundrift.data.local.dao.DownloadDao
import com.lycoris.noboundrift.data.local.dao.MangaDao
import com.lycoris.noboundrift.data.local.entity.ChapterEntity
import com.lycoris.noboundrift.data.local.entity.DownloadEntity
import com.lycoris.noboundrift.data.local.entity.MangaEntity

@Database(
    entities = [MangaEntity::class, ChapterEntity::class, DownloadEntity::class],
    version = 5,
    exportSchema = true,
)
abstract class NoBoundRiftDatabase : RoomDatabase() {
    abstract fun mangaDao(): MangaDao
    abstract fun chapterDao(): ChapterDao
    abstract fun downloadDao(): DownloadDao
}
