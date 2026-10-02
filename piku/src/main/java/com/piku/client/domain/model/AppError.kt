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

    /**
     * 作品作者被当前用户屏蔽：服务端把作品页 302 重定向到作者主页。
     * 同样是终态（重试无意义），但 UI 提示应指向屏蔽列表而不是"作品已删除"。
     */
    data object BlockedAuthor : AppError()

    /**
     * 作品存在但需要 pixiv 会话才可见：登录限定作品（login_only）走匿名网页接口、
     * 或 app-api 会话失效。同样是终态（重试无意义），UI 的出路是引导登录。
     */
    data object LoginRequired : AppError()
}