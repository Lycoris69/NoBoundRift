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
            // fallbackToDestructiveMigration is acceptable for personal use during
            // early development. Replace with explicit migrations before shipping.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideMangaDao(db: NoBoundRiftDatabase): MangaDao = db.mangaDao()

    @Provides
    fun provideChapterDao(db: NoBoundRiftDatabase): ChapterDao = db.chapterDao()

    @Provides
    fun provideDownloadDao(db: NoBoundRiftDatabase): DownloadDao = db.downloadDao()
}
