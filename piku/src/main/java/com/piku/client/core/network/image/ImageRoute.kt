package com.piku.client.core.network.image

/**
 * 一张图实际走哪条线路。
 *
 * [NATIVE] 是带点 SNI 的原生通道，只在直连与中继都被证明不通、而它确实取到过图时才上，
 * 且不持久化——它依赖一组固定 IP，换个网络就未必成立，重启后必须重新验证。
 */
enum class ImageRoute(val label: String) {
    DIRECT("直连"),
    RELAY("中转"),
    NATIVE("原生带点 SNI"),
}
