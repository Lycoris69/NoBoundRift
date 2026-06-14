package com.lycoris.noboundrift.presentation.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lycoris.noboundrift.data.local.EmptyChapterCache
import com.lycoris.noboundrift.data.local.entity.DownloadEntity
import com.lycoris.noboundrift.domain.model.Chapter
import com.lycoris.noboundrift.domain.model.Manga
import com.lycoris.noboundrift.domain.model.MangaPreview
import com.lycoris.noboundrift.domain.model.MangaStatus
import com.lycoris.noboundrift.domain.usecase.DeleteDownloadUseCase
import com.lycoris.noboundrift.domain.usecase.GetDownloadsUseCase
import com.lycoris.noboundrift.domain.usecase.GetMangaDetailUseCase
import com.lycoris.noboundrift.domain.usecase.QueueDownloadUseCase
import com.lycoris.noboundrift.domain.usecase.ToggleLibraryUseCase
import com.lycoris.noboundrift.domain.repository.MangaRepository
import com.lycoris.noboundrift.presentation.navigation.Screen
import com.lycoris.noboundrift.presentation.navigation.decodeFromNav
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class DetailTab { CHAPTERS, DOWNLOADS }

sealed interface DetailUiState {
    data object Loading : DetailUiState
    data class Success(
        val manga: Manga,
        val isInLibrary: Boolean = false,
        val isLoadingChapters: Boolean = false,
        val chaptersReversed: Boolean = true,
        val lastReadChapterUrl: String? = null,
        val availableLanguages: List<String> = emptyList(),
        val selectedLanguage: String = "",
        val selectedTab: DetailTab = DetailTab.CHAPTERS,
        val downloads: Map<String, DownloadEntity> = emptyMap(),
    ) : DetailUiState
    data class Error(val message: String) : DetailUiState
}

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getMangaDetail: GetMangaDetailUseCase,
    private val toggleLibrary: ToggleLibraryUseCase,
    private val repository: MangaRepository,
    private val emptyChapterCache: EmptyChapterCache,
    private val queueDownload: QueueDownloadUseCase,
    private val deleteDownloadUseCase: DeleteDownloadUseCase,
    private val getDownloads: GetDownloadsUseCase,
) : ViewModel() {

    private val sourceId: Long = checkNotNull(savedStateHandle[Screen.Detail.ARG_SOURCE_ID])
    private val mangaUrl: String =
        checkNotNull(savedStateHandle.get<String>(Screen.Detail.ARG_URL)).decodeFromNav()

    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var observersJob: Job? = null

    init {
        loadDetail()
    }

    fun retry() = loadDetail()

    fun toggleChapterOrder() {
        _uiState.update { state ->
            (state as? DetailUiState.Success)?.copy(chaptersReversed = !state.chaptersReversed) ?: state
        }
    }

    fun setLanguage(language: String) {
        _uiState.update { state ->
            (state as? DetailUiState.Success)?.copy(selectedLanguage = language) ?: state
        }
    }

    fun toggleLibrary() {
        val state = _uiState.value as? DetailUiState.Success ?: return
        viewModelScope.launch {
            val preview = state.manga.toPreview()
            toggleLibrary(preview, state.isInLibrary)
            // Room Flow collector will update isInLibrary within one write cycle; no optimistic update needed.
        }
    }

    fun setTab(tab: DetailTab) {
        _uiState.update { state ->
            (state as? DetailUiState.Success)?.copy(selectedTab = tab) ?: state
        }
    }

    fun downloadChapter(chapter: Chapter) {
        val state = _uiState.value as? DetailUiState.Success ?: return
        viewModelScope.launch {
            queueDownload(
                sourceId = state.manga.sourceId,
                mangaId = state.manga.id,
                mangaUrl = state.manga.url,
                mangaTitle = state.manga.title,
                mangaCoverUrl = state.manga.coverUrl,
                chapter = chapter,
            )
        }
    }

    fun downloadAllChapters() {
        val state = _uiState.value as? DetailUiState.Success ?: return
        viewModelScope.launch {
            queueDownload.all(
                sourceId = state.manga.sourceId,
                mangaId = state.manga.id,
                mangaUrl = state.manga.url,
                mangaTitle = state.manga.title,
                mangaCoverUrl = state.manga.coverUrl,
                chapters = state.manga.chapters,
            )
        }
    }

    fun deleteDownload(chapterUrl: String) {
        viewModelScope.launch { deleteDownloadUseCase(chapterUrl) }
    }

    private fun loadDetail() {
        loadJob?.cancel()
        observersJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = DetailUiState.Loading
            getMangaDetail(sourceId = sourceId, url = mangaUrl)
                .onSuccess { manga ->
                    // Show metadata immediately while chapters are still loading
                    _uiState.value = DetailUiState.Success(
                        manga = manga,
                        isLoadingChapters = true,
                    )
                    val chapters = repository.fetchChapterList(sourceId, mangaUrl)
                        .getOrElse { emptyList() }
                    val mangaWithChapters = manga.copy(chapters = chapters)
                    val languages = chapters.map { it.language }.filter { it.isNotBlank() }.distinct().sorted()
                    val defaultLang = if ("en" in languages) "en" else languages.firstOrNull() ?: ""
                    _uiState.value = DetailUiState.Success(
                        manga = mangaWithChapters,
                        isLoadingChapters = false,
                        availableLanguages = languages,
                        selectedLanguage = defaultLang,
                    )

                    if (chapters.isEmpty()) emptyChapterCache.mark(manga.id)
                    else emptyChapterCache.unmark(manga.id)
                    val latestAt = chapters.maxOfOrNull { it.dateUpload } ?: 0L
                    if (latestAt > 0L) {
                        launch { repository.updateLatestChapterAt(manga.id, latestAt) }
                    }

                    startObservers(manga.id)
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    // Offline fallback: if completed downloads exist for this URL, build
                    // a minimal detail view from local data so the user can still read.
                    val offlineChapters = getDownloads.forMangaUrl(mangaUrl)
                    if (offlineChapters.isNotEmpty()) {
                        val first = offlineChapters.first()
                        val offlineManga = Manga(
                            id = first.mangaId,
                            title = first.mangaTitle,
                            coverUrl = first.mangaCoverUrl,
                            synopsis = "",
                            genres = emptyList(),
                            status = MangaStatus.UNKNOWN,
                            sourceId = first.sourceId,
                            url = first.mangaUrl,
                            chapters = offlineChapters.map { entity ->
                                Chapter(
                                    id = entity.chapterUrl,
                                    mangaId = entity.mangaId,
                                    title = entity.chapterTitle,
                                    number = entity.chapterNumber,
                                    url = entity.chapterUrl,
                                )
                            },
                        )
                        _uiState.value = DetailUiState.Success(
                            manga = offlineManga,
                            isLoadingChapters = false,
                        )
                        startObservers(first.mangaId)
                    } else {
                        _uiState.value = DetailUiState.Error(
                            throwable.message ?: "Failed to load manga details"
                        )
                    }
                }
        }
    }

    private fun startObservers(mangaId: String) {
        observersJob?.cancel()
        observersJob = viewModelScope.launch {
            launch {
                repository.isInLibrary(mangaId).collect { inLib ->
                    _uiState.update { state ->
                        (state as? DetailUiState.Success)?.copy(isInLibrary = inLib) ?: state
                    }
                }
            }
            launch {
                repository.getLastReadChapter(mangaId).collect { url ->
                    _uiState.update { state ->
                        (state as? DetailUiState.Success)?.copy(lastReadChapterUrl = url) ?: state
                    }
                }
            }
            launch {
                repository.observeReadChapterUrls(mangaId).collect { readUrls ->
                    _uiState.update { state ->
                        (state as? DetailUiState.Success)?.let { s ->
                            val updated = s.manga.chapters.map { ch ->
                                ch.copy(read = ch.url.trimEnd('/') in readUrls)
                            }
                            s.copy(manga = s.manga.copy(chapters = updated))
                        } ?: state
                    }
                }
            }
            launch {
                getDownloads(mangaId).collect { entities ->
                    val map = entities.associateBy { it.chapterUrl }
                    _uiState.update { state ->
                        (state as? DetailUiState.Success)?.copy(downloads = map) ?: state
                    }
                }
            }
        }
    }

    private fun Manga.toPreview() = MangaPreview(
        id = id,
        title = title,
        coverUrl = coverUrl,
        sourceId = sourceId,
        url = url,
    )
}
