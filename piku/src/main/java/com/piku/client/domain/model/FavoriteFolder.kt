package com.piku.client.domain.model

data class FavoriteFolder(
    val id: Long,
    val name: String,
    val workCount: Int = 0,
    /** 最近收藏的作品缩略图，用于卡片直接预览内容。 */
    val previewUrls: List<String> = emptyList(),
    /** 是否为默认收藏夹（快速收藏的落点，全 App 唯一，不可删除）。 */
    val isDefault: Boolean = false,
)
