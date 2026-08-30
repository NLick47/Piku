package com.piku.client.domain.model

sealed class AppError : Exception() {
    data object Network : AppError()
    data class Http(val code: Int) : AppError()
    data object Parse : AppError()
    data object Unknown : AppError()

    /**
     * 内容不存在或不可访问：服务端返回 404（作品被删除、作者注销、链接有误），
     * 或页面不是作品详情页（非公开作品等）。与 [Parse] 的关键区别是重试无意义，
     * UI 据此不再给「重试」按钮。
     */
    data object NotFound : AppError()
}