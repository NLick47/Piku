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
     * [catalogEntry] 非空（选中项命中远程目录）时地址与模型名以目录为准：
     * 远程改了 baseUrl/换名/下架换新，无需用户重新选择即对所有人生效；
     * 列表外的自定义模型传 null，完全跟随设置里手填的值。
     */
    fun create(
        apiKey: String,
        catalogEntry: ModelEntry? = null,
        defaults: CatalogDefaults? = null,
    ): TranslationEngine? {
        if (apiKey.isBlank()) return null
        // 基础参数 + 目录全局默认 + 单模型覆盖，后者优先；参数名全部来自目录，不写死
        val params = buildJsonObject {
            put("temperature", JsonPrimitive(0.2))
            put("stream", JsonPrimitive(false))
            defaults?.params?.forEach { (k, v) -> put(k, v) }
            catalogEntry?.params?.forEach { (k, v) -> put(k, v) }
        }
        val prompts = catalogEntry?.prompts
        return LlmTranslateEngine(api) {
            LlmTranslateEngine.LlmConfig(
                baseUrl = catalogEntry?.baseUrl ?: settingsRepository.llmBaseUrl.value,
                apiKey = apiKey,
                model = catalogEntry?.model ?: settingsRepository.llmModel.value,
                params = params,
                prompts = prompts,
                defaultPrompts = defaults?.prompts,
            )
        }
    }
}
