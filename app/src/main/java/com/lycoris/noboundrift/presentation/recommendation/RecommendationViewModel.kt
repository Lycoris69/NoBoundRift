package com.lycoris.noboundrift.presentation.recommendation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lycoris.noboundrift.domain.model.RecommendedManga
import com.lycoris.noboundrift.domain.usecase.GetRecommendationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecommendationUiState(
    val items: List<RecommendedManga> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class RecommendationViewModel @Inject constructor(
    private val getRecommendations: GetRecommendationsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecommendationUiState())
    val uiState = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        loadRecommendations()
    }

    fun refresh() {
        loadJob?.cancel()
        loadRecommendations()
    }

    private fun loadRecommendations() {
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { getRecommendations() }
                .onSuccess { result ->
                    result
                        .onSuccess { items ->
                            _uiState.update { it.copy(items = items, isLoading = false) }
                        }
                        .onFailure { error ->
                            _uiState.update { it.copy(isLoading = false, error = error.message ?: "Failed to load recommendations") }
                        }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    _uiState.update { it.copy(isLoading = false, error = error.message ?: "Failed to load recommendations") }
                }
        }
    }
}
