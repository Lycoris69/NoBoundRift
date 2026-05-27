package com.lycoris.noboundrift.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity that tracks per-chapter reading progress.
 *
 * The [chapterUrl] is the stable canonical key — source scrapers identify chapters
 * by URL, so this is safe to use as a foreign-key-like reference even without a
 * strict FK constraint (since chapter data isn't fully persisted in Room).
 */
@Entity(
    tableName = "chapter_progress",
    indices = [Index(value = ["mangaId"]), Index(value = ["chapterUrl"], unique = true)],
)
data class ChapterEntity(
    @PrimaryKey
    val chapterUrl: String,
    val mangaId: String,
    val title: String,
    val number: Float,
    val read: Boolean = false,
    val dateUpload: Long = 0L,
    /** Unix epoch millis when the read state was last updated. */
    val lastReadAt: Long = 0L,
)
