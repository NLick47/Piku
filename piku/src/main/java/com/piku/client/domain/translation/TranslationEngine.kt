package com.piku.client.domain.translation

/**
 * 翻译引擎抽象。当前只有 LLM 实现（[com.piku.client.data.remote.translation.LlmTranslateEngine]），
 * 抽象出来是为了后续能加传统机器翻译（百度/腾讯免费额度）作兜底而不改调用方。
 */
interface TranslationEngine {

    /**
     * 引擎标识，用于译文缓存键（模型不同则缓存不同），形如 "llm:glm-4-flash"。
     */
    val engineId: String

    /**
     * 批量翻译。返回列表长度必须与 [texts] 一致且顺序对应；
     * 某条翻译不出来时返回空字符串，由调用方回退原文。
     *
     * @param targetLang 目标语言显示名（如 "简体中文"）
     */
    suspend fun translate(texts: List<String>, targetLang: String): List<String>
}
