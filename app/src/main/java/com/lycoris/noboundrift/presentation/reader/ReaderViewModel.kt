package com.lycoris.noboundrift.presentation.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lycoris.noboundrift.domain.model.Chapter
import com.lycoris.noboundrift.domain.model.Page
import com.lycoris.noboundrift.domain.usecase.GetChapterListUseCase
import com.lycoris.noboundrift.domain.usecase.GetChapterPagesUseCase
import com.lycoris.noboundrift.domain.usecase.MarkChapterReadUseCase
import com.lycoris.noboundrift.presentation.navigation.Screen
import com.lycoris.noboundrift.presentation.navigation.decodeFromNav
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getChapterPages: GetChapterPagesUseCase,
    private val getChapterList: GetChapterListUseCase,
    private val markChapterRead: MarkChapterReadUseCase,
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
        _uiState.update { it.copy(currentPageIndex = index) }
    }

    fun retry() {
        _uiState.update { it.copy(error = null, isLoading = true) }
        loadPages()
    }

    /**
     * Called when the user leaves the reader. Marks the chapter as read
     * if they reached at least 80% through the pages.
     */
    fun onExitReader() {
        val state = _uiState.value
        if (state.pages.isEmpty()) return
        val progress = (state.currentPageIndex + 1).toFloat() / state.pages.size
        if (progress >= 0.8f) {
            val chapter = sortedChapters.find { it.url.trimEnd('/') == currentChapterUrl.trimEnd('/') }
                ?: return
            viewModelScope.launch {
                markChapterRead(chapter)
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

        _uiState.update { it.copy(isLoadingNextChapter = true) }
        viewModelScope.launch {
            getChapterPages(sourceId = sourceId, chapterUrl = url)
                .onSuccess { newPages ->
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
                    _uiState.update { it.copy(pages = pages, isLoading = false) }
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
