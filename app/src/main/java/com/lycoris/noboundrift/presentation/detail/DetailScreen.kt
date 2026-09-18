package com.lycoris.noboundrift.presentation.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.lycoris.noboundrift.data.local.entity.DownloadEntity
import com.lycoris.noboundrift.data.local.entity.DownloadStatus
import com.lycoris.noboundrift.domain.model.Chapter
import com.lycoris.noboundrift.domain.model.Manga
import com.lycoris.noboundrift.domain.model.MangaPreview
import com.lycoris.noboundrift.presentation.theme.ReadIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    onChapterClick: (sourceId: Long, mangaId: String, chapterUrl: String, mangaTitle: String) -> Unit,
    onBackClick: () -> Unit,
    onMigrateToSource: ((title: String) -> Unit)? = null,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sourceSwitcher by viewModel.sourceSwitcher.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val title = (uiState as? DetailUiState.Success)?.manga?.title ?: ""
                    Text(text = title, maxLines = 1)
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState is DetailUiState.Success) {
                        val state = uiState as DetailUiState.Success
                        IconButton(onClick = viewModel::toggleLibrary) {
                            Icon(
                                imageVector = if (state.isInLibrary) Icons.Default.BookmarkRemove
                                else Icons.Default.BookmarkAdd,
                                contentDescription = if (state.isInLibrary) "Remove from library"
                                else "Add to library",
                                tint = if (state.isInLibrary) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        // Overflow menu — only when in library (source switching requires a library entry)
                        if (state.isInLibrary) {
                            var showMenu by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "More options")
                                }
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Change Source") },
                                        leadingIcon = {
                                            Icon(Icons.Default.SwapHoriz, contentDescription = null)
                                        },
                                        onClick = {
                                            showMenu = false
                                            viewModel.openSourceSwitcher()
                                        },
                                    )
                                }
                            }
                        }
                    }
                },
            )
        },
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (val state = uiState) {
                is DetailUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is DetailUiState.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(32.dp),
                        ) {
                            Text(text = state.message, color = MaterialTheme.colorScheme.error)
                            if (viewModel.isSourceOffline && onMigrateToSource != null) {
                                Button(onClick = { onMigrateToSource("") }) {
                                    Text("Search on MangaDex")
                                }
                            } else {
                                Button(onClick = viewModel::retry) { Text("Retry") }
                            }
                        }
                    }
                }
                is DetailUiState.Success -> {
                    MangaDetail(
                        manga = state.manga,
                        sourceName = viewModel.sourceName,
                        isLoadingChapters = state.isLoadingChapters,
                        chaptersReversed = state.chaptersReversed,
                        lastReadChapterUrl = state.lastReadChapterUrl,
                        availableLanguages = state.availableLanguages,
                        selectedLanguage = state.selectedLanguage,
                        selectedTab = state.selectedTab,
                        downloads = state.downloads,
                        onTabSelect = viewModel::setTab,
                        onToggleOrder = viewModel::toggleChapterOrder,
                        onLanguageSelect = viewModel::setLanguage,
                        onChapterClick = { chapter ->
                            onChapterClick(state.manga.sourceId, state.manga.url, chapter.url, state.manga.title)
                        },
                        onDownloadChapter = viewModel::downloadChapter,
                        onDownloadAll = viewModel::downloadAllChapters,
                        onCancelAll = viewModel::cancelAllDownloads,
                        onDeleteDownload = viewModel::deleteDownload,
                        onMigrateToSource = if (state.manga.sourceId == 3L) onMigrateToSource else null,
                    )
                }
            }
        }
    }

    // Source switcher sheet — rendered outside Scaffold so it overlays everything
    if (sourceSwitcher != null) {
        SourceSwitcherSheet(
            state = sourceSwitcher!!,
            sources = viewModel.switchSources,
            onSelectSource = viewModel::selectSwitchTarget,
            onQueryChange = viewModel::updateSwitchQuery,
            onConfirmSwitch = viewModel::confirmSourceSwitch,
            onBackToPickSource = viewModel::goBackToPickSource,
            onDismiss = viewModel::closeSourceSwitcher,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceSwitcherSheet(
    state: SourceSwitcherState,
    sources: List<Pair<Long, String>>,
    onSelectSource: (Long, String) -> Unit,
    onQueryChange: (String) -> Unit,
    onConfirmSwitch: (MangaPreview) -> Unit,
    onBackToPickSource: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        when (state.step) {
            SourceSwitcherStep.PICK_SOURCE -> {
                Text(
                    text = "Change Source",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
                HorizontalDivider()
                sources.forEach { (id, name) ->
                    ListItem(
                        headlineContent = { Text(name) },
                        modifier = Modifier.clickable { onSelectSource(id, name) },
                    )
                }
                Spacer(Modifier.height(24.dp))
            }

            SourceSwitcherStep.SEARCH -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBackToPickSource) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back to source list")
                    }
                    Text(
                        text = "Search on ${state.targetSourceName}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    label = { Text("Title") },
                    singleLine = true,
                )
                if (state.isSearching) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                if (state.searchError != null) {
                    Text(
                        text = state.searchError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 32.dp),
                ) {
                    items(state.results, key = { it.id }) { preview ->
                        ListItem(
                            headlineContent = { Text(preview.title) },
                            leadingContent = {
                                AsyncImage(
                                    model = preview.coverUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(width = 48.dp, height = 72.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                            },
                            modifier = Modifier.clickable { onConfirmSwitch(preview) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun MangaDetail(
    manga: Manga,
    sourceName: String,
    isLoadingChapters: Boolean,
    chaptersReversed: Boolean,
    lastReadChapterUrl: String?,
    availableLanguages: List<String>,
    selectedLanguage: String,
    selectedTab: DetailTab,
    downloads: Map<String, DownloadEntity>,
    onTabSelect: (DetailTab) -> Unit,
    onToggleOrder: () -> Unit,
    onLanguageSelect: (String) -> Unit,
    onChapterClick: (Chapter) -> Unit,
    onDownloadChapter: (Chapter) -> Unit,
    onDownloadAll: () -> Unit,
    onCancelAll: () -> Unit,
    onDeleteDownload: (String) -> Unit,
    onMigrateToSource: ((String) -> Unit)? = null,
) {
    val continueChapter = remember(manga.chapters, lastReadChapterUrl) {
        lastReadChapterUrl?.let { url -> manga.chapters.find { it.url.trimEnd('/') == url } }
    }

    val displayedChapters = remember(manga.chapters, chaptersReversed, selectedLanguage, availableLanguages) {
        val filtered = when {
            selectedLanguage.isBlank() -> manga.chapters
            availableLanguages.size > 1 -> manga.chapters.filter { it.language == selectedLanguage }
            else -> manga.chapters.filter { it.language.isEmpty() || it.language == selectedLanguage }
        }
        if (chaptersReversed) filtered.reversed() else filtered
    }

    val allDownloaded = remember(displayedChapters, downloads) {
        displayedChapters.isNotEmpty() &&
            displayedChapters.all { downloads[it.url.trimEnd('/')]?.status == DownloadStatus.COMPLETED }
    }

    val hasActiveDownloads = remember(displayedChapters, downloads) {
        displayedChapters.any {
            val s = downloads[it.url.trimEnd('/')]?.status
            s == DownloadStatus.QUEUED || s == DownloadStatus.DOWNLOADING
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Cover + metadata header
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AsyncImage(
                    model = manga.coverUrl,
                    contentDescription = manga.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(120.dp)
                        .height(180.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = manga.title,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        text = manga.status.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (sourceName.isNotBlank()) {
                        Text(
                            text = sourceName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        // Offline-source migration banner
        if (onMigrateToSource != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.WifiOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(20.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Source offline",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Text(
                                text = "Manhwaz is unreachable. Find this title elsewhere.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                        TextButton(onClick = { onMigrateToSource(manga.title) }) {
                            Text("MangaDex")
                        }
                    }
                }
            }
        }

        // Genre chips
        if (manga.genres.isNotEmpty()) {
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    manga.genres.forEach { genre ->
                        SuggestionChip(
                            onClick = {},
                            label = { Text(genre, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }
        }

        // Synopsis
        if (manga.synopsis.isNotBlank()) {
            item {
                Text(
                    text = manga.synopsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Tab row — Chapters / Downloads
        item {
            TabRow(selectedTabIndex = selectedTab.ordinal) {
                DetailTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { onTabSelect(tab) },
                        text = { Text(if (tab == DetailTab.CHAPTERS) "Chapters" else "Downloads") },
                    )
                }
            }
        }

        if (selectedTab == DetailTab.CHAPTERS) {
            // Chapter list header
            item {
                HorizontalDivider()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text(
                        text = "${displayedChapters.size} Chapters",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (isLoadingChapters) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    IconButton(onClick = onToggleOrder, enabled = !isLoadingChapters) {
                        Icon(
                            imageVector = if (chaptersReversed) Icons.Default.KeyboardArrowUp
                            else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (chaptersReversed) "Oldest first" else "Newest first",
                        )
                    }
                }
            }

            // Language selector
            if (availableLanguages.size > 1) {
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(availableLanguages) { lang ->
                            FilterChip(
                                selected = lang == selectedLanguage,
                                onClick = { onLanguageSelect(lang) },
                                label = {
                                    Text(
                                        text = java.util.Locale(lang).displayLanguage.ifBlank { lang.uppercase() },
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                },
                            )
                        }
                    }
                }
            }

            // Continue Reading button
            if (continueChapter != null) {
                item {
                    val n = continueChapter.number
                    val label = continueChapter.title.ifBlank { "Chapter ${if (n % 1f == 0f) n.toInt() else n}" }
                    Button(
                        onClick = { onChapterClick(continueChapter) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = "Continue: $label")
                    }
                }
            }

            // Chapter rows
            items(displayedChapters, key = { it.id }) { chapter ->
                ChapterRow(
                    chapter = chapter,
                    isDownloaded = downloads[chapter.url.trimEnd('/')]?.status == DownloadStatus.COMPLETED,
                    onClick = { onChapterClick(chapter) },
                )
            }
        } else {
            // Downloads tab content
            if (hasActiveDownloads) {
                item {
                    Button(
                        onClick = onCancelAll,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text("Cancel All")
                    }
                }
            } else if (!allDownloaded) {
                item {
                    Button(onClick = onDownloadAll, modifier = Modifier.fillMaxWidth()) {
                        Text("Download All")
                    }
                }
            }

            items(displayedChapters, key = { it.id }) { chapter ->
                DownloadChapterRow(
                    chapter = chapter,
                    entity = downloads[chapter.url.trimEnd('/')],
                    onDownload = { onDownloadChapter(chapter) },
                    onDelete = { onDeleteDownload(chapter.url) },
                )
            }
        }
    }
}

@Composable
private fun ChapterRow(chapter: Chapter, isDownloaded: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = chapter.title.ifBlank { val n = chapter.number; "Chapter ${if (n % 1f == 0f) n.toInt() else n}" },
                style = MaterialTheme.typography.bodyMedium,
                color = if (chapter.read) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
            )
            if (chapter.dateUpload > 0L) {
                Text(
                    text = formatChapterDate(chapter.dateUpload),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (chapter.read) {
            Box(
                modifier = Modifier
                    .background(ReadIndicator, shape = RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "READ",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }
        }
        if (isDownloaded) {
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.DownloadDone,
                contentDescription = "Downloaded",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        if (chapter.isLocked) {
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Premium chapter",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DownloadChapterRow(
    chapter: Chapter,
    entity: DownloadEntity?,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = chapter.title.ifBlank { val n = chapter.number; "Chapter ${if (n % 1f == 0f) n.toInt() else n}" },
                style = MaterialTheme.typography.bodyMedium,
            )
            val statusText = when (entity?.status) {
                DownloadStatus.QUEUED -> "Queued"
                DownloadStatus.DOWNLOADING -> "${entity.downloadedPages} / ${entity.totalPages} pages"
                DownloadStatus.COMPLETED -> "Downloaded"
                DownloadStatus.FAILED -> "Failed"
                null -> ""
            }
            if (statusText.isNotEmpty()) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        when (entity?.status) {
            null, DownloadStatus.FAILED -> {
                IconButton(onClick = onDownload) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = "Download",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING -> {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Cancel download",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
            DownloadStatus.COMPLETED -> {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete download",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private val CHAPTER_DATE_FORMATTER: java.time.format.DateTimeFormatter =
    java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy", java.util.Locale.ENGLISH)

private fun formatChapterDate(millis: Long): String =
    java.time.Instant.ofEpochMilli(millis)
        .atZone(java.time.ZoneOffset.UTC)
        .toLocalDate()
        .format(CHAPTER_DATE_FORMATTER)
