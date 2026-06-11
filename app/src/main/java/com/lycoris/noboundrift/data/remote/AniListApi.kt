package com.lycoris.noboundrift.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class AniListResult(
    val matchedId: Int,
    val matchedTitle: String,
    val recommendations: List<RecommendedEntry>,
)

data class RecommendedEntry(
    val aniListId: Int,
    val title: String,
    val coverUrl: String,
    val genres: List<String>,
    val synonyms: List<String>,
)

@Singleton
class AniListApi @Inject constructor(private val okHttpClient: OkHttpClient) {

    private val endpoint = "https://graphql.anilist.co"

    private val query = """
        query (${'$'}title: String) {
          Media(search: ${'$'}title, type: MANGA, format_not_in: [NOVEL]) {
            id
            title { romaji english }
            coverImage { large }
            recommendations(sort: RATING_DESC, perPage: 8) {
              nodes {
                mediaRecommendation {
                  id
                  title { romaji english }
                  coverImage { large }
                  genres
                  synonyms
                }
              }
            }
            relations {
              edges {
                relationType
                node {
                  id
                  type
                  title { romaji english }
                  coverImage { large }
                  genres
                  synonyms
                }
              }
            }
          }
        }
    """.trimIndent()

    private fun isLatinScript(s: String) = s.none { c ->
        val cp = c.code
        (cp in 0x3040..0x30FF) || (cp in 0x4E00..0x9FFF) || (cp in 0xAC00..0xD7AF)
    }

    private val relatedTypes = setOf(
        "ADAPTATION", "SIDE_STORY", "SPIN_OFF", "ALTERNATIVE", "SEQUEL", "PREQUEL"
    )

    suspend fun searchWithRecommendations(title: String): AniListResult? =
        withContext(Dispatchers.IO) {
            runCatching {
                val bodyJson = JSONObject().apply {
                    put("query", query)
                    put("variables", JSONObject().apply { put("title", title) })
                }.toString()

                val request = Request.Builder()
                    .url(endpoint)
                    .post(bodyJson.toRequestBody("application/json".toMediaType()))
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) return@runCatching null

                val body = response.body?.string() ?: return@runCatching null
                val media = JSONObject(body)
                    .getJSONObject("data")
                    .optJSONObject("Media") ?: return@runCatching null

                val matchedId = media.getInt("id")
                val matchedTitle = resolveTitle(media.getJSONObject("title"))

                val entries = mutableListOf<RecommendedEntry>()

                val recNodes = media
                    .getJSONObject("recommendations")
                    .getJSONArray("nodes")
                for (i in 0 until recNodes.length()) {
                    val rec = recNodes.getJSONObject(i).optJSONObject("mediaRecommendation")
                        ?: continue
                    val entry = parseEntry(rec)
                    if (entry.genres.none { it.equals("Hentai", ignoreCase = true) }) entries += entry
                }

                val relEdges = media
                    .getJSONObject("relations")
                    .getJSONArray("edges")
                for (i in 0 until relEdges.length()) {
                    val edge = relEdges.getJSONObject(i)
                    val relationType = edge.optString("relationType")
                    if (relationType !in relatedTypes) continue
                    val node = edge.optJSONObject("node") ?: continue
                    if (node.optString("type") != "MANGA") continue
                    val entry = parseEntry(node)
                    if (entry.genres.none { it.equals("Hentai", ignoreCase = true) }) entries += entry
                }

                AniListResult(
                    matchedId = matchedId,
                    matchedTitle = matchedTitle,
                    recommendations = entries,
                )
            }.getOrNull()
        }

    private fun resolveTitle(titleObj: JSONObject): String {
        val english = titleObj.optString("english")
        return if (!english.isNullOrBlank()) english else titleObj.optString("romaji", "")
    }

    private fun parseEntry(obj: JSONObject): RecommendedEntry {
        val id = obj.getInt("id")
        val title = resolveTitle(obj.getJSONObject("title"))
        val coverUrl = obj.optJSONObject("coverImage")?.optString("large") ?: ""
        val genresArray = obj.optJSONArray("genres")
        val genres = buildList {
            if (genresArray != null) {
                for (i in 0 until genresArray.length()) add(genresArray.getString(i))
            }
        }
        val synonymsArray = obj.optJSONArray("synonyms")
        val synonyms = buildList {
            if (synonymsArray != null) {
                for (i in 0 until synonymsArray.length()) {
                    val s = synonymsArray.getString(i)
                    if (s != title && isLatinScript(s)) add(s)
                }
            }
        }.take(3)
        return RecommendedEntry(aniListId = id, title = title, coverUrl = coverUrl, genres = genres, synonyms = synonyms)
    }
}
