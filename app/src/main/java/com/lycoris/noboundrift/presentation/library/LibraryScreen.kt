package com.lycoris.noboundrift.presentation.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.lycoris.noboundrift.data.local.LibraryLayout
import com.lycoris.noboundrift.data.local.entity.DownloadEntity
import com.lycoris.noboundrift.data.local.entity.DownloadStatus
import com.lycoris.noboundrift.domain.model.MangaPreview
import com.lycoris.noboundrift.presentation.common.MangaCard

private const val NEW_CHAPTER_WINDOW_MS = 7L * 24 * 3600 * 1000

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    onMangaClick: (MangaPreview) -> Unit,
    onChapterClick: (sourceId: Long, mangaId: String, chapterUrl: String, mangaTitle: String) -> Unit,
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
                } else if (uiState.libraryLayout == LibraryLayout.LIST) {
                    LibraryList(uiState = uiState, onMangaClick = onMangaClick, viewModel = viewModel)
                } else {
                    LibraryGrid(
                        uiState = uiState,
                        onMangaClick = onMangaClick,
                        viewModel = viewModel,
                    )
                }
            }
            LibraryTab.DOWNLOADS -> {
                DownloadsTab(
                    groups = uiState.downloadGroups,
                    onMangaClick = onMangaClick,
                    onChapterClick = onChapterClick,
                    onCancelDownload = viewModel::cancelDownload,
                    onRetryDownload = viewModel::retryDownload,
                    onDeleteDownload = viewModel::deleteDownload,
                    onCancelAll = viewModel::cancelAllDownloads,
                )
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
                    preview.latestChapterAt > System.currentTimeMillis() - NEW_CHAPTER_WINDOW_MS && !preview.isLatestChapterRead,
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
                    .pointerInput(preview.id) {
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
private fun LibraryList(
    uiState: LibraryUiState,
    onMangaClick: (MangaPreview) -> Unit,
    viewModel: LibraryViewModel,
) {
    val listState = rememberLazyListState()
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var touchYInViewport by remember { mutableStateOf(0f) }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        itemsIndexed(uiState.manga, key = { _, preview -> preview.id }) { index, preview ->
            val isDragging = draggingIndex == index
            val isNew = !isDragging && preview.latestChapterAt > System.currentTimeMillis() - NEW_CHAPTER_WINDOW_MS && !preview.isLatestChapterRead
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        if (isDragging) {
                            scaleX = 1.03f
                            scaleY = 1.03f
                            shadowElevation = 8f
                        }
                    }
                    .clickable { if (draggingIndex == null) onMangaClick(preview) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .pointerInput(preview.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offsetWithinItem ->
                                val itemInfo = listState.layoutInfo.visibleItemsInfo
                                    .firstOrNull { it.index == index }
                                if (itemInfo != null) {
                                    draggingIndex = index
                                    touchYInViewport = itemInfo.offset + offsetWithinItem.y
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                touchYInViewport += dragAmount.y
                                val currentDragging = draggingIndex ?: return@detectDragGesturesAfterLongPress
                                val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
                                    touchYInViewport >= info.offset && touchYInViewport < info.offset + info.size
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box {
                    AsyncImage(
                        model = preview.coverUrl,
                        contentDescription = preview.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(6.dp)),
                    )
                    if (isNew) {
                        Text(
                            text = "NEW",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onError,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .background(
                                    color = MaterialTheme.colorScheme.error,
                                    shape = RoundedCornerShape(topEnd = 6.dp, bottomStart = 4.dp),
                                )
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }
                }
                Text(
                    text = preview.title,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DownloadsTab(
    groups: List<MangaDownloadGroup>,
    onMangaClick: (MangaPreview) -> Unit,
    onChapterClick: (sourceId: Long, mangaId: String, chapterUrl: String, mangaTitle: String) -> Unit,
    onCancelDownload: (String) -> Unit,
    onRetryDownload: (DownloadEntity) -> Unit,
    onDeleteDownload: (String) -> Unit,
    onCancelAll: (String) -> Unit,
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
                MangaDownloadGroupCard(
                    group = group,
                    onCancelDownload = onCancelDownload,
                    onRetryDownload = onRetryDownload,
                    onDeleteDownload = onDeleteDownload,
                    onCancelAll = onCancelAll,
                    onChapterClick = { entity ->
                        onChapterClick(group.sourceId, group.mangaUrl, entity.chapterUrl, group.mangaTitle)
                    },
                    onClick = {
                        onMangaClick(
                            MangaPreview(
                                id = group.mangaId,
                                title = group.mangaTitle,
                                coverUrl = group.mangaCoverUrl,
                                sourceId = group.sourceId,
                                url = group.mangaUrl,
                            )
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun MangaDownloadGroupCard(
    group: MangaDownloadGroup,
    onCancelDownload: (String) -> Unit,
    onRetryDownload: (DownloadEntity) -> Unit,
    onDeleteDownload: (String) -> Unit,
    onCancelAll: (String) -> Unit,
    onChapterClick: (DownloadEntity) -> Unit,
    onClick: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val hasActive = group.chapters.any {
        it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
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
            if (hasActive) {
                IconButton(onClick = { onCancelAll(group.mangaId) }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel all",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) {
            group.chapters.forEach { entity ->
                LibraryDownloadChapterRow(
                    entity = entity,
                    onCancel = { onCancelDownload(entity.chapterUrl) },
                    onRetry = { onRetryDownload(entity) },
                    onDelete = { onDeleteDownload(entity.chapterUrl) },
                    onRead = { onChapterClick(entity) },
                )
            }
        }
    }
}

@Composable
private fun LibraryDownloadChapterRow(
    entity: DownloadEntity,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onRead: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (entity.status == DownloadStatus.COMPLETED) {
                    Modifier.clickable(onClick = onRead)
                } else {
                    Modifier
                }
            )
            .padding(start = 68.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(entity.chapterTitle, style = MaterialTheme.typography.bodySmall)
            val sub = when (entity.status) {
                DownloadStatus.QUEUED -> "Queued"
                DownloadStatus.DOWNLOADING -> "${entity.downloadedPages} / ${entity.totalPages} pages"
                DownloadStatus.COMPLETED -> "Downloaded"
                DownloadStatus.FAILED -> "Failed"
            }
            Text(
                text = sub,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when (entity.status) {
            DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING ->
                IconButton(onClick = onCancel) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            DownloadStatus.FAILED ->
                IconButton(onClick = onRetry) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Retry",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            DownloadStatus.COMPLETED -> {
                IconButton(onClick = onRead) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = "Read",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
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
