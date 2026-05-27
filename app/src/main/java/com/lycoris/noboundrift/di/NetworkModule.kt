package com.lycoris.noboundrift.di

import com.lycoris.noboundrift.BuildConfig
import com.lycoris.noboundrift.data.remote.NetworkClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        NetworkClient.build(enableLogging = BuildConfig.DEBUG)
}
