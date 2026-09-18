package com.lycoris.noboundrift.di

import android.content.Context
import androidx.room.Room
import com.lycoris.noboundrift.data.local.NoBoundRiftDatabase
import com.lycoris.noboundrift.data.local.dao.ChapterDao
import com.lycoris.noboundrift.data.local.dao.DownloadDao
import com.lycoris.noboundrift.data.local.dao.MangaDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NoBoundRiftDatabase =
        Room.databaseBuilder(
            context,
            NoBoundRiftDatabase::class.java,
            "noboundrift.db",
        )
            .addMigrations(
                NoBoundRiftDatabase.MIGRATION_4_5,
                NoBoundRiftDatabase.MIGRATION_5_6,
                NoBoundRiftDatabase.MIGRATION_6_7,
            )
            // Fallback for any version gap not covered by explicit migrations above
            // (e.g. a device that somehow skipped multiple versions).
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideMangaDao(db: NoBoundRiftDatabase): MangaDao = db.mangaDao()

    @Provides
    fun provideChapterDao(db: NoBoundRiftDatabase): ChapterDao = db.chapterDao()

    @Provides
    fun provideDownloadDao(db: NoBoundRiftDatabase): DownloadDao = db.downloadDao()
}
