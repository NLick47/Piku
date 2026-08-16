package com.piku.client.domain.usecase

import com.piku.client.data.repository.FavoriteRepository
import com.piku.client.domain.model.Work
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveFavoritesUseCase @Inject constructor(
    private val favoriteRepository: FavoriteRepository,
) {
    operator fun invoke(): Flow<List<Work>> = favoriteRepository.observeFavorites()
}