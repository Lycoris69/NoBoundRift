package com.lycoris.noboundrift.data.remote.source

import com.lycoris.noboundrift.domain.model.Chapter
import com.lycoris.noboundrift.domain.model.Manga
import com.lycoris.noboundrift.domain.model.MangaPreview
import com.lycoris.noboundrift.domain.model.Page

/**
 * Primary extension point of the app. Each supported website is a concrete [Source]
 * implementation. All functions are suspending and must be called from [kotlinx.coroutines.Dispatchers.IO].
 *
 * Scraping selectors are entirely internal to each implementing class and must never
 * leak into the domain or presentation layers.
 */
interface Source {
    /** Stable numeric identifier. Should never change after a source is shipped. */
    val id: Long

    /** Human-readable display name shown in the browse screen. */
    val name: String

    /** Root URL of the website (no trailing slash). Used for relative URL resolution. */
    val baseUrl: String

    /**
     * True when [fetchMangaList] populates [MangaPreview.latestChapterAt] with real update
     * timestamps. Set to false for sources whose browse listing carries no date information
     * — the UI will skip the Today / Before date-grouping sections and display items in
     * the order the source returns them.
     */
    val providesLatestDates: Boolean get() = true

    /**
     * Returns a page of manga entries from the source's catalogue or search results.
     * @param page 1-based page number.
     * @param query Optional search query. When blank, returns the catalogue browse order.
     */
    suspend fun fetchMangaList(page: Int, query: String = ""): List<MangaPreview>

    /**
     * Fetches full metadata for a single manga.
     * @param url The canonical URL as returned by [fetchMangaList].
     */
    suspend fun fetchMangaDetail(url: String): Manga

    /**
     * Returns the ordered chapter list for a manga (typically newest-first from the site,
     * but implementations should sort by [Chapter.number] ascending before returning).
     */
    suspend fun fetchChapterList(mangaUrl: String): List<Chapter>

    /**
     * Returns the ordered list of page image URLs for a chapter.
     * Images are passed directly to Coil — they must be publicly accessible or the
     * implementation must set appropriate headers (via [OkHttpClient]).
     */
    suspend fun fetchPageList(chapterUrl: String): List<Page>
}
