package com.lycoris.noboundrift.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lycoris.noboundrift.data.local.LibraryLayout
import com.lycoris.noboundrift.data.local.PreloadMode
import kotlin.math.roundToInt

private val CACHE_SIZE_OPTIONS = listOf(
    64L * 1024 * 1024 to "64 MB",
    128L * 1024 * 1024 to "128 MB (default)",
    256L * 1024 * 1024 to "256 MB",
    512L * 1024 * 1024 to "512 MB",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings") })
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).verticalScroll(rememberScrollState())) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Browse Source",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            uiState.sources.forEach { source ->
                SourceRow(
                    name = source.name,
                    url = source.baseUrl,
                    selected = source.id == uiState.selectedSourceId,
                    onClick = { viewModel.selectSource(source.id) },
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Cache Size",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Text(
                text = "Takes effect after restarting the app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
            CACHE_SIZE_OPTIONS.forEach { (bytes, label) ->
                CacheSizeRow(
                    label = label,
                    selected = uiState.cacheSizeBytes == bytes,
                    onClick = { viewModel.setCacheSize(bytes) },
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Preload Next Chapter",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            PreloadModeRow(
                label = "Always",
                selected = uiState.preloadMode == PreloadMode.ALWAYS,
                onClick = { viewModel.setPreloadMode(PreloadMode.ALWAYS) },
            )
            PreloadModeRow(
                label = "Wi-Fi only",
                selected = uiState.preloadMode == PreloadMode.WIFI_ONLY,
                onClick = { viewModel.setPreloadMode(PreloadMode.WIFI_ONLY) },
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Library Layout",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            PreloadModeRow(
                label = "Grid",
                selected = uiState.libraryLayout == LibraryLayout.GRID,
                onClick = { viewModel.setLibraryLayout(LibraryLayout.GRID) },
            )
            PreloadModeRow(
                label = "List",
                selected = uiState.libraryLayout == LibraryLayout.LIST,
                onClick = { viewModel.setLibraryLayout(LibraryLayout.LIST) },
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Parallel Downloads",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Text(
                text = "${uiState.downloadConcurrency} chapter${if (uiState.downloadConcurrency == 1) "" else "s"} at a time",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
            Slider(
                value = uiState.downloadConcurrency.toFloat(),
                onValueChange = { viewModel.setDownloadConcurrency(it.roundToInt()) },
                valueRange = 1f..20f,
                steps = 18,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Navigation",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            SwitchRow(
                label = "Show Discover tab",
                checked = uiState.showDiscoverTab,
                onCheckedChange = viewModel::setShowDiscoverTab,
            )
        }
    }
}

@Composable
private fun SourceRow(
    name: String,
    url: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CacheSizeRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun PreloadModeRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}
