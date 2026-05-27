package com.lycoris.noboundrift

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class — annotated with @HiltAndroidApp to trigger Hilt's code generation
 * and bootstrap the dependency injection component hierarchy.
 */
@HiltAndroidApp
class NoBoundRiftApp : Application()
