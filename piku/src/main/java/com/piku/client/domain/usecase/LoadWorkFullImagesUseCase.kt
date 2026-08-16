package com.piku.client.domain.usecase

import com.piku.client.data.repository.DetailRepository
import com.piku.client.domain.model.Work
import javax.inject.Inject

class LoadWorkFullImagesUseCase @Inject constructor(
    private val detailRepository: DetailRepository,
) {
    suspend operator fun invoke(work: Work, password: String = ""): Result<List<String>> =
        detailRepository.getWorkFullImages(work, password)
}