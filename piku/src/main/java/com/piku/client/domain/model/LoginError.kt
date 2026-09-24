package com.piku.client.domain.model

sealed class LoginError : Exception() {
    data object InvalidCredentials : LoginError()
    data object Locked : LoginError()
    data object Network : LoginError()
    data object Unknown : LoginError()

    /** 登录在途期间用户已登出：结果作废 */
    data object Cancelled : LoginError()
}
