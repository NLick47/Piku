package com.piku.client.domain.model

/**
 * 作品的译文字段。与 [WorkDetail] 的原文字段一一对应，**原文永不被覆盖**：
 * 复制/分享/历史记录始终使用原文，UI 只在用户要求时展示这里的译文。
 * 任一字段为 null 表示该字段未翻译（原文为空、尚未完成或翻译失败）。
 */
data class TranslatedFields(
    val title: String? = null,
    val description: String? = null,
    val authorProfile: String? = null,
    val tags: List<String>? = null,
    val novelText: String? = null,
) {
    /** 是否有任何可展示的译文，全空时 UI 不显示切换入口 */
    val hasAny: Boolean
        get() = !title.isNullOrBlank() ||
            !description.isNullOrBlank() ||
            !authorProfile.isNullOrBlank() ||
            !tags.isNullOrEmpty() ||
            !novelText.isNullOrBlank()
}

/**
 * 用新一批译文替换旧译文，但补回新批次缺失而旧值仍有的字段（目前只有正文）。
 * 复现场景：阅读器刚拉到长篇正文 → 随后的自动重跑不带长文（见
 * TranslationRepository.AUTO_NOVEL_MAX_CHARS）→ 若整体覆盖，
 * 正文译文会从 UI 状态中凭空消失（缓存虽在，但用户必须再点一次）。
 * [new] 为 null 表示本次没有可用结果，调用方维持原状。
 */
internal fun mergeTranslatedFields(old: TranslatedFields?, new: TranslatedFields?): TranslatedFields? =
    when {
        new == null -> null
        old?.novelText != null && new.novelText == null -> new.copy(novelText = old.novelText)
        else -> new
    }
