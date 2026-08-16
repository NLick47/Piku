package com.piku.client.domain.usecase

import com.piku.client.data.repository.FavoriteRepository
import com.piku.client.domain.model.Work
import javax.inject.Inject

class ToggleFavoriteUseCase @Inject constructor(
    private val favoriteRepository: FavoriteRepository,
) {
    suspend operator fun invoke(work: Work) = favoriteRepository.toggleFavorite(work)
}