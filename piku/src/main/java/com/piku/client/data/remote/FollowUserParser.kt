package com.piku.client.data.remote

import com.piku.client.domain.model.FollowUser

object FollowUserParser {

    private val ITEM_TAG = Regex("""<a class="UserInfo Thumb" href="/(\d+)/"""")

    private val AVATAR = Regex("""UserInfoUserThumb" style="background-image:url\('([^']*)'\)""")

    private val NAME = Regex("""UserInfoUserName">([^<]+)</span>""")

    private val TOTAL = Regex("""TOTAL=(\d+)""")

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
            FollowUser(userId = userId, name = name, avatarUrl = avatar)
        }
    }

    fun parseTotal(html: String): Int? = TOTAL.find(html)?.groupValues?.get(1)?.toIntOrNull()

    fun isLoginPage(html: String): Boolean = LOGIN_FORM.containsMatchIn(html)
}
