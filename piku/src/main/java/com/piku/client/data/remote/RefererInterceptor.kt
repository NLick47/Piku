package com.piku.client.data.remote

import okhttp3.Interceptor
import okhttp3.Response

class RefererInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val referer = request.url.newBuilder()
            .query(null)
            .fragment(null)
            .build()
            .toString()
        return chain.proceed(
            request.newBuilder()
                .header("Referer", referer)
                .build()
        )
    }
}