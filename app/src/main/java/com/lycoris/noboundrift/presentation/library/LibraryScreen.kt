package com.lycoris.noboundrift.presentation.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

    if (uiState.isEmpty) {
        EmptyLibrary()
    } else {
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
