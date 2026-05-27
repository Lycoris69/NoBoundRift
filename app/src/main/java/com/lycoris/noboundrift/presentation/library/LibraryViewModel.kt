package com.lycoris.noboundrift.presentation.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lycoris.noboundrift.domain.model.MangaPreview
import com.lycoris.noboundrift.domain.usecase.GetLibraryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class LibraryUiState(
    val manga: List<MangaPreview> = emptyList(),
    val isEmpty: Boolean = false,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    getLibrary: GetLibraryUseCase,
) : ViewModel() {

    val uiState: StateFlow<LibraryUiState> = getLibrary()
        .map { list -> LibraryUiState(manga = list, isEmpty = list.isEmpty()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LibraryUiState(),
        )
}
