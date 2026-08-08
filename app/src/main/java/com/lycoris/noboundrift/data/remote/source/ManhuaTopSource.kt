package com.lycoris.noboundrift.data.remote.source

import com.lycoris.noboundrift.domain.model.Chapter
import com.lycoris.noboundrift.domain.model.Manga
import com.lycoris.noboundrift.domain.model.MangaPreview
import com.lycoris.noboundrift.domain.model.MangaStatus
import com.lycoris.noboundrift.domain.model.Page
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/**
 * [Source] implementation for ManhuaTop (manhuatop.org).
 * Site runs the Madara WordPress manga theme.
 *
 * Unlike MangaRead (which embeds chapters in the detail page HTML), ManhuaTop loads
 * its chapter list via an AJAX POST to `/ajax/chapters/` — see [fetchChapterList].
 *
 * // Selectors verified: 2026-08-08
 */
class ManhuaTopSource @Inject constructor(
    okHttpClient: OkHttpClient,
) : BaseHttpSource(okHttpClient) {

    override val id: Long = 6L
    override val name: String = "ManhuaTop"
    override val baseUrl: String = "https://manhuatop.org"

    companion object {
        // DateTimeFormatter is immutable and thread-safe — declare once, reuse forever.
        // Format: "MM/dd/yyyy" (e.g. "07/25/2025") — used for chapter release dates.
        // Browse dates are relative ("8 minutes ago") and bypass this formatter via the
        // relative-expression fallback in BaseHttpSource.parseDateMillis.
        private val DATE_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.ENGLISH)
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
     * Fetches the catalogue browse page sorted by latest update.
     * Page 1 uses the base /manhua/ route; subsequent pages use /manhua/page/N/.
     * Selector verified: 2026-08-08
     */
    private fun fetchBrowseResults(page: Int): List<MangaPreview> {
        val url = if (page == 1) {
            "$baseUrl/manhua/?m_orderby=latest"
        } else {
            "$baseUrl/manhua/page/$page/?m_orderby=latest"
        }
        val doc = getDocument(url)
        // ManhuaTop uses the standard Madara page-item-detail card layout.
        // (An earlier assumption of comic_post__item was incorrect — verified 2026-08-08.)
        return doc.select("div.page-item-detail").mapNotNull { card ->
            val anchor = card.selectFirst(".post-title h3 a") ?: return@mapNotNull null
            val title = anchor.text().trim()
            val mangaUrl = anchor.absUrl("href")
            if (mangaUrl.isBlank()) return@mapNotNull null

            val imgElement = card.selectFirst(".item-thumb img")
            val rawCover = imgElement?.attr("data-src")?.takeIf { it.isNotBlank() }
                ?: imgElement?.attr("data-lazy-src")?.takeIf { it.isNotBlank() }
                ?: imgElement?.attr("src") ?: ""
            val coverUrl = if (rawCover.startsWith("//")) "https:$rawCover" else rawCover

            // Two date formats exist in .post-on:
            //  • Recent update: date is in <a class="c-new-tag" title="4 hours ago"> (text() is empty)
            //  • Older update: bare text node "07/16/2024"
            // Always try the title attribute of a.c-new-tag first.
            val postOnEl = card.selectFirst(".chapter-item .post-on")
            val dateText = postOnEl?.selectFirst("a.c-new-tag")?.attr("title")?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: postOnEl?.text()?.trim() ?: ""
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
     * Fetches the WordPress search results page for [query].
     * Uses the standard Madara search endpoint and card layout.
     * Selector verified: 2026-08-08
     */
    private fun fetchSearchResults(query: String): List<MangaPreview> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val doc = getDocument("$baseUrl/?s=$encoded&post_type=wp-manga")
        // Search results page uses a different container selector than the browse grid
        return doc.select("div.c-tabs-item__content").mapNotNull { card ->
            val anchor = card.selectFirst(".post-title h3 a") ?: return@mapNotNull null
            val title = anchor.text().trim()
            val mangaUrl = anchor.absUrl("href")
            if (mangaUrl.isBlank()) return@mapNotNull null

            val imgElement = card.selectFirst(".tab-thumb img")
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

            // h1 is preferred on single-manga pages; some themes use h3 instead
            val title = (doc.selectFirst(".post-title h1") ?: doc.selectFirst(".post-title h3"))
                ?.text()?.trim() ?: ""

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

    // Selectors verified: 2026-08-08
    // ManhuaTop uses AJAX POST to load chapters — they are NOT embedded in the detail page HTML.
    // Endpoint: {mangaUrl}/ajax/chapters/ with an empty form body and XHR header.
    override suspend fun fetchChapterList(mangaUrl: String): List<Chapter> =
        withContext(Dispatchers.IO) {
            val ajaxUrl = mangaUrl.trimEnd('/') + "/ajax/chapters/"
            val requestBody = FormBody.Builder().build()   // Madara expects an empty POST body
            val request = Request.Builder()
                .url(ajaxUrl)
                .post(requestBody)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", mangaUrl)
                .build()

            val html = okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("HTTP ${response.code} from $ajaxUrl")
                }
                response.body?.string()
                    ?: throw IllegalStateException("Empty body from $ajaxUrl")
            }

            // Supply ajaxUrl as base so that any relative hrefs in the fragment resolve correctly
            val doc = Jsoup.parse(html, ajaxUrl)
            val mangaId = mangaUrl.trimEnd('/').substringAfterLast('/')
            val chapterNumberRegex = Regex("Chapter\\s*([\\d.]+)", RegexOption.IGNORE_CASE)

            val chapters = doc.select("li.wp-manga-chapter").mapIndexedNotNull { index, li ->
                val anchor = li.selectFirst("a") ?: return@mapIndexedNotNull null
                val chapterUrl = anchor.absUrl("href")
                if (chapterUrl.isBlank()) return@mapIndexedNotNull null

                val chapterTitle = anchor.text().trim()
                val number = chapterNumberRegex.find(chapterTitle)
                    ?.groupValues?.get(1)?.toFloatOrNull()
                    ?: index.toFloat()

                // Date format: "07/25/2025" (MM/dd/yyyy)
                val dateText = li.selectFirst("span.chapter-release-date i")?.text()?.trim() ?: ""
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
            // ?style=list forces the single-page reader to show all images in one document,
            // which avoids paginated chapter views that load images via JavaScript
            val listUrl = if (chapterUrl.contains("?")) chapterUrl else "$chapterUrl?style=list"
            val doc = getDocument(listUrl)

            // mapNotNull first, then re-index with mapIndexed so that Page.index is always
            // the 0-based position in the returned list — skipped items (base64 placeholders,
            // blank src) would otherwise leave gaps that cause LazyColumn duplicate-key crashes
            // when onNearEnd offsets the next chapter's pages by pages.size.
            doc.select("div.reading-content img")
                .mapNotNull { img ->
                    val rawUrl = img.attr("data-src").takeIf { it.isNotBlank() }
                        ?: img.attr("data-lazy-src").takeIf { it.isNotBlank() }
                        ?: img.attr("src").takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null

                    rawUrl.trim().let {
                        when {
                            it.startsWith("data:") -> return@mapNotNull null  // skip base64 placeholders
                            it.startsWith("//") -> "https:$it"
                            else -> it
                        }
                    }
                }
                .mapIndexed { index, imageUrl ->
                    Page(index = index, imageUrl = imageUrl, refererUrl = chapterUrl)
                }
        }
}
