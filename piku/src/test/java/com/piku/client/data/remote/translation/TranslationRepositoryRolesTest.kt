package com.piku.client.data.remote.translation

import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 场景（role）解析与故障转移候选过滤的纯函数测试。
 *
 * 语义契约：
 * - 目录 defaults.roles 指向的条目优先，但必须可用且带内置 key；
 * - 退而求其次取首个该 role 的可用带 key 条目；
 * - 都没有返回 null——小说通道宁缺毋滥，绝不向其他场景借模型；
 * - 故障转移候选必须在同 role 内随机，小说正文不会被甩给文本免费模型。
 */
class TranslationRepositoryRolesTest {

    private fun model(
        id: String,
        vararg roles: String,
        apiKey: String? = "key",
        free: Boolean = true,
        available: Boolean = true,
    ) = ModelEntry(
        id = id,
        label = id,
        baseUrl = "https://example.com/v1",
        model = "model-$id",
        apiKey = apiKey,
        free = free,
        available = available,
        params = mapOf("enable_thinking" to JsonPrimitive(false)),
        roles = if (roles.isEmpty()) listOf(Role.TEXT) else roles.toList(),
    )

    @Test
    fun `roles map declaration wins over first matching model`() {
        val models = listOf(model("a", Role.TEXT), model("b", Role.TEXT))
        val resolved = ModelCatalog.resolveRoleDefault(Role.TEXT, models, mapOf(Role.TEXT to "b"))
        assertEquals("b", resolved?.id)
    }

    @Test
    fun `falls back to first keyed available entry of role`() {
        val models = listOf(
            model("novel-a", Role.NOVEL),
            model("text-b", Role.TEXT),
            model("novel-c", Role.NOVEL),
        )
        val resolved = ModelCatalog.resolveRoleDefault(Role.NOVEL, models, emptyMap())
        assertEquals("novel-a", resolved?.id)
    }

    @Test
    fun `ignores unavailable keyless or foreign-role declarations`() {
        // roles 指向不可用条目 → 跳过；role 内全部缺 key → 返回 null，不跨场景借模型
        val models = listOf(
            model("down", Role.NOVEL, available = false),
            model("nokey", Role.NOVEL, apiKey = null),
            model("text-only", Role.TEXT),
        )
        assertNull(ModelCatalog.resolveRoleDefault(Role.NOVEL, models, mapOf(Role.NOVEL to "ghost")))
        assertNull(ModelCatalog.resolveRoleDefault(Role.NOVEL, models, emptyMap()))
    }

    @Test
    fun `declared default that lost its role still resolves by id`() {
        // 目录作者改了条目 roles 但忘了同步 defaults.roles：按 id 指认依然生效（信任目录声明）
        val models = listOf(model("a", Role.TEXT), model("b", Role.TEXT))
        val resolved = ModelCatalog.resolveRoleDefault(Role.TEXT, models, mapOf(Role.TEXT to "b"))
        assertEquals("b", resolved?.id)
    }

    @Test
    fun `custom fork roles resolve generically`() {
        // 解析函数对任意 role 名通用：fork 加新场景无需改 app 代码；
        // 而 app 只查询自己认识的 role，未接管的场景条目自然被忽略（向前兼容）
        val models = listOf(model("img", "image"), model("txt", Role.TEXT))
        assertEquals("img", ModelCatalog.resolveRoleDefault("image", models, emptyMap())?.id)
        assertEquals("txt", ModelCatalog.resolveRoleDefault(Role.TEXT, models, emptyMap())?.id)
    }

    @Test
    fun `failover candidates stay within role`() {
        val models = listOf(
            model("cur", Role.NOVEL, free = false),
            model("other-novel", Role.NOVEL, free = false), // 非免费：不是候选
            model("free-text", Role.TEXT),                  // 跨 role：不是候选
            model("free-novel", Role.NOVEL),                // 合法候选
            model("paid-novel-down", Role.NOVEL, available = false),
        )
        repeat(20) {
            val candidate = ModelCatalog.failoverCandidate(models.first(), Role.NOVEL, models)
            assertTrue(candidate == null || candidate.id == "free-novel")
        }
        // 无同 role 免费候选时返回 null（保持失败），不会借文本模型
        val none = ModelCatalog.failoverCandidate(models.first(), Role.NOVEL, listOf(models[0], models[2]))
        assertNull(none)
    }

    @Test
    fun `stored selection resolves by id or bare model name`() {
        val models = listOf(model("qwen", Role.TEXT).copy(model = "Qwen/Qwen3-8B"))
        assertEquals("qwen", ModelCatalog.resolveStoredSelection("qwen", models)?.id)
        assertEquals("qwen", ModelCatalog.resolveStoredSelection("Qwen/Qwen3-8B", models)?.id)
    }

    @Test
    fun `stored selection on unavailable or keyless entry is inert`() {
        // 目录权威高于历史选中：下架/撤 key 的模型不再因旧选中值生效，回落场景默认由调用方处理
        val models = listOf(
            model("down", Role.TEXT, available = false),
            model("nokey", Role.TEXT, apiKey = null),
        )
        assertNull(ModelCatalog.resolveStoredSelection("down", models))
        assertNull(ModelCatalog.resolveStoredSelection("nokey", models))
        assertNull(ModelCatalog.resolveStoredSelection("ghost", models))
        assertNull(ModelCatalog.resolveStoredSelection("  ", models))
    }
}
