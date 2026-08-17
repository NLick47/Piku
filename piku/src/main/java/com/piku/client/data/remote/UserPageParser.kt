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

    /** 昵称：`<meta property="og:title" content="XXXのポイピク | イラストとか箱「ポイピク」">` */
    private val OG_TITLE = Regex("""<meta property="og:title" content="([^"]+)"""")

    /** 作品数：`<meta property="og:description" content="XXXはポイピクにN枚のイラストとかをポイポイしています。">` */
    private val OG_DESCRIPTION = Regex("""<meta property="og:description" content="([^"]+)"""")
    private val WORK_COUNT = Regex("""(\d+)枚のイラスト""")

    /** 内嵌 style 块 */
    private val STYLE_BLOCK = Regex("""<style>(.*?)</style>""", RegexOption.DOT_MATCHES_ALL)

    /** 背景色（hex）：`background: #XXXXXX` / `background-color: #XXXXXX` */
    private val BG_COLOR = Regex("""background(?:-color)?\s*:\s*(#[0-9a-fA-F]{3,8})""")

    /** 背景图：`background: url(...)` / `background-image: url(...)` */
    private val BG_IMAGE = Regex("""background(?:-image)?\s*:\s*url\('?([^')]+)'?\)""")

    /** 关注状态：`UserInfoCmdFollow` 按钮 class 含 Selected = 当前登录用户已关注（匿名恒无） */
    private val FOLLOW_BTN = Regex("""class="([^"]*UserInfoCmdFollow[^"]*)"""")

    fun parse(html: String): UserPageInfo {
        val headerUrl = HEADER_IMAGE.find(html)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }

        val avatarUrl = TWITTER_IMAGE.find(html)?.groupValues?.get(1)
            ?.takeIf { it.isNotBlank() && !it.contains("default_user") }

        val ogTitle = OG_TITLE.find(html)?.groupValues?.get(1)
        val userName = ogTitle?.substringBefore("のポイピク")?.takeIf { it.isNotBlank() }

        val ogDesc = OG_DESCRIPTION.find(html)?.groupValues?.get(1).orEmpty()
        val workCount = WORK_COUNT.find(ogDesc)?.groupValues?.get(1)?.toIntOrNull()

        // 关注状态：与关注列表（FollowListF）一致的 Selected 标记
        val followed = FOLLOW_BTN.find(html)
            ?.groupValues?.get(1)
            ?.split(" ")
            ?.any { it == "Selected" } == true

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
        )
    }
}
