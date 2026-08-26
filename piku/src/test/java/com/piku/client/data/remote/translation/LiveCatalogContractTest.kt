package com.piku.client.data.remote.translation

import com.piku.client.data.local.SettingsRepository
import kotlinx.serialization.json.Json
import org.junit.Ignore
import org.junit.Test
import java.io.File

/**
 * 真实远程目录的现场排查工具（联网，默认 [Ignore]，不落盘任何文件）。
 *
 * 用法：临时去掉 @Ignore 跑一次——拉取 [SettingsRepository.CATALOG_URL_DEFAULT]
 * 的 AES-256-GCM 密文，用 CryptoHelper 在内存解密后直接断言"正文/小文本提示词
 * 不窜"契约（场景默认隔离、已下发槽位的组间隔离与结构标记、batch 示例），
 * 并打印摘要。密钥从环境变量 PIKU_CATALOG_ENC_KEY 或 local.properties 的
 * piku.catalog.encKey 读取，绝不打印。
 *
 * 仓库内不保存任何目录快照；常规 CI 只跑离线测试。
 */
@Ignore("手动启用：需要排查线上目录时跑一次，完成后恢复 @Ignore")
class LiveCatalogContractTest {

    private val contextMarkers = listOf("⟦上文原句⟧", "⟦上文译文⟧", "⟦待翻正文⟧")

    private val languages = listOf("zh", "en", "ja")

    private fun PromptSet.group(name: String): Map<String, String> = when (name) {
        "batch" -> batch
        "novel" -> novel
        else -> single
    }

    @Test
    fun verifyLiveCatalog() {
        val envelopeBody = java.net.URI.create(SettingsRepository.CATALOG_URL_DEFAULT).toURL().readText()
        val envelope = Json { ignoreUnknownKeys = true }
            .decodeFromString(CryptoHelper.Envelope.serializer(), envelopeBody)
        val plain = CryptoHelper.decrypt(envelope, resolveKey())
        val catalog = Json { ignoreUnknownKeys = true }.decodeFromString<ModelCatalogDto>(plain)

        println("version=${catalog.version} models=${catalog.models.size} " +
            "keyed=${catalog.models.count { !it.apiKey.isNullOrBlank() }}")

        // 场景默认：声明即必须可解析且 text/novel 不同条目（两通道不共享模型与缓存）
        catalog.defaults?.roles?.let { roles ->
            val textId = roles[Role.TEXT]
            val novelId = roles[Role.NOVEL]
            if (textId != null && novelId != null) {
                check(textId != novelId) { "text 与 novel 默认同条目：$textId" }
                check(textId == ModelCatalog.resolveRoleDefault(Role.TEXT, catalog.models, roles)?.id) {
                    "text 默认 $textId 解析失败"
                }
                check(novelId == ModelCatalog.resolveRoleDefault(Role.NOVEL, catalog.models, roles)?.id) {
                    "novel 默认 $novelId 解析失败"
                }
            }
            println("roles: text=$textId novel=$novelId")
        }

        // 已下发的每个槽位：非空 + 组间隔离 + 结构标记只在 novel + [[1]] 只在 batch
        buildList {
            catalog.defaults?.prompts?.let { add("defaults" to it) }
            catalog.models.forEach { m -> m.prompts?.let { add(m.id to it) } }
        }.forEach { (name, ps) ->
            languages.forEach { lang ->
                val slots = mapOf(
                    "single" to ps.single[lang],
                    "batch" to ps.batch[lang],
                    "novel" to ps.novel[lang],
                ).filterValues { !it.isNullOrBlank() }
                slots.forEach { (group, prompt) ->
                    val text = prompt!!
                    slots.forEach { (other, otherText) ->
                        if (group < other) check(text != otherText) { "$name.$lang 的 $group 与 $other 相同" }
                    }
                    contextMarkers.forEach { marker ->
                        if (group == "novel") {
                            check(marker in text) { "$name.$lang novel 缺少 $marker" }
                        } else {
                            check(marker !in text) { "$name.$lang 的 $group 窜入 $marker" }
                        }
                    }
                    if (group == "batch") {
                        check("[[1]]" in text) { "$name.$lang batch 缺少 [[1]] 示例" }
                    } else {
                        check("[[1]]" !in text) { "$name.$lang 的 $group 混入批量示例" }
                    }
                }
            }
        }
        println("live catalog contract OK")
    }

    private fun resolveKey(): String {
        System.getenv("PIKU_CATALOG_ENC_KEY")?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        for (candidate in listOf(File("../local.properties"), File("local.properties"))) {
            if (!candidate.isFile) continue
            candidate.readLines().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("piku.catalog.encKey=")) {
                    return trimmed.removePrefix("piku.catalog.encKey=").trim()
                }
            }
        }
        error("未找到 piku.catalog.encKey（env PIKU_CATALOG_ENC_KEY 或 local.properties）")
    }
}
