package com.piku.client.source.poipiku.data.remote

import com.piku.client.core.model.FollowUser

/**
 * 作者搜索（SearchUserByKeywordV.jsp）结果页解析器。
 *
 * 结果页由多张用户卡片组成（`<a class="UserInfo ThumbSmall" href="/IllustListAppV.jsp?ID={userId}"`），
 * 卡片内依次是头像 `UserInfoUserThumb`、昵称 `UserInfoUserName`、简介 `UserInfoProfile`。
 * 该页不渲染关注按钮（关注态一律 false，以用户主页为准）。
 */
object UserSearchParser {

    /** 用户卡片起始标签：`<a class="UserInfo ThumbSmall" href="/IllustListAppV.jsp?ID={userId}"` */
    private val ITEM_TAG = Regex("""<a class="UserInfo ThumbSmall" href="/IllustListAppV\.jsp\?ID=(\d+)"""")

    /** 头像：`UserInfoUserThumb " style="background-image:url('...')"`（class 值后有空格） */
    private val AVATAR = Regex("""UserInfoUserThumb\s*" style="background-image:url\('([^']*)'\)""")

    /** 昵称：`UserInfoUserName ">昵称</span>` */
    private val NAME = Regex("""UserInfoUserName\s*">([^<]+)</span>""")

    /** 关注按钮：`UserInfoCmdFollow` class 含 Selected = 已关注（该页不渲染按钮，恒 false） */
    private val FOLLOW_BTN = Regex("""class="([^"]*UserInfoCmdFollow[^"]*)"""")

    fun parse(html: String): List<FollowUser> {
        val tags = ITEM_TAG.findAll(html).toList()
        if (tags.isEmpty()) return emptyList()
        return tags.mapIndexedNotNull { index, match ->
            val start = match.range.last + 1
            val end = tags.getOrNull(index + 1)?.range?.first ?: html.length
            val userId = match.groupValues[1].toLongOrNull() ?: return@mapIndexedNotNull null
            val block = html.substring(start, end)
            val avatar = AVATAR.find(block)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
            val name = NAME.find(block)?.groupValues?.get(1) ?: return@mapIndexedNotNull null
            val followed = FOLLOW_BTN.find(block)
                ?.groupValues?.get(1)
                ?.split(" ")
                ?.any { it == "Selected" } == true
            FollowUser(userId = userId, name = name, avatarUrl = avatar, followed = followed)
        }
    }

}
