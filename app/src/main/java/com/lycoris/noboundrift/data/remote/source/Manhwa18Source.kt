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
 * [Source] implementation for Manhwa18 (manhwa18.cc).
 * Custom site — not Madara/WordPress. Has its own CSS class conventions.
 *
 * Selectors verified: 2026-06-01
 */
class Manhwa18Source @Inject constructor(
    private val okHttpClient: OkHttpClient,
) : Source {

    override val id: Long = 5L
    override val name: String = "Manhwa18"
    override val baseUrl: String = "https://manhwa18.cc"

    // ---------------------------------------------------------------------------
    // Internal HTTP helpers
    // ---------------------------------------------------------------------------

    /**
     * GETs [url] and returns the parsed [Document]. Blocking — must run on IO dispatcher.
     */
    private fun getDocument(url: String): Document {
        val request = Request.Builder().url(url).build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code} from $url")
            }
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
     * Browse URL: /page/{n} (1-based).
     * Selector verified: 2026-06-01
     */
    private fun fetchBrowseResults(page: Int): List<MangaPreview> {
        val doc = getDocument("$baseUrl/page/$page")
        return parseMangaCards(doc)
    }

    /**
     * Search endpoint: /search?q={query}
     * Results use the same div.manga-item structure as the browse grid.
     * Selector verified: 2026-06-01
     */
    private fun fetchSearchResults(query: String): List<MangaPreview> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val doc = getDocument("$baseUrl/search?q=$encoded")
        return parseMangaCards(doc)
    }

    /**
     * Parses manga cards from both browse and search result pages.
     *
     * Card structure (verified: 2026-06-01):
     *   div.manga-item
     *     div.bsx
     *       div.thumb
     *         a[href="/webtoon/{slug}"]          ← manga URL
     *           img[data-src=..., src=...]       ← cover image
     *       div.data
     *         h3 > a                             ← title
     */
    private fun parseMangaCards(doc: Document): List<MangaPreview> {
        return doc.select("div.manga-item").mapNotNull { element ->
            // Title anchor is inside div.data h3
            val titleAnchor = element.selectFirst(".data h3 a") ?: return@mapNotNull null
            val title = titleAnchor.text().trim()

            // Manga URL comes from the thumb anchor (reliable absolute path)
            val thumbAnchor = element.selectFirst(".thumb a") ?: return@mapNotNull null
            val rawHref = thumbAnchor.attr("href")
            val mangaUrl = when {
                rawHref.startsWith("http") -> rawHref
                rawHref.startsWith("//") -> "https:$rawHref"
                rawHref.startsWith("/") -> "$baseUrl$rawHref"
                else -> return@mapNotNull null
            }
            if (mangaUrl.isBlank()) return@mapNotNull null

            val imgElement = element.selectFirst(".thumb img")
            val rawCover = imgElement?.attr("data-src")?.takeIf { it.isNotBlank() }
                ?: imgElement?.attr("src") ?: ""
            val coverUrl = when {
                rawCover.startsWith("//") -> "https:$rawCover"
                rawCover.startsWith("/") -> "$baseUrl$rawCover"
                else -> rawCover
            }

            val mangaId = mangaUrl.trimEnd('/').substringAfterLast('/')
            MangaPreview(id = mangaId, title = title, coverUrl = coverUrl, sourceId = id, url = mangaUrl)
        }
    }

    // ---------------------------------------------------------------------------
    // fetchMangaDetail
    // ---------------------------------------------------------------------------

    /**
     * Detail page structure (verified: 2026-06-01):
     *   .post-title h1                                   ← title (contains inner <span>18+</span>)
     *   .summary_image img[data-src]                     ← cover
     *   .panel-story-description .dsct p                 ← synopsis paragraphs
     *   .genres-content a                                ← genre tags
     *   .post-content_item where h5="Status" → .summary-content  ← status text
     */
    // Selectors verified: 2026-06-01
    override suspend fun fetchMangaDetail(url: String): Manga =
        withContext(Dispatchers.IO) {
            val doc = getDocument(url)

            // ownText() returns only direct text nodes of h1, skipping the inner <span>18+</span>
            val title = doc.selectFirst(".post-title h1")?.ownText()?.trim() ?: ""

            val imgElement = doc.selectFirst(".summary_image img")
            val rawCover = imgElement?.attr("data-src")?.takeIf { it.isNotBlank() }
                ?: imgElement?.attr("src") ?: ""
            val coverUrl = when {
                rawCover.startsWith("//") -> "https:$rawCover"
                rawCover.startsWith("/") -> "$baseUrl$rawCover"
                else -> rawCover
            }

            // Synopsis lives inside .panel-story-description .dsct; collect non-blank paragraphs
            val synopsis = doc.select(".panel-story-description .dsct p")
                .mapNotNull { it.text().trim().takeIf { t -> t.isNotBlank() } }
                .joinToString("\n\n")

            val genres = doc.select(".genres-content a")
                .map { it.text().trim() }
                .filter { it.isNotBlank() }

            // Status: find the .post-content_item whose summary-heading h5 text is "Status",
            // then read its sibling .summary-content.
            val statusText = doc.select(".post-content_item")
                .firstOrNull { item ->
                    item.selectFirst(".summary-heading h5")?.text()?.trim()
                        .equals("Status", ignoreCase = true)
                }
                ?.selectFirst(".summary-content")
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

    /**
     * Chapter list structure (verified: 2026-06-01):
     *   #chapterlist ul.row-content-chapter
     *     li.a-h
     *       a.chapter-name[href="/webtoon/{slug}/chapter-{n}"]   ← chapter URL + title
     *       span.chapter-time                                     ← date text ("dd MMM yyyy")
     *
     * Chapters are listed newest-first; we sort ascending before returning.
     */
    // Selectors verified: 2026-06-01
    override suspend fun fetchChapterList(mangaUrl: String): List<Chapter> =
        withContext(Dispatchers.IO) {
            val mangaId = mangaUrl.trimEnd('/').substringAfterLast('/')
            val doc = getDocument(mangaUrl)
            val chapterNumberRegex = Regex("Chapter\\s*([\\d.]+)", RegexOption.IGNORE_CASE)

            val chapters = doc.select("#chapterlist ul.row-content-chapter li.a-h")
                .mapIndexedNotNull { index, li ->
                    val anchor = li.selectFirst("a.chapter-name") ?: return@mapIndexedNotNull null
                    val rawHref = anchor.attr("href")
                    val chapterUrl = when {
                        rawHref.startsWith("http") -> rawHref
                        rawHref.startsWith("//") -> "https:$rawHref"
                        rawHref.startsWith("/") -> "$baseUrl$rawHref"
                        else -> return@mapIndexedNotNull null
                    }
                    if (chapterUrl.isBlank()) return@mapIndexedNotNull null

                    val chapterTitle = anchor.text().trim()
                    val number = chapterNumberRegex.find(chapterTitle)
                        ?.groupValues?.get(1)?.toFloatOrNull()
                        ?: index.toFloat()

                    // Date format: "dd MMM yyyy" (e.g. "25 May 2026") or absent for newest entry
                    val dateText = li.selectFirst("span.chapter-time")?.text()?.trim() ?: ""
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

    /**
     * Reader page image structure (verified: 2026-06-01):
     *   div.read-content
     *     img.loading.p{n}[data-src="https://img01.manhwa18.cc/chapters/..."]
     *
     * Images use class "loading p1", "loading p2", etc. The data-src attribute
     * holds the CDN URL on img01.manhwa18.cc; the src attr mirrors it.
     */
    // Selectors verified: 2026-06-01
    override suspend fun fetchPageList(chapterUrl: String): List<Page> =
        withContext(Dispatchers.IO) {
            val doc = getDocument(chapterUrl)

            doc.select("div.read-content img.loading")
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

    /**
     * Parses date text from chapter list spans.
     * Format: "dd MMM yyyy" (e.g. "25 May 2026"). Returns 0 if unparseable.
     * Verified: 2026-06-01
     */
    private fun parseDateMillis(text: String): Long {
        if (text.isBlank()) return 0L
        runCatching {
            java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.ENGLISH)
                .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                .parse(text)?.time
        }.getOrNull()?.let { return it }
        return 0L
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
