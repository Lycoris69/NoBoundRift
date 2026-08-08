package com.lycoris.noboundrift.data.remote.source

import com.lycoris.noboundrift.domain.model.Chapter
import com.lycoris.noboundrift.domain.model.Manga
import com.lycoris.noboundrift.domain.model.MangaPreview
import com.lycoris.noboundrift.domain.model.MangaStatus
import com.lycoris.noboundrift.domain.model.Page
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.net.URLEncoder
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/**
 * [Source] implementation for MangaKatana (mangakatana.com).
 * Custom site, no Cloudflare. All content is in static HTML — no JS rendering needed.
 *
 * // Selectors verified: 2026-08-08
 */
class MangaKatanaSource @Inject constructor(
    okHttpClient: OkHttpClient,
) : BaseHttpSource(okHttpClient) {

    override val id: Long = 8L
    override val name: String = "MangaKatana"
    override val baseUrl: String = "https://mangakatana.com"

    companion object {
        // DateTimeFormatter is immutable and thread-safe — declare once, reuse forever.
        // Format: "MMM-dd-yyyy" (e.g. "Jun-08-2019")
        private val DATE_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("MMM-dd-yyyy", Locale.ENGLISH)
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
        // /latest lists by most-recently-updated; /manga lists by popularity. Use /latest.
        val url = if (page <= 1) "$baseUrl/latest" else "$baseUrl/latest/page/$page"
        val doc = getDocument(url)
        return doc.select("div#book_list div.item[data-id]").mapNotNull { element ->
            val anchor = element.selectFirst("div.text h3.title > a") ?: return@mapNotNull null
            val title = anchor.text().trim()
            val mangaUrl = anchor.absUrl("href")
            if (mangaUrl.isBlank()) return@mapNotNull null

            val imgElement = element.selectFirst("div.wrap_img img")
            val rawCover = imgElement?.absUrl("src") ?: ""
            val coverUrl = if (rawCover.startsWith("//")) "https:$rawCover" else rawCover

            val dateText = element.selectFirst("div.text div.date")?.text()?.trim() ?: ""
            val latestChapterAt = parseDateMillis(dateText, DATE_FORMATTER)

            // Prefer the data-id attribute; fall back to deriving the ID from the URL slug.
            val rawId = element.attr("data-id")
            val mangaId = rawId.takeIf { it.isNotBlank() }
                ?: mangaUrl.trimEnd('/').substringAfterLast('/')

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
     * Fetches search results for [query]. Results share the same card structure as browse.
     * Selector verified: 2026-08-08
     */
    private fun fetchSearchResults(query: String): List<MangaPreview> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val doc = getDocument("$baseUrl/?search=$encoded&search_by=book_name")
        return doc.select("div#book_list div.item[data-id]").mapNotNull { element ->
            val anchor = element.selectFirst("div.text h3.title > a") ?: return@mapNotNull null
            val title = anchor.text().trim()
            val mangaUrl = anchor.absUrl("href")
            if (mangaUrl.isBlank()) return@mapNotNull null

            val imgElement = element.selectFirst("div.wrap_img img")
            val rawCover = imgElement?.absUrl("src") ?: ""
            val coverUrl = if (rawCover.startsWith("//")) "https:$rawCover" else rawCover

            val rawId = element.attr("data-id")
            val mangaId = rawId.takeIf { it.isNotBlank() }
                ?: mangaUrl.trimEnd('/').substringAfterLast('/')

            MangaPreview(
                id = mangaId,
                title = title,
                coverUrl = coverUrl,
                sourceId = id,
                url = mangaUrl,
            )
        }
    }

    // ---------------------------------------------------------------------------
    // fetchMangaDetail
    // ---------------------------------------------------------------------------

    // Selectors verified: 2026-08-08
    override suspend fun fetchMangaDetail(url: String): Manga =
        withContext(Dispatchers.IO) {
            val doc = getDocument(url)

            val title = doc.selectFirst("h1.heading")?.text()?.trim() ?: ""

            // Cover lives inside a <picture> element; target the <img> child directly.
            val imgElement = doc.selectFirst("div.cover img")
            val rawCover = imgElement?.absUrl("src") ?: ""
            val coverUrl = if (rawCover.startsWith("//")) "https:$rawCover" else rawCover

            // First non-blank paragraph of the synopsis block.
            val synopsis = doc.select("div.summary > p")
                .firstOrNull { it.text().isNotBlank() }
                ?.text()?.trim() ?: ""

            val genres = doc.select("div.genres a.text_0")
                .map { it.text().trim() }
                .filter { it.isNotBlank() }

            val statusText = doc.selectFirst("div.d-cell-small.value.status")
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
    // Chapters are embedded directly in the manga page HTML — no AJAX step needed.
    override suspend fun fetchChapterList(mangaUrl: String): List<Chapter> =
        withContext(Dispatchers.IO) {
            val mangaId = mangaUrl.trimEnd('/').substringAfterLast('/')
            val doc = getDocument(mangaUrl)
            val chapterNumberRegex = Regex("Chapter\\s*([\\d.]+)", RegexOption.IGNORE_CASE)

            // :has(div.chapter) filters out header <tr> rows that lack chapter content.
            val chapters = doc.select("div.chapters tr:has(div.chapter)").mapIndexedNotNull { index, tr ->
                val anchor = tr.selectFirst("td:first-child div.chapter > a") ?: return@mapIndexedNotNull null
                val chapterUrl = anchor.absUrl("href")
                if (chapterUrl.isBlank()) return@mapIndexedNotNull null

                val chapterTitle = anchor.text().trim()
                val number = chapterNumberRegex.find(chapterTitle)
                    ?.groupValues?.get(1)?.toFloatOrNull()
                    ?: index.toFloat()

                val dateText = tr.selectFirst("td:nth-child(2) div.update_time")?.text()?.trim() ?: ""
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

            // HTML is newest-first; return ascending by chapter number.
            chapters.sortedBy { it.number }
        }

    // ---------------------------------------------------------------------------
    // fetchPageList
    // ---------------------------------------------------------------------------

    // Selectors verified: 2026-08-08
    // Images are NOT in <img> tags — they live in a JavaScript array variable on the page.
    override suspend fun fetchPageList(chapterUrl: String): List<Page> =
        withContext(Dispatchers.IO) {
            val doc = getDocument(chapterUrl)

            // The page embeds images as: var thzq=['url1','url2',...];
            // Find the script block containing the variable, then extract the array body.
            val scriptContent = doc.select("script")
                .map { it.html() }
                .firstOrNull { "var thzq=" in it } ?: ""

            val match = Regex("""var thzq=\[(.*?)];""", setOf(RegexOption.DOT_MATCHES_ALL))
                .find(scriptContent)

            val imageUrls = match?.groupValues?.get(1)
                ?.split(",")
                ?.map { it.trim().trim('\'').trim('"') }
                ?.filter { it.startsWith("http") }
                ?: emptyList()

            // mapNotNull first, then re-index with mapIndexed so that Page.index is always
            // the 0-based position in the returned list — consistent with other sources and
            // avoids duplicate-key crashes in the reader's LazyColumn.
            imageUrls
                .mapNotNull { url -> url.takeIf { it.isNotBlank() } }
                .mapIndexed { index, imageUrl ->
                    // The CDN at i1.mangakatana.com requires Referer: https://mangakatana.com/
                    // The chapter HTML page itself does not need a special Referer.
                    Page(index = index, imageUrl = imageUrl, refererUrl = "$baseUrl/")
                }
        }
}
