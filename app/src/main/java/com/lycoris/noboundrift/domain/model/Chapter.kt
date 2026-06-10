package com.lycoris.noboundrift.domain.model

/**
 * A single chapter entry. [url] is the canonical identifier stored in Room;
 * re-fetching reading progress by URL is safe across app restarts.
 */
data class Chapter(
    val id: String,
    val mangaId: String,
    val title: String,
    /** Numeric order within the manga (e.g. 1.0, 2.5 for decimal chapters). */
    val number: Float,
    val url: String,
    val read: Boolean = false,
    /** Unix epoch millis. 0 if the source does not expose upload dates. */
    val dateUpload: Long = 0L,
    /** True while the chapter is behind a premium paywall. Not persisted in Room. */
    val isLocked: Boolean = false,
    val language: String = "",
)
