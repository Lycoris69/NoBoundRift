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
        checkNotNull(savedStateHandle[Screen.Detail.ARG_URL]).decodeFromNav()

    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    init {
        loadDetail()
    }

    fun retry() = loadDetail()

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
        viewModelScope.launch {
            _uiState.value = DetailUiState.Loading
            getMangaDetail(sourceId = sourceId, url = mangaUrl)
                .onSuccess { manga ->
                    // Check library status — we do a single read here; a real impl
                    // would collect a Flow<Boolean> and keep the state live.
                    _uiState.value = DetailUiState.Success(manga = manga)
                    // Observe library state reactively after initial load
                    repository.isInLibrary(manga.id).collect { inLib ->
                        _uiState.update { state ->
                            (state as? DetailUiState.Success)?.copy(isInLibrary = inLib) ?: state
                        }
                    }
                }
                .onFailure { throwable ->
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
