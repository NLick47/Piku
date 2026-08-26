package com.piku.client.data.remote.translation

import com.piku.client.data.local.SettingsRepository
import com.piku.client.domain.translation.TranslationEngine
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 按当前设置构造翻译引擎。
 *
 * 每次调用都读一遍设置（而不是缓存实例）：用户在抽屉里改了模型/地址后，
 * 下一次翻译立即生效，不需要重启应用。
 */
@Singleton
class TranslationEngineFactory @Inject constructor(
    private val api: LlmChatApi,
    private val settingsRepository: SettingsRepository,
) {

    /**
     * key 为空返回 null，调用方跳过翻译（避免发出注定 401 的请求）。
     *
     * [role] 是该引擎服务的场景（[Role.TEXT]/[Role.NOVEL]）：它决定单条路径的
     * 提示词组并进缓存键，文本与小说两条通道由此彻底隔离。
     *
     * [catalogEntry] 非空（选中项命中远程目录）时地址与模型名以目录为准：
     * 远程改了 baseUrl/换名/下架换新，无需用户重新选择即对所有人生效；
     * 列表外的自定义模型传 null，完全跟随设置里手填的值。
     */
    fun create(
        apiKey: String,
        role: String = Role.TEXT,
        catalogEntry: ModelEntry? = null,
        defaults: CatalogDefaults? = null,
    ): TranslationEngine? {
        if (apiKey.isBlank()) return null
        return LlmTranslateEngine(api) {
            buildConfig(
                apiKey = apiKey,
                role = role,
                catalogEntry = catalogEntry,
                defaults = defaults,
                fallbackBaseUrl = settingsRepository.llmBaseUrl.value,
                fallbackModel = settingsRepository.llmModel.value,
            )
        }
    }

    companion object {

        /**
         * 引擎配置合成（纯函数，便于用真实目录 JSON 单测）：
         * 基础参数 + 目录全局默认 + 单模型覆盖（后者优先）；参数名全部来自目录，不写死；
         * 提示词挂模型级 [ModelEntry.prompts] 与目录级 [CatalogDefaults.prompts]，
         * 由引擎内部按 (场景, 路径, 语言) 逐级回退。
         */
        internal fun buildConfig(
            apiKey: String,
            role: String,
            catalogEntry: ModelEntry?,
            defaults: CatalogDefaults?,
            fallbackBaseUrl: String,
            fallbackModel: String,
        ): LlmTranslateEngine.LlmConfig {
            val params = buildJsonObject {
                put("temperature", JsonPrimitive(0.2))
                put("stream", JsonPrimitive(false))
                defaults?.params?.forEach { (k, v) -> put(k, v) }
                catalogEntry?.params?.forEach { (k, v) -> put(k, v) }
            }
            return LlmTranslateEngine.LlmConfig(
                baseUrl = catalogEntry?.baseUrl ?: fallbackBaseUrl,
                apiKey = apiKey,
                model = catalogEntry?.model ?: fallbackModel,
                role = role,
                params = params,
                prompts = catalogEntry?.prompts,
                defaultPrompts = defaults?.prompts,
            )
        }
    }
}
