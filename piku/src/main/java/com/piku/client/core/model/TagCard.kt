package com.piku.client.core.model

data class TagCard(
    val name: String,
    val thumbnailUrl: String? = null,
    /** 站点作品数，文字源的行式列表用它区分同名标签 */
    val count: Int? = null,
)