package com.lycoris.noboundrift.presentation.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lycoris.noboundrift.domain.model.MangaPreview
import com.lycoris.noboundrift.presentation.common.MangaCard
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.Calendar

@Composable
fun BrowseScreen(
    onMangaClick: (MangaPreview) -> Unit,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        SearchBar(
            query = uiState.searchQuery,
            onQueryChange = viewModel::onSearchQueryChange,
        )

        when {
            uiState.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            uiState.error != null && uiState.manga.isEmpty() -> {
                ErrorState(
                    message = uiState.error!!,
                    onRetry = viewModel::retry,
                )
            }

            else -> {
                BrowseGrid(
                    uiState = uiState,
                    onMangaClick = onMangaClick,
                    onLoadMore = viewModel::loadNextPage,
                )
            }
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Search…") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear")
                }
            }
        },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

private data class MangaGroups(
    val today: List<MangaPreview>,
    val lastThreeDays: List<MangaPreview>,
    val before: List<MangaPreview>,
)

private fun groupByDate(list: List<MangaPreview>): MangaGroups {
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val todayStart = cal.timeInMillis
    val threeDaysAgoStart = todayStart - 3L * 24 * 60 * 60 * 1000
    return MangaGroups(
        today = list.filter { it.latestChapterAt >= todayStart },
        lastThreeDays = list.filter { it.latestChapterAt >= threeDaysAgoStart && it.latestChapterAt < todayStart },
        before = list.filter { it.latestChapterAt < threeDaysAgoStart },
    )
}

@Composable
private fun BrowseGrid(
    uiState: BrowseUiState,
    onMangaClick: (MangaPreview) -> Unit,
    onLoadMore: () -> Unit,
) {
    val gridState = rememberLazyGridState()

    // Trigger pagination when the user scrolls near the bottom
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo }
            .map { info ->
                val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                val total = info.totalItemsCount
                total > 0 && lastVisible >= total - 6
            }
            .distinctUntilChanged()
            .collect { nearEnd -> if (nearEnd) onLoadMore() }
    }

    val isSearching = uiState.searchQuery.isNotBlank()
    val groups = remember(uiState.manga, isSearching) {
        if (isSearching) null else groupByDate(uiState.manga)
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = 120.dp),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (groups != null) {
            if (groups.today.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { DateSectionHeader("Today") }
                items(groups.today, key = { it.id }) { preview ->
                    MangaCard(preview = preview, onClick = { onMangaClick(preview) })
                }
            }
            if (groups.lastThreeDays.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { DateSectionHeader("Last 3 Days") }
                items(groups.lastThreeDays, key = { it.id }) { preview ->
                    MangaCard(preview = preview, onClick = { onMangaClick(preview) })
                }
            }
            if (groups.before.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { DateSectionHeader("Before") }
                items(groups.before, key = { it.id }) { preview ->
                    MangaCard(preview = preview, onClick = { onMangaClick(preview) })
                }
            }
        } else {
            items(uiState.manga, key = { it.id }) { preview ->
                MangaCard(preview = preview, onClick = { onMangaClick(preview) })
            }
        }

        if (uiState.isLoadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun DateSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
    )
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}
