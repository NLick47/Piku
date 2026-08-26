package com.piku.client.domain.translation

/**
 * 跨块上下文（小说分块流式翻译专用）：
 * 上一块的结尾原文与译文，仅供模型理解剧情衔接与指代，不得翻译或复读。
 * 注入格式与回声剥离都由引擎实现负责——调用方只提供素材。
 *
 * [glossaryBlock] 为作品标签派生的译名对照表（可空），由引擎拼进系统提示词尾部。
 */
data class ChunkContext(
    val originalTail: String,
    val translatedTail: String,
    val glossaryBlock: String? = null,
)

/**
 * 翻译引擎抽象。当前只有 LLM 实现（[com.piku.client.data.remote.translation.LlmTranslateEngine]），
 * 抽象出来是为了后续能加传统机器翻译（百度/腾讯免费额度）作兜底而不改调用方。
 */
interface TranslationEngine {

    /**
     * 引擎标识，用于译文缓存键。含场景 + 模型 + 地址三元组
     * （形如 "llm:novel:glm-4-flash:https://…"），任一不同则缓存互不污染。
     */
    val engineId: String

    /**
     * 批量翻译。返回列表长度必须与 [texts] 一致且顺序对应；
     * 某条翻译不出来时返回空字符串，由调用方回退原文。
     *
     * @param targetLang 目标语言显示名（如 "简体中文"）
     * @param context 小说分块场景的跨块上下文；null 表示普通单次翻译
     * （短字段路径完全不感知此参数）。
     */
    suspend fun translate(
        texts: List<String>,
        targetLang: String,
        context: ChunkContext? = null,
    ): List<String>
}
