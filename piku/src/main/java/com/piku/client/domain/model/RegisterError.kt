package com.piku.client.domain.model

sealed class RegisterError : Exception() {
    data object EmailInUse : RegisterError()
    data object InvalidNickname : RegisterError()
    data object InvalidEmail : RegisterError()
    data object Network : RegisterError()
    data object Unknown : RegisterError()
}