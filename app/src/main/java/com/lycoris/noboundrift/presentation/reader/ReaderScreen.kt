package com.lycoris.noboundrift.presentation.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.lycoris.noboundrift.domain.model.Page

/**
 * Full-screen reader with two display modes:
 * - [ReaderMode.WEBTOON]: vertical [LazyColumn] — ideal for long-strip webtoons.
 * - [ReaderMode.PAGE_FLIP]: [HorizontalPager] — ideal for traditional manga pages.
 *
 * Tapping the center of the screen toggles the top chrome (back button + mode toggle).
 * The chapter is automatically marked read on exit if the user reached 80%+ of the pages.
 */
@Composable
fun ReaderScreen(
    onBackClick: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val view = LocalView.current

    // Hide/show system bars to match chrome visibility
    LaunchedEffect(uiState.showChrome) {
        val window = (context as android.app.Activity).window
        val controller = androidx.core.view.WindowCompat.getInsetsController(window, view)
        if (uiState.showChrome) {
            controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        } else {
            controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    // Mark chapter read when leaving — DisposableEffect runs onDispose
    DisposableEffect(Unit) {
        onDispose {
            viewModel.onExitReader()
            // Restore system bars so other screens are not affected
            val window = (context as android.app.Activity).window
            val controller = androidx.core.view.WindowCompat.getInsetsController(window, view)
            controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        when {
            uiState.isLoading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            uiState.error != null -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = uiState.error!!,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = viewModel::retry) {
                        Text("Retry")
                    }
                }
            }

            else -> {
                // Content area — tapping center toggles chrome
                val centerClickModifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = viewModel::toggleChrome,
                )

                when (uiState.readerMode) {
                    ReaderMode.WEBTOON -> key(uiState.chapterKey) {
                        WebtoonReader(
                            pages = uiState.pages,
                            imageRetryKey = uiState.imageRetryKey,
                            isLoadingNextChapter = uiState.isLoadingNextChapter,
                            onPageVisible = viewModel::onPageChanged,
                            onNearEnd = viewModel::onNearEnd,
                            modifier = centerClickModifier,
                        )
                    }
                    ReaderMode.PAGE_FLIP -> key(uiState.chapterKey) {
                        PageFlipReader(
                            pages = uiState.pages,
                            imageRetryKey = uiState.imageRetryKey,
                            currentPage = uiState.currentPageIndex,
                            onPageChanged = viewModel::onPageChanged,
                            onNearEnd = viewModel::onNearEnd,
                            modifier = centerClickModifier,
                        )
                    }
                }

                // Overlay chrome (top bar)
                AnimatedVisibility(
                    visible = uiState.showChrome,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopStart),
                ) {
                    ReaderTopBar(
                        currentPage = uiState.currentPageIndex + 1,
                        totalPages = uiState.pages.size,
                        mangaTitle = uiState.mangaTitle,
                        currentChapterTitle = uiState.currentChapterTitle,
                        readerMode = uiState.readerMode,
                        canGoToPrevChapter = uiState.canGoToPrevChapter,
                        canGoToNextChapter = uiState.canGoToNextChapter,
                        onBackClick = { onBackClick() },
                        onToggleMode = viewModel::toggleReaderMode,
                        onPrevChapter = viewModel::goToPrevChapter,
                        onNextChapter = viewModel::goToNextChapter,
                        onRetryImages = viewModel::retryImages,
                    )
                }
            }
        }
    }
}

// ── Webtoon (vertical scroll) ──────────────────────────────────────────────

@Composable
private fun WebtoonReader(
    pages: List<Page>,
    imageRetryKey: Int,
    isLoadingNextChapter: Boolean,
    onPageVisible: (Int) -> Unit,
    onNearEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { onPageVisible(it) }
    }

    LaunchedEffect(listState, pages.size) {
        snapshotFlow {
            val info = listState.layoutInfo.visibleItemsInfo
            if (info.isEmpty()) null else info.last().index
        }
            .filterNotNull()
            .map { lastVisible -> pages.isNotEmpty() && lastVisible >= pages.size - 3 }
            .distinctUntilChanged()
            .collect { nearEnd -> if (nearEnd) onNearEnd() }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
    ) {
        itemsIndexed(pages, key = { _, page -> page.index }) { _, page ->
            PageImage(
                page = page,
                imageRetryKey = imageRetryKey,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (isLoadingNextChapter) {
            item(key = "next-chapter-loader") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = Color.White,
                    )
                }
            }
        }
    }
}

