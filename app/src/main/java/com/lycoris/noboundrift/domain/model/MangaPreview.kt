package com.lycoris.noboundrift.domain.model

/**
 * Lightweight representation of a manga entry as returned by a source's browse/search page.
 * Used in lists — full details are loaded lazily via [Manga].
 */
data class MangaPreview(
    val id: String,
    val title: String,
    val coverUrl: String,
    /** ID of the [Source][com.lycoris.noboundrift.data.remote.source.Source] this entry came from. */
    val sourceId: Long,
    /** Canonical URL on the source website. Used as the stable key for fetching full details. */
    val url: String,
)
