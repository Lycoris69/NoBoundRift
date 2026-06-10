package com.lycoris.noboundrift

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.hilt.android.HiltAndroidApp
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class NoBoundRiftApp : Application(), ImageLoaderFactory {

    // Field-injected by Hilt after Application.onCreate so the singleton client
    // is shared between all scrapers and Coil (one connection pool, shared cookies).
    @Inject lateinit var okHttpClient: OkHttpClient

    override fun newImageLoader(): ImageLoader {
        // Derive a Coil-specific client from the shared singleton: keep all interceptors,
        // cookies, and timeouts but cap concurrent requests per host to 2 to avoid
        // rate-limiting on sites like manhwaz.com that drop connections under burst load.
        val dispatcher = Dispatcher().apply { maxRequestsPerHost = 2 }
        val coilClient = okHttpClient.newBuilder()
            .dispatcher(dispatcher)
            // Image requests need a browser-like Accept header; some CDNs reject without it.
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                        .build()
                )
            }
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(coilClient)
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.05) // 5 % of free disk space
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
