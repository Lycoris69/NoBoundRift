package com.lycoris.noboundrift.domain.repository

import com.lycoris.noboundrift.domain.model.RecommendedManga

interface RecommendationRepository {
    suspend fun getRecommendations(): Result<List<RecommendedManga>>
}
