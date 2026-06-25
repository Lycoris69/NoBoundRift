package com.lycoris.noboundrift.presentation.browse

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lycoris.noboundrift.data.local.EmptyChapterCache
import com.lycoris.noboundrift.data.local.SourcePreferences
import com.lycoris.noboundrift.data.remote.source.SourceManager
import com.lycoris.noboundrift.domain.model.MangaPreview
import com.lycoris.noboundrift.domain.repository.MangaRepository
import com.lycoris.noboundrift.domain.usecase.GetMangaListUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
    val isCrossSourceSearch: Boolean = false,
    val isDiscoverMode: Boolean = false,
)

@HiltViewModel
class BrowseViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sourcePreferences: SourcePreferences,
    private val getMangaList: GetMangaListUseCase,
    private val repository: MangaRepository,
    private val sourceManager: SourceManager,
    private val emptyChapterCache: EmptyChapterCache,
) : ViewModel() {

    val allSourceNames: Map<Long, String> = sourceManager.getAllSources().associate { it.id to it.name }

    private var sourceId: Long = 0L

    private val initialQuery = savedStateHandle.get<String>("query") ?: ""
    private val altTitles: List<String> = savedStateHandle.get<String>("altTitles")
        ?.split("|")
        ?.filter { it.isNotBlank() }
        ?: emptyList()

    private val _uiState = MutableStateFlow(
        BrowseUiState(
            searchQuery = initialQuery,
            isDiscoverMode = altTitles.isNotEmpty(),
        )
    )
    val uiState: StateFlow<BrowseUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            var initialized = false
            sourcePreferences.observeSelectedSourceId().collect { newSourceId ->
                if (!initialized) {
                    initialized = true
                    sourceId = newSourceId
                    if (altTitles.isNotEmpty()) {
                        val allTitles = (listOf(initialQuery) + altTitles).filter { it.isNotBlank() }.distinct()
                        launchMultiTitleSearch(allTitles)
                    } else {
                        loadPage(page = 1)
                    }
                } else if (newSourceId != sourceId) {
                    sourceId = newSourceId
                    searchJob?.cancel()
                    loadJob?.cancel()
                    crossSourceJob?.cancel()
                    _uiState.update { BrowseUiState(searchQuery = it.searchQuery) }
                    loadPage(page = 1)
                }
            }
        }
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

    fun refresh() {
        searchJob?.cancel()
        loadJob?.cancel()
        crossSourceJob?.cancel()
        enrichJob?.cancel()
        _uiState.update { it.copy(manga = emptyList(), currentPage = 1, canLoadMore = true, error = null, isLoading = false, isLoadingMore = false) }
        loadPage(page = 1)
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
        crossSourceJob?.cancel()
        enrichJob?.cancel()
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

    private var crossSourceJob: Job? = null

    // Enrichment coroutine: queries Room for stored latestChapterAt values and patches
    // them into the browse list as a non-blocking second pass.
    private var enrichJob: Job? = null

    /**
     * Queries Room for stored [MangaPreview.latestChapterAt] values and patches them into
     * the current browse list. Runs as a fire-and-forget coroutine so the list is never
     * blocked waiting for the DB round-trip. Any previous enrichment job is cancelled
     * first to avoid stale data racing with a fresh load.
     */
    private fun enrichWithStoredDates() {
        enrichJob?.cancel()
        enrichJob = viewModelScope.launch {
            val currentIds = _uiState.value.manga.map { it.id }
            if (currentIds.isEmpty()) return@launch
            runCatching { repository.getLatestChapterDates(currentIds) }
                .onSuccess { dateMap ->
                    if (dateMap.isEmpty()) return@onSuccess
                    val emptyIds = emptyChapterCache.getAll()
                    _uiState.update { state ->
                        state.copy(
                            manga = state.manga.map { preview ->
                                val stored = dateMap[preview.id] ?: 0L
                                when {
                                    preview.id in emptyIds -> preview.copy(chapterCount = 0)
                                    stored > preview.latestChapterAt -> preview.copy(latestChapterAt = stored)
                                    else -> preview
                                }
                            }
                        )
                    }
                }
                .onFailure { if (it is CancellationException) throw it }
        }
    }

    private fun loadPage(page: Int) {
        // Snapshot the query synchronously before launching — guarantees the launched
        // coroutine always uses the query that was current at call-site, not whatever
        // the user has typed by the time the coroutine is scheduled.
        val querySnapshot = _uiState.value.searchQuery

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (page == 1) {
                _uiState.update { it.copy(isLoading = true, error = null, isCrossSourceSearch = false) }
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
                            canLoadMore = newItems.isNotEmpty() && querySnapshot.isBlank(),
                        )
                    }
                    if (newItems.isEmpty() && querySnapshot.isNotBlank()) {
                        launchCrossSourceSearch(querySnapshot)
                    } else {
                        // Non-blocking second pass: inject stored latestChapterAt from Room
                        // for any manga the user has previously visited. The browse list is
                        // already visible at this point — this only adds date labels.
                        enrichWithStoredDates()
                    }
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) return@launch
                    val message = when (throwable) {
                        is java.net.UnknownHostException,
                        is java.net.SocketException -> "No internet connection"
                        is java.net.SocketTimeoutException -> "Connection timed out"
                        else -> throwable.message ?: "Failed to load manga"
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            error = message,
                        )
                    }
                }
        }
    }

    private fun launchCrossSourceSearch(query: String) {
        crossSourceJob?.cancel()
        crossSourceJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val otherSources = sourceManager.getAllSources().filter { it.id != sourceId }
            val deferreds = otherSources.map { source ->
                async {
                    runCatching {
                        getMangaList(sourceId = source.id, page = 1, query = query)
                            .getOrElse { emptyList() }
                    }
                        .onFailure { if (it is CancellationException) throw it }
                        .getOrElse { emptyList() }
                }
            }
            val allResults = deferreds.awaitAll().flatten().distinctBy { "${it.sourceId}_${it.url}" }
            _uiState.update {
                it.copy(
                    manga = allResults,
                    isLoading = false,
                    isCrossSourceSearch = allResults.isNotEmpty(),
                    canLoadMore = false,
                )
            }
        }
    }

    private fun launchMultiTitleSearch(titles: List<String>) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, isCrossSourceSearch = true) }
            val allSources = sourceManager.getAllSources()
            val deferreds = allSources.flatMap { source ->
                titles.map { title ->
                    async {
                        runCatching {
                            getMangaList(sourceId = source.id, page = 1, query = title)
                                .getOrElse { emptyList() }
                        }
                            .onFailure { if (it is CancellationException) throw it }
                            .getOrElse { emptyList() }
                    }
                }
            }
            val allResults = deferreds.awaitAll()
                .flatten()
                .distinctBy { "${it.sourceId}_${it.url}" }
            _uiState.update {
                it.copy(
                    manga = allResults,
                    isLoading = false,
                    isCrossSourceSearch = true,
                    canLoadMore = false,
                )
            }
        }
    }

    fun exitDiscoverMode() {
        loadJob?.cancel()
        searchJob?.cancel()
        crossSourceJob?.cancel()
        _uiState.update { BrowseUiState(searchQuery = "", isDiscoverMode = false) }
        loadPage(page = 1)
    }
}
