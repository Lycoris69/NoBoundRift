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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
                    readChapterUrls = uiState.readChapterUrls,
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
    val haptic = LocalHapticFeedback.current
    // rememberUpdatedState so the coroutine always sees the live setting even though
    // pointerInput(Unit) never restarts when uiState.hapticFeedback changes.
    val hapticEnabledState = rememberUpdatedState(uiState.hapticFeedback)
    var ratingTarget by remember { mutableStateOf<MangaPreview?>(null) }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(uiState.libraryGridColumns),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        // Gesture lives on the CONTAINER, not on individual items. This prevents
        // Compose from cancelling the ongoing drag when an item moves to a new
        // grid slot after onMove() triggers recomposition — the container is stable.
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        // Start drag only when touch lands directly on an item.
                        val hit = gridState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
                            offset.x >= info.offset.x &&
                                offset.x < info.offset.x + info.size.width &&
                                offset.y >= info.offset.y &&
                                offset.y < info.offset.y + info.size.height
                        }
                        if (hit != null) {
                            if (hapticEnabledState.value) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            draggingIndex = hit.index
                            touchPosInViewport = offset
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val currentDragging = draggingIndex ?: return@detectDragGesturesAfterLongPress
                        touchPosInViewport += dragAmount
                        // Closest-centre search: immune to inter-item gaps and the
                        // fact that layoutInfo may reflect a frame behind the
                        // optimistic reorder while animateItem() is running.
                        val target = gridState.layoutInfo.visibleItemsInfo.minByOrNull { info ->
                            val dx = touchPosInViewport.x - (info.offset.x + info.size.width / 2f)
                            val dy = touchPosInViewport.y - (info.offset.y + info.size.height / 2f)
                            dx * dx + dy * dy
                        }
                        if (target != null && target.index != currentDragging) {
                            viewModel.onMove(currentDragging, target.index)
                            draggingIndex = target.index
                        }
                    },
                    onDragEnd = {
                        if (draggingIndex != null) viewModel.onDragEnd()
                        draggingIndex = null
                    },
                    onDragCancel = {
                        if (draggingIndex != null) viewModel.onDragEnd()
                        draggingIndex = null
                    },
                )
            },
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
                blurred = uiState.blurCovers,
                cornerRadius = if (uiState.roundedCovers) 8.dp else 0.dp,
                onRatingClick = { ratingTarget = preview },
                modifier = Modifier
                    .animateItem()
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        if (isDragging) {
                            scaleX = 1.05f
                            scaleY = 1.05f
                            shadowElevation = 16f
                        }
                    },
            )
        }
    }

    ratingTarget?.let { target ->
        RatingDialog(
            currentRating = target.rating,
            onRatingSelected = { viewModel.setRating(target.id, it) },
            onDismiss = { ratingTarget = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryList(
    uiState: LibraryUiState,
    onMangaClick: (MangaPreview) -> Unit,
    viewModel: LibraryViewModel,
) {
    val listState = rememberLazyListState()
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var touchYInViewport by remember { mutableStateOf(0f) }
    val haptic = LocalHapticFeedback.current
    val hapticEnabledState = rememberUpdatedState(uiState.hapticFeedback)
    var ratingTarget by remember { mutableStateOf<MangaPreview?>(null) }

    LazyColumn(
        state = listState,
        // Gesture lives on the CONTAINER so it is never cancelled when an item
        // moves to a different list slot after onMove() recomposes the list.
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        val hit = listState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
                            offset.y >= info.offset && offset.y < info.offset + info.size
                        }
                        if (hit != null) {
                            if (hapticEnabledState.value) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            draggingIndex = hit.index
                            touchYInViewport = offset.y
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val currentDragging = draggingIndex ?: return@detectDragGesturesAfterLongPress
                        touchYInViewport += dragAmount.y
                        // Closest-centre search (y-axis) — handles item gaps and
                        // layout frames that may lag behind the optimistic reorder.
                        val target = listState.layoutInfo.visibleItemsInfo.minByOrNull { info ->
                            val cy = info.offset + info.size / 2f
                            kotlin.math.abs(touchYInViewport - cy)
                        }
                        if (target != null && target.index != currentDragging) {
                            viewModel.onMove(currentDragging, target.index)
                            draggingIndex = target.index
                        }
                    },
                    onDragEnd = {
                        if (draggingIndex != null) viewModel.onDragEnd()
                        draggingIndex = null
                    },
                    onDragCancel = {
                        if (draggingIndex != null) viewModel.onDragEnd()
                        draggingIndex = null
                    },
                )
            },
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        itemsIndexed(uiState.manga, key = { _, preview -> preview.id }) { index, preview ->
            val isDragging = draggingIndex == index
            val isNew = !isDragging && preview.latestChapterAt > System.currentTimeMillis() - NEW_CHAPTER_WINDOW_MS && !preview.isLatestChapterRead
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateItem()
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        if (isDragging) {
                            scaleX = 1.03f
                            scaleY = 1.03f
                            shadowElevation = 8f
                        }
                    }
                    .clickable { if (draggingIndex == null) onMangaClick(preview) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
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
                // Compact star chip — always shown in list view for easy rating access.
                // Shows filled amber stars when rated, dim outline star when unrated.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { ratingTarget = preview }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                ) {
                    if (preview.rating > 0) {
                        repeat(preview.rating) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                tint = Color(0xFFFFC107),
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.StarOutline,
                            contentDescription = "Rate",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }

    ratingTarget?.let { target ->
        RatingDialog(
            currentRating = target.rating,
            onRatingSelected = { viewModel.setRating(target.id, it) },
            onDismiss = { ratingTarget = null },
        )
    }
}

@Composable
private fun DownloadsTab(
    groups: List<MangaDownloadGroup>,
    readChapterUrls: Set<String>,
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
                    readChapterUrls = readChapterUrls,
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
    readChapterUrls: Set<String>,
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
                    isRead = entity.chapterUrl.trimEnd('/') in readChapterUrls,
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
    isRead: Boolean,
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = entity.chapterTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isRead) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                )
                if (isRead) {
                    Text(
                        text = "READ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(3.dp),
                            )
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }
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

/**
 * Five-star rating picker dialog.
 *
 * [currentRating] is 0–5 (0 = unrated). Tapping the same star that is already
 * selected clears the rating (sets it to 0). The "Clear" button is shown
 * whenever the manga has an existing rating.
 */
@Composable
fun RatingDialog(
    currentRating: Int,
    onRatingSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rate this manga") },
        text = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                for (star in 1..5) {
                    val filled = star <= currentRating
                    Icon(
                        imageVector = if (filled) Icons.Filled.Star else Icons.Outlined.StarOutline,
                        contentDescription = "$star star${if (star > 1) "s" else ""}",
                        tint = if (filled) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(36.dp)
                            .clickable {
                                // Tap the same filled star → clear; otherwise set new rating
                                onRatingSelected(if (star == currentRating) 0 else star)
                                onDismiss()
                            },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            Row {
                if (currentRating > 0) {
                    TextButton(onClick = { onRatingSelected(0); onDismiss() }) {
                        Text("Clear")
                    }
                    Spacer(Modifier.width(8.dp))
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
