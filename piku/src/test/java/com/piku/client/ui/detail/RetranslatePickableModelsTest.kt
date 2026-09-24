package com.piku.client.ui.detail

import com.piku.client.data.remote.translation.ModelEntry
import com.piku.client.data.remote.translation.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetranslatePickableModelsTest {

    private fun model(
        id: String,
        vararg roles: String,
        apiKey: String? = "key",
        available: Boolean = true,
    ) = ModelEntry(
        id = id,
        label = id,
        baseUrl = "https://example.com/v1",
        model = "model-$id",
        available = available,
        apiKey = apiKey,
        roles = roles.toList(),
    )

    private val text = model("text-a", Role.TEXT)
    private val novel = model("novel-a", Role.NOVEL)
    private val image = model("img-a", Role.IMAGE)

    @Test
    fun onlyTextRoleModelsArePickable() {
        assertEquals(
            listOf("text-a"),
            retranslatePickableModels(listOf(text, novel, image)).map { it.id },
        )
        // 目录只有正文/图片模型时没有可重翻短字段的模型，入口不出现
        assertTrue(retranslatePickableModels(listOf(novel, image)).isEmpty())
    }

    @Test
    fun unavailableOrKeylessModelsAreNotPickable() {
        val down = model("text-down", Role.TEXT, available = false)
        val keyless = model("text-keyless", Role.TEXT, apiKey = null)
        assertTrue(retranslatePickableModels(listOf(down, keyless)).isEmpty())
    }

    @Test
    fun multiRoleEntryIsPickableByItsTextRole() {
        // 同时声明文本与图片的条目仍能翻短字段，不该被"带图片角色"连坐排除
        val hybrid = model("hybrid", Role.TEXT, Role.IMAGE)
        assertEquals(listOf("hybrid"), retranslatePickableModels(listOf(hybrid)).map { it.id })
    }
}
