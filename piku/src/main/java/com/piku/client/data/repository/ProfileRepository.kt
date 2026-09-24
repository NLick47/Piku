package com.piku.client.data.repository

import android.util.Log
import com.piku.client.data.local.CredentialStore
import com.piku.client.data.remote.AuthApi
import com.piku.client.data.remote.MySettingPageParser
import com.piku.client.data.remote.UserPageParser
import com.piku.client.data.remote.apiCall
import com.piku.client.domain.model.AppError
import com.piku.client.domain.model.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 登录者自己的资料卡（头像 / 昵称 / 主页地址）。
 *
 * 刷新跟着 [AuthRepository.sessionVersion] 走：登录、重登成功、冷启动拿回会话都是
 * 一次会话变化，所以"谁负责刷资料"只有一个主人，别的层不用再自己触发
 */
@Singleton
class ProfileRepository @Inject constructor(
    private val authApi: AuthApi,
    private val credentialStore: CredentialStore,
    private val authRepository: AuthRepository,
    private val runtime: SessionRuntime,
) {

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + runtime.dispatcher)

    init {
        // 本地缓存先上屏，网络回来再覆盖
        _userProfile.value = credentialStore.loadProfile()
        scope.launch { if (authRepository.isLoggedIn()) refresh() }
        scope.reloadOnSessionChange(authRepository.sessionVersion) {
            if (authRepository.isLoggedIn()) refresh() else _userProfile.value = null
        }
    }

    /** 拉设置页与用户主页拼出资料卡；失败保留旧缓存 */
    suspend fun refresh() {
        val myUid = authRepository.currentUserId()
        if (!authRepository.isLoggedIn() || myUid == null) {
            Log.d(TAG, "refresh: not logged in, skip")
            _userProfile.value = null
            return
        }
        val version = authRepository.sessionVersion.value
        val profile = runCatching {
            val settingResponse = authApi.getMyEditSetting(myUid)
            val settingHtml = settingResponse.body()?.string() ?: ""
            Log.d(
                TAG,
                "getMyEditSetting: uid=$myUid code=${settingResponse.code()} len=${settingHtml.length}",
            )
            UserProfile(
                uid = myUid.toString(),
                avatarUrl = MySettingPageParser.parseAvatarUrl(settingHtml),
                // 设置页上的主页链接实测就是 https://poipiku.com/{uid}/（与兜底值一致），
                // 直接从 uid 拼，不再从页面里抠
                profileUrl = "https://poipiku.com/$myUid/",
                name = fetchDisplayName(myUid),
            )
        }.getOrNull()
        Log.d(TAG, "profile=$profile")
        // 请求期间会话或 uid 变了就不写回：防登出后在途响应写回，防换号后写错账号
        if (profile != null &&
            authRepository.isLoggedIn() &&
            authRepository.currentUserId() == myUid &&
            authRepository.sessionVersion.value == version
        ) {
            _userProfile.value = profile
            credentialStore.saveProfile(profile)
        }
    }

    /**
     * 修改昵称（网页端 MyEditSettingPcV 的 UpdateNickName 同款）。
     * 成功（result>0）后立即更新本地 profile，无需整页刷新。
     */
    suspend fun updateNickName(name: String): Result<Unit> {
        val myUid = authRepository.currentUserId()
        if (myUid == null) {
            Log.d(TAG, "updateNickName: uid null, skip")
            return Result.failure(AppError.Unknown)
        }
        val response = apiCall { authApi.updateNickName(myUid, name) }
            .getOrElse { return Result.failure(it) }
        Log.d(TAG, "updateNickName: result=${response.result}")
        return if (response.result > 0) {
            _userProfile.value = _userProfile.value?.copy(name = name)
            _userProfile.value?.let { credentialStore.saveProfile(it) }
            Result.success(Unit)
        } else {
            Result.failure(UpdateRejected(response.result))
        }
    }

    /**
     * 上传头像（网页端 updateFile("/f/UpdateProfileFileF.jsp", ...) 同款：
     * form 提交 UID + DATA(base64)，result==0 表示成功）。
     * 成功后头像 URL 会变化，重新解析设置页刷新 profile。
     */
    suspend fun updateAvatar(imageFile: java.io.File): Result<Unit> {
        val myUid = authRepository.currentUserId()
        if (myUid == null) {
            Log.d(TAG, "updateAvatar: uid null, skip")
            return Result.failure(AppError.Unknown)
        }
        val dataBase64 = runCatching {
            java.util.Base64.getEncoder().encodeToString(imageFile.readBytes())
        }.getOrElse {
            Log.d(TAG, "updateAvatar: read/encode failed", it)
            return Result.failure(AppError.Unknown)
        }
        if (dataBase64.length > MAX_AVATAR_BASE64_LEN) {
            Log.d(TAG, "updateAvatar: too large b64Len=${dataBase64.length}")
            return Result.failure(AvatarTooLarge)
        }
        val response = apiCall { authApi.updateProfileFile(myUid, dataBase64) }
            .getOrElse { return Result.failure(it) }
        Log.d(TAG, "updateAvatar: result=${response.result}")
        return if (response.result == 0) {
            refresh()
            Result.success(Unit)
        } else {
            Result.failure(UpdateRejected(response.result))
        }
    }

    /** 从公开用户主页取昵称，解析规则见 [UserPageParser.parseDisplayName]；失败保留旧缓存 */
    private suspend fun fetchDisplayName(uid: Long): String? = runCatching {
        val response = authApi.getUserTop(uid)
        val html = response.body()?.string()
        Log.d(TAG, "getUserTop: uid=$uid code=${response.code()} len=${html?.length ?: -1}")
        val name = html?.takeIf { it.isNotEmpty() }?.let(UserPageParser::parseDisplayName)
        Log.d(TAG, "fetchDisplayName: uid=$uid name=$name")
        name
    }.getOrNull()

    private companion object {
        const val TAG = "PikuDiag"

        /**
         * 网页端 updateFile 的客户端限制：base64 长度 <= limitMiByte(1.0) * 1e6 * 1.3。
         * 留一点余量防止服务端 -1。
         */
        const val MAX_AVATAR_BASE64_LEN = 1_250_000
    }

    /** 服务端返回 result<=0 时抛出，携带原始码供上层提示 */
    class UpdateRejected(val code: Int) : Exception("update rejected: $code")

    /** 头像超出网页端 1MB 限制 */
    data object AvatarTooLarge : Exception("avatar too large")
}
