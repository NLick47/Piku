package com.piku.client.source.poipiku.domain.usecase

import com.piku.client.source.poipiku.data.repository.DetailRepository
import com.piku.client.core.model.Work
import javax.inject.Inject

class LoadWorkFullImagesUseCase @Inject constructor(
    private val detailRepository: DetailRepository,
) {
    suspend operator fun invoke(work: Work, password: String = ""): Result<List<String>> =
        detailRepository.getWorkFullImages(work, password)
}