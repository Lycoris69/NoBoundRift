package com.lycoris.noboundrift.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lycoris.noboundrift.data.local.AccentColor
import com.lycoris.noboundrift.data.local.AppearancePreferences
import com.lycoris.noboundrift.data.local.AppFont
import com.lycoris.noboundrift.data.local.AppTheme
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
    val appTheme: AppTheme = AppearancePreferences.DEFAULT_THEME,
    val accentColor: AccentColor = AppearancePreferences.DEFAULT_ACCENT,
    val appFont: AppFont = AppearancePreferences.DEFAULT_FONT,
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
    private val appearancePreferences: AppearancePreferences,
) : ViewModel() {

    // combine() supports up to 5 typed flows per call. We have 9 total, so we nest two
    // inner combines (5 + 4) and merge their results in a third outer combine.
    val uiState: StateFlow<SettingsUiState> = combine(
        // ── inner 1: reader / library / downloads ──────────────────────────────
        combine(
            sourcePreferences.observeSelectedSourceId(),
            cachePreferences.observeCacheSizeBytes(),
            readerPreferences.observePreloadMode(),
            libraryPreferences.observeLibraryLayout(),
            downloadPreferences.observeConcurrency(),
        ) { selectedId, cacheSizeBytes, preloadMode, libraryLayout, concurrency ->
            PartialSettings(selectedId, cacheSizeBytes, preloadMode, libraryLayout, concurrency)
        },
        // ── inner 2: navigation + appearance ──────────────────────────────────
        combine(
            navigationPreferences.observeShowDiscover(),
            appearancePreferences.observeAppTheme(),
            appearancePreferences.observeAccentColor(),
            appearancePreferences.observeAppFont(),
        ) { showDiscover, appTheme, accentColor, appFont ->
            AppearancePartial(showDiscover, appTheme, accentColor, appFont)
        },
    ) { partial, appearance ->
        SettingsUiState(
            sources = sourceManager.getAllSources().sortedBy { it.id },
            selectedSourceId = partial.selectedSourceId,
            cacheSizeBytes = partial.cacheSizeBytes,
            preloadMode = partial.preloadMode,
            libraryLayout = partial.libraryLayout,
            downloadConcurrency = partial.downloadConcurrency,
            showDiscoverTab = appearance.showDiscover,
            appTheme = appearance.appTheme,
            accentColor = appearance.accentColor,
            appFont = appearance.appFont,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun selectSource(id: Long) { sourcePreferences.setSelectedSourceId(id) }
    fun setCacheSize(bytes: Long) { cachePreferences.setCacheSizeBytes(bytes) }
    fun setPreloadMode(mode: PreloadMode) { readerPreferences.setPreloadMode(mode) }
    fun setLibraryLayout(layout: LibraryLayout) { libraryPreferences.setLibraryLayout(layout) }
    fun setDownloadConcurrency(value: Int) { downloadPreferences.setConcurrency(value) }
    fun setShowDiscoverTab(show: Boolean) { navigationPreferences.setShowDiscover(show) }
    fun setAppTheme(theme: AppTheme) { appearancePreferences.setAppTheme(theme) }
    fun setAccentColor(color: AccentColor) { appearancePreferences.setAccentColor(color) }
    fun setAppFont(font: AppFont) { appearancePreferences.setAppFont(font) }
}

/** Intermediate tuple for the first inner [combine] in [SettingsViewModel.uiState]. */
private data class PartialSettings(
    val selectedSourceId: Long,
    val cacheSizeBytes: Long,
    val preloadMode: PreloadMode,
    val libraryLayout: LibraryLayout,
    val downloadConcurrency: Int,
)

/** Intermediate tuple for the second inner [combine] in [SettingsViewModel.uiState]. */
private data class AppearancePartial(
    val showDiscover: Boolean,
    val appTheme: AppTheme,
    val accentColor: AccentColor,
    val appFont: AppFont,
)
