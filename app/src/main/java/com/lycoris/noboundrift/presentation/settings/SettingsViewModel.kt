package com.lycoris.noboundrift.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lycoris.noboundrift.data.local.CachePreferences
import com.lycoris.noboundrift.data.local.DownloadPreferences
import com.lycoris.noboundrift.data.local.LibraryLayout
import com.lycoris.noboundrift.data.local.LibraryPreferences
import com.lycoris.noboundrift.data.local.NavigationPreferences
import com.lycoris.noboundrift.data.local.PreloadMode
import com.lycoris.noboundrift.data.local.ReaderPreferences
import com.lycoris.noboundrift.data.local.SourcePreferences
import com.lycoris.noboundrift.data.remote.source.Source
import com.lycoris.noboundrift.data.remote.source.SourceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class SettingsUiState(
    val sources: List<Source> = emptyList(),
    val selectedSourceId: Long = 2L,
    val cacheSizeBytes: Long = 128L * 1024 * 1024,
    val preloadMode: PreloadMode = PreloadMode.ALWAYS,
    val libraryLayout: LibraryLayout = LibraryLayout.GRID,
    val downloadConcurrency: Int = DownloadPreferences.DEFAULT_CONCURRENCY,
    val showDiscoverTab: Boolean = NavigationPreferences.DEFAULT_SHOW_DISCOVER,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val sourceManager: SourceManager,
    private val sourcePreferences: SourcePreferences,
    private val cachePreferences: CachePreferences,
    private val readerPreferences: ReaderPreferences,
    private val libraryPreferences: LibraryPreferences,
    private val downloadPreferences: DownloadPreferences,
    private val navigationPreferences: NavigationPreferences,
) : ViewModel() {

    // combine() has type-safe overloads only up to 5 flows. For the 6th (showDiscover)
    // we nest a second combine so each lambda receives properly-typed parameters.
    val uiState: StateFlow<SettingsUiState> = combine(
        combine(
            sourcePreferences.observeSelectedSourceId(),
            cachePreferences.observeCacheSizeBytes(),
            readerPreferences.observePreloadMode(),
            libraryPreferences.observeLibraryLayout(),
            downloadPreferences.observeConcurrency(),
        ) { selectedId, cacheSizeBytes, preloadMode, libraryLayout, concurrency ->
            PartialSettings(selectedId, cacheSizeBytes, preloadMode, libraryLayout, concurrency)
        },
        navigationPreferences.observeShowDiscover(),
    ) { partial, showDiscover ->
        SettingsUiState(
            sources = sourceManager.getAllSources().sortedBy { it.id },
            selectedSourceId = partial.selectedSourceId,
            cacheSizeBytes = partial.cacheSizeBytes,
            preloadMode = partial.preloadMode,
            libraryLayout = partial.libraryLayout,
            downloadConcurrency = partial.downloadConcurrency,
            showDiscoverTab = showDiscover,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun selectSource(id: Long) {
        sourcePreferences.setSelectedSourceId(id)
    }

    fun setCacheSize(bytes: Long) {
        cachePreferences.setCacheSizeBytes(bytes)
    }

    fun setPreloadMode(mode: PreloadMode) {
        readerPreferences.setPreloadMode(mode)
    }

    fun setLibraryLayout(layout: LibraryLayout) {
        libraryPreferences.setLibraryLayout(layout)
    }

    fun setDownloadConcurrency(value: Int) {
        downloadPreferences.setConcurrency(value)
    }

    fun setShowDiscoverTab(show: Boolean) {
        navigationPreferences.setShowDiscover(show)
    }
}

/** Intermediate tuple for the nested [combine] inside [SettingsViewModel.uiState]. */
private data class PartialSettings(
    val selectedSourceId: Long,
    val cacheSizeBytes: Long,
    val preloadMode: PreloadMode,
    val libraryLayout: LibraryLayout,
    val downloadConcurrency: Int,
)
