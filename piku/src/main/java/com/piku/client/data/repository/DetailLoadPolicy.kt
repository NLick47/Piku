package com.piku.client.data.repository

import com.piku.client.domain.model.WorkDetail

object DetailLoadPolicy {

    // 密码作品不提前画：锁页 HTML 的主图通常是 publish_pass 占位图（占位图判定已能挡住），
    // 但那只是服务端当前的渲染结果。墙后有没有内容只能由 append 的答复决定，
    // 所以这里显式挡一道，避免哪天锁页 HTML 直接渲染真实主图时先出图再要密码。
    fun canPaintPartialFromHtml(detail: WorkDetail): Boolean =
        !detail.passwordProtected &&
            detail.imageUrls.isNotEmpty() &&
            detail.imageUrls.none { ThumbnailResolver.isPlaceholderImage(it) }

    fun partialFromHtml(detail: WorkDetail): WorkDetail? =
        if (canPaintPartialFromHtml(detail)) detail.copy(novelText = "") else null
}
