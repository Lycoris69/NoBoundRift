package com.lycoris.noboundrift.data.remote.source

import com.lycoris.noboundrift.domain.model.Chapter
import com.lycoris.noboundrift.domain.model.Manga
import com.lycoris.noboundrift.domain.model.MangaPreview
import com.lycoris.noboundrift.domain.model.MangaStatus
import com.lycoris.noboundrift.domain.model.Page
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/**
 * [Source] implementation for ManhwaTop (manhwatop.com).
 * Runs the Madara WordPress manga theme with Cloudflare Managed Challenge active.
 * Cloudflare bypass is handled at the ViewModel level — not in this source.
 *
 * // Selectors verified: 2026-08-08
 */
class ManhwaTopSource @Inject constructor(
    okHttpClient: OkHttpClient,
) : BaseHttpSource(okHttpClient) {

    override val id: Long = 9L
    override val name: String = "ManhwaTop"
    override val baseUrl: String = "https://manhwatop.com"

    companion object {
        // DateTimeFormatter is immutable and thread-safe — declare once, reuse forever.
        // Format: "MMM dd, yyyy" (e.g. "Jan 18, 2025")
        private val DATE_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH)
    }

    // ---------------------------------------------------------------------------
    // fetchMangaList
    // ---------------------------------------------------------------------------

    // Selectors verified: 2026-08-08
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
     * Selector verified: 2026-08-08
     */
    private fun fetchBrowseResults(page: Int): List<MangaPreview> {
        val url = if (page <= 1) "$baseUrl/manga/" else "$baseUrl/manga/page/$page/"
        val doc = getDocument(url)
        return doc.select("div.page-item-detail").mapNotNull { element ->
            val anchor = element.selectFirst(".post-title h3 a") ?: return@mapNotNull null
            val title = anchor.text().trim()
            val mangaUrl = anchor.absUrl("href")
            if (mangaUrl.isBlank()) return@mapNotNull null

            val imgElement = element.selectFirst(".item-thumb img")
            val rawCover = imgElement?.attr("data-src")?.takeIf { it.isNotBlank() }
                ?: imgElement?.attr("src") ?: ""
            val coverUrl = if (rawCover.startsWith("//")) "https:$rawCover" else rawCover

            // Can be relative ("8 minutes ago") or absolute — BaseHttpSource's relative
            // regex fallback in parseDateMillis handles both forms.
            val dateText = element.selectFirst(".chapter-item .post-on")?.text()?.trim() ?: ""
            val latestChapterAt = parseDateMillis(dateText, DATE_FORMATTER)

            val mangaId = mangaUrl.trimEnd('/').substringAfterLast('/')

            MangaPreview(
                id = mangaId,
                title = title,
                coverUrl = coverUrl,
                sourceId = id,
                url = mangaUrl,
                latestChapterAt = latestChapterAt,
            )
        }
    }

    /**
     * Fetches WordPress search results for [query].
     * Tries standard Madara search card layout first; falls back to the browse-style
     * card layout in case the site redirects the search URL to its catalogue homepage.
     * Selector verified: 2026-08-08
     */
    private fun fetchSearchResults(query: String): List<MangaPreview> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val doc = getDocument("$baseUrl/?s=$encoded&post_type=wp-manga")

        // Standard Madara search layout.
        // NOTE: search results use <h2 class="h5"> for titles while the browse page uses
        // <h3 class="h5">. Using ".post-title a" (no heading tag) handles both.
        // Verified: 2026-08-08 — every card on /?s=test&post_type=wp-manga uses h2, never h3.
        val searchCards = doc.select("div.c-tabs-item__content")
        if (searchCards.isNotEmpty()) {
            return searchCards.mapNotNull { element ->
                val anchor = element.selectFirst(".post-title a") ?: return@mapNotNull null
                val title = anchor.text().trim()
                val mangaUrl = anchor.absUrl("href")
                if (mangaUrl.isBlank()) return@mapNotNull null

                val imgElement = element.selectFirst(".tab-thumb img")
                val rawCover = imgElement?.attr("data-src")?.takeIf { it.isNotBlank() }
                    ?: imgElement?.attr("src") ?: ""
                val coverUrl = if (rawCover.startsWith("//")) "https:$rawCover" else rawCover

                val mangaId = mangaUrl.trimEnd('/').substringAfterLast('/')
                MangaPreview(id = mangaId, title = title, coverUrl = coverUrl, sourceId = id, url = mangaUrl)
            }
        }

        // Fallback: some Madara installs redirect ?post_type=wp-manga to the browse grid,
        // returning page-item-detail cards instead of c-tabs-item__content cards.
        return doc.select("div.page-item-detail").mapNotNull { element ->
            val anchor = element.selectFirst(".post-title a") ?: return@mapNotNull null
            val title = anchor.text().trim()
            val mangaUrl = anchor.absUrl("href")
            if (mangaUrl.isBlank()) return@mapNotNull null

            val imgElement = element.selectFirst(".item-thumb img")
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

    // Selectors verified: 2026-08-08
    override suspend fun fetchMangaDetail(url: String): Manga =
        withContext(Dispatchers.IO) {
            val doc = getDocument(url)

            val title = doc.selectFirst(".post-title h1")?.text()?.trim() ?: ""

            val imgElement = doc.selectFirst(".summary_image img")
            val rawCover = imgElement?.attr("data-src")?.takeIf { it.isNotBlank() }
                ?: imgElement?.attr("src") ?: ""
            val coverUrl = if (rawCover.startsWith("//")) "https:$rawCover" else rawCover

            // First non-blank paragraph of the synopsis block.
            val synopsis = doc.select(".summary__content p")
                .firstOrNull { it.text().isNotBlank() }
                ?.text()?.trim() ?: ""

            val genres = doc.select(".genres-content a")
                .map { it.text().trim() }
                .filter { it.isNotBlank() }

            // Madara themes vary in which selector carries the status value — try both.
            val statusText = doc.selectFirst(".post-status .summary-content")?.text()?.trim()
                ?: doc.selectFirst(".post-status .mg_status")?.text()?.trim()
                ?: ""
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

    // Selectors verified: 2026-08-08
    // manhwatop.com does NOT embed li.wp-manga-chapter in static page HTML.
    // The detail page has <div id="manga-chapters-holder" data-id="..."></div>
    // as a placeholder; chapters are fetched by JS at runtime via two possible
    // AJAX endpoints. We try all three tiers in order:
    //   Tier 1 — static HTML (kept as a cheap check for future site changes)
    //   Tier 2 — POST wp-admin/admin-ajax.php with action=manga_get_chapters
    //   Tier 3 — POST {mangaUrl}/ajax/chapters/ (Madara fork fallback)
    override suspend fun fetchChapterList(mangaUrl: String): List<Chapter> =
        withContext(Dispatchers.IO) {
            val mangaId = mangaUrl.trimEnd('/').substringAfterLast('/')
            val doc = getDocument(mangaUrl)
            var chapterDoc = doc

            if (doc.select("li.wp-manga-chapter").isEmpty()) {
                // Tier 2 — wp-admin/admin-ajax.php
                val postId = doc.selectFirst("div#manga-chapters-holder")?.attr("data-id").orEmpty()
                if (postId.isNotBlank()) {
                    val body = "action=manga_get_chapters&manga=$postId"
                        .toRequestBody("application/x-www-form-urlencoded; charset=UTF-8".toMediaType())
                    val request = Request.Builder()
                        .url("$baseUrl/wp-admin/admin-ajax.php")
                        .post(body)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Referer", mangaUrl)
                        .build()
                    val html = okHttpClient.newCall(request).execute().use { resp ->
                        if (resp.isSuccessful) resp.body?.string() else null
                    }
                    if (!html.isNullOrBlank()) chapterDoc = Jsoup.parse(html, mangaUrl)
                }

                // Tier 3 — {mangaUrl}/ajax/chapters/ (fallback for some Madara installs)
                if (chapterDoc.select("li.wp-manga-chapter").isEmpty()) {
                    val ajaxUrl = mangaUrl.trimEnd('/') + "/ajax/chapters/"
                    val emptyBody = ByteArray(0).toRequestBody(null, 0, 0)
                    val request = Request.Builder()
                        .url(ajaxUrl)
                        .post(emptyBody)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Content-Length", "0")
                        .build()
                    val html = okHttpClient.newCall(request).execute().use { resp ->
                        if (resp.isSuccessful) resp.body?.string() else null
                    }
                    if (!html.isNullOrBlank()) chapterDoc = Jsoup.parse(html, mangaUrl)
                }
            }

            val chapterNumberRegex = Regex("Chapter\\s*([\\d.]+)", RegexOption.IGNORE_CASE)

            val chapters = chapterDoc.select("li.wp-manga-chapter").mapIndexedNotNull { index, li ->
                val anchor = li.selectFirst("a") ?: return@mapIndexedNotNull null
                val chapterUrl = anchor.absUrl("href")
                if (chapterUrl.isBlank()) return@mapIndexedNotNull null

                val chapterTitle = anchor.text().trim()
                val number = chapterNumberRegex.find(chapterTitle)
                    ?.groupValues?.get(1)?.toFloatOrNull()
                    ?: index.toFloat()

                val dateText = li.selectFirst(".chapter-release-date i")?.text()?.trim() ?: ""
                val dateUpload = parseDateMillis(dateText, DATE_FORMATTER)

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

    // Selectors verified: 2026-08-08
    override suspend fun fetchPageList(chapterUrl: String): List<Page> =
        withContext(Dispatchers.IO) {
            val doc = getDocument(chapterUrl)

            // mapNotNull first, then re-index with mapIndexed so that Page.index is always
            // the 0-based position in the returned list. Using mapIndexedNotNull directly
            // assigns the DOM element position as the index — if any elements are skipped
            // (base64 placeholders, blank src), the resulting indices have gaps that cause
            // duplicate-key crashes in the reader's LazyColumn.
            doc.select("div.reading-content img")
                .mapNotNull { img ->
                    val rawUrl = img.attr("data-src").takeIf { it.isNotBlank() }
                        ?: img.attr("data-lazy-src").takeIf { it.isNotBlank() }
                        ?: img.attr("src").takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null

                    val trimmed = rawUrl.trim()
                    when {
                        trimmed.startsWith("data:") -> return@mapNotNull null // skip base64 placeholders
                        trimmed.startsWith("//") -> "https:$trimmed"
                        else -> trimmed
                    }
                }
                .mapIndexed { index, imageUrl ->
                    Page(index = index, imageUrl = imageUrl, refererUrl = chapterUrl)
                }
        }
}
