package com.piku.client.ui.publish

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piku.client.R
import com.piku.client.data.local.CustomTagRepository
import com.piku.client.data.local.DraftRepository
import com.piku.client.data.repository.PublishFailure
import com.piku.client.data.repository.PublishRepository
import com.piku.client.data.repository.UploadTarget
import com.piku.client.domain.model.AppError
import com.piku.client.domain.model.NsfwLevel
import com.piku.client.domain.model.PublishDraft
import com.piku.client.domain.model.ShowVisibility
import com.piku.client.domain.model.UploadKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** 免费账号本地硬上限：200 张 / 50MB（POIPASS 更高，未接入判定前按免费档拦） */
const val FREE_IMAGE_LIMIT = 200
const val FREE_BYTES_LIMIT = 50L * 1024 * 1024

sealed interface PublishPhase {
    data object Idle : PublishPhase
    data object Creating : PublishPhase
    data class Uploading(
        val index: Int,
        val total: Int,
        val sentBytes: Long,
        val fileBytes: Long,
        val fileName: String,
    ) : PublishPhase

    data class PageFailed(
        val index: Int,
        val total: Int,
        val fileName: String,
    ) : PublishPhase
}

data class PublishUiState(
    val kind: UploadKind = UploadKind.ILLUST,
    val categoryCd: Int? = null,
    val tagsText: String = "",
    val description: String = "",
    val title: String = "",
    val body: String = "",
    val novelDirection: Int = 0,
    val publish: Boolean = true,
    val nsfw: NsfwLevel = NsfwLevel.ALL,
    val visibility: ShowVisibility = ShowVisibility.ANYONE,
    /** 浏览密码是否开启；开=显示输入框并要求非空，关=清空 */
    val passwordEnabled: Boolean = false,
    val password: String = "",
    val showRecent: Boolean = true,
    val showFirstOnly: Boolean = false,
    val images: List<String> = emptyList(),
    val totalBytes: Long = 0,
    /** 相对"进入页面/恢复草稿"是否有改动——决定离开是否弹保存确认 */
    val dirty: Boolean = false,
    val phase: PublishPhase = PublishPhase.Idle,
    /** 一次性提示（错误/草稿已存），UI 展示后调 [consumeNotice] */
    val noticeRes: Int? = null,
) {
    val isBusy: Boolean get() = phase != PublishPhase.Idle
    val hasContent: Boolean
        get() = images.isNotEmpty() || categoryCd != null || tagsText.isNotBlank() ||
            description.isNotBlank() || title.isNotBlank() || body.isNotBlank() ||
            !publish || nsfw != NsfwLevel.ALL || visibility != ShowVisibility.ANYONE ||
            passwordEnabled
}

