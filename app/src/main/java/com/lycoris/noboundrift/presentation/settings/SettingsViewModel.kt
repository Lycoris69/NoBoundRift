package com.lycoris.noboundrift.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lycoris.noboundrift.data.local.AccentColor
import com.lycoris.noboundrift.data.local.AccessibilityPreferences
import com.lycoris.noboundrift.data.local.AppFont
import com.lycoris.noboundrift.data.local.AppPreset
import com.lycoris.noboundrift.data.local.AppTheme
import com.lycoris.noboundrift.data.local.AppearancePreferences
import com.lycoris.noboundrift.data.local.BrowsePreferences
import com.lycoris.noboundrift.data.local.CachePreferences
import com.lycoris.noboundrift.data.local.DownloadPreferences
import com.lycoris.noboundrift.data.local.LibraryLayout
import com.lycoris.noboundrift.data.local.LibraryPreferences
import com.lycoris.noboundrift.data.local.LibrarySortOrder
import com.lycoris.noboundrift.data.local.NavigationPreferences
import com.lycoris.noboundrift.data.local.PreloadMode
import com.lycoris.noboundrift.data.local.ReaderPreferences
import com.lycoris.noboundrift.data.local.ReadingDirection
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
    val appPreset: AppPreset = AppearancePreferences.DEFAULT_PRESET,
    // Reader extras
    val readingDirection: ReadingDirection = ReadingDirection.LTR,
    val keepScreenOn: Boolean = false,
    // Library extras
    val libraryGridColumns: Int = 3,
    val librarySortOrder: LibrarySortOrder = LibrarySortOrder.CUSTOM,
    val roundedCovers: Boolean = true,
    // Browse extras
    val browseGridColumns: Int = 3,
    val blurCovers: Boolean = false,
    // Appearance extras
    val hideBottomBarLabels: Boolean = false,
    // Accessibility
    val hapticFeedback: Boolean = true,
    // Downloads
    val wifiOnlyDownload: Boolean = false,
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
    private val browsePreferences: BrowsePreferences,
    private val accessibilityPreferences: AccessibilityPreferences,
) : ViewModel() {

    // combine() supports up to 5 typed flows per call. We split across 4 inner combines
    // (5 + 5 + 5 + 5) merged by one outer combine (4 flows — within the limit).
    val uiState: StateFlow<SettingsUiState> = combine(
        // inner 1: source / cache / preload / library layout / download concurrency
        combine(
            sourcePreferences.observeSelectedSourceId(),
            cachePreferences.observeCacheSizeBytes(),
            readerPreferences.observePreloadMode(),
            libraryPreferences.observeLibraryLayout(),
            downloadPreferences.observeConcurrency(),
        ) { selectedId, cacheSizeBytes, preloadMode, libraryLayout, concurrency ->
            PartialSettings(selectedId, cacheSizeBytes, preloadMode, libraryLayout, concurrency)
        },
        // inner 2: navigation + appearance core + preset
        combine(
            navigationPreferences.observeShowDiscover(),
            appearancePreferences.observeAppTheme(),
            appearancePreferences.observeAccentColor(),
            appearancePreferences.observeAppFont(),
            appearancePreferences.observeAppPreset(),
        ) { showDiscover, appTheme, accentColor, appFont, appPreset ->
            AppearancePartial(showDiscover, appTheme, accentColor, appFont, appPreset)
        },
        // inner 3: reader extras + library extras
        combine(
            readerPreferences.observeReadingDirection(),
            readerPreferences.observeKeepScreenOn(),
            libraryPreferences.observeGridColumns(),
            libraryPreferences.observeSortOrder(),
            libraryPreferences.observeRoundedCovers(),
        ) { readingDirection, keepScreenOn, libCols, sortOrder, rounded ->
            ReaderLibraryPartial(readingDirection, keepScreenOn, libCols, sortOrder, rounded)
        },
        // inner 4: browse + appearance extras + accessibility + wifi-only
        combine(
            browsePreferences.observeGridColumns(),
            browsePreferences.observeBlurCovers(),
            appearancePreferences.observeHideBottomBarLabels(),
            accessibilityPreferences.observeHapticFeedback(),
            downloadPreferences.observeWifiOnly(),
        ) { browseCols, blur, hideLabels, haptic, wifiOnly ->
            BrowseExtrasPartial(browseCols, blur, hideLabels, haptic, wifiOnly)
        },
    ) { partial, appearance, readerLibrary, browseExtras ->
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
            appPreset = appearance.appPreset,
            readingDirection = readerLibrary.readingDirection,
            keepScreenOn = readerLibrary.keepScreenOn,
            libraryGridColumns = readerLibrary.libCols,
            librarySortOrder = readerLibrary.sortOrder,
            roundedCovers = readerLibrary.rounded,
            browseGridColumns = browseExtras.browseCols,
            blurCovers = browseExtras.blur,
            hideBottomBarLabels = browseExtras.hideLabels,
            hapticFeedback = browseExtras.haptic,
            wifiOnlyDownload = browseExtras.wifiOnly,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    // Setters

    fun selectSource(id: Long) { sourcePreferences.setSelectedSourceId(id) }
    fun setCacheSize(bytes: Long) { cachePreferences.setCacheSizeBytes(bytes) }
    fun setPreloadMode(mode: PreloadMode) { readerPreferences.setPreloadMode(mode) }
    fun setLibraryLayout(layout: LibraryLayout) { libraryPreferences.setLibraryLayout(layout) }
    fun setDownloadConcurrency(value: Int) { downloadPreferences.setConcurrency(value) }
    fun setShowDiscoverTab(show: Boolean) { navigationPreferences.setShowDiscover(show) }
    fun setAppTheme(theme: AppTheme) { appearancePreferences.setAppTheme(theme) }
    fun setAccentColor(color: AccentColor) { appearancePreferences.setAccentColor(color) }
    fun setAppFont(font: AppFont) { appearancePreferences.setAppFont(font) }
    fun setAppPreset(preset: AppPreset) { appearancePreferences.setAppPreset(preset) }
    fun setReadingDirection(dir: ReadingDirection) { readerPreferences.setReadingDirection(dir) }
    fun setKeepScreenOn(enabled: Boolean) { readerPreferences.setKeepScreenOn(enabled) }
    fun setLibraryGridColumns(cols: Int) { libraryPreferences.setGridColumns(cols) }
    fun setLibrarySortOrder(order: LibrarySortOrder) { libraryPreferences.setSortOrder(order) }
    fun setRoundedCovers(rounded: Boolean) { libraryPreferences.setRoundedCovers(rounded) }
    fun setBrowseGridColumns(cols: Int) { browsePreferences.setGridColumns(cols) }
    fun setBlurCovers(enabled: Boolean) { browsePreferences.setBlurCovers(enabled) }
    fun setHideBottomBarLabels(hide: Boolean) { appearancePreferences.setHideBottomBarLabels(hide) }
    fun setHapticFeedback(enabled: Boolean) { accessibilityPreferences.setHapticFeedback(enabled) }
    fun setWifiOnlyDownload(enabled: Boolean) { downloadPreferences.setWifiOnly(enabled) }
}

private data class PartialSettings(
    val selectedSourceId: Long,
    val cacheSizeBytes: Long,
    val preloadMode: PreloadMode,
    val libraryLayout: LibraryLayout,
    val downloadConcurrency: Int,
)

private data class AppearancePartial(
    val showDiscover: Boolean,
    val appTheme: AppTheme,
    val accentColor: AccentColor,
    val appFont: AppFont,
    val appPreset: AppPreset,
)

private data class ReaderLibraryPartial(
    val readingDirection: ReadingDirection,
    val keepScreenOn: Boolean,
    val libCols: Int,
    val sortOrder: LibrarySortOrder,
    val rounded: Boolean,
)

private data class BrowseExtrasPartial(
    val browseCols: Int,
    val blur: Boolean,
    val hideLabels: Boolean,
    val haptic: Boolean,
    val wifiOnly: Boolean,
)
