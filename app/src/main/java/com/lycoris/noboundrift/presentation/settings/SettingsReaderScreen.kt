package com.lycoris.noboundrift.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lycoris.noboundrift.data.local.PreloadMode

private val CACHE_SIZE_OPTIONS = listOf(
    64L * 1024 * 1024 to "64 MB",
    128L * 1024 * 1024 to "128 MB (default)",
    256L * 1024 * 1024 to "256 MB",
    512L * 1024 * 1024 to "512 MB",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsReaderScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showCacheSizeDialog by remember { mutableStateOf(false) }

    if (showCacheSizeDialog) {
        AlertDialog(
            onDismissRequest = { showCacheSizeDialog = false },
            title = { Text("Cache Size") },
            text = {
                Column {
                    Text(
                        text = "Takes effect after restarting the app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    CACHE_SIZE_OPTIONS.forEach { (bytes, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setCacheSize(bytes); showCacheSizeDialog = false }
                                .padding(vertical = 4.dp),
                        ) {
                            RadioButton(
                                selected = bytes == uiState.cacheSizeBytes,
                                onClick = { viewModel.setCacheSize(bytes); showCacheSizeDialog = false },
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCacheSizeDialog = false }) { Text("Done") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reader") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            // Preload Next Chapter — segmented button
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text("Preload Next Chapter", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        selected = uiState.preloadMode == PreloadMode.ALWAYS,
                        onClick = { viewModel.setPreloadMode(PreloadMode.ALWAYS) },
                        label = { Text("Always") },
                    )
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        selected = uiState.preloadMode == PreloadMode.WIFI_ONLY,
                        onClick = { viewModel.setPreloadMode(PreloadMode.WIFI_ONLY) },
                        label = { Text("Wi-Fi only") },
                    )
                }
            }
            HorizontalDivider()
            // Cache size — dialog picker
            val cacheLabel = CACHE_SIZE_OPTIONS.find { it.first == uiState.cacheSizeBytes }?.second ?: "—"
            ListItem(
                headlineContent = { Text("Cache Size") },
                supportingContent = { Text(cacheLabel) },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                modifier = Modifier.clickable { showCacheSizeDialog = true },
            )
        }
    }
}
