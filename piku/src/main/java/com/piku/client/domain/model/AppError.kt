package com.piku.client.domain.model

sealed class AppError : Exception() {
    data object Network : AppError()
    data class Http(val code: Int) : AppError()
    data object Parse : AppError()
    data object Unknown : AppError()
}