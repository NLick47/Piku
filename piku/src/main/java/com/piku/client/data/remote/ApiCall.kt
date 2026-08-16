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
    Result.failure(AppError.Http(e.code()))
} catch (e: SerializationException) {
    Result.failure(AppError.Parse)
} catch (e: Exception) {
    Result.failure(AppError.Unknown)
}