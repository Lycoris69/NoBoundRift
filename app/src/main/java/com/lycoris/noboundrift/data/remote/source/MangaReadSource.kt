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
 * [Source] implementation for MangaRead (mangaread.org).
 * Site runs the Madara WordPress manga theme.
 *
 * // Selectors verified: 2025-05-27
 */
class MangaReadSource @Inject constructor(
    private val okHttpClient: OkHttpClient,
) : Source {

    override val id: Long = 2L
    override val name: String = "MangaRead"
    override val baseUrl: String = "https://www.mangaread.org"

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

    // Selectors verified: 2025-05-27
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
     * Selector verified: 2025-05-27
     */
    private fun fetchBrowseResults(page: Int): List<MangaPreview> {
        val doc = getDocument("$baseUrl/manga/?page=$page&order=update")
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
     * Fetches the WordPress search results page for [query].
     * Selector verified: 2025-05-27
     */
    private fun fetchSearchResults(query: String): List<MangaPreview> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val doc = getDocument("$baseUrl/?s=$encoded&post_type=wp-manga")
        // Search results page uses a different container selector than the browse grid
        return doc.select("div.c-tabs-item__content").mapNotNull { element ->
            val anchor = element.selectFirst(".post-title h3 a") ?: return@mapNotNull null
            val title = anchor.text().trim()
            val mangaUrl = anchor.attr("href").let { href ->
                if (href.startsWith("//")) "https:$href" else href
            }
            if (mangaUrl.isBlank()) return@mapNotNull null

            val imgElement = element.selectFirst(".tab-thumb img")
            val rawCover = imgElement?.attr("data-src")?.takeIf { it.isNotBlank() }
                ?: imgElement?.attr("src") ?: ""
            val coverUrl = if (rawCover.startsWith("//")) "https:$rawCover" else rawCover

            val mangaId = mangaUrl.trimEnd('/').substringAfterLast('/')
            MangaPreview(id = mangaId, title = title, coverUrl = coverUrl, sourceId = id, url = mangaUrl)
        }
    }

    // ---------------------------------------------------------------------------
    // fetchMangaDetail
    // ---------------------------------------------------------------------------

    // Selectors verified: 2025-05-27
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

    // Selectors verified: 2025-05-28
    // Chapters are embedded directly in the manga page — no AJAX step needed.
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

                val dateText = li.selectFirst(".chapter-release-date i")?.attr("title") ?: ""
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

    // Selectors verified: 2025-05-27
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

    private fun parseStatus(text: String): MangaStatus = when {
        text.contains("ongoing", ignoreCase = true) -> MangaStatus.ONGOING
        text.contains("completed", ignoreCase = true) -> MangaStatus.COMPLETED
        text.contains("hiatus", ignoreCase = true) -> MangaStatus.HIATUS
        text.contains("cancelled", ignoreCase = true) ||
            text.contains("canceled", ignoreCase = true) -> MangaStatus.CANCELLED
        else -> MangaStatus.UNKNOWN
    }

    /**
     * Attempts to parse a date string from a Madara chapter date tooltip into epoch millis.
     * Returns 0L on any parse failure rather than throwing.
     */
    private fun parseDateMillis(text: String): Long {
        if (text.isBlank()) return 0L
        return runCatching {
            java.text.SimpleDateFormat("MMMM d, yyyy", java.util.Locale.ENGLISH)
                .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                .parse(text)?.time ?: 0L
        }.getOrDefault(0L)
    }
}
