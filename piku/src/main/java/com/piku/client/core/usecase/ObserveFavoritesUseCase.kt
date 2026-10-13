package com.piku.client.core.usecase

import com.piku.client.core.data.FavoriteRepository
import com.piku.client.core.model.Work
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveFavoritesUseCase @Inject constructor(
    private val favoriteRepository: FavoriteRepository,
) {
    operator fun invoke(): Flow<List<Work>> = favoriteRepository.observeFavorites()
}