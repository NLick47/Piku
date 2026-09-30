package com.piku.client.domain.usecase

import com.piku.client.data.repository.FavoriteRepository
import com.piku.client.domain.model.WorkKey
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveFavoriteIdsUseCase @Inject constructor(
    private val favoriteRepository: FavoriteRepository,
) {
    operator fun invoke(): Flow<Set<WorkKey>> = favoriteRepository.observeFavoriteIds()
}
