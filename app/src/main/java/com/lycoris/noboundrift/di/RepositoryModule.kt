package com.lycoris.noboundrift.di

import com.lycoris.noboundrift.data.repository.DownloadRepositoryImpl
import com.lycoris.noboundrift.data.repository.MangaRepositoryImpl
import com.lycoris.noboundrift.domain.repository.DownloadRepository
import com.lycoris.noboundrift.domain.repository.MangaRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the domain repository interface to its concrete implementation.
 * Using @Binds instead of @Provides is more efficient — it generates a simple
 * delegation instead of a full provider method in the generated component.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindMangaRepository(impl: MangaRepositoryImpl): MangaRepository

    @Binds
    @Singleton
    abstract fun bindDownloadRepository(impl: DownloadRepositoryImpl): DownloadRepository
}
