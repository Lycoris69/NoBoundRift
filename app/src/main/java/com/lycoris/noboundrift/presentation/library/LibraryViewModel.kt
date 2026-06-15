package com.lycoris.noboundrift.presentation.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lycoris.noboundrift.data.local.LibraryLayout
import com.lycoris.noboundrift.data.local.LibraryPreferences
import com.lycoris.noboundrift.data.local.entity.DownloadEntity
import com.lycoris.noboundrift.data.local.entity.DownloadStatus
import com.lycoris.noboundrift.domain.model.MangaPreview
import com.lycoris.noboundrift.domain.repository.DownloadRepository
import com.lycoris.noboundrift.domain.repository.MangaRepository
import com.lycoris.noboundrift.domain.usecase.CancelAllDownloadsUseCase
import com.lycoris.noboundrift.domain.usecase.DeleteDownloadUseCase
import com.lycoris.noboundrift.domain.usecase.GetDownloadsUseCase
import com.lycoris.noboundrift.domain.usecase.GetLibraryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LibraryTab { LIBRARY, DOWNLOADS }

data class MangaDownloadGroup(
    val mangaId: String,
    val mangaUrl: String,
    val sourceId: Long,
    val mangaTitle: String,
    val mangaCoverUrl: String,
    val chapters: List<DownloadEntity>,
    val completedCount: Int,
    val totalCount: Int,
)

data class LibraryUiState(
    val manga: List<MangaPreview> = emptyList(),
    val isEmpty: Boolean = false,
    val selectedTab: LibraryTab = LibraryTab.LIBRARY,
    val downloadGroups: List<MangaDownloadGroup> = emptyList(),
    val libraryLayout: LibraryLayout = LibraryLayout.GRID,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    getLibrary: GetLibraryUseCase,
    private val repository: MangaRepository,
    getDownloads: GetDownloadsUseCase,
    libraryPreferences: LibraryPreferences,
    private val deleteDownloadUseCase: DeleteDownloadUseCase,
    private val cancelAllDownloadsUseCase: CancelAllDownloadsUseCase,
    private val downloadRepository: DownloadRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    // Counter of in-flight reorder writes. DB Flow emissions are suppressed while > 0.
    // Using a counter (not boolean) so a rapid cancel+relaunch can't clear the flag
    // while a new write is already in flight: the old job's finally { -- } fires after
    // the new job's ++ , keeping suppressDbUpdates true until the new write completes.
    private var activeReorderWrites = 0
    private val suppressDbUpdates get() = activeReorderWrites > 0
    private var reorderJob: Job? = null

    init {
        viewModelScope.launch {
            getLibrary().collect { list ->
                if (!suppressDbUpdates) {
                    _uiState.update { current ->
                        current.copy(manga = list, isEmpty = list.isEmpty())
                    }
                }
            }
        }

        viewModelScope.launch {
            libraryPreferences.observeLibraryLayout().collect { layout ->
                _uiState.update { it.copy(libraryLayout = layout) }
            }
        }

        viewModelScope.launch {
            getDownloads.all().collect { entities ->
                val groups = entities
                    .groupBy { it.mangaId }
                    .map { (_, list) ->
                        val first = list.first()
                        MangaDownloadGroup(
                            mangaId = first.mangaId,
                            mangaUrl = first.mangaUrl,
                            sourceId = first.sourceId,
                            mangaTitle = first.mangaTitle,
                            mangaCoverUrl = first.mangaCoverUrl,
                            chapters = list.sortedBy { it.chapterNumber },
                            completedCount = list.count { it.status == DownloadStatus.COMPLETED },
                            totalCount = list.size,
                        )
                    }
                    .sortedBy { it.mangaTitle }
                _uiState.update { it.copy(downloadGroups = groups) }
            }
        }
    }

    fun setTab(tab: LibraryTab) {
        _uiState.update { it.copy(selectedTab = tab) }
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
            activeReorderWrites++
            try {
                repository.reorderLibrary(orderedIds)
            } finally {
                activeReorderWrites--
            }
        }
    }

    fun cancelDownload(chapterUrl: String) {
        viewModelScope.launch { downloadRepository.cancelDownload(chapterUrl) }
    }

    fun retryDownload(entity: DownloadEntity) {
        viewModelScope.launch { downloadRepository.retryDownload(entity) }
    }

    fun deleteDownload(chapterUrl: String) {
        viewModelScope.launch { deleteDownloadUseCase(chapterUrl) }
    }

    fun cancelAllDownloads(mangaId: String) {
        viewModelScope.launch { cancelAllDownloadsUseCase(mangaId) }
    }
}
