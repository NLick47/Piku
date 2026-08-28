package com.piku.client.ui.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piku.client.R
import com.piku.client.data.repository.AuthRepository
import com.piku.client.domain.model.AppError
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class ProfileEditUiState(
    /** 昵称保存中 */
    val savingName: Boolean = false,
    /** 头像上传中 */
    val uploadingAvatar: Boolean = false,
    /** 最近一次失败的错误文案资源（展示后由 consumeError 清除） */
    val errorRes: Int? = null,
    /** 保存成功事件（展示后由 consumeSaved 清除） */
    val saved: Boolean = false,
)

@HiltViewModel
class ProfileEditViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileEditUiState())
    val uiState: StateFlow<ProfileEditUiState> = _uiState.asStateFlow()

    fun updateNickName(name: String) {
        val trimmed = name.trim()
        // 与网页端一致：3~16 字符
        if (trimmed.length < 3 || trimmed.length > 16) {
            _uiState.update { it.copy(errorRes = R.string.profile_edit_name_length) }
            return
        }
        if (_uiState.value.savingName || _uiState.value.uploadingAvatar) return
        viewModelScope.launch {
            _uiState.update { it.copy(savingName = true, errorRes = null) }
            authRepository.updateNickName(trimmed)
                .onSuccess { _uiState.update { it.copy(savingName = false, saved = true) } }
                .onFailure { error ->
                    android.util.Log.d(
                        "PikuDiag",
                        "updateNickName fail error=${error::class.simpleName}: ${error.message}",
                        error,
                    )
                    _uiState.update {
                        it.copy(savingName = false, errorRes = error.toErrorRes())
                    }
                }
        }
    }

    fun updateAvatar(uri: Uri) {
        if (_uiState.value.savingName || _uiState.value.uploadingAvatar) return
        viewModelScope.launch {
            _uiState.update { it.copy(uploadingAvatar = true, errorRes = null) }
            val cacheFile = withContext(Dispatchers.IO) { copyToCache(uri) }
            if (cacheFile == null) {
                _uiState.update { it.copy(uploadingAvatar = false, errorRes = R.string.profile_edit_avatar_read_failed) }
                return@launch
            }
            val sizedFile = withContext(Dispatchers.IO) { ensureSize(cacheFile) }
            authRepository.updateAvatar(sizedFile)
                .onSuccess {
                    cacheFile.delete()
                    _uiState.update { it.copy(uploadingAvatar = false, saved = true) }
                }
                .onFailure { error ->
                    cacheFile.delete()
                    android.util.Log.d(
                        "PikuDiag",
                        "updateAvatar fail error=${error::class.simpleName}: ${error.message}",
                        error,
                    )
                    _uiState.update {
                        it.copy(uploadingAvatar = false, errorRes = error.toErrorRes())
                    }
                }
        }
    }

    fun consumeError() {
        _uiState.update { it.copy(errorRes = null) }
    }

    fun consumeSaved() {
        _uiState.update { it.copy(saved = false) }
    }

    private fun copyToCache(uri: Uri): File? = runCatching {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "image/jpeg"
        val ext = when {
            mime.contains("png") -> "png"
            mime.contains("gif") -> "gif"
            mime.contains("webp") -> "webp"
            else -> "jpg"
        }
        val out = File(context.cacheDir, "avatar_upload_${System.currentTimeMillis()}.$ext")
        resolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        out
    }.getOrNull()

    /**
     * 网页端限制：base64 长度 <= 1.0MiB * 1e6 * 1.3（约 130 万字符）。
     * 超限时自动缩小图片（最长边 1024 → 逐级减半，JPEG 压缩）直到满足，避免上传被拒。
     */
    private fun ensureSize(file: File): File {
        if (estimatedBase64Len(file.length()) <= MAX_BASE64_CHARS) return file
        val src = BitmapFactory.decodeFile(file.absolutePath) ?: return file
        var maxSide = 1024
        while (true) {
            val scaled = scaleDown(src, maxSide)
            val out = File(
                context.cacheDir,
                "avatar_upload_scaled_${System.currentTimeMillis()}.jpg",
            )
            out.outputStream().use { os -> scaled.compress(Bitmap.CompressFormat.JPEG, 85, os) }
            scaled.recycle()
            if (estimatedBase64Len(out.length()) <= MAX_BASE64_CHARS || maxSide <= 128) {
                src.recycle()
                return out
            }
            out.delete()
            maxSide /= 2
        }
    }

    private fun scaleDown(src: Bitmap, maxSide: Int): Bitmap {
        val w = src.width
        val h = src.height
        val longest = maxOf(w, h)
        if (longest <= maxSide) return src
        val scale = maxSide.toFloat() / longest
        return Bitmap.createScaledBitmap(src, (w * scale).toInt(), (h * scale).toInt(), true)
    }

    private fun estimatedBase64Len(bytes: Long): Long = bytes * 4 / 3 + 4

    private fun Throwable.toErrorRes(): Int = when (this) {
        is AppError.Network -> R.string.profile_edit_error_network
        is AuthRepository.UpdateRejected -> R.string.profile_edit_error_rejected
        is AuthRepository.AvatarTooLarge -> R.string.profile_edit_error_size
        else -> R.string.profile_edit_error_unknown
    }

    private companion object {
        /** 网页端 updateFile 的 base64 长度上限：1.0 * 1e6 * 1.3，留余量 */
        const val MAX_BASE64_CHARS = 1_250_000L
    }
}
