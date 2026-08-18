package com.piku.client.data.remote

import com.piku.client.domain.model.FollowUser

/**
 * 作者搜索（SearchUserByKeywordPcV.jsp）结果页解析器。
 *
 * 结果页由多张用户卡片组成，卡片结构与关注列表（FollowListF）一致：
 * `<a class="UserInfo Thumb" href="/{userId}/"><span class="UserInfoUserThumb" style="background-image:url('...')"></span>...<span class="UserInfoUserName">昵称</span></a>`。
 * 关注状态从卡片所在行的 `UserInfoCmdFollow` 按钮 class 中的 `Selected` 标记解析
 * （与用户主页 UserPageParser 同一约定；页面未携带按钮时视为未关注）。
 */
object UserSearchParser {

    /** 用户卡片起始标签：`<a class="UserInfo Thumb" href="/{userId}/"` */
    private val ITEM_TAG = Regex("""<a class="UserInfo Thumb" href="/(\d+)/"""")

    /** 头像：`UserInfoUserThumb" style="background-image:url('...')"` */
    private val AVATAR = Regex("""UserInfoUserThumb" style="background-image:url\('([^']*)'\)""")

    /** 昵称：`UserInfoUserName">昵称</span>` */
    private val NAME = Regex("""UserInfoUserName">([^<]+)</span>""")

    /** 关注按钮：`UserInfoCmdFollow` class 含 Selected = 已关注（匿名恒无） */
    private val FOLLOW_BTN = Regex("""class="([^"]*UserInfoCmdFollow[^"]*)"""")

    /** 登录页：`LoginUserF.jsp`（会话失效时服务端返回登录页而非结果页） */
    private val LOGIN_FORM = Regex("""LoginUserF\.jsp""")

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

    fun isLoginPage(html: String): Boolean = LOGIN_FORM.containsMatchIn(html)
}
