package com.lycoris.noboundrift.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lycoris.noboundrift.data.local.dao.ChapterDao
import com.lycoris.noboundrift.data.local.dao.DownloadDao
import com.lycoris.noboundrift.data.local.dao.MangaDao
import com.lycoris.noboundrift.data.local.entity.ChapterEntity
import com.lycoris.noboundrift.data.local.entity.DownloadEntity
import com.lycoris.noboundrift.data.local.entity.MangaEntity

@Database(
    entities = [MangaEntity::class, ChapterEntity::class, DownloadEntity::class],
    version = 7,
    exportSchema = true,
)
abstract class NoBoundRiftDatabase : RoomDatabase() {
    abstract fun mangaDao(): MangaDao
    abstract fun chapterDao(): ChapterDao
    abstract fun downloadDao(): DownloadDao

    companion object {
        // Each migration only adds a column — no data loss, no table drops.
        // fallbackToDestructiveMigration in DatabaseModule remains as a last-resort
        // safety net for any gap not covered here (e.g. fresh installs that skipped versions).

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE manga_library ADD COLUMN latestChapterUrl TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE manga_library ADD COLUMN rating INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE manga_library ADD COLUMN sourceUrls TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
