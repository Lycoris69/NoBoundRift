package com.lycoris.noboundrift.domain.model

data class RecommendedManga(
    val aniListId: Int,
    val title: String,
    val coverUrl: String,
    val genres: List<String>,
    val score: Int,
)
