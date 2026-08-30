package com.piku.client.data.remote

import com.piku.client.common.LinkText
import com.piku.client.domain.model.AppError
import com.piku.client.domain.model.Work
import com.piku.client.domain.model.WorkDetail

object WorkDetailParser {

    /**
     * 解析作品详情页。
     *
     * @throws AppError.NotFound 页面不是作品详情页（作品被删除后的兜底页、非公开作品等）。
     *   这类页面不含任何作品区块，继续解析只会得到一个全空的 [WorkDetail]，
     *   让 UI 显示一片空白——比报错更让人摸不着头脑，所以在此直接失败。
     */
    fun parse(html: String): WorkDetail {
        ensureWorkPage(html)
        val mainBlock = html.substringAfter("<div class=\"IllustItem ", html)
        val titleBlock = REGEX_TITLE.find(mainBlock)?.groupValues?.get(1)?.trim() ?: ""
        val marked = LinkText.convert(titleBlock)
        val normalized = marked
            .replace("<br />", "\n")
            .replace("<br/>", "\n")
            .replace("<br>", "\n")
        val title = normalized.substringBefore("\n").cleanText()
        val description = normalized.substringAfter("\n", "").cleanText()
        val authorName = REGEX_AUTHOR.find(mainBlock)?.groupValues?.get(1)?.cleanText() ?: ""
        val authorAvatarUrl = REGEX_AVATAR.find(mainBlock)?.groupValues?.get(1) ?: ""
        val category = REGEX_CATEGORY.find(mainBlock)
        val categoryCd = category?.groupValues?.get(1)?.toIntOrNull() ?: -1
        val categoryName = category?.groupValues?.get(2)?.cleanText() ?: ""
        val tags = REGEX_TAGS.findAll(mainBlock)
            .map { it.groupValues[1].trim().removePrefix("#") }
            .filter { it.isNotBlank() }
            .toList()
        val rawReactions = REGEX_REACTION.findAll(mainBlock).map { it.groupValues[1] }.toList()
        val authorProfile = REGEX_PROFILE.find(html)?.groupValues?.get(1)
            ?.let { LinkText.convert(it) }?.cleanText() ?: ""
        val currentWorkId = REGEX_CANONICAL.find(html)?.groupValues?.get(2)?.toLongOrNull() ?: 0L
        val relatedWorks = parseRelatedWorks(html, currentWorkId)
        val mainImage = extractImageUrls(mainBlock).firstOrNull() ?: ""
        // 文字作品无真实图片，主图是 publish_pass/poipiku_icon/R-18 等占位图；
        // 不放入 imageUrls，否则 UI 显示占位图而非小说正文（NovelView 仅在 imageUrls 为空时渲染）。
        // 判定依据：IllustItem 区块 class 含 "Text" 标记（登录/匿名/锁页均恒在），
        // 并保留 NovelSection 检测兜底（正文区在首屏 HTML 直接可见时）。
        // 注意：mainBlock 已剔除 <div class="IllustItem 前缀，class 检测需基于完整 html。
        val isTextWork = REGEX_ILLUST_TEXT_CLASS.find(html) != null ||
            REGEX_NOVEL.find(mainBlock) != null
        val passwordProtected = REGEX_PASSWORD_PASS.find(mainBlock) != null
        // 关注按钮状态：作者行 span 的 class 含 Selected 表示当前用户已关注该作者。
        // 匿名/未关注时服务端渲染无 Selected（客户端切换成功也是增删这个类）。
        val followed = REGEX_FOLLOW_BTN.find(mainBlock)
            ?.groupValues?.get(1)
            ?.split(" ")
            ?.any { it == "Selected" } == true
        return WorkDetail(
            title = title,
            description = description,
            authorName = authorName,
            authorAvatarUrl = authorAvatarUrl,
            authorProfile = authorProfile,
            categoryCd = categoryCd,
            categoryName = categoryName,
            imageUrls = if (isTextWork) emptyList() else listOf(mainImage).filter { it.isNotEmpty() },
            tags = tags,
            reactions = rawReactions.distinct(),
            reactionCounts = rawReactions.groupingBy { it }.eachCount(),
            reactionCount = rawReactions.size,
            relatedWorks = relatedWorks,
            r18 = mainImage.contains("/img/R-18"),
            warning = mainImage.contains("/img/warning"),
            passwordProtected = passwordProtected,
            followed = followed,
        )
    }

