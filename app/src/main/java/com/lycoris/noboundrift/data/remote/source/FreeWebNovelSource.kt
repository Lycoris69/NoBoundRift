package com.lycoris.noboundrift.data.remote.source

import com.lycoris.noboundrift.domain.model.Chapter
import com.lycoris.noboundrift.domain.model.Manga
import com.lycoris.noboundrift.domain.model.MangaPreview
import com.lycoris.noboundrift.domain.model.Page
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder
import javax.inject.Inject

/**
 * [Source] implementation for FreeWebNovel (freewebnovel.com) — a web-novel site.
 * Plain server-rendered HTML, no Cloudflare challenge. Novel chapters are returned as text
 * [Page]s (one per paragraph); see [Source.isNovel].
 *
 * The site is ad-heavy: ad iframes / `.reader-ad-skip` blocks are injected *inside* the
 * chapter body, so [fetchPageList] strips them before reading paragraphs. Nothing here ever
 * follows or renders an ad URL — only the HTML text is read.
 *
 * Pagination differs per endpoint (verified 2026-11-20):
 * - browse `/sort/latest-release/{n}` and genre `/genre/{Genre}/{n}` use a **path** segment
 *   (`?page=n` is silently ignored and returns page 1)
 * - search `/search?keyword=q&page=n` and the novel's chapter list `?page=n` use a **query** param
 *
 * // Selectors verified: 2026-11-20
 */
