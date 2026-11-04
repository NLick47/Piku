package com.piku.client.core.translation

data class ManualProviderPreset(
    val id: String,
    val name: String,
    val hint: String,
    val baseUrl: String = "",
    val baseUrlPlaceholder: String = "http://192.168.1.10:8000/v1",
    val authMode: AuthMode = AuthMode.BEARER,
    val defaultModel: String = "",
    val roles: List<String> = listOf(Role.TEXT, Role.NOVEL),
) {
    val needsKey: Boolean get() = authMode != AuthMode.NONE

    fun toDraft(): ManualProvider = ManualProvider(
        name = name,
        baseUrl = baseUrl,
        model = defaultModel,
        authMode = authMode,
        roles = roles,
    )

    companion object {
        const val CUSTOM_ID = "custom"

        val DEEPSEEK = ManualProviderPreset(
            id = "deepseek",
            name = "DeepSeek",
            hint = "deepseek-chat",
            baseUrl = "https://api.deepseek.com/v1",
            defaultModel = "deepseek-chat",
        )

        val SILICON_FLOW = ManualProviderPreset(
            id = "siliconflow",
            name = "硅基流动",
            hint = "DeepSeek / Qwen",
            baseUrl = "https://api.siliconflow.cn/v1",
            defaultModel = "",
        )

        val ZHIPU = ManualProviderPreset(
            id = "zhipu",
            name = "智谱 GLM",
            hint = "glm-4-flash",
            baseUrl = "https://open.bigmodel.cn/api/paas/v4",
            defaultModel = "glm-4-flash",
        )

        val KIMI = ManualProviderPreset(
            id = "kimi",
            name = "Kimi",
            hint = "moonshot-v1-8k",
            baseUrl = "https://api.moonshot.cn/v1",
            defaultModel = "moonshot-v1-8k",
        )

        val OPENAI = ManualProviderPreset(
            id = "openai",
            name = "OpenAI",
            hint = "gpt-4o-mini",
            baseUrl = "https://api.openai.com/v1",
            defaultModel = "gpt-4o-mini",
        )

        val OLLAMA = ManualProviderPreset(
            id = "ollama",
            name = "Ollama",
            hint = "qwen2.5:7b",
            baseUrl = "",
            baseUrlPlaceholder = "http://192.168.1.10:11434/v1",
            authMode = AuthMode.NONE,
            defaultModel = "",
        )

        val QUICK_ADD: List<ManualProviderPreset> = listOf(
            DEEPSEEK,
            SILICON_FLOW,
            ZHIPU,
            KIMI,
            OPENAI,
            OLLAMA,
        )

        fun find(id: String?): ManualProviderPreset? =
            QUICK_ADD.firstOrNull { it.id == id }
    }
}