    fun extractImageUrls(html: String): List<String> =
        REGEX_IMAGE.findAll(html).map { it.groupValues[1] }.toList()

    fun extractFullImageUrls(html: String): List<String> =
        REGEX_FULL_IMAGE.findAll(html).map { it.groupValues[1] }.toList()

    fun extractAppendAds(html: String): List<Int> =
        REGEX_APPEND_AD.findAll(html).map { it.groupValues[1].toIntOrNull() ?: -1 }.toList()

    fun extractNovelText(html: String): String =
        REGEX_NOVEL.find(html)?.groupValues?.get(1)
            ?.let { LinkText.convert(it) }?.cleanNovelText() ?: ""

    /**
     * 提取 append 解锁被拒（result_num=-4）时服务器返回的提示文本
     * （如"请关联 Twitter 账号"）。响应 html 为纯文本，剥离残留标签后解码实体。
     */
    fun extractUnlockBlockedMessage(html: String): String = html
        .replace(REGEX_ANY_TAG, "")
        .let { LinkText.decodeEntities(it) }
        .trim()
        .takeIf { it.isNotBlank() }
        .orEmpty()

    private fun parseRelatedWorks(html: String, excludeId: Long): List<Work> =
        html.split("<div class=\"IllustThumb\">")
            .drop(1)
            .mapNotNull { item ->
                val workId = REGEX_RELATED_WORK_ID.find(item)?.groupValues?.get(1)?.toLongOrNull()
                    ?: return@mapNotNull null
                val authorId = REGEX_RELATED_AUTHOR_ID.find(item)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                val authorName = REGEX_RELATED_AUTHOR_NAME.find(item)?.groupValues?.get(1)?.cleanText() ?: ""
                val authorAvatar = REGEX_RELATED_AVATAR.find(item)?.groupValues?.get(1) ?: ""
                val thumb = REGEX_RELATED_THUMB.find(item)?.groupValues?.get(1) ?: ""
                val title = REGEX_RELATED_TITLE.find(item)?.groupValues?.get(1)?.cleanText() ?: ""
                val imageCount = REGEX_RELATED_COUNT.find(item)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val category = REGEX_RELATED_CATEGORY.find(item)
                Work(
                    id = workId,
                    authorId = authorId,
                    authorName = authorName,
                    authorAvatarUrl = authorAvatar,
                    categoryCd = category?.groupValues?.get(1)?.toIntOrNull() ?: -1,
                    categoryName = category?.groupValues?.get(2)?.cleanText() ?: "",
                    title = title,
                    thumbnailUrl = thumb,
                    imageCount = imageCount,
                    r18 = thumb.contains("/img/R-18"),
                    warning = thumb.contains("/img/warning"),
                    loginRequired = thumb.contains("/img/publish_login"),
                )
            }
            .filter { it.id > 0 && it.id != excludeId }
            .distinctBy { it.id }

    /**
     * 判定是否为作品页：作品区块（`<div class="IllustItem `）存在即可，
     * 匿名/锁页/文字作品该区块恒在。
     *
     * 找不到时再看 canonical：它存在说明页面仍指向某个作品（多半是 poipiku 改了
     * 结构），此时宁可退回"字段解析为空"也不要误报"作品已删除"——
     * 后者会让用户以为收藏的作品没了。
     */
    private fun ensureWorkPage(html: String) {
        if (REGEX_ILLUST_BLOCK.containsMatchIn(html)) return
        if (REGEX_CANONICAL.containsMatchIn(html)) return
        throw AppError.NotFound
    }

    private fun String.cleanText(): String = LinkText.decodeEntities(
        this.replace("<br />", "\n").replace("<br/>", "\n").replace("<br>", "\n"),
    ).trim()

