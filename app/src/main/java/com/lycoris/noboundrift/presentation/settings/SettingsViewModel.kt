package com.lycoris.noboundrift.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lycoris.noboundrift.data.local.SourcePreferences
import com.lycoris.noboundrift.data.remote.source.Source
import com.lycoris.noboundrift.data.remote.source.SourceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class SettingsUiState(
    val sources: List<Source> = emptyList(),
    val selectedSourceId: Long = 2L,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val sourceManager: SourceManager,
    private val sourcePreferences: SourcePreferences,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = sourcePreferences.observeSelectedSourceId()
        .map { selectedId ->
            SettingsUiState(
                sources = sourceManager.getAllSources().sortedBy { it.id },
                selectedSourceId = selectedId,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun selectSource(id: Long) {
        sourcePreferences.setSelectedSourceId(id)
    }
}
