package com.lycoris.noboundrift.presentation.browse

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lycoris.noboundrift.domain.model.MangaPreview
import com.lycoris.noboundrift.domain.usecase.GetMangaListUseCase
import com.lycoris.noboundrift.presentation.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BrowseUiState(
    val manga: List<MangaPreview> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val currentPage: Int = 1,
    val canLoadMore: Boolean = true,
)

@HiltViewModel
class BrowseViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getMangaList: GetMangaListUseCase,
) : ViewModel() {

    private val sourceId: Long = checkNotNull(savedStateHandle[Screen.Browse.ARG_SOURCE_ID])

    private val _uiState = MutableStateFlow(BrowseUiState())
    val uiState: StateFlow<BrowseUiState> = _uiState.asStateFlow()

    init {
        loadPage(page = 1)
    }

    fun loadNextPage() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.canLoadMore) return
        loadPage(state.currentPage + 1)
    }

    fun retry() {
        val state = _uiState.value
        _uiState.update { it.copy(error = null) }
        loadPage(state.currentPage)
    }

    private fun loadPage(page: Int) {
        viewModelScope.launch {
            if (page == 1) {
                _uiState.update { it.copy(isLoading = true, error = null) }
            } else {
                _uiState.update { it.copy(isLoadingMore = true) }
            }

            getMangaList(sourceId = sourceId, page = page)
                .onSuccess { newItems ->
                    _uiState.update { state ->
                        val combined = if (page == 1) newItems else state.manga + newItems
                        state.copy(
                            manga = combined,
                            isLoading = false,
                            isLoadingMore = false,
                            currentPage = page,
                            // Assume no more pages if the source returned fewer than 20 items
                            canLoadMore = newItems.size >= 20,
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            error = throwable.message ?: "Failed to load manga",
                        )
                    }
                }
        }
    }
}
