package com.piku.client.core.network.image

import coil3.intercept.Interceptor
import coil3.request.CachePolicy
import coil3.request.ImageResult

object ImageDiskCacheGate {
    @Volatile
    var enabled: Boolean = true
}

class ImageDiskCacheInterceptor : Interceptor {

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        if (ImageDiskCacheGate.enabled) return chain.proceed()
        val request = chain.request.newBuilder()
            .diskCachePolicy(CachePolicy.DISABLED)
            .build()
        return chain.withRequest(request).proceed()
    }
}
