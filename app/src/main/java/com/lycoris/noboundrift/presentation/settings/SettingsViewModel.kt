package com.lycoris.noboundrift.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lycoris.noboundrift.data.local.CachePreferences
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
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val sourceManager: SourceManager,
    private val sourcePreferences: SourcePreferences,
    private val cachePreferences: CachePreferences,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        sourcePreferences.observeSelectedSourceId(),
        cachePreferences.observeCacheSizeBytes(),
    ) { selectedId, cacheSizeBytes ->
        SettingsUiState(
            sources = sourceManager.getAllSources().sortedBy { it.id },
            selectedSourceId = selectedId,
            cacheSizeBytes = cacheSizeBytes,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun selectSource(id: Long) {
        sourcePreferences.setSelectedSourceId(id)
    }

    fun setCacheSize(bytes: Long) {
        cachePreferences.setCacheSizeBytes(bytes)
    }
}
