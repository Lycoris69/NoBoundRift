package com.lycoris.noboundrift.data.remote.source

import com.lycoris.noboundrift.domain.model.Chapter
import com.lycoris.noboundrift.domain.model.Manga
import com.lycoris.noboundrift.domain.model.MangaPreview
import com.lycoris.noboundrift.domain.model.MangaStatus
import com.lycoris.noboundrift.domain.model.Page
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLEncoder
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

class AsuraScanSource @Inject constructor(
    okHttpClient: OkHttpClient,
) : BaseHttpSource(okHttpClient) {

    companion object {
        // DateTimeFormatter is immutable and thread-safe — declare once, reuse forever.
        // Format: "MMM d, yyyy" (e.g. "Dec 31, 2023")
        private val DATE_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)
    }

    override val id: Long = 4L
    override val name: String = "AsuraScans"
    override val baseUrl: String = "https://asurascans.com"

    // ---------------------------------------------------------------------------
    // fetchMangaList
    // ---------------------------------------------------------------------------

    // Selectors verified: 2026-05-29
    override suspend fun fetchMangaList(page: Int, query: String): List<MangaPreview> =
        withContext(Dispatchers.IO) {
            if (query.isNotBlank()) {
                fetchSearchResults(query)
            } else {
                fetchBrowseResults(page)
            }
        }

    private fun fetchBrowseResults(page: Int): List<MangaPreview> {
        val doc = getDocument("$baseUrl/browse?page=$page")
        return parseMangaCards(doc)
    }

    private fun fetchSearchResults(query: String): List<MangaPreview> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val doc = getDocument("$baseUrl/browse?search=$encoded")
        return parseMangaCards(doc)
    }

    // Selectors verified: 2026-06-01
    // Handles both relative hrefs (/comics/...) and absolute hrefs (https://asurascans.com/comics/...)
    // so the same parser works for both browse and search result pages.
    private fun parseMangaCards(doc: Document): List<MangaPreview> {
        return doc.select("a:has(img)").mapNotNull { anchor ->
            val href = anchor.attr("href").trim()
            if (href.isBlank()) return@mapNotNull null
            // Accept only manga-level links; skip chapter-level links
            val isRelativeComics = href.startsWith("/comics/") && !href.contains("/chapter/")
            val isAbsoluteComics = href.contains("$baseUrl/comics/") && !href.contains("/chapter/")
            if (!isRelativeComics && !isAbsoluteComics) return@mapNotNull null

            val img = anchor.selectFirst("img") ?: return@mapNotNull null
            val title = img.attr("alt").trim()
            if (title.isBlank()) return@mapNotNull null

            val mangaUrl = if (href.startsWith("http")) href else baseUrl + href
            val relativeHref = if (href.startsWith("http")) href.removePrefix(baseUrl) else href

            val coverUrl = img.attr("src").trim().takeIf { it.isNotBlank() }
                ?: img.attr("data-src").trim().ifBlank { "" }
            val mangaId = relativeHref.trimEnd('/').substringAfterLast('/')

            MangaPreview(id = mangaId, title = title, coverUrl = coverUrl, sourceId = id, url = mangaUrl)
        }
    }

    // ---------------------------------------------------------------------------
    // fetchMangaDetail
    // ---------------------------------------------------------------------------

    // Selectors verified: 2026-05-29
    override suspend fun fetchMangaDetail(url: String): Manga =
        withContext(Dispatchers.IO) {
            val rawHtml = getRawHtml(url)
            val doc = Jsoup.parse(rawHtml, url)

            val jsonLd = doc.select("script[type=application/ld+json]")
                .map { it.html() }
                .firstOrNull { it.contains("\"ComicSeries\"") }

            var title = ""
            var coverUrl = ""
            var synopsis = ""
            var genres = emptyList<String>()

            val json = jsonLd?.let { runCatching { JSONObject(it) }.getOrNull() }
            if (json != null) {
                title = json.optString("name", "").trim()
                synopsis = json.optString("description", "").trim()
                coverUrl = json.optString("image", "").trim()
                val genreArray = json.optJSONArray("genre")
                if (genreArray != null) {
                    genres = (0 until genreArray.length()).map { genreArray.getString(it).trim() }
                }
            }

            // Selectors verified: 2026-05-29
            val statusMatch = Regex("""capitalize[^>]*>\s*(\w+)\s*</span>""").find(rawHtml)
            val statusText = statusMatch?.groupValues?.get(1)?.trim() ?: ""
            val status = parseStatus(statusText)

            val mangaId = url.trimEnd('/').substringAfterLast('/')

            Manga(
                id = mangaId,
                title = title,
                coverUrl = coverUrl,
                synopsis = synopsis,
                genres = genres,
                status = status,
                sourceId = id,
                url = url,
            )
        }

    // ---------------------------------------------------------------------------
    // fetchChapterList
    // ---------------------------------------------------------------------------

    // Selectors verified: 2026-05-29
    // Chapters are embedded directly in the manga detail page HTML — no AJAX needed.
    override suspend fun fetchChapterList(mangaUrl: String): List<Chapter> =
        withContext(Dispatchers.IO) {
            val mangaId = mangaUrl.trimEnd('/').substringAfterLast('/')
            val doc = getDocument(mangaUrl)

            // Build number->isLocked map from ChapterListReact props (more reliable than HTML).
            val isLockedMap: Map<Float, Boolean> = runCatching {
                val island = doc.selectFirst("astro-island[component-url*=ChapterListReact]")
                    ?: return@runCatching emptyMap()
                val propsRaw = island.attr("props").replace("&quot;", "\"")
                val props = JSONObject(propsRaw)
                val chaptersOuter = props.optJSONArray("chapters")
                    ?: return@runCatching emptyMap()
                val chaptersArray = chaptersOuter.optJSONArray(1)
                    ?: return@runCatching emptyMap()
                (0 until chaptersArray.length()).mapNotNull { i ->
                    runCatching {
                        val item = chaptersArray.getJSONArray(i)
                        val obj = item.getJSONObject(1)
                        val number = obj.getJSONArray("number").getDouble(1).toFloat()
                        val isLocked = obj.getJSONArray("is_locked").getBoolean(1)
                        number to isLocked
                    }.getOrNull()
                }.toMap()
            }.getOrElse { emptyMap() }

            // Extract the manga path segment (e.g. "/comics/nano-machine-7b57f74d") to
            // filter out sidebar/related links that also contain "/chapter/".
            val mangaPath = mangaUrl.removePrefix(baseUrl).trimEnd('/')

            val chapters = doc.select("a[href*=/chapter/]")
                .filter { it.attr("href").startsWith(mangaPath) }
                .mapIndexedNotNull { index, anchor ->
                    val href = anchor.attr("href").trim()
                    if (href.isBlank()) return@mapIndexedNotNull null
                    val chapterUrl = baseUrl + href

                    val chapterTitle = anchor.selectFirst("span.font-medium")?.text()?.trim() ?: ""
                    val number = href.substringAfterLast("/").toFloatOrNull() ?: index.toFloat()

                    val dateText = anchor.selectFirst("div[class~=flex-shrink-0] span")?.text()?.trim() ?: ""
                    val dateUpload = parseDateMillis(dateText, DATE_FORMATTER)

                    val chapterId = href.substringAfterLast("/")

                    Chapter(
                        id = chapterId,
                        mangaId = mangaId,
                        title = chapterTitle,
                        number = number,
                        url = chapterUrl,
                        dateUpload = dateUpload,
                        isLocked = isLockedMap[number] ?: false,
                    )
                }

            // The page lists the same chapter link multiple times (sidebar + main list);
            // deduplicate by URL before sorting to avoid duplicate keys in the UI.
            chapters.distinctBy { it.url }.sortedBy { it.number }
        }

    // ---------------------------------------------------------------------------
    // fetchPageList
    // ---------------------------------------------------------------------------

    // Pages are server-rendered inside an <astro-island> props blob, not as <img> tags.
    // The props use Astro's tuple encoding: [0, value] = scalar, [1, array] = list.
    // Verified: 2026-05-29
    override suspend fun fetchPageList(chapterUrl: String): List<Page> =
        withContext(Dispatchers.IO) {
            val doc = getDocument(chapterUrl)
            val island = doc.selectFirst("astro-island[component-url*=ChapterReader]")
                ?: return@withContext emptyList()

            val propsRaw = island.attr("props").replace("&quot;", "\"")
            val props = runCatching { JSONObject(propsRaw) }.getOrNull()
                ?: return@withContext emptyList()

            // "pages": [1, [ [0, {"url": [0, "https://cdn.asurascans.com/..."]}], ... ]]
            val pagesOuter = props.optJSONArray("pages")
                ?: return@withContext emptyList()
            val pagesArray = pagesOuter.optJSONArray(1)
                ?: return@withContext emptyList()

            (0 until pagesArray.length()).mapIndexedNotNull { index, _ ->
                runCatching {
                    val item = pagesArray.getJSONArray(index)   // [0, {url: [0, "..."]}]
                    val obj = item.getJSONObject(1)             // {url: [0, "..."]}
                    val imageUrl = obj.getJSONArray("url").getString(1)
                    if (imageUrl.isBlank()) null else Page(index = index, imageUrl = imageUrl)
                }.getOrNull()
            }
        }

}
