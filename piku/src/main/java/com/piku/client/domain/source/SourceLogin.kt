package com.piku.client.domain.source

import com.piku.client.domain.model.WorkSource

fun interface SourceLogin {
    operator fun invoke(source: WorkSource): Boolean
}
