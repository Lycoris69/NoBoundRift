package com.lycoris.noboundrift.presentation.browse

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lycoris.noboundrift.domain.model.MangaPreview
import com.lycoris.noboundrift.domain.usecase.GetMangaListUseCase
import com.lycoris.noboundrift.presentation.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val searchQuery: String = "",
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
        if (state.isLoading || state.isLoadingMore || !state.canLoadMore) return
        loadPage(state.currentPage + 1)
    }

    fun retry() {
        val state = _uiState.value
        _uiState.update { it.copy(error = null) }
        loadPage(state.currentPage)
    }

    /**
     * Called whenever the search field value changes. Resets the list to page 1 and
     * debounces the network call by 400 ms so we don't fire on every keystroke.
     *
     * Both [searchJob] (the debounce delay) and [loadJob] (any in-flight network call
     * from a previous search) are cancelled so only one request is ever in flight.
     */
    fun onSearchQueryChange(query: String) {
        _uiState.update {
            it.copy(
                searchQuery = query,
                manga = emptyList(),
                currentPage = 1,
                canLoadMore = true,
            )
        }
        searchJob?.cancel()
        loadJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(400)
            loadPage(page = 1)
        }
    }

    private var searchJob: Job? = null

    // Tracks the active page-load coroutine so it can be cancelled when a new search
    // fires before the previous network call completes. Without this, two concurrent
    // loadPage jobs can interleave their _uiState.update calls producing stale results.
    private var loadJob: Job? = null

    private fun loadPage(page: Int) {
        // Snapshot the query synchronously before launching — guarantees the launched
        // coroutine always uses the query that was current at call-site, not whatever
        // the user has typed by the time the coroutine is scheduled.
        val querySnapshot = _uiState.value.searchQuery

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (page == 1) {
                _uiState.update { it.copy(isLoading = true, error = null) }
            } else {
                _uiState.update { it.copy(isLoadingMore = true) }
            }

            getMangaList(
                sourceId = sourceId,
                page = page,
                query = querySnapshot,
            )
                .onSuccess { newItems ->
                    _uiState.update { state ->
                        val combined = if (page == 1) newItems else state.manga + newItems
                        state.copy(
                            manga = combined.distinctBy { it.id },
                            isLoading = false,
                            isLoadingMore = false,
                            currentPage = page,
                            canLoadMore = newItems.isNotEmpty(),
                        )
                    }
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) return@launch
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
