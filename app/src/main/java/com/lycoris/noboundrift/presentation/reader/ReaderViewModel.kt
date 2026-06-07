package com.lycoris.noboundrift.presentation.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lycoris.noboundrift.domain.model.Chapter
import com.lycoris.noboundrift.domain.model.Page
import com.lycoris.noboundrift.domain.repository.MangaRepository
import com.lycoris.noboundrift.domain.usecase.GetChapterListUseCase
import com.lycoris.noboundrift.domain.usecase.GetChapterPagesUseCase
import com.lycoris.noboundrift.domain.usecase.MarkChapterReadUseCase
import com.lycoris.noboundrift.presentation.navigation.Screen
import com.lycoris.noboundrift.presentation.navigation.decodeFromNav
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class ReaderMode { WEBTOON, PAGE_FLIP }

data class ReaderUiState(
    val pages: List<Page> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val currentPageIndex: Int = 0,
    val readerMode: ReaderMode = ReaderMode.WEBTOON,
    val showChrome: Boolean = true,
    val isLoadingNextChapter: Boolean = false,
    val canGoToPrevChapter: Boolean = false,
    val canGoToNextChapter: Boolean = false,
    val chapterKey: Int = 0,
    val mangaTitle: String = "",
    val currentChapterTitle: String = "",
    val imageRetryKey: Int = 0,
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getChapterPages: GetChapterPagesUseCase,
    private val getChapterList: GetChapterListUseCase,
    private val markChapterRead: MarkChapterReadUseCase,
    private val repository: MangaRepository,
) : ViewModel() {

    private val sourceId: Long = checkNotNull(savedStateHandle[Screen.Reader.ARG_SOURCE_ID])
    private val mangaUrl: String =
        checkNotNull(savedStateHandle.get<String>(Screen.Reader.ARG_MANGA_ID)).decodeFromNav()
    private val chapterUrl: String =
        checkNotNull(savedStateHandle.get<String>(Screen.Reader.ARG_CHAPTER_URL)).decodeFromNav()

    private val _uiState = MutableStateFlow(
        ReaderUiState(
            mangaTitle = savedStateHandle.get<String>(Screen.Reader.ARG_MANGA_TITLE)
                ?.decodeFromNav()
                .orEmpty(),
        )
    )
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    // Full chapter list in ascending reading order, populated once on init
    private var sortedChapters: List<Chapter> = emptyList()
    // URL of the next chapter to append; advances after each successful load; null = last chapter
    private var nextChapterUrl: String? = null

    private var currentChapterUrl: String = chapterUrl

    // Bug 1: track chapter segment boundaries for correct exit-progress accounting
    private data class ChapterSegment(val url: String, val startIndex: Int)
    private val chapterSegments = mutableListOf<ChapterSegment>()

    // Bug 2: track the near-end job so it can be cancelled on chapter jump
    private var nearEndJob: Job? = null

    init {
        loadPages()
        resolveNextChapter()
    }

    fun toggleReaderMode() {
        _uiState.update { it.copy(readerMode = it.readerMode.toggle()) }
    }

    fun toggleChrome() {
        _uiState.update { it.copy(showChrome = !it.showChrome) }
    }

    fun onPageChanged(index: Int) {
        val activeSegment = chapterSegments.lastOrNull { it.startIndex <= index }
        if (activeSegment != null && activeSegment.url.trimEnd('/') != currentChapterUrl.trimEnd('/')) {
            currentChapterUrl = activeSegment.url
            val newTitle = chapterTitleFor(activeSegment.url)
            _uiState.update {
                it.copy(
                    currentPageIndex = index,
                    currentChapterTitle = newTitle.ifEmpty { it.currentChapterTitle },
                    canGoToPrevChapter = findPrevBefore(activeSegment.url) != null,
                    canGoToNextChapter = findNextAfter(activeSegment.url) != null,
                )
            }
        } else {
            _uiState.update { it.copy(currentPageIndex = index) }
        }
    }

    fun retry() {
        _uiState.update { it.copy(error = null, isLoading = true) }
        loadPages()
    }

    /** Increments [ReaderUiState.imageRetryKey], causing all [PageImage]s to bust their cache. */
    fun retryImages() {
        _uiState.update { it.copy(imageRetryKey = it.imageRetryKey + 1) }
    }

    fun onExitReader() {
        val state = _uiState.value
        if (state.pages.isEmpty()) return

        val activeSegment = chapterSegments.lastOrNull { it.startIndex <= state.currentPageIndex }
            ?: return
        val chapter = sortedChapters.find { it.url.trimEnd('/') == activeSegment.url.trimEnd('/') }
            ?: return

        val nextSegmentStart = chapterSegments.firstOrNull { it.startIndex > state.currentPageIndex }?.startIndex
            ?: state.pages.size
        val chapterPageCount = nextSegmentStart - activeSegment.startIndex
        val pageWithinChapter = state.currentPageIndex - activeSegment.startIndex + 1
        val shouldMarkRead = chapterPageCount > 0 && (pageWithinChapter.toFloat() / chapterPageCount) >= 0.8f

        viewModelScope.launch {
            withContext(NonCancellable) {
                repository.touchLastOpenedChapter(chapter)
                if (shouldMarkRead) markChapterRead(chapter)
            }
        }
    }

    /**
     * Called by the UI when the reader is near the last page. Fetches the next
     * chapter's pages and appends them. Idempotent — does nothing if already
     * loading, already loaded, or there is no next chapter.
     */
    fun onNearEnd() {
        if (_uiState.value.isLoading) return   // guard: don't auto-append while initial pages are still loading
        if (_uiState.value.isLoadingNextChapter) return
        val url = nextChapterUrl ?: return
        val completingUrl = currentChapterUrl  // capture before any state changes

        _uiState.update { it.copy(isLoadingNextChapter = true) }
        nearEndJob = viewModelScope.launch {
            getChapterPages(sourceId = sourceId, chapterUrl = url)
                .onSuccess { newPages ->
                    if (newPages.isEmpty()) {
                        _uiState.update { it.copy(isLoadingNextChapter = false) }
                        return@onSuccess
                    }
                    // Mark the just-completed chapter as read. NonCancellable ensures the
                    // DB write goes through even if jumpToChapter cancels nearEndJob in flight.
                    sortedChapters.find { it.url.trimEnd('/') == completingUrl.trimEnd('/') }
                        ?.let { chapter -> withContext(NonCancellable) { markChapterRead(chapter) } }
                    // Record the segment boundary before appending (captures current page count)
                    chapterSegments.add(ChapterSegment(url, _uiState.value.pages.size))
                    val offset = _uiState.value.pages.size
                    val offsetPages = newPages.map { it.copy(index = offset + it.index) }
                    _uiState.update {
                        it.copy(pages = it.pages + offsetPages, isLoadingNextChapter = false)
                    }
                    // Advance the pointer so the next call loads the chapter after this one
                    nextChapterUrl = findNextAfter(url)
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    _uiState.update { it.copy(isLoadingNextChapter = false) }
                }
        }
    }

    fun goToPrevChapter() {
        val prev = findPrevBefore(currentChapterUrl) ?: return
        jumpToChapter(prev)
    }

    fun goToNextChapter() {
        val next = findNextAfter(currentChapterUrl) ?: return
        jumpToChapter(next)
    }

    private fun jumpToChapter(url: String) {
        nearEndJob?.cancel()
        chapterSegments.clear()
        currentChapterUrl = url
        nextChapterUrl = findNextAfter(url)
        _uiState.update {
            it.copy(
                pages = emptyList(),
                isLoading = true,
                error = null,
                currentPageIndex = 0,
                isLoadingNextChapter = false,
                canGoToPrevChapter = findPrevBefore(url) != null,
                canGoToNextChapter = findNextAfter(url) != null,
                chapterKey = it.chapterKey + 1,
                currentChapterTitle = chapterTitleFor(url),
            )
        }
        loadPages()
    }

    private fun loadPages() {
        viewModelScope.launch {
            getChapterPages(sourceId = sourceId, chapterUrl = currentChapterUrl)
                .onSuccess { pages ->
                    if (pages.isEmpty()) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = "No pages found. This chapter may require a premium subscription.",
                            )
                        }
                    } else {
                        // Seed the segment list for this chapter before updating state
                        chapterSegments.clear()
                        chapterSegments.add(ChapterSegment(currentChapterUrl, 0))
                        _uiState.update { it.copy(pages = pages, isLoading = false) }
                    }
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = throwable.message ?: "Failed to load pages",
                        )
                    }
                }
        }
    }

    private fun resolveNextChapter() {
        viewModelScope.launch {
            getChapterList(sourceId = sourceId, mangaUrl = mangaUrl)
                .onSuccess { chapters ->
                    sortedChapters = chapters.sortedBy { it.number }
                    nextChapterUrl = findNextAfter(currentChapterUrl)
                    _uiState.update {
                        it.copy(
                            canGoToPrevChapter = findPrevBefore(currentChapterUrl) != null,
                            canGoToNextChapter = findNextAfter(currentChapterUrl) != null,
                            currentChapterTitle = chapterTitleFor(currentChapterUrl),
                        )
                    }
                    // Bug 3: if pages are already loaded and the reader is near the end,
                    // the onNearEnd that fired during init found nextChapterUrl == null and
                    // returned early — trigger it now that the chapter list is resolved.
                    val state = _uiState.value
                    if (nextChapterUrl != null &&
                        state.pages.isNotEmpty() &&
                        !state.isLoadingNextChapter &&
                        state.currentPageIndex >= state.pages.size - 3
                    ) {
                        onNearEnd()
                    }
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                }
        }
    }

    private fun chapterTitleFor(url: String): String {
        val chapter = sortedChapters.find { it.url.trimEnd('/') == url.trimEnd('/') }
            ?: return ""
        return chapter.title.ifBlank { "Chapter ${chapter.number.toInt()}" }
    }

    private fun findNextAfter(url: String): String? {
        val normalized = url.trimEnd('/')
        val idx = sortedChapters.indexOfFirst { it.url.trimEnd('/') == normalized }
        return if (idx != -1 && idx + 1 < sortedChapters.size) sortedChapters[idx + 1].url else null
    }

    private fun findPrevBefore(url: String): String? {
        val normalized = url.trimEnd('/')
        val idx = sortedChapters.indexOfFirst { it.url.trimEnd('/') == normalized }
        return if (idx > 0) sortedChapters[idx - 1].url else null
    }

    private fun ReaderMode.toggle() = when (this) {
        ReaderMode.WEBTOON -> ReaderMode.PAGE_FLIP
        ReaderMode.PAGE_FLIP -> ReaderMode.WEBTOON
    }
}
