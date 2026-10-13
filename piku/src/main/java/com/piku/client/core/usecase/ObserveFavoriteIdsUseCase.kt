package com.piku.client.core.usecase

import com.piku.client.core.data.FavoriteRepository
import com.piku.client.core.model.WorkKey
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveFavoriteIdsUseCase @Inject constructor(
    private val favoriteRepository: FavoriteRepository,
) {
    operator fun invoke(): Flow<Set<WorkKey>> = favoriteRepository.observeFavoriteIds()
}
