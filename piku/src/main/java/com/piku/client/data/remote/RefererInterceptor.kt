package com.piku.client.data.remote

import okhttp3.Interceptor
import okhttp3.Response

class RefererInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        // poipiku 的 CDN 认本站地址作 Referer；pixiv 的图片 CDN 反过来只认站点页，
        // 拿图片自己的地址当 Referer 会 403
        val referer = if (request.url.host.endsWith(PIXIV_IMAGE_HOST_SUFFIX)) {
            PIXIV_REFERER
        } else {
            request.url.newBuilder()
                .query(null)
                .fragment(null)
                .build()
                .toString()
        }
        return chain.proceed(
            request.newBuilder()
                .header("Referer", referer)
                .build()
        )
    }

    private companion object {
        const val PIXIV_IMAGE_HOST_SUFFIX = "pximg.net"
        const val PIXIV_REFERER = "https://www.pixiv.net/"
    }
}
