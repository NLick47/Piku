package com.piku.client.domain.model

sealed class LoginError : Exception() {
    data object InvalidCredentials : LoginError()
    data object Locked : LoginError()
    data object Network : LoginError()
    data object Unknown : LoginError()
}
