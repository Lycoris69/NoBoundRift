package com.lycoris.noboundrift.presentation.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lycoris.noboundrift.data.local.AccessibilityPreferences
import com.lycoris.noboundrift.data.local.BrowsePreferences
import com.lycoris.noboundrift.data.local.LibraryLayout
import com.lycoris.noboundrift.data.local.LibraryPreferences
import com.lycoris.noboundrift.data.local.LibrarySortOrder
import com.lycoris.noboundrift.data.local.entity.DownloadEntity
import com.lycoris.noboundrift.data.local.entity.DownloadStatus
import com.lycoris.noboundrift.domain.model.MangaPreview
import com.lycoris.noboundrift.domain.repository.DownloadRepository
import com.lycoris.noboundrift.domain.repository.MangaRepository
import com.lycoris.noboundrift.domain.usecase.CancelAllDownloadsUseCase
import com.lycoris.noboundrift.domain.usecase.DeleteDownloadUseCase
import com.lycoris.noboundrift.domain.usecase.GetDownloadsUseCase
import com.lycoris.noboundrift.domain.usecase.GetLibraryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

enum class LibraryTab { LIBRARY, DOWNLOADS }

data class MangaDownloadGroup(
    val mangaId: String,
    val mangaUrl: String,
    val sourceId: Long,
    val mangaTitle: String,
    val mangaCoverUrl: String,
    val chapters: List<DownloadEntity>,
    val completedCount: Int,
    val totalCount: Int,
)

