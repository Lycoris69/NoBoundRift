package com.lycoris.noboundrift

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.lycoris.noboundrift.data.local.AppearancePreferences
import com.lycoris.noboundrift.presentation.navigation.NoBoundRiftNavHost
import com.lycoris.noboundrift.presentation.theme.NoBoundRiftTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Single activity entry point. All screens are Compose destinations managed by
 * [NoBoundRiftNavHost]. Edge-to-edge rendering is enabled so the reader screen
 * can use the full display area.
 *
 * [AppearancePreferences] is injected directly (not via a ViewModel) because the
 * theme must be applied above the nav host, before any ViewModel is created.
 * The flows are collected as state here so the whole composition recomposes when
 * the user changes the theme, accent colour, or font in Settings.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var appearancePreferences: AppearancePreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appTheme by appearancePreferences.observeAppTheme()
                .collectAsState(initial = appearancePreferences.getAppTheme())
            val accentColor by appearancePreferences.observeAccentColor()
                .collectAsState(initial = appearancePreferences.getAccentColor())
            val appFont by appearancePreferences.observeAppFont()
                .collectAsState(initial = appearancePreferences.getAppFont())

            NoBoundRiftTheme(
                appTheme = appTheme,
                accentColor = accentColor,
                appFont = appFont,
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NoBoundRiftNavHost()
                }
            }
        }
    }
}
