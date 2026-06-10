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
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.OffsetDateTime
import javax.inject.Inject

class MangaDexSource @Inject constructor(
    okHttpClient: OkHttpClient,
) : BaseHttpSource(okHttpClient) {

    override val id: Long = 5L
    override val name: String = "MangaDex"
    override val baseUrl: String = "https://mangadex.org"

    private val apiBase = "https://api.mangadex.org"

    private fun getJson(url: String): JSONObject {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code} from $url")
            val body = response.body?.string()
                ?: throw IllegalStateException("Empty response body from $url")
            return JSONObject(body)
        }
    }

    override suspend fun fetchMangaList(page: Int, query: String): List<MangaPreview> =
        withContext(Dispatchers.IO) {
            if (query.isNotBlank()) fetchSearchResults(query) else fetchBrowseResults(page)
        }

    private fun fetchBrowseResults(page: Int): List<MangaPreview> {
        val offset = (page - 1) * 20
        val url = "$apiBase/manga?order[updatedAt]=desc&limit=20&offset=$offset" +
            "&includes[]=cover_art" +
            "&contentRating[]=safe&contentRating[]=suggestive&contentRating[]=erotica"
        return parseMangaPreviews(getJson(url).getJSONArray("data"))
    }

    private fun fetchSearchResults(query: String): List<MangaPreview> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "$apiBase/manga?title=$encoded&limit=20" +
            "&includes[]=cover_art" +
            "&contentRating[]=safe&contentRating[]=suggestive&contentRating[]=erotica"
        return parseMangaPreviews(getJson(url).getJSONArray("data"))
    }

    private fun parseMangaPreviews(data: JSONArray): List<MangaPreview> =
        (0 until data.length()).mapNotNull { i ->
            runCatching { parseMangaPreview(data.getJSONObject(i)) }.getOrNull()
        }

    private fun parseMangaPreview(obj: JSONObject): MangaPreview {
        val uuid = obj.getString("id")
        val attrs = obj.getJSONObject("attributes")

        val title = firstStringValue(attrs.getJSONObject("title"))
        val coverFileName = findCoverFileName(obj.getJSONArray("relationships"))
        val coverUrl = if (coverFileName != null) {
            "https://uploads.mangadex.org/covers/$uuid/$coverFileName.256.jpg"
        } else {
            ""
        }

        val updatedAt = attrs.optString("updatedAt", "")
        val latestChapterAt = if (updatedAt.isNotBlank()) {
            runCatching { OffsetDateTime.parse(updatedAt).toInstant().toEpochMilli() }.getOrElse { 0L }
        } else {
            0L
        }

        return MangaPreview(
            id = uuid,
            title = title,
            coverUrl = coverUrl,
            sourceId = id,
            url = "$baseUrl/title/$uuid",
            latestChapterAt = latestChapterAt,
        )
    }

    override suspend fun fetchMangaDetail(url: String): Manga =
        withContext(Dispatchers.IO) {
            val uuid = url.trimEnd('/').substringAfterLast('/')
            val data = getJson("$apiBase/manga/$uuid?includes[]=cover_art").getJSONObject("data")
            val attrs = data.getJSONObject("attributes")

            val title = firstStringValue(attrs.getJSONObject("title"))

            val descriptionObj = attrs.optJSONObject("description")
            val synopsis = if (descriptionObj != null) firstStringValue(descriptionObj) else ""

            val coverFileName = findCoverFileName(data.getJSONArray("relationships"))
            val coverUrl = if (coverFileName != null) {
                "https://uploads.mangadex.org/covers/$uuid/$coverFileName.256.jpg"
            } else {
                ""
            }

            val status = when (attrs.optString("status", "").lowercase()) {
                "ongoing" -> MangaStatus.ONGOING
                "completed" -> MangaStatus.COMPLETED
                "hiatus" -> MangaStatus.HIATUS
                "cancelled" -> MangaStatus.CANCELLED
                else -> MangaStatus.UNKNOWN
            }

            val tagsArray = attrs.optJSONArray("tags")
            val genres = if (tagsArray != null) {
                (0 until tagsArray.length()).mapNotNull { i ->
                    runCatching {
                        val nameObj = tagsArray.getJSONObject(i)
                            .getJSONObject("attributes")
                            .getJSONObject("name")
                        firstStringValue(nameObj).takeIf { it.isNotBlank() }
                    }.getOrNull()
                }
            } else {
                emptyList()
            }

            Manga(
                id = uuid,
                title = title,
                coverUrl = coverUrl,
                synopsis = synopsis,
                genres = genres,
                status = status,
                sourceId = id,
                url = url,
            )
        }

    override suspend fun fetchChapterList(mangaUrl: String): List<Chapter> =
        withContext(Dispatchers.IO) {
            val mangaId = mangaUrl.trimEnd('/').substringAfterLast('/')
            val chapters = mutableListOf<Chapter>()
            val limit = 100
            var offset = 0
            var total: Int

            do {
                val url = "$apiBase/manga/$mangaId/feed" +
                    "?translatedLanguage[]=en" +
                    "&order[chapter]=asc" +
                    "&limit=$limit" +
                    "&offset=$offset" +
                    "&contentRating[]=safe&contentRating[]=suggestive&contentRating[]=erotica" +
                    "&includeExternalUrl=0"
                val json = getJson(url)
                total = json.getInt("total")
                val data = json.getJSONArray("data")

                for (i in 0 until data.length()) {
                    val obj = data.getJSONObject(i)
                    val chapterId = obj.getString("id")
                    val attrs = obj.getJSONObject("attributes")

                    if (!attrs.isNull("externalUrl") && attrs.optString("externalUrl").isNotBlank()) continue
                    if (attrs.optInt("pages", 0) == 0) continue

                    val chapterStr = attrs.optString("chapter", "")
                    val number = chapterStr.toFloatOrNull() ?: (chapters.size + offset).toFloat()

                    val rawTitle = attrs.optString("title", "")
                    val title = if (rawTitle.isNotBlank()) {
                        rawTitle
                    } else {
                        val intNum = chapterStr.toFloatOrNull()?.let {
                            if (it % 1 == 0f) it.toInt().toString() else chapterStr
                        } ?: chapterStr
                        if (intNum.isNotBlank()) "Chapter $intNum" else "Chapter ${chapters.size + offset + 1}"
                    }

                    val publishAt = attrs.optString("publishAt", "")
                    val dateUpload = if (publishAt.isNotBlank()) {
                        runCatching { OffsetDateTime.parse(publishAt).toInstant().toEpochMilli() }.getOrElse { 0L }
                    } else {
                        0L
                    }

                    chapters.add(
                        Chapter(
                            id = chapterId,
                            mangaId = mangaId,
                            title = title,
                            number = number,
                            url = "https://mangadex.org/chapter/$chapterId",
                            dateUpload = dateUpload,
                        )
                    )
                }

                offset += data.length()
            } while (offset < total)

            chapters.sortedBy { it.number }
        }

    override suspend fun fetchPageList(chapterUrl: String): List<Page> =
        withContext(Dispatchers.IO) {
            val chapterId = chapterUrl.trimEnd('/').substringAfterLast('/')
            val json = getJson("$apiBase/at-home/server/$chapterId")
            val baseUrl = json.getString("baseUrl")
            val chapterObj = json.getJSONObject("chapter")
            val hash = chapterObj.getString("hash")
            val dataArray = chapterObj.getJSONArray("data")

            (0 until dataArray.length()).map { i ->
                Page(
                    index = i,
                    imageUrl = "$baseUrl/data/$hash/${dataArray.getString(i)}",
                    refererUrl = chapterUrl,
                )
            }
        }

    private fun findCoverFileName(relationships: JSONArray): String? {
        for (i in 0 until relationships.length()) {
            val rel = relationships.getJSONObject(i)
            if (rel.optString("type") == "cover_art") {
                return rel.optJSONObject("attributes")
                    ?.optString("fileName", "")
                    ?.takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    // Returns the English value if present, otherwise the first available non-blank value.
    private fun firstStringValue(obj: JSONObject): String {
        obj.optString("en", "").takeIf { it.isNotBlank() }?.let { return it }
        val keys = obj.keys()
        while (keys.hasNext()) {
            val v = obj.optString(keys.next(), "")
            if (v.isNotBlank()) return v
        }
        return ""
    }
}