data class LibraryUiState(
    val manga: List<MangaPreview> = emptyList(),
    val isEmpty: Boolean = false,
    val selectedTab: LibraryTab = LibraryTab.LIBRARY,
    val downloadGroups: List<MangaDownloadGroup> = emptyList(),
    val libraryLayout: LibraryLayout = LibraryLayout.GRID,
    val libraryGridColumns: Int = 3,
    val librarySortOrder: LibrarySortOrder = LibrarySortOrder.CUSTOM,
    val roundedCovers: Boolean = true,
    val blurCovers: Boolean = false,
    val hapticFeedback: Boolean = true,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    getLibrary: GetLibraryUseCase,
    private val repository: MangaRepository,
    getDownloads: GetDownloadsUseCase,
    libraryPreferences: LibraryPreferences,
    private val browsePreferences: BrowsePreferences,
    private val accessibilityPreferences: AccessibilityPreferences,
    private val deleteDownloadUseCase: DeleteDownloadUseCase,
    private val cancelAllDownloadsUseCase: CancelAllDownloadsUseCase,
    private val downloadRepository: DownloadRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        LibraryUiState(
            libraryGridColumns = libraryPreferences.getGridColumns(),
            librarySortOrder = libraryPreferences.getSortOrder(),
            roundedCovers = libraryPreferences.isRoundedCovers(),
            blurCovers = browsePreferences.isBlurCovers(),
            hapticFeedback = accessibilityPreferences.isHapticFeedback(),
        )
    )
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    // Counter of in-flight reorder writes. DB Flow emissions are suppressed while > 0.
    // Using a counter (not boolean) so a rapid cancel+relaunch can't clear the flag
    // while a new write is already in flight: the old job's finally { -- } fires after
    // the new job's ++ , keeping suppressDbUpdates true until the new write completes.
    private var activeReorderWrites = 0
    private val suppressDbUpdates get() = activeReorderWrites > 0
    private var reorderJob: Job? = null
    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            getLibrary().collect { list ->
                if (!suppressDbUpdates) {
                    val sorted = when (_uiState.value.librarySortOrder) {
                        LibrarySortOrder.CUSTOM -> list.sortedByDescending {
                            it.latestChapterAt > System.currentTimeMillis() - 7L * 24 * 3600 * 1000 && !it.isLatestChapterRead
                        }
                        LibrarySortOrder.TITLE -> list.sortedBy { it.title.lowercase() }
                        LibrarySortOrder.UPDATED -> list.sortedByDescending { it.latestChapterAt }
                        // Unrated (0) treated as mid (3) so they land between rated items;
                        // explicit ratings always win over the unrated default.
                        LibrarySortOrder.RATING -> list.sortedWith(
                            compareByDescending<MangaPreview> { if (it.rating == 0) 3 else it.rating }
                                .thenByDescending { it.rating != 0 }
                        )
                    }
                    _uiState.update { current ->
                        current.copy(manga = sorted, isEmpty = sorted.isEmpty())
                    }
                }
            }
        }

        viewModelScope.launch {
            libraryPreferences.observeLibraryLayout().collect { layout ->
                _uiState.update { it.copy(libraryLayout = layout) }
            }
        }

        viewModelScope.launch {
            libraryPreferences.observeGridColumns().collect { cols ->
                _uiState.update { it.copy(libraryGridColumns = cols) }
            }
        }

        viewModelScope.launch {
            libraryPreferences.observeSortOrder().collect { order ->
                _uiState.update { it.copy(librarySortOrder = order) }
            }
        }

        viewModelScope.launch {
            libraryPreferences.observeRoundedCovers().collect { rounded ->
                _uiState.update { it.copy(roundedCovers = rounded) }
            }
        }

        viewModelScope.launch {
            browsePreferences.observeBlurCovers().collect { blur ->
                _uiState.update { it.copy(blurCovers = blur) }
            }
        }

        viewModelScope.launch {
            accessibilityPreferences.observeHapticFeedback().collect { enabled ->
                _uiState.update { it.copy(hapticFeedback = enabled) }
            }
        }

        viewModelScope.launch {
            getDownloads.all().collect { entities ->
                val groups = entities
                    .groupBy { it.mangaId }
                    .map { (_, list) ->
                        val first = list.first()
                        MangaDownloadGroup(
                            mangaId = first.mangaId,
                            mangaUrl = first.mangaUrl,
                            sourceId = first.sourceId,
                            mangaTitle = first.mangaTitle,
                            mangaCoverUrl = first.mangaCoverUrl,
                            chapters = list.sortedBy { it.chapterNumber },
                            completedCount = list.count { it.status == DownloadStatus.COMPLETED },
                            totalCount = list.size,
                        )
                    }
                    .sortedBy { it.mangaTitle }
                _uiState.update { it.copy(downloadGroups = groups) }
            }
        }

        refreshLatestChapters()
    }

    private fun refreshLatestChapters() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val library = withTimeoutOrNull(10_000L) {
                _uiState.first { it.manga.isNotEmpty() }
            }?.manga ?: return@launch
            val semaphore = Semaphore(3)
            library.forEach { preview ->
                launch {
                    semaphore.withPermit {
                        repository.fetchChapterList(preview.sourceId, preview.url)
                            .onSuccess { chapters ->
                                if (chapters.isEmpty()) return@onSuccess
                                val latestUrl = chapters.last().url.trimEnd('/')
                                if (latestUrl == preview.latestChapterUrl) return@onSuccess
                                val latestAt = chapters.last().dateUpload
                                if (latestAt > 0L) repository.updateLatestChapterAt(preview.id, latestAt, latestUrl)
                            }
                    }
                }
            }
        }
    }

    fun setTab(tab: LibraryTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun onMove(fromIndex: Int, toIndex: Int) {
        val current = _uiState.value.manga.toMutableList()
        if (fromIndex !in current.indices || toIndex !in current.indices) return
        val item = current.removeAt(fromIndex)
        current.add(toIndex, item)
        _uiState.update { it.copy(manga = current) }
    }

    fun onDragEnd() {
        val orderedIds = _uiState.value.manga.map { it.id }
        reorderJob?.cancel()
        reorderJob = viewModelScope.launch {
            activeReorderWrites++
            try {
                repository.reorderLibrary(orderedIds)
            } finally {
                activeReorderWrites--
            }
        }
    }

    fun cancelDownload(chapterUrl: String) {
        viewModelScope.launch { downloadRepository.cancelDownload(chapterUrl) }
    }

    fun retryDownload(entity: DownloadEntity) {
        viewModelScope.launch { downloadRepository.retryDownload(entity) }
    }

    fun deleteDownload(chapterUrl: String) {
        viewModelScope.launch { deleteDownloadUseCase(chapterUrl) }
    }

    fun cancelAllDownloads(mangaId: String) {
        viewModelScope.launch { cancelAllDownloadsUseCase(mangaId) }
    }

    /** Sets a 1–5 star rating for a manga. Pass 0 to clear. */
    fun setRating(mangaId: String, rating: Int) {
        viewModelScope.launch { repository.setRating(mangaId, rating) }
    }
}
