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
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
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
import com.lycoris.noboundrift.data.local.LibrarySortOrder
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsLibraryScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showSortDialog by remember { mutableStateOf(false) }

    if (showSortDialog) {
        AlertDialog(
            onDismissRequest = { showSortDialog = false },
            title = { Text("Sort Order") },
            text = {
                Column {
                    LibrarySortOrder.entries.forEach { order ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setLibrarySortOrder(order)
                                    showSortDialog = false
                                }
                                .padding(vertical = 4.dp),
                        ) {
                            RadioButton(
                                selected = order == uiState.librarySortOrder,
                                onClick = {
                                    viewModel.setLibrarySortOrder(order)
                                    showSortDialog = false
                                },
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = order.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSortDialog = false }) { Text("Done") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library") },
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
            // Layout grid/list toggle
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
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

            // Grid columns slider (2–5)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Grid Columns",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${uiState.libraryGridColumns}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Slider(
                    value = uiState.libraryGridColumns.toFloat(),
                    onValueChange = { viewModel.setLibraryGridColumns(it.roundToInt()) },
                    valueRange = 2f..5f,
                    steps = 2, // discrete steps between 2 and 5: 3 and 4
                )
            }

            HorizontalDivider()

            // Sort order picker
            ListItem(
                headlineContent = { Text("Sort Order") },
                supportingContent = { Text(uiState.librarySortOrder.displayName) },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                modifier = Modifier.clickable { showSortDialog = true },
            )

            HorizontalDivider()

            // Rounded covers toggle
            ListItem(
                headlineContent = { Text("Rounded Covers") },
                trailingContent = {
                    Switch(
                        checked = uiState.roundedCovers,
                        onCheckedChange = viewModel::setRoundedCovers,
                    )
                },
                modifier = Modifier.clickable { viewModel.setRoundedCovers(!uiState.roundedCovers) },
            )

            HorizontalDivider()

            // Blur covers (parental control — shared with Browse)
            ListItem(
                headlineContent = { Text("Blur Covers") },
                supportingContent = { Text("Hides all cover images") },
                trailingContent = {
                    Switch(
                        checked = uiState.blurCovers,
                        onCheckedChange = viewModel::setBlurCovers,
                    )
                },
                modifier = Modifier.clickable { viewModel.setBlurCovers(!uiState.blurCovers) },
            )
        }
    }
}
