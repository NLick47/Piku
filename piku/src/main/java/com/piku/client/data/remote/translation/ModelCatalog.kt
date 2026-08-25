package com.piku.client.data.remote.translation

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * 一个可选的翻译模型条目。
 *
 * [baseUrl] 为 OpenAI 兼容服务地址（不含 /chat/completions），[model] 为模型名。
 * 选中某条目即把这两项写进设置，用户也可继续手改成列表里没有的模型。
 */
@Serializable
data class ModelEntry(
    /** 稳定标识，远程目录按它下发与更新条目 */
    val id: String,
    /** 展示名，如 "智谱 GLM-4-Flash（免费）" */
    val label: String,
    val baseUrl: String,
    val model: String,
    /** 是否有免费额度，仅用于展示提示 */
    val free: Boolean = false,
    /** 是否已实测可用；false 时 UI 标注"未实测" */
    val verified: Boolean = false,
    /** 远程列表可把已下架模型标记为不可用，UI 置灰但保留（用户可能还在用） */
    val available: Boolean = true,
    /** 内置共享 key（来自加密的远程目录）；用户自填的 key 优先于它 */
    val apiKey: String? = null,
    /** 模型特点提示，UI 芯片下展示，如"质量最佳""速度最快" */
    val hint: String = "",
    /** 请求级参数覆盖（如 enable_thinking / temperature），由远程目录下发，避免写死在代码里 */
    val params: Map<String, JsonElement>? = null,
    /** 该模型自带的翻译提示词（单条/批量 × 各语言）；缺省时继承目录全局默认 */
    val prompts: PromptSet? = null,
    /**
     * 用途归类（仅作默认分组提示，不限制选择器）：
     * "text" 普通文本翻译、"novel" 小说正文、"image" 预留给未来图片翻译；
     * null 视为 "text"。fork 仓库自行加模型无需填此字段也能用。
     */
    val kind: String? = null,
    /** 是否为文本翻译的默认模型（设置未选时高亮/生效）；同时至多一个 */
    val defaultText: Boolean = false,
    /** 是否为小说正文的默认模型（设置未选时高亮/生效）；同时至多一个 */
    val defaultNovel: Boolean = false,
)

/** 翻译提示词集：单条 / 批量，各目标语言（zh/en/ja）一套。远程下发实现热更新。 */
@Serializable
data class PromptSet(
    val single: Map<String, String> = emptyMap(),
    val batch: Map<String, String> = emptyMap(),
)

/** 目录全局默认：请求参数与提示词，供未自带覆盖的模型继承。 */
@Serializable
data class CatalogDefaults(
    val params: Map<String, JsonElement>? = null,
    val prompts: PromptSet? = null,
)

/** 远程模型列表 JSON 结构：{"version":3,"defaults":{...},"models":[...]} */
@Serializable
data class ModelCatalogDto(
    val version: Int = 0,
    val defaults: CatalogDefaults? = null,
    val models: List<ModelEntry> = emptyList(),
)

/**
 * 内置默认模型列表。
 *
 * 与 piku-models 远程目录保持同一集合：这里只是离线兜底，
 * 正常情况启动时由远程列表整体替换（key 只经远程密文分发，不进源码）。
 */
object ModelCatalog {

    /** 智谱 GLM：已实测可用的免费模型 */
    const val GLM_ID = "zhipu-glm-4-flash"

    val DEFAULTS: List<ModelEntry> = listOf(
        ModelEntry(
            id = "siliconflow-qwen3-8b",
            label = "Qwen3-8B (SiliconFlow)",
            baseUrl = "https://api.siliconflow.cn/v1",
            model = "Qwen/Qwen3-8B",
            free = true,
            verified = true,
            defaultText = true,
            kind = "text",
            hint = "默认 · 推荐",
            params = mapOf("enable_thinking" to JsonPrimitive(false)),
        ),
        ModelEntry(
            id = GLM_ID,
            label = "GLM-4 Flash",
            baseUrl = "https://open.bigmodel.cn/api/paas/v4",
            model = "glm-4-flash",
            free = true,
            verified = true,
            kind = "text",
            hint = "",
        ),
        // 付费赞助模型：地址/模型名内置作为离线兜底，apiKey 仅经远程加密目录分发（不进源码）。
        // 远程目录加载后整体替换此处并带上 key；defaultNovel 使小说正文默认走它。
        ModelEntry(
            id = "scnet-deepseek-novel",
            label = "DeepSeek 小说模型 (赞助)",
            baseUrl = "https://api.scnet.cn/api/llm/v1",
            model = "DeepSeek-V4-Flash-0731-Event",
            free = false,
            verified = true,
            defaultNovel = true,
            kind = "novel",
            hint = "赞助付费模型 · 小说正文默认",
            params = emptyMap(),
        ),
    )

    val default: ModelEntry get() = DEFAULTS.first()
}