@HiltViewModel
class PublishViewModel @Inject constructor(
    private val publishRepository: PublishRepository,
    private val draftRepository: DraftRepository,
    val customTagRepository: CustomTagRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PublishUiState())
    val uiState: StateFlow<PublishUiState> = _uiState.asStateFlow()

    private val _published = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val published: SharedFlow<Long> = _published.asSharedFlow()

    /** 草稿箱数据直接内嵌发布页：进入/删除后刷新 */
    private val _drafts = MutableStateFlow<List<PublishDraft>>(emptyList())
    val drafts: StateFlow<List<PublishDraft>> = _drafts.asStateFlow()

    val myTags: StateFlow<List<String>> get() = customTagRepository.customTags

    /** 正在编辑的草稿行 id；null = 全新创作（首次存草稿时才分配） */
    private var currentDraftId: Long? = null

    private var resume: Resume? = null

    init {
        refreshDrafts()
    }

    fun refreshDrafts() = viewModelScope.launch {
        _drafts.value = draftRepository.drafts()
    }

    // ---- 表单编辑（全部标记 dirty）----

    fun setKind(kind: UploadKind) = edit { it.copy(kind = kind) }
    fun setCategory(cd: Int?) = edit { it.copy(categoryCd = cd) }
    fun setTags(text: String) = edit { it.copy(tagsText = text.take(100)) }
    fun setDescription(text: String) = edit { it.copy(description = text.take(200)) }
    fun setTitle(text: String) = edit { it.copy(title = text.take(200)) }
    fun setBody(text: String) = edit { it.copy(body = text) }
    fun setNovelDirection(vertical: Boolean) = edit {
        it.copy(novelDirection = if (vertical) 1 else 0)
    }
    fun setPublish(v: Boolean) = edit {
        if (v) it.copy(publish = true)
        // 私密作品不需要浏览密码
        else it.copy(publish = false, passwordEnabled = false, password = "")
    }
    fun setNsfw(level: NsfwLevel) = edit { it.copy(nsfw = level) }
    fun setVisibility(v: ShowVisibility) = edit { it.copy(visibility = v) }
    fun setPasswordEnabled(v: Boolean) = edit {
        if (v) it.copy(passwordEnabled = true)
        else it.copy(passwordEnabled = false, password = "")
    }
    fun setPassword(text: String) = edit { it.copy(password = text.take(16)) }
    fun setShowRecent(v: Boolean) = edit { it.copy(showRecent = v) }
    fun setShowFirstOnly(v: Boolean) = edit { it.copy(showFirstOnly = v) }

    private inline fun edit(transform: (PublishUiState) -> PublishUiState) =
        _uiState.update { transform(it).let { n -> if (n == it) n else n.copy(dirty = true) } }

    fun appendTag(tag: String) {
        val t = tag.trim().removePrefix("#").trim()
        if (t.isEmpty()) return
        val state = _uiState.value
        val existing = state.tagsText.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (t in existing) return
        val next = (existing + t).joinToString(" ")
        if (next.length <= 100) setTags(next) else setNotice(R.string.publish_error_tags_too_long)
    }

    fun removeTagWord(tag: String) = edit { state ->
        state.copy(
            tagsText = state.tagsText.split(Regex("\\s+"))
                .filter { it.isNotBlank() && it != tag }
                .joinToString(" "),
        )
    }

    // ---- 图集 ----

    fun addImages(uris: List<Uri>) = viewModelScope.launch {
        if (_uiState.value.kind != UploadKind.ILLUST) return@launch
        val id = ensureDraftId()
        withContext(Dispatchers.IO) {
            var added = 0
            for (uri in uris) {
                if (_uiState.value.images.size + added >= FREE_IMAGE_LIMIT) {
                    setNotice(R.string.publish_images_limit)
                    break
                }
                if (draftRepository.imageType(uri)?.startsWith("image/") != true) {
                    setNotice(R.string.publish_error_unsupported_image)
                    continue
                }
                val file = draftRepository.copyImage(id, uri) ?: continue
                _uiState.update {
                    it.copy(
                        images = it.images + file.absolutePath,
                        totalBytes = it.totalBytes + file.length(),
                        dirty = true,
                    )
                }
                added++
            }
            if (_uiState.value.totalBytes > FREE_BYTES_LIMIT) {
                setNotice(R.string.publish_images_limit)
            }
        }
    }

    /** 首次加图时分配草稿 id（后续存草稿就落同一行/目录） */
    private fun ensureDraftId(): Long {
        currentDraftId?.let { return it }
        val id = System.currentTimeMillis()
        currentDraftId = id
        return id
    }

    fun removeImage(index: Int) {
        val file = _uiState.value.images.getOrNull(index) ?: return
        draftRepository.deleteImage(File(file))
        _uiState.update {
            it.copy(
                images = it.images.filterIndexed { i, _ -> i != index },
                totalBytes = (it.totalBytes - File(file).length()).coerceAtLeast(0),
                dirty = true,
            )
        }
    }

    fun moveImage(index: Int, delta: Int) {
        _uiState.update { state ->
            val list = state.images.toMutableList()
            val target = index + delta
            if (index !in list.indices || target !in list.indices) return@update state
            val tmp = list[index]
            list[index] = list[target]
            list[target] = tmp
            state.copy(images = list, dirty = true)
        }
    }

    // ---- 草稿箱 ----

    /** 从草稿箱恢复一份草稿继续编辑 */
    fun loadDraft(id: Long) = viewModelScope.launch {
        val draft = draftRepository.load(id) ?: return@launch
        currentDraftId = id
        _uiState.value = PublishUiState(
            kind = draft.kind,
            categoryCd = draft.categoryCd.takeIf { it != 0 },
            tagsText = draft.tags,
            description = draft.description,
            title = draft.title,
            body = draft.body,
            novelDirection = draft.novelDirection,
            publish = draft.publish,
            nsfw = draft.nsfw,
            visibility = draft.visibility,
            passwordEnabled = draft.password.isNotEmpty(),
            password = draft.password,
            showRecent = draft.showRecent,
            showFirstOnly = draft.showFirstOnly,
            images = draft.imageFiles,
            totalBytes = draft.imageFiles.sumOf { File(it).length() },
        )
    }

    /** 删除草稿箱里的一份（行 + 图片目录），随后刷新内嵌列表 */
    fun deleteDraft(id: Long) = viewModelScope.launch {
        draftRepository.delete(id)
        refreshDrafts()
    }

    /** 离开三选之「保存草稿」：写入草稿箱（新稿分配行 id，继续编辑则覆盖原行）。
     *  密码不落盘（敏感），恢复后需重新设置。 */
    fun saveDraftToBoxAndExit(onExited: () -> Unit) = viewModelScope.launch {
        val id = currentDraftId ?: System.currentTimeMillis()
        draftRepository.save(buildDraft().copy(draftId = id, password = ""))
        resetToFresh()
        refreshDrafts()
        onExited()
    }

    /** 离开三选之「丢弃」：删行（若来自草稿箱）并清掉本会话拷贝的图片 */
    fun discardDraftAndExit(onExited: () -> Unit) = viewModelScope.launch {
        val id = currentDraftId
        if (id != null) {
            if (draftRepository.load(id) != null) draftRepository.delete(id)
            else draftRepository.deleteImagesOf(id)
        }
        resetToFresh()
        refreshDrafts()
        onExited()
    }

    /** 离开/发布成功后回到全新编辑态（VM 随 Activity 存活，必须显式复位） */
    private fun resetToFresh() {
        currentDraftId = null
        resume = null
        _uiState.value = PublishUiState()
    }

    fun consumeNotice() = _uiState.update { it.copy(noticeRes = null) }

    // ---- 发布 ----

    fun publish() {
        val error = validate()
        if (error != null) {
            setNotice(error)
            return
        }
        if (_uiState.value.isBusy) return
        viewModelScope.launch {
            val state = _uiState.value
            val draft = buildDraft(state)
            when (state.kind) {
                UploadKind.NOVEL -> publishNovel(draft)
                UploadKind.ILLUST -> publishIllust(draft)
            }
        }
    }

    /** 失败页后重试：从该页续传（repo 内部单张自动重试 3 次） */
    fun retryPublish() {
        val r = resume ?: return
        viewModelScope.launch { uploadRemaining(r.draft, r.target, r.files, r.fromIndex) }
    }

    /** 失败页后放弃：删服务端半成品条目，回编辑态（本地素材不受影响） */
    fun abortPublish() = viewModelScope.launch {
        val r = resume
        resume = null
        _uiState.update { it.copy(phase = PublishPhase.Idle) }
        if (r != null) publishRepository.deleteEntry(r.target.contentId)
    }

    private suspend fun publishNovel(draft: PublishDraft) {
        _uiState.update { it.copy(phase = PublishPhase.Creating) }
        publishRepository.createNovel(draft)
            .onSuccess { workId ->
                clearAfterPublished()
                _published.tryEmit(workId)
            }
            .onFailure { fail -> handlePublishFailure(fail) }
    }

    private suspend fun publishIllust(draft: PublishDraft) {
        _uiState.update { it.copy(phase = PublishPhase.Creating) }
        val target = publishRepository.createEntry(draft)
            .getOrElse {
                handlePublishFailure(it)
                return
            }
        uploadRemaining(draft, target, _uiState.value.images, 0)
    }

    private suspend fun uploadRemaining(
        draft: PublishDraft,
        target: UploadTarget,
        files: List<String>,
        fromIndex: Int,
    ) {
        val total = files.size
        for (i in fromIndex until total) {
            val file = File(files[i])
            _uiState.update {
                it.copy(
                    phase = PublishPhase.Uploading(
                        index = i, total = total, sentBytes = 0,
                        fileBytes = file.length(), fileName = file.name,
                    ),
                )
            }
            // 进度节流：整页状态每 ~80 分之一或 128KB 才更新一次，避免上传中整页高频重组
            val fileLen = file.length()
            val step = maxOf(fileLen / 80, 128 * 1024L)
            var lastReported = 0L
            val result = publishRepository.uploadPage(
                draft = draft, target = target, page = i, file = file,
            ) { written, fileBytes ->
                if (written - lastReported >= step || written >= fileBytes) {
                    lastReported = written
                    _uiState.update { state ->
                        val phase = state.phase as? PublishPhase.Uploading ?: return@update state
                        state.copy(phase = phase.copy(sentBytes = written, fileBytes = fileBytes))
                    }
                }
            }
            if (result.isFailure) {
                val e = result.exceptionOrNull() ?: PublishFailure.Rejected
                if (e is PublishFailure.SessionExpired || e is PublishFailure.Rejected) {
                    // 条目已建但无法继续：先清服务端半成品，避免残留
                    publishRepository.deleteEntry(target.contentId)
                    resume = null
                    handlePublishFailure(e)
                    return
                }
                // 网络/HTTP 瞬态：可续传，进失败页由用户选重试或放弃（覆盖层表达，不再重复弹提示）
                resume = Resume(draft, target, files, i)
                _uiState.update { it.copy(phase = PublishPhase.PageFailed(i, total, file.name)) }
                return
            }
        }
        resume = null
        clearAfterPublished()
        _published.tryEmit(target.contentId)
    }

    /** 发布成功：删除对应草稿行与其图片目录，回到全新状态 */
    private suspend fun clearAfterPublished() {
        val id = currentDraftId
        if (id != null) {
            if (draftRepository.load(id) != null) draftRepository.delete(id)
            else draftRepository.deleteImagesOf(id)
        }
        resetToFresh()
    }

    private fun handlePublishFailure(error: Throwable) {
        resume = null
        _uiState.update { it.copy(phase = PublishPhase.Idle) }
        val res = when (error) {
            is PublishFailure.NotLoggedIn -> R.string.publish_error_not_logged_in
            is PublishFailure.SessionExpired -> R.string.publish_error_session
            is AppError.Network, is PublishFailure.Http -> R.string.publish_error_network
            is PublishFailure.Rejected -> R.string.publish_error_rejected
            else -> R.string.publish_error_unknown
        }
        setNotice(res)
    }

    private fun validate(): Int? {
        val s = _uiState.value
        if (s.categoryCd == null) return R.string.publish_error_require_category
        if (s.kind == UploadKind.ILLUST && s.images.isEmpty()) {
            return R.string.publish_error_require_image
        }
        if (s.kind == UploadKind.NOVEL && s.title.isBlank()) {
            return R.string.publish_error_require_title
        }
        if (s.tagsText.length > 100) return R.string.publish_error_tags_too_long
        if (s.description.length > 200) return R.string.publish_error_description_too_long
        if (s.publish && s.passwordEnabled && s.password.isBlank()) {
            return R.string.publish_error_password_empty
        }
        if (s.password.length > 16) return R.string.publish_error_password_too_long
        if (s.images.size > FREE_IMAGE_LIMIT || s.totalBytes > FREE_BYTES_LIMIT) {
            return R.string.publish_images_limit
        }
        return null
    }

    private fun buildDraft(s: PublishUiState = _uiState.value): PublishDraft = PublishDraft(
        draftId = currentDraftId,
        kind = s.kind,
        categoryCd = s.categoryCd ?: 0,
        tags = s.tagsText.trim(),
        description = s.description.trim(),
        publish = s.publish,
        nsfw = s.nsfw,
        visibility = s.visibility,
        password = if (s.publish && s.passwordEnabled) s.password.trim() else "",
        showRecent = s.showRecent,
        showFirstOnly = s.showFirstOnly,
        title = s.title.trim(),
        body = s.body,
        novelDirection = s.novelDirection,
        imageFiles = s.images,
    )

    private fun setNotice(res: Int) {
        _uiState.update { it.copy(noticeRes = res) }
    }

    private data class Resume(
        val draft: PublishDraft,
        val target: UploadTarget,
        val files: List<String>,
        val fromIndex: Int,
    )
}
