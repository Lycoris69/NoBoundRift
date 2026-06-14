package com.lycoris.noboundrift.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lycoris.noboundrift.data.local.CachePreferences
import com.lycoris.noboundrift.data.local.LibraryLayout
import com.lycoris.noboundrift.data.local.LibraryPreferences
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
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val sourceManager: SourceManager,
    private val sourcePreferences: SourcePreferences,
    private val cachePreferences: CachePreferences,
    private val readerPreferences: ReaderPreferences,
    private val libraryPreferences: LibraryPreferences,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        sourcePreferences.observeSelectedSourceId(),
        cachePreferences.observeCacheSizeBytes(),
        readerPreferences.observePreloadMode(),
        libraryPreferences.observeLibraryLayout(),
    ) { selectedId, cacheSizeBytes, preloadMode, libraryLayout ->
        SettingsUiState(
            sources = sourceManager.getAllSources().sortedBy { it.id },
            selectedSourceId = selectedId,
            cacheSizeBytes = cacheSizeBytes,
            preloadMode = preloadMode,
            libraryLayout = libraryLayout,
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
}
