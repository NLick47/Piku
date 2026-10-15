package com.piku.client.source.poipiku.domain.usecase

import com.piku.client.core.model.Work

fun interface PoipikuTagFeed {
    suspend operator fun invoke(tag: String, page: Int): Result<List<Work>>
}
