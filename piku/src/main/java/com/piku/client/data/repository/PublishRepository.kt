package com.piku.client.data.repository

import com.piku.client.data.remote.SessionMonitor
import com.piku.client.data.remote.UploadApi
import com.piku.client.domain.model.AppError
import com.piku.client.domain.model.PublishDraft
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Step1 建条目成功后的句柄，图片上传期间各步骤都要回传 */
data class UploadTarget(val contentId: Long, val openId: Long)

/** 发布链路中可让 UI 区分的失败类型 */
sealed class PublishFailure(message: String) : Exception(message) {
    data object NotLoggedIn : PublishFailure("not logged in")
    data object SessionExpired : PublishFailure("session expired")
    data object Rejected : PublishFailure("server rejected")
    data class Http(val code: Int) : PublishFailure("http $code")
}

/**
 * 发布编排。图片 = Step1 建条目 + 逐张串行上传（单张失败自动重试 3 次，
 * 仍失败抛给调用方决定"重试该张/放弃"）；小说 = 单 POST。放弃/取消由调用方
 * 调 [deleteEntry] 清掉半成品。响应体可能是 JSON 也可能是"会话失效回登录页"的
 * HTML，统一在此识别并上报 [SessionMonitor]。
 */
@Singleton
class PublishRepository @Inject constructor(
    private val api: UploadApi,
    private val json: Json,
    private val authRepository: AuthRepository,
    private val sessionMonitor: SessionMonitor,
) {

    /** 建条目（图片 Step1；小说后续不用它） */
    suspend fun createEntry(draft: PublishDraft): Result<UploadTarget> {
        val uid = requireUid() ?: return Result.failure(PublishFailure.NotLoggedIn)
        return request {
            api.createImageWork(UploadFormBuilder.entryForm(draft, uid))
        }.fold(
            onSuccess = { body ->
                val resp = decodeObject(body) ?: return Result.failure(PublishFailure.Rejected)
                if (resp.content_id > 0) {
                    Result.success(UploadTarget(resp.content_id, resp.open_id))
                } else {
                    Result.failure(PublishFailure.Rejected)
                }
            },
            onFailure = { Result.failure(it) },
        )
    }

    /** 上传第 [page] 张（0 起）；单张内部自动重试 [MAX_PAGE_RETRIES] 次 */
    suspend fun uploadPage(
        draft: PublishDraft,
        target: UploadTarget,
        page: Int,
        file: File,
        onProgress: ((written: Long, total: Long) -> Unit)? = null,
    ): Result<Unit> {
        val uid = requireUid() ?: return Result.failure(PublishFailure.NotLoggedIn)
        var lastError: Throwable = PublishFailure.Rejected
        repeat(MAX_PAGE_RETRIES) {
            val body = UploadFormBuilder.imageUploadBody(draft, uid, target.contentId, target.openId, file, onProgress)
            val result = request {
                if (page == 0) api.uploadFirstImage(body) else api.uploadAppendImage(body)
            }.fold(
                onSuccess = { body ->
                    val resp = decodeObject(body) ?: return Result.failure(PublishFailure.Rejected)
                    if (resp.success) Result.success(Unit)
                    else Result.failure(PublishFailure.Rejected)
                },
                onFailure = { Result.failure(it) },
            )
            if (result.isSuccess) return result
            lastError = result.exceptionOrNull() ?: PublishFailure.Rejected
            // 会话/拒绝类失败重试无意义，直接返回；网络/HTTP 瞬态失败才继续下一次
            if (lastError !is AppError.Network && lastError !is PublishFailure.Http) {
                return Result.failure(lastError)
            }
        }
        return Result.failure(lastError)
    }

    /** 小说单步发布，成功返回 content_id */
    suspend fun createNovel(draft: PublishDraft): Result<Long> {
        val uid = requireUid() ?: return Result.failure(PublishFailure.NotLoggedIn)
        return request {
            api.createNovelWork(UploadFormBuilder.entryForm(draft, uid))
        }.fold(
            onSuccess = { body ->
                val resp = decodeObject(body) ?: return Result.failure(PublishFailure.Rejected)
                if (resp.content_id > 0) Result.success(resp.content_id)
                else Result.failure(PublishFailure.Rejected)
            },
            onFailure = { Result.failure(it) },
        )
    }

    /** 删除已建条目（中途放弃清半成品）；body 为纯文本 true */
    suspend fun deleteEntry(contentId: Long): Result<Unit> {
        val uid = requireUid() ?: return Result.failure(PublishFailure.NotLoggedIn)
        return request {
            api.deleteContent(UploadFormBuilder.deleteForm(uid, contentId))
        }.fold(
            onSuccess = { raw ->
                if (raw.trim() == "true") Result.success(Unit)
                else Result.failure(PublishFailure.Rejected)
            },
            onFailure = { Result.failure(it) },
        )
    }

    /** 发布成功统一收尾由调用方负责（清本地草稿），仓库保持无状态 */

    // ---- 内部工具 ----

    private fun requireUid(): Long? = authRepository.currentUserId()

    private suspend inline fun request(
        crossinline block: suspend () -> Response<ResponseBody>,
    ): Result<String> = try {
        val resp = block()
        if (!resp.isSuccessful) {
            Result.failure(PublishFailure.Http(resp.code()))
        } else {
            val raw = resp.body()?.string().orEmpty()
            if (authRepository.isLoggedIn() && isLoginPage(raw)) {
                sessionMonitor.notifySessionCleared()
                Result.failure(PublishFailure.SessionExpired)
            } else {
                Result.success(raw)
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        Result.failure(AppError.Network)
    } catch (e: Exception) {
        Result.failure(AppError.Unknown)
    }

    private fun decodeObject(body: String): CreateWorkPayload? = try {
        json.decodeFromString(CreateWorkPayload.serializer(), body)
    } catch (e: Exception) {
        null
    }

    private fun isLoginPage(body: String): Boolean =
        LOGIN_MARKERS.any { body.contains(it) }

    private companion object {
        const val MAX_PAGE_RETRIES = 3
        val LOGIN_MARKERS = listOf("LoginUserF.jsp", "LoginFormEmailV.jsp", "LoginEmV.jsp")
    }
}

/** 上传响应的公共外形（content_id / open_id / success / append_id / reset），缺省即失败 */
@kotlinx.serialization.Serializable
internal data class CreateWorkPayload(
    val content_id: Long = 0,
    val open_id: Long = 0,
    val success: Boolean = false,
    val append_id: Long = 0,
    val reset: Boolean = false,
)