    /**
     * 小说正文清洗：换行标签先转为 \n，再剥离其余残留标签（<span class="NovelTitle">、
     * <b>、<strong> 等），最后解码实体。与 [cleanText] 不同，正文允许剔除所有标签；
     * 顺序保证字面量 &lt; 转义在剥标签之后，不会被误删。
     * <a> 已在 LinkText.convert 阶段转为 [url] 标记，此处无尖括号，不会被误删。
     */
    private fun String.cleanNovelText(): String = LinkText.decodeEntities(
        this.replace("<br />", "\n").replace("<br/>", "\n").replace("<br>", "\n")
            .replace(REGEX_ANY_TAG, ""),
    ).trim()

    private val REGEX_TITLE =
        Regex("<h1 id=\"IllustItemDesc_\\d+\" class=\"IllustItemDesc\"\\s*>(.*?)</h1>", RegexOption.DOT_MATCHES_ALL)
    private val REGEX_AUTHOR =
        Regex("<h2 class=\"IllustItemUserName\"><a href=\"/\\d+/\">(.*?)</a></h2>", RegexOption.DOT_MATCHES_ALL)
    private val REGEX_AVATAR =
        Regex("IllustItemUserThumb\" href=\"/\\d+/\" style=\"background-image:url\\('([^']+)'\\)")
    private val REGEX_CATEGORY =
        Regex("<a class=\"Category C(\\d+)\" href=\"/NewArrivalPcV\\.jsp\\?CD=\\d+\">(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
    private val REGEX_TAGS = Regex("<div class=\"TagName\">(.*?)</div>", RegexOption.DOT_MATCHES_ALL)
    private val REGEX_IMAGE = Regex("<img class=\"IllustItemThumbImg\" src=\"([^\"]+)\"")
    private val REGEX_FULL_IMAGE = Regex("<img class=\"DetailIllustItemImage\" src=\"([^\"]+)\"")
    private val REGEX_APPEND_AD = Regex("showIllustDetail\\(\\d+,\\s*\\d+,\\s*(-?\\d+)\\)")
    private val REGEX_PASSWORD_PASS = Regex("""IllustItemExpandPass"\s+name="PAS"""")
    private val REGEX_ANY_TAG = Regex("<[^>]*>")
    private val REGEX_FOLLOW_BTN = Regex("""class="([^"]*UserInfoCmdFollow[^"]*)"""")
    private val REGEX_NOVEL = Regex("""<div class="NovelSection">(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)
    private val REGEX_ILLUST_TEXT_CLASS = Regex("""<div class="IllustItem[^"]*\bText\b""")
    private val REGEX_ILLUST_BLOCK = Regex("""<div class="IllustItem """)
    private val REGEX_REACTION =
        Regex("""<span class="ResEmoji"><img class="Twemoji"[^>]*alt="([^"]+)"[^>]*/>""")
    private val REGEX_PROFILE =
        Regex("""<h3 class="UserInfoProfile">(.*?)</h3>""", RegexOption.DOT_MATCHES_ALL)
    private val REGEX_CANONICAL =
        Regex("""<link rel="canonical" href="https://poipiku\.com/(\d+)/(\d+)\.html"""")
    private val REGEX_RELATED_WORK_ID =
        Regex("""<a class="IllustInfo" href="/\d+/(\d+)\.html"""")
    private val REGEX_RELATED_AUTHOR_ID =
        Regex("""<a class="IllustUser" href="/(\d+)/"""")
    private val REGEX_RELATED_AUTHOR_NAME =
        Regex("""<h2 class="IllustUserName">(.*?)</h2>""", RegexOption.DOT_MATCHES_ALL)
    private val REGEX_RELATED_AVATAR =
        Regex("""<img class="IllustUserThumb" src="([^"]+)"""")
    private val REGEX_RELATED_THUMB =
        Regex("""<img class="IllustThumbImgPic" src="([^"]+)"""")
    private val REGEX_RELATED_TITLE =
        Regex("""<span class="IllustInfoDesc">(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)
    private val REGEX_RELATED_COUNT =
        Regex("""<span class="Num"><i class="far fa-images"></i>\s*(\d+)</span>""")
    private val REGEX_RELATED_CATEGORY =
        Regex("""<span class="Category C(\d+)">(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)
}