package com.piku.client.data.remote

import com.piku.client.domain.model.UserPageInfo

/**
 * 用户主页头部信息解析器。
 *
 * 数据源：`IllustListPcV.jsp` / `/{userId}/` 返回的完整用户主页 HTML（匿名可用）。
 * 该页面在 `<head>` 内嵌 `<style>` 块中携带作者的页头 banner 图规则：
 * `.UserInfo {background-image: url('https://cdn.poipiku.com/{uid:09d}/header_{ts}.{ext}_640.jpg')}`
 * （未设置页头图时整条规则缺失）；作者背景色/背景图（Poipass 会员 β 功能）若已设置，
 * 推测以同块中的 background 规则出现，本解析器做宽容提取，未命中则为 null。
 */
object UserPageParser {

    /** 页头 banner 图：`.UserInfo { ... background-image: url('...') ... }` */
    private val HEADER_IMAGE = Regex("""\.UserInfo\s*\{[^}]*?background-image:\s*url\('([^']+)'\)""")

    /** 头像（全尺寸）：`<meta name="twitter:image" content="...">` */
    private val TWITTER_IMAGE = Regex("""<meta name="twitter:image" content="([^"]+)"""")

    /**
     * 昵称：`<meta property="og:title" content="XXXのポイピク | イラストとか箱「ポイピク」">`。
     * 后缀是必须的：站点首页的 og:title 没有它，不能把一个"不是用户页"的标题当成昵称
     */
    private val OG_USER_NAME = Regex("""<meta property="og:title" content="([^"]+?)のポイピク""")

    /** 昵称（页主区块）：`<h2 class="IllustUserName">XXX</h2>` */
    private val H2_USER_NAME = Regex("""<h2 class="IllustUserName">([^<]+)</h2>""")

    /** 昵称（页标题）：必须带完整后缀，否则"页面不存在"那种标题会被当成昵称 */
    private val PAGE_TITLE = Regex("""<title>([^<]+)のポイピク \| イラストとか箱「ポイピク」</title>""")

    /** 昵称（头像 alt）：`<img class="IllustUserThumb" ... alt="XXX">` */
    private val AVATAR_ALT = Regex("""<img class="IllustUserThumb"[^>]*alt="([^"]+)"""")

    private val HTML_ENTITY_DECIMAL = Regex("&#(\\d+);")

    /** 作品数：`<meta property="og:description" content="XXXはポイピクにN枚のイラストとかをポイポイしています。">` */
    private val OG_DESCRIPTION = Regex("""<meta property="og:description" content="([^"]+)"""")
    private val WORK_COUNT = Regex("""(\d+)枚のイラスト""")

    /** 内嵌 style 块 */
    private val STYLE_BLOCK = Regex("""<style>(.*?)</style>""", RegexOption.DOT_MATCHES_ALL)

    /** 背景色（hex）：`background: #XXXXXX` / `background-color: #XXXXXX` */
    private val BG_COLOR = Regex("""background(?:-color)?\s*:\s*(#[0-9a-fA-F]{3,8})""")

    /** 背景图：`background: url(...)` / `background-image: url(...)` */
    private val BG_IMAGE = Regex("""background(?:-image)?\s*:\s*url\('?([^')]+)'?\)""")

    /** Twitter 链接：`<a class="fab fa-twitter" ... href="...">` */
    private val TWITTER_LINK = Regex("""<a[^>]*class="[^"]*fa-twitter[^"]*"[^>]*href="([^"]+)"""")

    /** 关注状态：`UserInfoCmdFollow` 按钮 class 含 Selected = 当前登录用户已关注（匿名恒无） */
    private val FOLLOW_BTN = Regex("""class="([^"]*UserInfoCmdFollow[^"]*)"""")

    /** 屏蔽状态：`UserInfoCmdBlock` 按钮 class 含 Selected = 当前登录用户已屏蔽（整页唯一） */
    private val BLOCK_BTN = Regex("""class="([^"]*UserInfoCmdBlock[^"]*)"""")

    /**
     * 昵称。四个来源在真实用户页上实测完全一致（2026-09-24 核对了 3 个 uid），
     * 按 og:title → 页主 h2 → <title> → 头像 alt 取第一个命中，并解 HTML 实体。
     * 页主页和用户主页共用这一份实现。
     */
    fun parseDisplayName(html: String): String? {
        val raw = OG_USER_NAME.find(html)?.groupValues?.get(1)
            ?: H2_USER_NAME.find(html)?.groupValues?.get(1)
            ?: PAGE_TITLE.find(html)?.groupValues?.get(1)
            ?: AVATAR_ALT.find(html)?.groupValues?.get(1)
        return raw?.let(::decodeHtmlEntities)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun decodeHtmlEntities(input: String): String = input
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace(HTML_ENTITY_DECIMAL) { m ->
            m.groupValues[1].toIntOrNull()?.let { it.toChar().toString() } ?: m.value
        }

    fun parse(html: String): UserPageInfo {
        val headerUrl = HEADER_IMAGE.find(html)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }

        val avatarUrl = TWITTER_IMAGE.find(html)?.groupValues?.get(1)
            ?.takeIf { it.isNotBlank() && !it.contains("default_user") }

        val userName = parseDisplayName(html)

        val ogDesc = OG_DESCRIPTION.find(html)?.groupValues?.get(1).orEmpty()
        val workCount = WORK_COUNT.find(ogDesc)?.groupValues?.get(1)?.toIntOrNull()

        // 关注状态：与关注列表（FollowListF）一致的 Selected 标记
        val followed = FOLLOW_BTN.find(html)
            ?.groupValues?.get(1)
            ?.split(" ")
            ?.any { it == "Selected" } == true

        // 屏蔽状态：同一套 Selected 标记（实测被屏蔽时 class 为 "typcn typcn-cancel BtnBase UserInfoCmdBlock Selected"）
        val blocked = BLOCK_BTN.find(html)
            ?.groupValues?.get(1)
            ?.split(" ")
            ?.any { it == "Selected" } == true

        val twitterUrl = TWITTER_LINK.find(html)?.groupValues?.get(1)

        // 整页背景：提取 style 块中 .UserInfo 规则之外的 background 属性（Poipass 背景色/背景图）
        var bgColorHex: String? = null
        var bgImageUrl: String? = null
        STYLE_BLOCK.findAll(html).forEach { block ->
            // 去掉 .UserInfo 规则（页头图属于它），剩余规则里的 background 才视为整页背景
            val rest = block.groupValues[1].replace(HEADER_IMAGE, "")
            if (bgColorHex == null) {
                BG_COLOR.find(rest)?.let { bgColorHex = it.groupValues[1] }
            }
            if (bgImageUrl == null) {
                BG_IMAGE.find(rest)?.let { bgImageUrl = it.groupValues[1] }
            }
        }

        return UserPageInfo(
            userName = userName,
            avatarUrl = avatarUrl,
            headerUrl = headerUrl,
            workCount = workCount,
            bgColorHex = bgColorHex,
            bgImageUrl = bgImageUrl,
            followed = followed,
            blocked = blocked,
            twitterUrl = twitterUrl,
        )
    }
}
