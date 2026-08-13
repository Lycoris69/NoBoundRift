package com.lycoris.noboundrift.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lycoris.noboundrift.data.local.LibraryLayout
import com.lycoris.noboundrift.data.local.PreloadMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToAppearance: () -> Unit,
    onNavigateToBrowse: () -> Unit,
    onNavigateToReader: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToNavigation: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedSource = uiState.sources.find { it.id == uiState.selectedSourceId }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionEntry(
                icon = Icons.Default.Palette,
                title = "Appearance",
                subtitle = "${uiState.appTheme.displayName} · ${uiState.accentColor.displayName}",
                onClick = onNavigateToAppearance,
            )
            InsetDivider()
            SectionEntry(
                icon = Icons.Default.Language,
                title = "Browse",
                subtitle = selectedSource?.name ?: "—",
                onClick = onNavigateToBrowse,
            )
            InsetDivider()
            SectionEntry(
                icon = Icons.Default.MenuBook,
                title = "Reader",
                subtitle = if (uiState.preloadMode == PreloadMode.ALWAYS) "Preload: Always" else "Preload: Wi-Fi only",
                onClick = onNavigateToReader,
            )
            InsetDivider()
            SectionEntry(
                icon = Icons.Default.Bookmarks,
                title = "Library",
                subtitle = if (uiState.libraryLayout == LibraryLayout.GRID) "Grid" else "List",
                onClick = onNavigateToLibrary,
            )
            InsetDivider()
            SectionEntry(
                icon = Icons.Default.Download,
                title = "Downloads",
                subtitle = "${uiState.downloadConcurrency} ${if (uiState.downloadConcurrency == 1) "chapter" else "chapters"} at a time",
                onClick = onNavigateToDownloads,
            )
            InsetDivider()
            SectionEntry(
                icon = Icons.Default.Tune,
                title = "Navigation",
                subtitle = if (uiState.showDiscoverTab) "Discover tab visible" else "Discover tab hidden",
                onClick = onNavigateToNavigation,
            )
        }
    }
}

@Composable
private fun SectionEntry(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    ListItem(
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** Inset divider aligned with the list text content (icon width 24 + padding 32). */
@Composable
private fun InsetDivider() {
    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
}
