package com.lycoris.noboundrift

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.lycoris.noboundrift.presentation.navigation.NoBoundRiftNavHost
import com.lycoris.noboundrift.presentation.theme.NoBoundRiftTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single activity entry point. All screens are Compose destinations managed by
 * [NoBoundRiftNavHost]. Edge-to-edge rendering is enabled so the reader screen
 * can use the full display area.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NoBoundRiftTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NoBoundRiftNavHost()
                }
            }
        }
    }
}
