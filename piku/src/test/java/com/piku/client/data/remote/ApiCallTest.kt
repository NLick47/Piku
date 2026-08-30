package com.piku.client.data.remote

import com.piku.client.domain.model.AppError
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class ApiCallTest {

    private fun httpError(code: Int): HttpException =
        HttpException(Response.error<String>(code, "".toResponseBody(null)))

    @Test
    fun maps404ToNotFound() = runTest {
        // 404 = 作品被删除/作者注销/链接有误，UI 据此给出准确文案并去掉重试按钮
        val result = apiCall<Unit> { throw httpError(404) }
        assertTrue("expected NotFound, got ${result.exceptionOrNull()}", result.exceptionOrNull() is AppError.NotFound)
    }

    @Test
    fun keepsOtherHttpCodesAsHttp() = runTest {
        val result = apiCall<Unit> { throw httpError(500) }
        val error = result.exceptionOrNull()
        assertTrue("expected Http(500), got $error", error is AppError.Http && error.code == 500)
    }
}
