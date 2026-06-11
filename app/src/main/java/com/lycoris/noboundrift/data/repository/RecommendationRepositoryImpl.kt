package com.lycoris.noboundrift.data.repository

import com.lycoris.noboundrift.data.local.dao.MangaDao
import com.lycoris.noboundrift.data.remote.AniListApi
import com.lycoris.noboundrift.data.remote.RecommendedEntry
import com.lycoris.noboundrift.domain.model.RecommendedManga
import com.lycoris.noboundrift.domain.repository.RecommendationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecommendationRepositoryImpl @Inject constructor(
    private val mangaDao: MangaDao,
    private val aniListApi: AniListApi,
) : RecommendationRepository {

    override suspend fun getRecommendations(): Result<List<RecommendedManga>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val libraryEntries = mangaDao.observeAll().first()
                    .sortedByDescending { it.addedAt }
                    .take(10)

                val libraryTitlesLower = libraryEntries.map { it.title.lowercase() }.toSet()

                val results = coroutineScope {
                    libraryEntries.map { entry ->
                        async { aniListApi.searchWithRecommendations(entry.title) }
                    }.awaitAll()
                }

                val matchedIds = results.filterNotNull().map { it.matchedId }.toSet()

                // keyed by aniListId: Pair<RecommendedEntry, score>
                val scoreMap = LinkedHashMap<Int, Pair<RecommendedEntry, Int>>()

                for (result in results.filterNotNull()) {
                    for (entry in result.recommendations) {
                        val existing = scoreMap[entry.aniListId]
                        if (existing == null) {
                            scoreMap[entry.aniListId] = entry to 1
                        } else {
                            scoreMap[entry.aniListId] = existing.first to existing.second + 1
                        }
                    }
                }

                scoreMap.values
                    .filter { (entry, _) ->
                        val titleLower = entry.title.lowercase()
                        entry.aniListId !in matchedIds &&
                            libraryTitlesLower.none { it.contains(titleLower) || titleLower.contains(it) } &&
                            entry.genres.none { it.equals("Hentai", ignoreCase = true) }
                    }
                    .sortedByDescending { (_, score) -> score }
                    .take(30)
                    .map { (entry, score) ->
                        RecommendedManga(
                            aniListId = entry.aniListId,
                            title = entry.title,
                            coverUrl = entry.coverUrl,
                            genres = entry.genres,
                            score = score,
                            alternativeTitles = entry.synonyms,
                        )
                    }
            }.onFailure { t ->
                if (t is CancellationException) throw t
            }
        }
}
