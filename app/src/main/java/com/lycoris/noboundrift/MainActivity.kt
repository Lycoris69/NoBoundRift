package com.lycoris.noboundrift

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.lycoris.noboundrift.data.local.AppearancePreferences
import com.lycoris.noboundrift.data.remote.UpdateChecker
import com.lycoris.noboundrift.data.remote.UpdateInfo
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
    @Inject lateinit var updateChecker: UpdateChecker

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
            val appPreset by appearancePreferences.observeAppPreset()
                .collectAsState(initial = appearancePreferences.getAppPreset())

            // Check for a newer GitHub release once per launch (silently ignored on error)
            var pendingUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
            LaunchedEffect(Unit) {
                pendingUpdate = updateChecker.checkForUpdate()
            }

            NoBoundRiftTheme(
                appTheme = appTheme,
                accentColor = accentColor,
                appFont = appFont,
                appPreset = appPreset,
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NoBoundRiftNavHost()
                }

                // Show update dialog on top of everything once the check resolves
                pendingUpdate?.let { update ->
                    val context = LocalContext.current
                    AlertDialog(
                        onDismissRequest = { pendingUpdate = null },
                        title = { Text("Update available") },
                        text = { Text("${update.latestTag} is out. Download it now?") },
                        confirmButton = {
                            TextButton(onClick = {
                                pendingUpdate = null
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(update.releaseUrl))
                                )
                            }) { Text("Download") }
                        },
                        dismissButton = {
                            TextButton(onClick = { pendingUpdate = null }) { Text("Later") }
                        },
                    )
                }
            }
        }
    }
}
