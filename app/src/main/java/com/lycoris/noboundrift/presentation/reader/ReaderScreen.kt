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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Dimension
import coil.size.Size as CoilSize
import com.lycoris.noboundrift.domain.model.Chapter
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
        val activity = context as? android.app.Activity ?: return@LaunchedEffect
        val window = activity.window
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
            val activity = context as? android.app.Activity
            if (activity != null) {
                val controller = androidx.core.view.WindowCompat.getInsetsController(activity.window, view)
                controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
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
                        sortedChapters = uiState.sortedChapters,
                        currentChapterUrl = uiState.currentChapterUrl,
                        onBackClick = { onBackClick() },
                        onToggleMode = viewModel::toggleReaderMode,
                        onPrevChapter = viewModel::goToPrevChapter,
                        onNextChapter = viewModel::goToNextChapter,
                        onRetryImages = viewModel::retryImages,
                        onChapterSelect = viewModel::jumpToChapter,
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

    // Maps page.index → the display height (in dp) of that page when it was last successfully
    // decoded. Used to give the loading placeholder the same height as the loaded image so
    // that the LazyColumn never sees a height change when Coil transitions loading↔success.
    // Keyed by page.index (stable for the lifetime of this WebtoonReader instance).
    // mutableStateMapOf is read inside itemsIndexed, so writing to it (on decode success)
    // automatically recomposes only the affected item.
    val knownHeights = remember { mutableStateMapOf<Int, Dp>() }

    // Track the page whose midpoint is closest to the viewport center.
    // Using firstVisibleItemIndex is unreliable when off-screen items are evicted and
    // collapse to zero height before re-measuring, which causes spurious backward jumps.
    LaunchedEffect(listState) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val viewportCenter = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
            layout.visibleItemsInfo
                .minByOrNull { item -> kotlin.math.abs((item.offset + item.size / 2) - viewportCenter) }
                ?.index
        }
            .filterNotNull()
            .distinctUntilChanged()
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
                knownHeight = knownHeights[page.index],
                onHeightKnown = { h -> knownHeights[page.index] = h },
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
private fun PageImage(
    page: Page,
    imageRetryKey: Int,
    knownHeight: Dp? = null,
    onHeightKnown: ((Dp) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    // rememberUpdatedState lets the ImageRequest listener (inside `remember`) always call the
    // current onHeightKnown lambda without adding it to the remember key (which would rebuild
    // the ImageRequest and restart the download every time the parent recomposes).
    val onHeightKnownState = rememberUpdatedState(onHeightKnown)

    // Decode at exactly screen width. For images narrower than the screen (e.g. 800 px webtoon
    // strips on a 1080 px display) inSampleSize stays 1 — no width reduction, no blur.
    // The height cap prevents OOM on extremely tall single-strip chapters (16 000+ px).
    // Choosing 8× screen width as the cap: for a typical 800×16 000 strip on a 1080 px device
    // the cap is 8 640 px, keeping heightRatio < 2 → inSampleSize=1 → no blur.
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    val maxHeightPx = (screenWidthPx * 8).coerceAtMost(16384)

    val model = remember(page.imageUrl, imageRetryKey, screenWidthPx) {
        ImageRequest.Builder(context)
            .data(page.imageUrl)
            .diskCacheKey(page.imageUrl)
            .size(CoilSize(Dimension.Pixels(screenWidthPx), Dimension.Pixels(maxHeightPx)))
            // Disable hardware bitmaps: hardware allocation silently returns null on some
            // devices when GPU memory is tight, causing the BitmapFactory null error.
            .allowHardware(false)
            // Do not keep off-screen reader pages in the memory cache. With large webtoon
            // strips (20-40 MB each), the LRU cache fills available heap before the next
            // chapter even starts decoding. The 512 MB disk cache is fast enough to
            // re-decode on re-scroll without the OOM risk.
            .memoryCachePolicy(CachePolicy.DISABLED)
            // Once a page decodes successfully, record its display height (screen-width × aspect
            // ratio). On subsequent loads from disk (memory cache is disabled, so every scroll-
            // back re-triggers a load) the placeholder will use this stored height, eliminating
            // the height oscillation that was causing the LazyColumn to shift the viewport.
            // Coil calls this listener on the main thread, so writing to a SnapshotStateMap here
            // is safe.
            .listener(onSuccess = { _, result ->
                val drawable = result.drawable
                val intrinsicW = drawable.intrinsicWidth
                val intrinsicH = drawable.intrinsicHeight
                if (intrinsicW > 0 && intrinsicH > 0) {
                    val displayHeightPx = screenWidthPx.toFloat() * intrinsicH / intrinsicW
                    onHeightKnownState.value?.invoke(with(density) { displayHeightPx.toDp() })
                }
            })
            .apply {
                page.refererUrl?.let { addHeader("Referer", it) }
            }
            .build()
    }
    // key() forces the AsyncImagePainter to be fully discarded and recreated on retry,
    // bypassing any internal Coil state that would short-circuit back to the error state.
    key(imageRetryKey) {
    SubcomposeAsyncImage(
        model = model,
        contentDescription = "Page ${page.index + 1}",
        contentScale = ContentScale.FillWidth,
        modifier = modifier,
        loading = {
            Box(
                // Use the last known decoded height so the placeholder is exactly the same
                // size as the loaded image. This prevents any layout shift when Coil
                // transitions between loading and success states — the LazyColumn never
                // sees a height change and therefore never snaps the scroll position.
                // Falls back to heightIn(min = 600.dp) only for pages that have never
                // loaded before in this session (first visit, no cached height yet).
                modifier = if (knownHeight != null) {
                    Modifier.fillMaxWidth().height(knownHeight)
                } else {
                    Modifier.fillMaxWidth().heightIn(min = 600.dp)
                },
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        },
        error = {
            val errState = painter.state as? AsyncImagePainter.State.Error
            val throwable = errState?.result?.throwable
            android.util.Log.e("PageImage", "Page ${page.index + 1} [${page.imageUrl}]", throwable)
            val errLabel = when (throwable) {
                is java.net.UnknownHostException -> "DNS failure"
                is java.net.SocketTimeoutException -> "Timeout"
                is java.io.IOException -> throwable.message?.take(80) ?: "Network error"
                is OutOfMemoryError -> "OOM"
                null -> "Unknown"
                else -> "${throwable.javaClass.simpleName}: ${throwable.message?.take(60)}"
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.3f),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.layout.Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    Text("Failed to load page ${page.index + 1}", color = Color.White, style = MaterialTheme.typography.bodySmall)
                    Text(errLabel, color = Color.Red, style = MaterialTheme.typography.labelSmall)
                    Text(page.imageUrl.take(80), color = Color.Gray, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                }
            }
        },
    )
    } // key(imageRetryKey)
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
    sortedChapters: List<Chapter>,
    currentChapterUrl: String,
    onBackClick: () -> Unit,
    onToggleMode: () -> Unit,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onRetryImages: () -> Unit,
    onChapterSelect: (String) -> Unit,
) {
    var showChapterPicker by remember { mutableStateOf(false) }
    val chapterScrollState = rememberScrollState()
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
        Box(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = sortedChapters.isNotEmpty()) { showChapterPicker = true },
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = if (currentChapterTitle.isNotEmpty()) "$currentChapterTitle  $currentPage / $totalPages"
                               else "$currentPage / $totalPages",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (sortedChapters.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            DropdownMenu(
                expanded = showChapterPicker,
                onDismissRequest = { showChapterPicker = false },
            ) {
                Column(
                    modifier = Modifier
                        .heightIn(max = 400.dp)
                        .verticalScroll(chapterScrollState),
                ) {
                    sortedChapters.forEach { chapter ->
                        val isActive = chapter.url.trimEnd('/') == currentChapterUrl.trimEnd('/')
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = chapter.title.ifBlank { "Chapter ${chapter.number.toInt()}" },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (isActive) MaterialTheme.colorScheme.primary
                                            else Color.Unspecified,
                                )
                            },
                            onClick = {
                                showChapterPicker = false
                                onChapterSelect(chapter.url)
                            },
                        )
                    }
                }
            }
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
