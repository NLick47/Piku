package com.piku.client.domain.model

/**
 * 收藏夹内的排序方式。
 *
 * 纯本地排序：全部只用已落库的数据（memberships.addedAt / favorites 字段），
 * 不产生任何网络请求。
 */
enum class FolderSort {
    /** 加入收藏夹的时间倒序（默认） */
    ADDED,

    /** 标题升序 */
    TITLE,

    /** 作者名升序，顺带按作者分组显示 */
    AUTHOR,
}
