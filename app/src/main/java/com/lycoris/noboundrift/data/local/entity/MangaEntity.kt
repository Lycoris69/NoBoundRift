package com.lycoris.noboundrift.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "manga_library")
data class MangaEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val coverUrl: String,
    val sourceId: Long,
    val url: String,
    val addedAt: Long = System.currentTimeMillis(),
    val latestChapterAt: Long = 0L,
)