// ── Page-flip (horizontal pager) ──────────────────────────────────────────

@Composable
private fun PageFlipReader(
    pages: List<Page>,
    imageRetryKey: Int,
    currentPage: Int,
    onPageChanged: (Int) -> Unit,
    onNearEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(
        initialPage = currentPage,
        pageCount = { pages.size },
    )

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { onPageChanged(it) }
    }

    LaunchedEffect(pagerState, pages.size) {
        snapshotFlow { pagerState.currentPage }
            .map { current -> pages.isNotEmpty() && current >= pages.size - 2 }
            .distinctUntilChanged()
            .collect { nearEnd -> if (nearEnd) onNearEnd() }
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize(),
    ) { pageIndex ->
        pages.getOrNull(pageIndex)?.let { page ->
            PageImage(page = page, imageRetryKey = imageRetryKey, modifier = Modifier.fillMaxSize())
        }
    }
}

// ── Shared page image ─────────────────────────────────────────────────────

@Composable
private fun PageImage(page: Page, imageRetryKey: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val model = remember(page.imageUrl, imageRetryKey) {
        ImageRequest.Builder(context)
            .data(page.imageUrl)
            .memoryCacheKey("${page.imageUrl}:$imageRetryKey")
            // Also bust the disk cache on retry so a previously bad/partial download
            // doesn't get served again (memory cache key alone is insufficient).
            .diskCacheKey("${page.imageUrl}:$imageRetryKey")
            .build()
    }
    SubcomposeAsyncImage(
        model = model,
        contentDescription = "Page ${page.index + 1}",
        contentScale = ContentScale.FillWidth,
        modifier = modifier,
        loading = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.5f),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        },
        error = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.3f),
                contentAlignment = Alignment.Center,
            ) {
                Text("Failed to load page ${page.index + 1}", color = Color.White)
            }
        },
    )
}

// ── Chrome overlay ────────────────────────────────────────────────────────

@Composable
private fun ReaderTopBar(
    currentPage: Int,
    totalPages: Int,
    mangaTitle: String,
    currentChapterTitle: String,
    readerMode: ReaderMode,
    canGoToPrevChapter: Boolean,
    canGoToNextChapter: Boolean,
    onBackClick: () -> Unit,
    onToggleMode: () -> Unit,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onRetryImages: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.65f))
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBackClick) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
        IconButton(onClick = onPrevChapter, enabled = canGoToPrevChapter) {
            Icon(
                Icons.Default.SkipPrevious,
                contentDescription = "Previous chapter",
                tint = Color.White.copy(alpha = if (canGoToPrevChapter) 1f else 0.4f),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (mangaTitle.isNotEmpty()) {
                Text(
                    text = mangaTitle,
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = if (currentChapterTitle.isNotEmpty()) "$currentChapterTitle  $currentPage / $totalPages"
                       else "$currentPage / $totalPages",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onNextChapter, enabled = canGoToNextChapter) {
            Icon(
                Icons.Default.SkipNext,
                contentDescription = "Next chapter",
                tint = Color.White.copy(alpha = if (canGoToNextChapter) 1f else 0.4f),
            )
        }
        IconButton(onClick = onToggleMode) {
            Icon(
                imageVector = if (readerMode == ReaderMode.WEBTOON) Icons.Default.SwapHoriz
                else Icons.Default.ViewDay,
                contentDescription = "Toggle reader mode",
                tint = Color.White,
            )
        }
        IconButton(onClick = onRetryImages) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Retry failed images",
                tint = Color.White,
            )
        }
    }
}
