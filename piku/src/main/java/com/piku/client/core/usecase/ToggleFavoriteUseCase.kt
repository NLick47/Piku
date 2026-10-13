package com.piku.client.core.usecase

import com.piku.client.core.data.FavoriteRepository
import com.piku.client.core.model.Work
import javax.inject.Inject

class ToggleFavoriteUseCase @Inject constructor(
    private val favoriteRepository: FavoriteRepository,
) {
    suspend operator fun invoke(work: Work) = favoriteRepository.toggleFavorite(work)
}