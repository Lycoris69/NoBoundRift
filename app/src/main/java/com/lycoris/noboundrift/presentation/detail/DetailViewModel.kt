package com.lycoris.noboundrift.presentation.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lycoris.noboundrift.domain.model.Manga
import com.lycoris.noboundrift.domain.model.MangaPreview
import com.lycoris.noboundrift.domain.usecase.GetMangaDetailUseCase
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
    ) : DetailUiState
    data class Error(val message: String) : DetailUiState
}

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getMangaDetail: GetMangaDetailUseCase,
    private val toggleLibrary: ToggleLibraryUseCase,
    private val repository: MangaRepository,
) : ViewModel() {

    private val sourceId: Long = checkNotNull(savedStateHandle[Screen.Detail.ARG_SOURCE_ID])
    private val mangaUrl: String =
        checkNotNull(savedStateHandle.get<String>(Screen.Detail.ARG_URL)).decodeFromNav()

    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

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
            // Optimistically update the UI without waiting for the DB flow
            _uiState.update { (it as? DetailUiState.Success)?.copy(isInLibrary = !state.isInLibrary) ?: it }
        }
    }

    private fun loadDetail() {
        loadJob?.cancel()
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

                    val latestAt = chapters.maxOfOrNull { it.dateUpload } ?: 0L
                    when {
                        // -1L signals browse enrichment to show "0 ch" badge on the card
                        chapters.isEmpty() -> launch { repository.updateLatestChapterAt(manga.id, -1L) }
                        latestAt > 0L -> launch { repository.updateLatestChapterAt(manga.id, latestAt) }
                    }
    
                    // Both observers are children of loadJob — cancelled automatically when loadJob is cancelled
                    launch {
                        repository.isInLibrary(manga.id).collect { inLib ->
                            _uiState.update { state ->
                                (state as? DetailUiState.Success)?.copy(isInLibrary = inLib) ?: state
                            }
                        }
                    }
                    launch {
                        repository.getLastReadChapter(manga.id).collect { url ->
                            _uiState.update { state ->
                                (state as? DetailUiState.Success)?.copy(lastReadChapterUrl = url) ?: state
                            }
                        }
                    }
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    _uiState.value = DetailUiState.Error(
                        throwable.message ?: "Failed to load manga details"
                    )
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