class FreeWebNovelSource @Inject constructor(
    okHttpClient: OkHttpClient,
) : BaseHttpSource(okHttpClient) {

    override val id: Long = 10L
    override val name: String = "FreeWebNovel"
    override val baseUrl: String = "https://freewebnovel.com"

    // The listing carries no per-novel update dates.
    override val providesLatestDates: Boolean = false
    override val isNovel: Boolean = true

    // Genre names exactly as listed in the site's nav menu (verified 2026-11-20).
    override val genres: List<String> = listOf(
        "Action", "Adult", "Adventure", "Comedy", "Drama", "Eastern", "Ecchi", "Fan-fic",
        "Fantasy", "Game", "Gender Bender", "Harem", "Historical", "Horror", "Josei",
        "Martial Arts", "Mature", "Mecha", "Mystery", "Psychological", "Reincarnation",
        "Romance", "School Life", "Sci-fi", "Seinen", "Shoujo", "Shounen Ai", "Shounen",
        "Slice of Life", "Smut", "Sports", "Supernatural", "System", "Tragedy", "Wuxia",
        "Xianxia", "Xuanhuan", "Yaoi",
    )

    private companion object {
        const val ID_PREFIX = "fwn_"
        // The site answers HTTP 429 when hit with many requests at once (seen on page 17 of a
        // 17-page chapter list) — keep concurrency low and retry with backoff.
        const val CHAPTER_PAGE_CONCURRENCY = 2
        const val MAX_ATTEMPTS = 4
        val CHAPTER_NUMBER_REGEX = Regex("chapter[-_ ]*(\\d+)(?:[-.](\\d+))?", RegexOption.IGNORE_CASE)
        val ORDER_STYLE_REGEX = Regex("(?:^|;)\\s*order\\s*:\\s*(\\d+)", RegexOption.IGNORE_CASE)
    }

    // ---------------------------------------------------------------------------
    // Browse / search / genre
    // ---------------------------------------------------------------------------

    override suspend fun fetchMangaList(page: Int, query: String): List<MangaPreview> =
        withContext(Dispatchers.IO) {
            val url = if (query.isNotBlank()) {
                val encoded = URLEncoder.encode(query.trim(), "UTF-8")
                if (page <= 1) "$baseUrl/search?keyword=$encoded"
                else "$baseUrl/search?keyword=$encoded&page=$page"
            } else {
                pagedPath("$baseUrl/sort/latest-release", page)
            }
            parseNovelCards(getDocument(url))
        }

    override suspend fun fetchGenreList(page: Int, genre: String): List<MangaPreview> =
        withContext(Dispatchers.IO) {
            // URLEncoder turns spaces into '+', which is exactly the site's own form
            // ("/genre/Gender+Bender").
            val encoded = URLEncoder.encode(genre, "UTF-8")
            parseNovelCards(getDocument(pagedPath("$baseUrl/genre/$encoded", page)))
        }

    private fun pagedPath(base: String, page: Int) = if (page <= 1) base else "$base/$page"

    // Selector verified: 2026-11-20 — identical card markup on sort, genre and search pages.
    private fun parseNovelCards(doc: Document): List<MangaPreview> =
        doc.select("div.li-row div.li").mapNotNull { card ->
            val anchor = card.selectFirst("h3.tit > a[href]") ?: return@mapNotNull null
            val novelUrl = anchor.absUrl("href").trimEnd('/')
            val slug = slugOf(novelUrl)
            if (slug.isBlank()) return@mapNotNull null
            MangaPreview(
                id = ID_PREFIX + slug,
                title = anchor.text().trim(),
                coverUrl = card.selectFirst("div.pic img")?.let(::imageUrl).orEmpty(),
                sourceId = id,
                url = novelUrl,
            )
        }

    // ---------------------------------------------------------------------------
    // Detail
    // ---------------------------------------------------------------------------

    // Selectors verified: 2026-11-20
    override suspend fun fetchMangaDetail(url: String): Manga =
        withContext(Dispatchers.IO) {
            val doc = getDocument(url)
            val info = doc.selectFirst("div.m-book1")

            val title = info?.selectFirst("h1.tit")?.text()?.trim().orEmpty()
            val coverUrl = info?.selectFirst("div.m-imgtxt div.pic img")?.let(::imageUrl).orEmpty()

            val synopsis = info?.select("div.m-desc div.txt div.inner p")
                ?.map { it.text().trim() }
                ?.filter { it.isNotBlank() }
                ?.joinToString("\n\n")
                .orEmpty()

            val genres = info?.select("div.m-imgtxt a[href^=/genre/]")
                ?.map { it.text().trim() }
                ?.filter { it.isNotBlank() }
                ?.distinct()
                .orEmpty()

            // The status sits in the .item that carries the "time" glyph (OnGoing / Completed).
            val statusText = info?.selectFirst("div.m-imgtxt span.glyphicon-time + div.right")
                ?.text()?.trim().orEmpty()

            Manga(
                id = ID_PREFIX + slugOf(url),
                title = title,
                coverUrl = coverUrl,
                synopsis = synopsis,
                genres = genres,
                status = parseStatus(statusText),
                sourceId = id,
                url = url,
            )
        }

    // ---------------------------------------------------------------------------
    // Chapter list
    // ---------------------------------------------------------------------------

    // Selectors verified: 2026-11-20
    // The page lists "6 Latest Chapters" and, separately, the full list in `ul#idData`, paginated
    // 40 per page via `?page=n` (total in `#indexListPage[data-total-page]`). Only `#idData` is read.
    override suspend fun fetchChapterList(mangaUrl: String): List<Chapter> =
        withContext(Dispatchers.IO) {
            val novelUrl = mangaUrl.trimEnd('/')
            val mangaId = ID_PREFIX + slugOf(novelUrl)

            val firstDoc = getDocumentWithRetry(novelUrl)
            val totalPages = firstDoc.selectFirst("#indexListPage")
                ?.attr("data-total-page")?.toIntOrNull()?.coerceAtLeast(1) ?: 1

            val semaphore = Semaphore(CHAPTER_PAGE_CONCURRENCY)
            val otherDocs = coroutineScope {
                (2..totalPages).map { p ->
                    async(Dispatchers.IO) { semaphore.withPermit { getDocumentWithRetry("$novelUrl?page=$p") } }
                }.awaitAll()
            }

            val seen = HashSet<String>()
            val chapters = (listOf(firstDoc) + otherDocs)
                .flatMap { it.select("ul#idData li a[href]") }
                .mapNotNull { anchor ->
                    val chapterUrl = anchor.absUrl("href").trimEnd('/')
                    if (chapterUrl.isBlank() || !seen.add(chapterUrl)) return@mapNotNull null
                    val title = anchor.attr("title").ifBlank { anchor.text() }.trim()
                    Chapter(
                        id = chapterUrl.substringAfterLast('/'),
                        mangaId = mangaId,
                        title = title,
                        number = chapterNumber(chapterUrl, title),
                        url = chapterUrl,
                    )
                }

            // Site order is already ascending; the sort keeps that guarantee (stable for ties).
            chapters.sortedBy { it.number }
        }

    // "…/chapter-12" → 12, "…/chapter-12-5" → 12.5. Falls back to the "Chapter N" title prefix.
    private fun chapterNumber(url: String, title: String): Float {
        val m = CHAPTER_NUMBER_REGEX.find(url.substringAfterLast('/'))
            ?: CHAPTER_NUMBER_REGEX.find(title)
            ?: return 0f
        val whole = m.groupValues[1]
        val frac = m.groupValues[2]
        return (if (frac.isNotEmpty()) "$whole.$frac" else whole).toFloatOrNull() ?: 0f
    }

    // ---------------------------------------------------------------------------
    // Chapter text
    // ---------------------------------------------------------------------------

    // Selectors verified: 2026-11-20
    // The chapter body is `div#article`: an <h4> title followed by <p> paragraphs, with ad
    // blocks (`.reader-ad-skip` wrapping an a-ads <iframe>) spliced in between paragraphs.
    override suspend fun fetchPageList(chapterUrl: String): List<Page> =
        withContext(Dispatchers.IO) {
            val doc = getDocumentWithRetry(chapterUrl)
            val article = doc.selectFirst("div#article")
                ?: throw IllegalStateException("Chapter text not found at $chapterUrl")

            // Drop everything that is not text: ad wrappers, iframes, scripts, ad <ins> slots.
            article.select(".reader-ad-skip, iframe, script, style, ins, noscript").remove()

            val heading = article.selectFirst("h4")?.text()?.cleanText()?.takeIf { it.isNotBlank() }

            var paragraphs = article.select("p")
            // The site's reader script restores shuffled paragraphs via an inline CSS `order`.
            // Plain responses carry none, but honour it if it ever shows up so text never scrambles.
            val orders = paragraphs.map { ORDER_STYLE_REGEX.find(it.attr("style"))?.groupValues?.get(1)?.toIntOrNull() }
            if (orders.isNotEmpty() && orders.all { it != null }) {
                paragraphs = org.jsoup.select.Elements(
                    paragraphs.withIndex().sortedBy { orders[it.index] }.map { it.value }
                )
            }

            val texts = paragraphs.map { it.text().cleanText() }.filter { it.isNotBlank() }

            val lines = buildList {
                if (heading != null) add(heading to true)
                texts.forEach { add(it to false) }
            }
            lines.mapIndexed { index, (text, isHeading) ->
                Page(index = index, imageUrl = "", text = text, isHeading = isHeading)
            }
        }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    /** [getDocument] with linear backoff; the site rate-limits (HTTP 429) bursts of requests. */
    private suspend fun getDocumentWithRetry(url: String): Document {
        var attempt = 0
        while (true) {
            try {
                return getDocument(url)
            } catch (e: IllegalStateException) {
                // Only HTTP-status failures are retried; 404 etc. will not get better.
                val retriable = e.message?.let { it.contains("HTTP 429") || it.contains("HTTP 5") } == true
                if (!retriable || ++attempt >= MAX_ATTEMPTS) throw e
                delay(700L * attempt)
            } catch (e: java.io.IOException) {
                if (++attempt >= MAX_ATTEMPTS) throw e
                delay(700L * attempt)
            }
        }
    }

    private fun String.cleanText(): String =
        replace(' ', ' ').replace(Regex("\\s+"), " ").trim()

    private fun slugOf(url: String): String =
        url.trimEnd('/').substringBefore('?').substringAfter("/novel/", "").substringBefore('/')

    /** Cover URL: prefer the plain JPEG `src` (covers need no Referer — verified). */
    private fun imageUrl(img: Element): String {
        val raw = img.absUrl("src").ifBlank { img.attr("src") }
        return if (raw.startsWith("//")) "https:$raw" else raw
    }
}
