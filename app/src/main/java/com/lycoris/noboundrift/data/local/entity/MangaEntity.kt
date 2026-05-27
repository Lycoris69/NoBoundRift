package com.lycoris.noboundrift.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for manga saved to the user's library.
 * This stores [MangaPreview]-level data; full detail (synopsis, genres, chapters) is
 * always fetched fresh from the source and not persisted here to avoid stale data.
 */
@Entity(tableName = "manga_library")
data class MangaEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val coverUrl: String,
    val sourceId: Long,
    val url: String,
    /** Unix epoch millis when this entry was added to the library. */
    val addedAt: Long = System.currentTimeMillis(),
)
