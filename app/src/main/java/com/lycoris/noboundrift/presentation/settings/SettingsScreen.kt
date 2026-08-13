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
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import com.lycoris.noboundrift.data.local.LibraryLayout
import com.lycoris.noboundrift.data.local.PreloadMode
import com.lycoris.noboundrift.data.remote.source.Source
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

    var showSourceDialog by remember { mutableStateOf(false) }
    var showCacheSizeDialog by remember { mutableStateOf(false) }

    if (showSourceDialog) {
        SourcePickerDialog(
            sources = uiState.sources,
            selectedSourceId = uiState.selectedSourceId,
            onSelect = { viewModel.selectSource(it); showSourceDialog = false },
            onDismiss = { showSourceDialog = false },
        )
    }
    if (showCacheSizeDialog) {
        CacheSizeDialog(
            selectedBytes = uiState.cacheSizeBytes,
            onSelect = { viewModel.setCacheSize(it); showCacheSizeDialog = false },
            onDismiss = { showCacheSizeDialog = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings") })
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {

            // ── Browse ────────────────────────────────────────────────────────
            SectionHeader("Browse")
            val selectedSource = uiState.sources.find { it.id == uiState.selectedSourceId }
            ListItem(
                headlineContent = { Text("Source") },
                supportingContent = { Text(selectedSource?.name ?: "—") },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                modifier = Modifier.clickable { showSourceDialog = true },
            )

            HorizontalDivider()

            // ── Reader ────────────────────────────────────────────────────────
            SectionHeader("Reader")
            // Preload mode — segmented button
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
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
            // Cache size
            ListItem(
                headlineContent = { Text("Cache Size") },
                supportingContent = {
                    val label = CACHE_SIZE_OPTIONS.find { it.first == uiState.cacheSizeBytes }?.second ?: "—"
                    Text(label)
                },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                modifier = Modifier.clickable { showCacheSizeDialog = true },
            )

            HorizontalDivider()

            // ── Library ───────────────────────────────────────────────────────
            SectionHeader("Library")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text("Layout", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        selected = uiState.libraryLayout == LibraryLayout.GRID,
                        onClick = { viewModel.setLibraryLayout(LibraryLayout.GRID) },
                        icon = { Icon(Icons.Default.GridView, contentDescription = null) },
                        label = { Text("Grid") },
                    )
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        selected = uiState.libraryLayout == LibraryLayout.LIST,
                        onClick = { viewModel.setLibraryLayout(LibraryLayout.LIST) },
                        icon = { Icon(Icons.AutoMirrored.Filled.ViewList, contentDescription = null) },
                        label = { Text("List") },
                    )
                }
            }

            HorizontalDivider()

            // ── Downloads ─────────────────────────────────────────────────────
            SectionHeader("Downloads")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Parallel Downloads",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${uiState.downloadConcurrency}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Slider(
                    value = uiState.downloadConcurrency.toFloat(),
                    onValueChange = { viewModel.setDownloadConcurrency(it.roundToInt()) },
                    valueRange = 1f..20f,
                    steps = 18,
                )
            }

            HorizontalDivider()

            // ── Navigation ────────────────────────────────────────────────────
            SectionHeader("Navigation")
            ListItem(
                headlineContent = { Text("Show Discover tab") },
                trailingContent = {
                    Switch(
                        checked = uiState.showDiscoverTab,
                        onCheckedChange = viewModel::setShowDiscoverTab,
                    )
                },
                modifier = Modifier.clickable { viewModel.setShowDiscoverTab(!uiState.showDiscoverTab) },
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── Section header ────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

// ── Dialogs ───────────────────────────────────────────────────────────────────

@Composable
private fun SourcePickerDialog(
    sources: List<Source>,
    selectedSourceId: Long,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Browse Source") },
        text = {
            Column {
                sources.forEach { source ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(source.id) }
                            .padding(vertical = 4.dp),
                    ) {
                        RadioButton(
                            selected = source.id == selectedSourceId,
                            onClick = { onSelect(source.id) },
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = source.name,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = source.baseUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}

@Composable
private fun CacheSizeDialog(
    selectedBytes: Long,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
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
                            .clickable { onSelect(bytes) }
                            .padding(vertical = 4.dp),
                    ) {
                        RadioButton(
                            selected = bytes == selectedBytes,
                            onClick = { onSelect(bytes) },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}
