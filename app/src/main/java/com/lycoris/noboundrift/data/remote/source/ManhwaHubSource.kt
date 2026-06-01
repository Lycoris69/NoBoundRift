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
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLEncoder
import javax.inject.Inject

/**
 * [Source] implementation for ManhwaHub (manhwahub.net).
 * Site runs the Madara WordPress manga theme.
 *
 * // Selectors verified: 2026-06-01
 */
class ManhwaHubSource @Inject constructor(
    private val okHttpClient: OkHttpClient,
) : Source {

    override val id: Long = 5L
    override val name: String = "ManhwaHub"
    override val baseUrl: String = "https://manhwahub.net"

    // ---------------------------------------------------------------------------
    // Internal HTTP helpers
    // ---------------------------------------------------------------------------

    /**
     * GETs [url] and returns the parsed [Document]. Blocking — must run on IO dispatcher.
     */
    private fun getDocument(url: String): Document {
        val request = Request.Builder().url(url).build()
        okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string()
                ?: throw IllegalStateException("Empty response body from $url")
            return Jsoup.parse(body, url)
        }
    }

    // ---------------------------------------------------------------------------
    // fetchMangaList
    // ---------------------------------------------------------------------------

    // Selectors verified: 2026-06-01
    override suspend fun fetchMangaList(page: Int, query: String): List<MangaPreview> =
        withContext(Dispatchers.IO) {
            if (query.isNotBlank()) {
                fetchSearchResults(query)
            } else {
                fetchBrowseResults(page)
            }
        }

    /**
     * Fetches the standard catalogue browse page.
     * Selector verified: 2026-06-01
     */
    // Selectors verified: 2026-06-01
    private fun fetchBrowseResults(page: Int): List<MangaPreview> {
        val doc = getDocument("$baseUrl/?page=$page")
        return parseMangaCards(doc)
    }

    private fun parseMangaCards(doc: Document): List<MangaPreview> {
        return doc.select("div.page-item-detail").mapNotNull { element ->
            val anchor = element.selectFirst(".post-title h3 a") ?: return@mapNotNull null
            val title = anchor.text().trim()
            val mangaUrl = anchor.attr("href").let { href ->
                if (href.startsWith("//")) "https:$href" else href
            }
            if (mangaUrl.isBlank()) return@mapNotNull null

            val imgElement = element.selectFirst(".item-thumb img")
            val rawCover = imgElement?.attr("data-src")?.takeIf { it.isNotBlank() }
                ?: imgElement?.attr("src") ?: ""
            val coverUrl = if (rawCover.startsWith("//")) "https:$rawCover" else rawCover

            val mangaId = mangaUrl.trimEnd('/').substringAfterLast('/')
            MangaPreview(id = mangaId, title = title, coverUrl = coverUrl, sourceId = id, url = mangaUrl)
        }
    }

    /**
     * Fetches the search results page for [query].
     * Endpoint: /search?s={query} (NOT /?s=...&post_type=wp-manga — that returns the homepage).
     * Search results use the same div.page-item-detail / .item-thumb structure as the browse
     * grid, so parseMangaCards handles both. Cover images appear in the plain `src` attribute
     * on the search page (no `data-src`), which the parseMangaCards fallback already handles.
     * Verified: 2026-06-01
     */
    private fun fetchSearchResults(query: String): List<MangaPreview> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val doc = getDocument("$baseUrl/search?s=$encoded")
        return parseMangaCards(doc)
    }

    // ---------------------------------------------------------------------------
    // fetchMangaDetail
    // ---------------------------------------------------------------------------

    // Selectors verified: 2026-06-01
    override suspend fun fetchMangaDetail(url: String): Manga =
        withContext(Dispatchers.IO) {
            val doc = getDocument(url)

            val title = doc.selectFirst(".post-title h1")?.text()?.trim() ?: ""

            val imgElement = doc.selectFirst(".summary_image img")
            val rawCover = imgElement?.attr("data-src")?.takeIf { it.isNotBlank() }
                ?: imgElement?.attr("src") ?: ""
            val coverUrl = if (rawCover.startsWith("//")) "https:$rawCover" else rawCover

            // First non-blank paragraph of the synopsis block
            val synopsis = doc.select(".summary__content p")
                .firstOrNull { it.text().isNotBlank() }
                ?.text()?.trim() ?: ""

            val genres = doc.select(".genres-content a")
                .map { it.text().trim() }
                .filter { it.isNotBlank() }

            val statusText = doc.selectFirst(".post-status .summary-content")
                ?.text()?.trim() ?: ""
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

    // Selectors verified: 2026-06-01
    // ManhwaHub is a Madara/WordPress site — chapters are embedded directly in the manga page,
    // no AJAX step needed.
    override suspend fun fetchChapterList(mangaUrl: String): List<Chapter> =
        withContext(Dispatchers.IO) {
            val mangaId = mangaUrl.trimEnd('/').substringAfterLast('/')
            val doc = getDocument(mangaUrl)
            val chapterNumberRegex = Regex("Chapter\\s*([\\d.]+)", RegexOption.IGNORE_CASE)

            val chapters = doc.select("li.wp-manga-chapter").mapIndexedNotNull { index, li ->
                val anchor = li.selectFirst("a") ?: return@mapIndexedNotNull null
                val chapterUrl = anchor.attr("href").let { href ->
                    if (href.startsWith("//")) "https:$href" else href
                }
                if (chapterUrl.isBlank()) return@mapIndexedNotNull null

                val chapterTitle = anchor.text().trim()
                val number = chapterNumberRegex.find(chapterTitle)
                    ?.groupValues?.get(1)?.toFloatOrNull()
                    ?: index.toFloat()

                val dateText = li.selectFirst(".chapter-release-date i")?.text()?.trim() ?: ""
                val dateUpload = parseDateMillis(dateText)

                val chapterId = chapterUrl.trimEnd('/').substringAfterLast('/')

                Chapter(
                    id = chapterId,
                    mangaId = mangaId,
                    title = chapterTitle,
                    number = number,
                    url = chapterUrl,
                    dateUpload = dateUpload,
                )
            }

            chapters.sortedBy { it.number }
        }

    // ---------------------------------------------------------------------------
    // fetchPageList
    // ---------------------------------------------------------------------------

    // Selectors verified: 2026-06-01
    override suspend fun fetchPageList(chapterUrl: String): List<Page> =
        withContext(Dispatchers.IO) {
            val doc = getDocument(chapterUrl)

            doc.select("div.reading-content img")
                .mapIndexedNotNull { index, img ->
                    val rawUrl = img.attr("data-src").takeIf { it.isNotBlank() }
                        ?: img.attr("src").takeIf { it.isNotBlank() }
                        ?: return@mapIndexedNotNull null

                    val imageUrl = rawUrl.trim().let {
                        when {
                            it.startsWith("//") -> "https:$it"
                            it.startsWith("data:") -> return@mapIndexedNotNull null // skip base64 placeholders
                            else -> it
                        }
                    }

                    Page(index = index, imageUrl = imageUrl)
                }
        }

    // ---------------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------------

    // Selectors verified: 2026-06-01
    // Date text is the inner text of .chapter-release-date i (no title attr).
    // Format: "dd.MM.yyyy" for older chapters, relative "X days ago" for recent.
    private fun parseDateMillis(text: String): Long {
        if (text.isBlank()) return 0L
        runCatching {
            java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.ENGLISH)
                .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                .parse(text)?.time
        }.getOrNull()?.let { return it }
        val match = Regex("(\\d+)\\s+(min|hour|day|week|month|year)s?\\s+ago", RegexOption.IGNORE_CASE)
            .find(text) ?: return 0L
        val amount = match.groupValues[1].toLong()
        val unitMs = when (match.groupValues[2].lowercase()) {
            "min"   -> 60_000L
            "hour"  -> 3_600_000L
            "day"   -> 86_400_000L
            "week"  -> 7 * 86_400_000L
            "month" -> 30 * 86_400_000L
            "year"  -> 365 * 86_400_000L
            else    -> return 0L
        }
        return System.currentTimeMillis() - amount * unitMs
    }

    private fun parseStatus(text: String): MangaStatus = when {
        text.contains("ongoing", ignoreCase = true) -> MangaStatus.ONGOING
        text.contains("completed", ignoreCase = true) -> MangaStatus.COMPLETED
        text.contains("hiatus", ignoreCase = true) -> MangaStatus.HIATUS
        text.contains("cancelled", ignoreCase = true) ||
            text.contains("canceled", ignoreCase = true) -> MangaStatus.CANCELLED
        else -> MangaStatus.UNKNOWN
    }

}
