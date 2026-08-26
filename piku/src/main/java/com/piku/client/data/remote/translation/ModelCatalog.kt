package com.piku.client.data.remote.translation

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * 翻译场景（role）。
 *
 * 目录里 [ModelEntry.roles] 声明模型服务哪些场景，[CatalogDefaults.roles]
 * 以 "场景 → 模型 id" 集中声明每个场景的默认模型。
 *
 * fork 仓库可自由定义新 role 名扩展场景：本版 app 不认识的 role 直接忽略，
 * 不报错、不影响已知场景，列表格式向前兼容。
 */
object Role {
    /** 普通短文本（标题/简介/作者简介/标签等） */
    const val TEXT = "text"

    /** 小说正文（长文独立通道：独立模型选择器与默认模型） */
    const val NOVEL = "novel"

    /** 图片翻译（预留）：当前版本尚无此场景，仅供目录提前布局 */
    const val IMAGE = "image"
}

/**
 * 一个可选的翻译模型条目。
 *
 * [baseUrl] 为 OpenAI 兼容服务地址（不含 /chat/completions），[model] 为模型名。
 * 选中某条目即把这两项写进设置，用户也可继续手改成列表里没有的模型。
 */
@Serializable
data class ModelEntry(
    /** 稳定标识，远程目录按它下发与更新条目；[CatalogDefaults.roles] 也用它指认默认 */
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
     * 服务场景列表（[Role] 名）；缺省视为 ["text"]。
     * 模型只声明"能干什么"，某场景用谁作默认由 [CatalogDefaults.roles] 集中声明，
     * fork 换默认模型只需改目录开头一处，不必翻找模型条目。
     */
    val roles: List<String> = listOf(Role.TEXT),
)

/** 翻译提示词集：单条 / 批量 / 小说分块，各目标语言（zh/en/ja）一套。远程下发实现热更新。 */
@Serializable
data class PromptSet(
    val single: Map<String, String> = emptyMap(),
    val batch: Map<String, String> = emptyMap(),
    /** 小说正文分块翻译（含上下文标记规则）；缺省回退内置 [TranslationPrompts.novelSystemPrompt] */
    val novel: Map<String, String> = emptyMap(),
)

/** 目录全局默认：请求参数、提示词与各场景默认模型，供未自带覆盖的模型继承。 */
@Serializable
data class CatalogDefaults(
    val params: Map<String, JsonElement>? = null,
    val prompts: PromptSet? = null,
    /** 场景 → 默认模型 id，如 {"text": "…", "novel": "scnet-deepseek-novel"}；指向不存在的 id 时按未声明处理 */
    val roles: Map<String, String> = emptyMap(),
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
 * 正常情况启动时由远程列表整体替换（key 与 defaults.roles 只经远程密文分发，不进源码）。
 * 注意内置条目不带 apiKey，离线时小说通道解析不出可用模型——按宁缺毋滥语义正文保留原文，
 * 远程目录到达后自动恢复。
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
            roles = listOf(Role.TEXT),
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
            roles = listOf(Role.TEXT),
            hint = "",
        ),
        // 付费赞助模型：地址/模型名内置作为离线兜底，apiKey 仅经远程加密目录分发（不进源码）；
        // 远程目录经 defaults.roles 把 novel 场景默认指向它。
        ModelEntry(
            id = "scnet-deepseek-novel",
            label = "DeepSeek 小说模型 (赞助)",
            baseUrl = "https://api.scnet.cn/api/llm/v1",
            model = "DeepSeek-V4-Flash-0731-Event",
            free = false,
            verified = true,
            roles = listOf(Role.NOVEL),
            hint = "赞助付费模型 · 小说正文默认",
            params = emptyMap(),
        ),
    )

    val default: ModelEntry get() = DEFAULTS.first()

    /**
     * 场景默认模型的纯解析函数（翻译仓库选路与选择器高亮共用同一语义）：
     * 1) 目录 [roleDefaults] 指向且可用、自带内置 key 的条目优先；
     * 2) 否则取首个该 [role] 且可用、带 key 的条目；
     * 3) 都没有返回 null——小说通道据此宁缺毋滥（正文保留原文），绝不向其他场景借模型。
     */
    fun resolveRoleDefault(
        role: String,
        models: List<ModelEntry>,
        roleDefaults: Map<String, String>,
    ): ModelEntry? {
        val preferred = roleDefaults[role]
        return models.firstOrNull {
            preferred != null && it.id == preferred && it.available && !it.apiKey.isNullOrBlank()
        } ?: models.firstOrNull { role in it.roles && it.available && !it.apiKey.isNullOrBlank() }
    }

    /**
     * 故障转移候选：同场景（[role]）、免费、可用、带内置共享 key 的其他模型里随机挑一个。
     * 按 role 过滤保证小说正文的备选仍是小说类模型，不会被甩给纯文本免费模型。
     */
    fun failoverCandidate(
        current: ModelEntry?,
        role: String,
        models: List<ModelEntry>,
    ): ModelEntry? = models.filter {
        role in it.roles && it.free && it.available && !it.apiKey.isNullOrBlank() && it.id != current?.id
    }.randomOrNull()

    /**
     * 解析存储的历史选中值（设置里的 id 或裸模型名）。
     *
     * 目录权威高于历史选择：命中条目必须可用且带内置 key，否则视为未选择返回 null，
     * 由调用方回落到场景默认——下架/撤 key 的模型不会因旧选中值而继续生效。
     * 惰性失效不写回设置：目录哪天恢复该模型，选中自动复活。
     */
    fun resolveStoredSelection(
        stored: String,
        models: List<ModelEntry>,
    ): ModelEntry? {
        val value = stored.trim()
        if (value.isEmpty()) return null
        return models.firstOrNull {
            (it.id == value || it.model == value) &&
                it.available && !it.apiKey.isNullOrBlank()
        }
    }
}
