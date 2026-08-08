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
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/**
 * [Source] implementation for Mgeko (mgeko.cc).
 * Custom site — NOT Madara/WordPress. No Cloudflare protection.
 *
 * Notable differences from Madara sources:
 *  - Chapters are fetched from a dedicated /all-chapters/ sub-page, not via AJAX.
 *  - Browse cards use `li.novel-item`; the swiper carousel shares the same class and
 *    must be excluded via [filterNot] on `swiper-slide`.
 *  - Page images use eager loading (`src`); the CDN (imgsrv5.com) validates the
 *    Referer header, so [Page.refererUrl] is set to the site root.
 *  - Chapter datetime strings require pre-processing before parsing (see [parseMgekoDatetime]).
 *
 * // Selectors verified: 2026-08-08
 */
class MgekoSource @Inject constructor(
    okHttpClient: OkHttpClient,
) : BaseHttpSource(okHttpClient) {

    override val id: Long = 7L
    override val name: String = "Mgeko"
    override val baseUrl: String = "https://www.mgeko.cc"
    // The browse-comics JSON API does not include last-update timestamps.
    // Skip date grouping so items stay in their source-defined order.
    override val providesLatestDates: Boolean = false

    companion object {
        // DateTimeFormatter is immutable and thread-safe — declare once, reuse forever.

        // Format: "MMM d yyyy" (e.g. "Aug 8 2026") — used after extracting the date
        // portion from the chapter datetime string (e.g. "Aug. 8, 2026, 7:24 a.m.").
        private val CHAPTER_DATE_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("MMM d yyyy", Locale.ENGLISH)

        // Captures "Aug. 8, 2026" → groups: month="Aug", day="8", year="2026"
        private val CHAPTER_DATE_REGEX = Regex("""(\w+)\. (\d+), (\d{4})""")

        // Captures the first number (with optional decimal) from a chapter title.
        // e.g. "Chapter 123.5 - Title" → "123.5"
        private val CHAPTER_NUMBER_REGEX = Regex("""(\d+(?:\.\d+)?)""")
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
     * Fetches the browse catalogue via the site's JSON API.
     *
     * The /browse-comics/ shell page is JS-rendered — card HTML is injected by the
     * browser fetching /browse-comics/data/?page=N. We call that endpoint directly:
     *   • `results_html` — pre-rendered card markup (string, parsed with Jsoup)
     *   • `num_pages`    — total page count (289 pages, ~24 cards each)
     *   • `page`         — current page echoed back for validation
     *
     * Card structure (inside results_html):
     *   article.comic-card
     *     .comic-card__cover a[href]   → manga URL
     *     .comic-card__cover img[src]  → cover (eager, no data-src)
     *     h3.comic-card__title a       → title text
     *
     * No last-update date is exposed in the browse listing.
     *
     * Selector verified: 2026-08-08
     */
    private fun fetchBrowseResults(page: Int): List<MangaPreview> {
        val apiUrl = "$baseUrl/browse-comics/data/?page=$page"
        val request = Request.Builder()
            .url(apiUrl)
            .header("Referer", "$baseUrl/browse-comics/")
            .header("X-Requested-With", "XMLHttpRequest")
            .build()

        val responseBody = okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code} from $apiUrl")
            }
            response.body?.string()
                ?: throw IllegalStateException("Empty body from $apiUrl")
        }

        val json = JSONObject(responseBody)
        val numPages = json.optInt("num_pages", Int.MAX_VALUE)
        if (page > numPages) return emptyList()

        val resultsHtml = json.optString("results_html", "")
        if (resultsHtml.isBlank()) return emptyList()

        val doc = Jsoup.parse(resultsHtml, baseUrl)
        return doc.select("article.comic-card").mapNotNull { card ->
            val anchor = card.selectFirst(".comic-card__cover a") ?: return@mapNotNull null
            val mangaUrl = anchor.absUrl("href")
            if (mangaUrl.isBlank()) return@mapNotNull null

            val title = card.selectFirst("h3.comic-card__title a")?.text()?.trim()
                ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null

            // Browse listing uses eager loading (plain src), no lazy data-src
            val imgElement = card.selectFirst(".comic-card__cover img")
            val rawCover = imgElement?.attr("src") ?: ""
            val coverUrl = when {
                rawCover.startsWith("//") -> "https:$rawCover"
                rawCover.isNotBlank() -> rawCover
                else -> ""
            }

            val mangaId = mangaUrl.trimEnd('/').substringAfterLast('/')
            MangaPreview(
                id = mangaId,
                title = title,
                coverUrl = coverUrl,
                sourceId = id,
                url = mangaUrl,
                latestChapterAt = 0L,   // browse listing does not expose update dates
            )
        }
    }

    /**
     * Queries the autocomplete endpoint, which returns an HTML fragment of up to ~10 results.
     * No pagination — autocomplete is a one-shot lookup.
     * Search results use eager loading (src), unlike browse which uses data-src.
     * Selector verified: 2026-08-08
     */
    private fun fetchSearchResults(query: String): List<MangaPreview> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val doc = getDocument("$baseUrl/autocomplete?term=$encoded")

        return doc.select("li.novel-item")
            .filterNot { it.hasClass("swiper-slide") }
            .mapNotNull { card ->
                val anchor = card.selectFirst("a") ?: return@mapNotNull null
                val mangaUrl = anchor.absUrl("href")
                if (mangaUrl.isBlank()) return@mapNotNull null

                val title = card.selectFirst("h4.novel-title")?.text()?.trim()
                    ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null

                // Autocomplete results are eagerly loaded — src is populated directly;
                // data-src is a fallback for any lazy-loaded items mixed in
                val imgElement = card.selectFirst("figure.novel-cover img")
                val rawCover = imgElement?.absUrl("src")?.takeIf { it.isNotBlank() }
                    ?: imgElement?.attr("data-src")?.takeIf { it.isNotBlank() } ?: ""
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

            val title = doc.selectFirst("h1.novel-title")?.text()?.trim() ?: ""

            // Detail cover uses figure.cover (not figure.novel-cover as in list cards)
            val imgElement = doc.selectFirst("figure.cover img")
            val rawCover = imgElement?.attr("data-src")?.takeIf { it.isNotBlank() }
                ?: imgElement?.attr("src") ?: ""
            val coverUrl = if (rawCover.startsWith("//")) "https:$rawCover" else rawCover

            // Strip boilerplate prefix that some entries prepend to their synopsis
            val rawSynopsis = doc.selectFirst("p.description")?.text()?.trim() ?: ""
            val synopsis = if (rawSynopsis.contains("The Summary is")) {
                rawSynopsis.substringAfter("The Summary is").trim()
            } else {
                rawSynopsis
            }

            val genres = doc.select("div.categories ul li a.property-item")
                .map { it.text().trim() }
                .filter { it.isNotBlank() }

            // Status is encoded as a CSS class on a <strong> element rather than as text
            val status = when {
                doc.selectFirst("strong.ongoing") != null -> MangaStatus.ONGOING
                doc.selectFirst("strong.completed") != null -> MangaStatus.COMPLETED
                else -> MangaStatus.UNKNOWN
            }

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
    // Chapters live on a dedicated /all-chapters/ sub-page, not the detail page itself
    override suspend fun fetchChapterList(mangaUrl: String): List<Chapter> =
        withContext(Dispatchers.IO) {
            val allChaptersUrl = "${mangaUrl.trimEnd('/')}/all-chapters/"
            val doc = getDocument(allChaptersUrl)
            val mangaId = mangaUrl.trimEnd('/').substringAfterLast('/')

            val chapters = doc.select("ul.chapter-list li").mapIndexedNotNull { index, li ->
                val anchor = li.selectFirst("a") ?: return@mapIndexedNotNull null
                val chapterUrl = anchor.absUrl("href")
                if (chapterUrl.isBlank()) return@mapIndexedNotNull null

                val chapterTitle = li.selectFirst("strong.chapter-title")?.text()?.trim()
                    ?.takeIf { it.isNotBlank() } ?: anchor.text().trim()

                val number = CHAPTER_NUMBER_REGEX.find(chapterTitle)
                    ?.groupValues?.get(1)?.toFloatOrNull()
                    ?: index.toFloat()

                // datetime attribute format: "Aug. 8, 2026, 7:24 a.m."
                val datetime = li.selectFirst("time.chapter-update")?.attr("datetime") ?: ""
                val dateUpload = parseMgekoDatetime(datetime)

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

            // mgeko reader uses eager loading — src is always populated, no lazy data-src.
            // The CDN (imgsrv5.com) validates the Referer header, so refererUrl must be set
            // to the site root so OkHttp sends the correct header when downloading images.
            //
            // mapNotNull first, then re-index with mapIndexed so that Page.index is always
            // the 0-based position in the returned list — gaps from skipped items would
            // cause LazyColumn duplicate-key crashes when appending the next chapter.
            doc.select("div#chapter-reader img")
                .mapNotNull { img ->
                    val src = img.attr("src").trim()
                    when {
                        src.isBlank() -> null
                        src.startsWith("data:") -> null    // skip base64 placeholders
                        src.startsWith("//") -> "https:$src"
                        else -> src
                    }
                }
                .mapIndexed { index, imageUrl ->
                    Page(index = index, imageUrl = imageUrl, refererUrl = "https://www.mgeko.cc/")
                }
        }

    // ---------------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------------

    /**
     * Parses a Mgeko chapter datetime string into epoch milliseconds.
     *
     * Input format: "Aug. 8, 2026, 7:24 a.m."
     * Strategy: extract the date portion with a regex (discarding the time component),
     * reassemble as "MMM d yyyy", then parse with [CHAPTER_DATE_FORMATTER].
     * Returns 0L on any parse failure.
     */
    private fun parseMgekoDatetime(datetime: String): Long {
        val match = CHAPTER_DATE_REGEX.find(datetime) ?: return 0L
        val (month, day, year) = match.destructured
        return try {
            val localDate = LocalDate.parse("$month $day $year", CHAPTER_DATE_FORMATTER)
            localDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        } catch (e: Exception) {
            0L
        }
    }
}
