package com.lycoris.noboundrift.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DownloadStatus { QUEUED, DOWNLOADING, COMPLETED, FAILED }

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val chapterUrl: String,
    val mangaId: String,
    val mangaUrl: String,
    val sourceId: Long,
    val mangaTitle: String,
    val mangaCoverUrl: String,
    val chapterTitle: String,
    val chapterNumber: Float,
    val status: DownloadStatus,
    val totalPages: Int = 0,
    val downloadedPages: Int = 0,
    val localDir: String,
    val queuedAt: Long,
)
