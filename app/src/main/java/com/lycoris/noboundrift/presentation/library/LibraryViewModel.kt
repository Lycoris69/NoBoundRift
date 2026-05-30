package com.lycoris.noboundrift.presentation.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lycoris.noboundrift.domain.model.MangaPreview
import com.lycoris.noboundrift.domain.repository.MangaRepository
import com.lycoris.noboundrift.domain.usecase.GetLibraryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    val manga: List<MangaPreview> = emptyList(),
    val isEmpty: Boolean = false,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    getLibrary: GetLibraryUseCase,
    private val repository: MangaRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    // When true, DB Flow emissions are ignored so in-flight saves don't reset the list
    private var suppressDbUpdates = false
    private var reorderJob: Job? = null

    init {
        viewModelScope.launch {
            getLibrary().collect { list ->
                if (!suppressDbUpdates) {
                    _uiState.update { LibraryUiState(manga = list, isEmpty = list.isEmpty()) }
                }
            }
        }
    }

    fun onMove(fromIndex: Int, toIndex: Int) {
        val current = _uiState.value.manga.toMutableList()
        if (fromIndex !in current.indices || toIndex !in current.indices) return
        val item = current.removeAt(fromIndex)
        current.add(toIndex, item)
        _uiState.update { it.copy(manga = current) }
    }

    fun onDragEnd() {
        val orderedIds = _uiState.value.manga.map { it.id }
        reorderJob?.cancel()
        reorderJob = viewModelScope.launch {
            suppressDbUpdates = true
            try {
                repository.reorderLibrary(orderedIds)
            } finally {
                suppressDbUpdates = false
            }
        }
    }
}
