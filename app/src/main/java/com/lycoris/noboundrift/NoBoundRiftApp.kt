package com.lycoris.noboundrift

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import dagger.hilt.android.HiltAndroidApp
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class NoBoundRiftApp : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        // Limit to 2 concurrent image requests per host to avoid rate-limiting on
        // sites like manhwaz.com that drop connections under burst load.
        val dispatcher = Dispatcher().apply { maxRequestsPerHost = 2 }
        val client = OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header(
                            "User-Agent",
                            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
                        )
                        .build()
                )
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(client)
            .build()
    }
}
