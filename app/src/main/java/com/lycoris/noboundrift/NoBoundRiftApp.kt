package com.lycoris.noboundrift

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.lycoris.noboundrift.data.local.CachePreferences
import dagger.hilt.android.HiltAndroidApp
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class NoBoundRiftApp : Application(), ImageLoaderFactory {

    // Field-injected by Hilt after Application.onCreate so the singleton client
    // is shared between all scrapers and Coil (one connection pool, shared cookies).
    @Inject lateinit var okHttpClient: OkHttpClient
    @Inject lateinit var cachePreferences: CachePreferences

    override fun newImageLoader(): ImageLoader {
        // Derive a Coil-specific client from the shared singleton: keep all interceptors,
        // cookies, and timeouts but cap concurrent requests per host to 2 to avoid
        // rate-limiting on sites like manhwaz.com that drop connections under burst load.
        val dispatcher = Dispatcher().apply { maxRequestsPerHost = 2 }
        val coilClient = okHttpClient.newBuilder()
            .dispatcher(dispatcher)
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(coilClient)
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(cachePreferences.getCacheSizeBytes())
                    .build()
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .build()
    }
}
