package com.piku.client.data.remote

import com.piku.client.domain.model.AppError
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException

suspend fun <T> apiCall(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: IOException) {
    Result.failure(AppError.Network)
} catch (e: HttpException) {
    Result.failure(if (e.code() == 404) AppError.NotFound else AppError.Http(e.code()))
} catch (e: SerializationException) {
    Result.failure(AppError.Parse)
} catch (e: AppError) {
    // 解析器抛出的业务错误（NotFound / BlockedAuthor 等）必须原样透传，
    // 不能落到 Unknown 兜底——UI 靠具体类型区分"重试"与"终态"提示
    Result.failure(e)
} catch (e: Exception) {
    Result.failure(AppError.Unknown)
}