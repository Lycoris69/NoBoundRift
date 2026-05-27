package com.lycoris.noboundrift.data.remote.source

import com.lycoris.noboundrift.domain.model.Chapter
import com.lycoris.noboundrift.domain.model.Manga
import com.lycoris.noboundrift.domain.model.MangaPreview
import com.lycoris.noboundrift.domain.model.MangaStatus
import com.lycoris.noboundrift.domain.model.Page
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject

/**
 * [Source] implementation backed by the official MangaDex REST API (api.mangadex.org).
 * No HTML scraping is used — all data comes from the JSON API.
 */
class MangaDexSource @Inject constructor(
    private val okHttpClient: OkHttpClient,
) : Source {

    override val id: Long = 1L
    override val name: String = "MangaDex"
    override val baseUrl: String = "https://mangadex.org"

    private val apiBase = "https://api.mangadex.org"

    // ---------------------------------------------------------------------------
    // Internal HTTP helper
    // ---------------------------------------------------------------------------

    /**
     * Executes a GET request to [url] and returns the response body parsed as a [JSONObject].
     * Must be called from a background dispatcher — it is blocking.
     */
    private fun apiGet(url: String): JSONObject {
        val request = Request.Builder().url(url).build()
        okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string()
                ?: throw IllegalStateException("Empty response body from $url")
            return JSONObject(body)
        }
    }

    // ---------------------------------------------------------------------------
    // fetchMangaList
    // ---------------------------------------------------------------------------

    /**
     * Returns a page of manga sorted by latest uploaded chapter.
     * @param page 1-based page number.
     */
    override suspend fun fetchMangaList(page: Int): List<MangaPreview> =
        withContext(Dispatchers.IO) {
            val offset = (page - 1) * 20
            val url = "$apiBase/manga" +
                "?limit=20" +
                "&offset=$offset" +
                "&includes[]=cover_art" +
                "&order[latestUploadedChapter]=desc" +
                "&availableTranslatedLanguage[]=en"

            val root = apiGet(url)
            val data = root.getJSONArray("data")

            buildList {
                for (i in 0 until data.length()) {
                    val item = data.getJSONObject(i)
                    val mangaId = item.optString("id", "")
                    val attributes = item.optJSONObject("attributes") ?: continue

                    val title = run {
                        val titleObj = attributes.optJSONObject("title")
                        titleObj?.optString("en")?.takeIf { it.isNotBlank() }
                            ?: titleObj?.let { obj ->
                                // Fall back to first available language key
                                obj.keys().asSequence()
                                    .mapNotNull { key -> obj.optString(key).takeIf { it.isNotBlank() } }
                                    .firstOrNull()
                            } ?: ""
                    }

                    val coverUrl = buildCoverUrl(mangaId, item)

                    add(
                        MangaPreview(
                            id = mangaId,
                            title = title,
                            coverUrl = coverUrl,
                            sourceId = id,
                            url = "$baseUrl/title/$mangaId",
                        )
                    )
                }
            }
        }

    // ---------------------------------------------------------------------------
    // fetchMangaDetail
    // ---------------------------------------------------------------------------

    override suspend fun fetchMangaDetail(url: String): Manga =
        withContext(Dispatchers.IO) {
            val mangaId = url.trimEnd('/').substringAfterLast('/')
            val root = apiGet("$apiBase/manga/$mangaId?includes[]=cover_art")
            val item = root.getJSONObject("data")
            val attributes = item.getJSONObject("attributes")

            val title = run {
                val titleObj = attributes.optJSONObject("title")
                titleObj?.optString("en")?.takeIf { it.isNotBlank() }
                    ?: titleObj?.let { obj ->
                        obj.keys().asSequence()
                            .mapNotNull { key -> obj.optString(key).takeIf { it.isNotBlank() } }
                            .firstOrNull()
                    } ?: ""
            }

            val synopsis = attributes.optJSONObject("description")?.optString("en") ?: ""

            val genres = buildList {
                val tagsArray = attributes.optJSONArray("tags") ?: return@buildList
                for (i in 0 until tagsArray.length()) {
                    val tagAttrs = tagsArray.getJSONObject(i).optJSONObject("attributes")
                    val genreName = tagAttrs?.optJSONObject("name")?.optString("en")
                    if (!genreName.isNullOrBlank()) add(genreName)
                }
            }

            val status = when (attributes.optString("status")) {
                "ongoing" -> MangaStatus.ONGOING
                "completed" -> MangaStatus.COMPLETED
                "hiatus" -> MangaStatus.HIATUS
                "cancelled" -> MangaStatus.CANCELLED
                else -> MangaStatus.UNKNOWN
            }

            val coverUrl = buildCoverUrl(mangaId, item)

            Manga(
                id = mangaId,
                title = title,
                coverUrl = coverUrl,
                synopsis = synopsis,
                genres = genres,
                status = status,
                sourceId = id,
                url = "$baseUrl/title/$mangaId",
            )
        }

    // ---------------------------------------------------------------------------
    // fetchChapterList
    // ---------------------------------------------------------------------------

    override suspend fun fetchChapterList(mangaUrl: String): List<Chapter> =
        withContext(Dispatchers.IO) {
            val mangaId = mangaUrl.trimEnd('/').substringAfterLast('/')
            val chapters = mutableListOf<Chapter>()
            var offset = 0
            val limit = 500

            while (true) {
                val url = "$apiBase/manga/$mangaId/feed" +
                    "?limit=$limit" +
                    "&offset=$offset" +
                    "&translatedLanguage[]=en" +
                    "&order[chapter]=asc"

                val root = apiGet(url)
                val data = root.getJSONArray("data")
                val total = root.optInt("total", 0)

                for (i in 0 until data.length()) {
                    val item = data.getJSONObject(i)
                    val chapterId = item.optString("id", "")
                    val attrs = item.optJSONObject("attributes") ?: continue

                    val chapterTitle = attrs.optString("title") ?: ""
                    val number = attrs.optString("chapter")?.toFloatOrNull() ?: 0f

                    chapters.add(
                        Chapter(
                            id = chapterId,
                            mangaId = mangaId,
                            title = chapterTitle,
                            number = number,
                            url = "$baseUrl/chapter/$chapterId",
                        )
                    )
                }

                offset += data.length()
                // Stop when all chapters have been fetched
                if (offset >= total || data.length() == 0) break
            }

            chapters.sortBy { it.number }
            chapters
        }

    // ---------------------------------------------------------------------------
    // fetchPageList
    // ---------------------------------------------------------------------------

    override suspend fun fetchPageList(chapterUrl: String): List<Page> =
        withContext(Dispatchers.IO) {
            val chapterId = chapterUrl.trimEnd('/').substringAfterLast('/')
            val root = apiGet("$apiBase/at-home/server/$chapterId")

            val serverBaseUrl = root.optString("baseUrl", "")
            val chapterObj = root.optJSONObject("chapter")
                ?: return@withContext emptyList()

            val hash = chapterObj.optString("hash", "")
            val dataArray = chapterObj.optJSONArray("data")
                ?: return@withContext emptyList()

            buildList {
                for (i in 0 until dataArray.length()) {
                    val filename = dataArray.optString(i)
                    if (filename.isNullOrBlank()) continue
                    add(
                        Page(
                            index = i,
                            imageUrl = "$serverBaseUrl/data/$hash/$filename",
                        )
                    )
                }
            }
        }

    // ---------------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------------

    /**
     * Finds the cover_art relationship in [mangaItem] and constructs the CDN URL.
     * Returns an empty string if no cover is available.
     */
    private fun buildCoverUrl(mangaId: String, mangaItem: JSONObject): String {
        val relationships = mangaItem.optJSONArray("relationships") ?: return ""
        for (i in 0 until relationships.length()) {
            val rel = relationships.getJSONObject(i)
            if (rel.optString("type") == "cover_art") {
                val filename = rel.optJSONObject("attributes")?.optString("fileName") ?: continue
                return "https://uploads.mangadex.org/covers/$mangaId/$filename"
            }
        }
        return ""
    }
}
