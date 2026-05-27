package com.lycoris.noboundrift.domain.model

/**
 * Full manga metadata including chapter list.
 * Populated by [Source.fetchMangaDetail] + [Source.fetchChapterList].
 */
data class Manga(
    val id: String,
    val title: String,
    val coverUrl: String,
    val synopsis: String,
    val genres: List<String>,
    val status: MangaStatus,
    val sourceId: Long,
    /** Canonical URL on the source website. */
    val url: String,
    val chapters: List<Chapter> = emptyList(),
)

enum class MangaStatus {
    ONGOING,
    COMPLETED,
    HIATUS,
    CANCELLED,
    UNKNOWN,
}
