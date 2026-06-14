package com.lycoris.noboundrift.presentation.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.lycoris.noboundrift.domain.model.MangaPreview
import com.lycoris.noboundrift.presentation.common.MangaCard

private const val NEW_CHAPTER_WINDOW_MS = 7L * 24 * 3600 * 1000

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    onMangaClick: (MangaPreview) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = uiState.selectedTab.ordinal) {
            LibraryTab.entries.forEach { tab ->
                Tab(
                    selected = uiState.selectedTab == tab,
                    onClick = { viewModel.setTab(tab) },
                    text = { Text(if (tab == LibraryTab.LIBRARY) "Library" else "Downloads") },
                )
            }
        }

        when (uiState.selectedTab) {
            LibraryTab.LIBRARY -> {
                if (uiState.isEmpty) {
                    EmptyLibrary()
                } else {
                    LibraryGrid(
                        uiState = uiState,
                        onMangaClick = onMangaClick,
                        viewModel = viewModel,
                    )
                }
            }
            LibraryTab.DOWNLOADS -> {
                DownloadsTab(groups = uiState.downloadGroups, onMangaClick = onMangaClick)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryGrid(
    uiState: LibraryUiState,
    onMangaClick: (MangaPreview) -> Unit,
    viewModel: LibraryViewModel,
) {
    val gridState = rememberLazyGridState()
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var touchPosInViewport by remember { mutableStateOf(Offset.Zero) }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = 120.dp),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(
            items = uiState.manga,
            key = { _, preview -> preview.id },
        ) { index, preview ->
            val isDragging = draggingIndex == index
            MangaCard(
                preview = preview,
                onClick = { if (draggingIndex == null) onMangaClick(preview) },
                showNewBadge = !isDragging &&
                    preview.latestChapterAt > System.currentTimeMillis() - NEW_CHAPTER_WINDOW_MS,
                modifier = Modifier
                    .animateItem()
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        if (isDragging) {
                            scaleX = 1.05f
                            scaleY = 1.05f
                            shadowElevation = 16f
                        }
                    }
                    .pointerInput(index) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offsetWithinItem ->
                                // Compute the touch position in the grid's scroll viewport
                                val itemInfo = gridState.layoutInfo.visibleItemsInfo
                                    .firstOrNull { it.index == index }
                                if (itemInfo != null) {
                                    draggingIndex = index
                                    touchPosInViewport = Offset(
                                        x = itemInfo.offset.x + offsetWithinItem.x,
                                        y = itemInfo.offset.y + offsetWithinItem.y,
                                    )
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                touchPosInViewport += dragAmount
                                val currentDragging = draggingIndex ?: return@detectDragGesturesAfterLongPress
                                val target = gridState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
                                    touchPosInViewport.x >= info.offset.x &&
                                        touchPosInViewport.x < info.offset.x + info.size.width &&
                                        touchPosInViewport.y >= info.offset.y &&
                                        touchPosInViewport.y < info.offset.y + info.size.height
                                }
                                if (target != null && target.index != currentDragging) {
                                    viewModel.onMove(currentDragging, target.index)
                                    draggingIndex = target.index
                                }
                            },
                            onDragEnd = {
                                draggingIndex = null
                                viewModel.onDragEnd()
                            },
                            onDragCancel = {
                                draggingIndex = null
                                viewModel.onDragEnd()
                            },
                        )
                    },
            )
        }
    }
}

@Composable
private fun DownloadsTab(
    groups: List<MangaDownloadGroup>,
    onMangaClick: (MangaPreview) -> Unit,
) {
    if (groups.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No downloaded chapters yet.\nOpen a manga and tap Downloads.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(32.dp),
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(groups, key = { it.mangaId }) { group ->
                MangaDownloadGroupCard(group = group, onClick = {
                    onMangaClick(
                        MangaPreview(
                            id = group.mangaId,
                            title = group.mangaTitle,
                            coverUrl = group.mangaCoverUrl,
                            sourceId = group.sourceId,
                            url = group.mangaUrl,
                        )
                    )
                })
            }
        }
    }
}

@Composable
private fun MangaDownloadGroupCard(group: MangaDownloadGroup, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model = group.mangaCoverUrl,
            contentDescription = group.mangaTitle,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(6.dp)),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = group.mangaTitle,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
            )
            Text(
                text = "${group.completedCount} / ${group.totalCount} chapters downloaded",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyLibrary() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Your library is empty.\nBrowse sources to add manga.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(32.dp),
        )
    }
}
