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
import org.jsoup.nodes.Document
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

    /**
     * POSTs a form-encoded body to [url] and returns the parsed [Document].
     * Used for the Madara AJAX chapter list endpoint. Blocking — must run on IO dispatcher.
     */
    private fun postDocument(url: String, formParams: Map<String, String>): Document {
        val formBody = FormBody.Builder().apply {
            formParams.forEach { (key, value) -> add(key, value) }
        }.build()

        val request = Request.Builder()
            .url(url)
            .post(formBody)
            .header("X-Requested-With", "XMLHttpRequest")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string()
                ?: throw IllegalStateException("Empty POST response body from $url")
            return Jsoup.parse(body, url)
        }
    }

    // ---------------------------------------------------------------------------
    // fetchMangaList
    // ---------------------------------------------------------------------------

    // Selectors verified: 2025-05-27
    override suspend fun fetchMangaList(page: Int): List<MangaPreview> =
        withContext(Dispatchers.IO) {
            val doc = getDocument("$baseUrl/manga/?page=$page&order=update")

            doc.select("div.page-item-detail").mapNotNull { element ->
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

    // Selectors verified: 2025-05-27
    override suspend fun fetchChapterList(mangaUrl: String): List<Chapter> =
        withContext(Dispatchers.IO) {
            val mangaId = mangaUrl.trimEnd('/').substringAfterLast('/')

            // Step 1: fetch the manga page to obtain the post ID used by the AJAX endpoint
            val mangaDoc = getDocument(mangaUrl)
            val postId = mangaDoc.selectFirst("#manga-chapters-holder")
                ?.attr("data-id") ?: ""

            // Step 2: POST to admin-ajax.php to retrieve the chapter list HTML fragment
            val ajaxDoc = postDocument(
                url = "$baseUrl/wp-admin/admin-ajax.php",
                formParams = mapOf(
                    "action" to "manga_get_chapters",
                    "manga" to postId,
                )
            )

            // Step 3: parse the returned fragment
            // Selectors verified: 2025-05-27
            val chapterNumberRegex = Regex("Chapter\\s*([\\d.]+)", RegexOption.IGNORE_CASE)

            val chapters = ajaxDoc.select("li.wp-manga-chapter").mapIndexedNotNull { index, li ->
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
                .parse(text)?.time ?: 0L
        }.getOrDefault(0L)
    }
}
