package com.lycoris.noboundrift.presentation.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
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

    // Mark chapter read when leaving — DisposableEffect runs onDispose
    DisposableEffect(Unit) {
        onDispose { viewModel.onExitReader() }
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
                Text(
                    text = uiState.error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                )
            }

            else -> {
                // Content area — tapping center toggles chrome
                val centerClickModifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = viewModel::toggleChrome,
                )

                when (uiState.readerMode) {
                    ReaderMode.WEBTOON -> WebtoonReader(
                        pages = uiState.pages,
                        onPageVisible = viewModel::onPageChanged,
                        modifier = centerClickModifier,
                    )
                    ReaderMode.PAGE_FLIP -> PageFlipReader(
                        pages = uiState.pages,
                        currentPage = uiState.currentPageIndex,
                        onPageChanged = viewModel::onPageChanged,
                        modifier = centerClickModifier,
                    )
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
                        readerMode = uiState.readerMode,
                        onBackClick = {
                            viewModel.onExitReader()
                            onBackClick()
                        },
                        onToggleMode = viewModel::toggleReaderMode,
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
    onPageVisible: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // Report the first fully-visible item index as current page
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { onPageVisible(it) }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
    ) {
        itemsIndexed(pages, key = { _, page -> page.index }) { _, page ->
            PageImage(
                page = page,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ── Page-flip (horizontal pager) ──────────────────────────────────────────

@Composable
private fun PageFlipReader(
    pages: List<Page>,
    currentPage: Int,
    onPageChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(
        initialPage = currentPage,
        pageCount = { pages.size },
    )

    // Sync pager position → ViewModel
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { onPageChanged(it) }
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize(),
    ) { pageIndex ->
        PageImage(
            page = pages[pageIndex],
            modifier = Modifier.fillMaxSize(),
        )
    }
}

// ── Shared page image ─────────────────────────────────────────────────────

@Composable
private fun PageImage(page: Page, modifier: Modifier = Modifier) {
    SubcomposeAsyncImage(
        model = page.imageUrl,
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
    readerMode: ReaderMode,
    onBackClick: () -> Unit,
    onToggleMode: () -> Unit,
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
        Spacer(Modifier.width(8.dp))
        Text(
            text = "$currentPage / $totalPages",
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onToggleMode) {
            Icon(
                imageVector = if (readerMode == ReaderMode.WEBTOON) Icons.Default.SwapHoriz
                else Icons.Default.ViewDay,
                contentDescription = "Toggle reader mode",
                tint = Color.White,
            )
        }
    }
}
