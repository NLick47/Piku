package com.piku.client.data.local

import androidx.room.Entity

/**
 * 译文缓存。
 *
 * 主键用 (srcHash, targetLang, engineId) 三元组而非自增 id：
 * - srcHash 为原文的 SHA-256，避免把可能很长的小说正文当主键；
 * - 换目标语言或换模型都视为不同缓存，不会互相污染；
 * - 同一段文本在多个作品间复用（如相同标签）自然命中。
 */
@Entity(tableName = "translations", primaryKeys = ["srcHash", "targetLang", "engineId"])
data class TranslationEntity(
    val srcHash: String,
    val targetLang: String,
    /** 引擎标识，形如 "llm:novel:model:https://…"，场景/模型/地址任一不同则缓存不同 */
    val engineId: String,
    val translated: String,
    val updatedAt: Long,
)
