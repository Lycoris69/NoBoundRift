package com.lycoris.noboundrift.domain.usecase

import com.lycoris.noboundrift.domain.model.RecommendedManga
import com.lycoris.noboundrift.domain.repository.RecommendationRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetRecommendationsUseCase @Inject constructor(
    private val repository: RecommendationRepository,
) {
    suspend operator fun invoke(): Result<List<RecommendedManga>> =
        repository.getRecommendations()
}
