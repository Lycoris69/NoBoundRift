package com.lycoris.noboundrift.domain.model

data class MangaPreview(
    val id: String,
    val title: String,
    val coverUrl: String,
    val sourceId: Long,
    val url: String,
    val latestChapterAt: Long = 0L,
    val latestChapterUrl: String = "",
    val isLatestChapterRead: Boolean = false,
    // -1 = unknown, 0 = confirmed no readable chapters
    val chapterCount: Int = -1,
)
