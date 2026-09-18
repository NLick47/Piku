package com.piku.client.data.remote

import com.piku.client.domain.model.NsfwLevel
import com.piku.client.domain.model.ShowVisibility
import com.piku.client.domain.model.UploadKind

data class EditPageData(
    val kind: UploadKind,
    val categoryCd: Int,
    val description: String,
    /** 空格分隔、无 # 前缀（与 PublishDraft.tags 同格式；编辑页 value 带 #，此处已归一） */
    val tags: String,
    val publish: Boolean,
    val nsfw: NsfwLevel,
    val visibility: ShowVisibility,
    /** 浏览密码开关。密码值服务端不回显（恒为空），开启时用户必须重新输入 */
    val passwordEnabled: Boolean,
    val showRecent: Boolean,
    val showFirstOnly: Boolean,
    /** 定时公开相关原值透传：App 不支持编辑定时，但提交时必须带回，否则会把定时作品改成即时公开 */
    val notTimeLimited: Boolean,
    val timeLimitedStart: String,
    val timeLimitedEnd: String,
    val novelDirection: Int,
    val title: String,
    val body: String,
)

object EditPageParser {

    private val CATEGORY = Regex("""<option value="(\d+)" selected""")
    private val DESCRIPTION = Regex("""<textarea id="EditDescription"[^>]*>(.*?)</textarea>""", RegexOption.DOT_MATCHES_ALL)
    private val TAGS = Regex("""id="EditTagList"[^>]*value="([^"]*)"""")
    private val NOVEL_TITLE = Regex("""<input\s+id="EditTextTitle"[^>]*value="([^"]*)"""")
    private val NOVEL_BODY = Regex("""<textarea id="EditTextBody"[^>]*>(.*?)</textarea>""", RegexOption.DOT_MATCHES_ALL)

    /** contentParams 的三种赋值形态（值可能是 true/false、整数、或带引号字符串） */
    private fun boolParam(name: String) = Regex("""contentParams\.$name\.value = (true|false);""")
    private fun intParam(name: String) = Regex("""contentParams\.$name\.value = (\d+);""")
    private fun strParam(name: String) = Regex("""contentParams\.$name\.value = "([^"]*)";""")

    /**
     * 解析编辑页；结构不符（非编辑页 / 会话失效壳）返回 null。
     * [expectedKind] 与页面自述（EditTextTitle 存在 → 小说）不一致时也判失败，防错类型提交。
     */
    fun parse(html: String, expectedKind: UploadKind): EditPageData? {
        val categoryCd = CATEGORY.find(html)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val hasNovelFields = html.contains("id=\"EditTextTitle\"")
        val kind = if (hasNovelFields) UploadKind.NOVEL else UploadKind.ILLUST
        if (kind != expectedKind) return null

        val notNsfw = boolParam("OPTION_NOT_PUBLISH_NSFW").find(html)?.groupValues?.get(1) == "true"
        val nsfw = if (notNsfw) {
            NsfwLevel.ALL
        } else {
            // wire 值未命中时兜底 R18（正常只会出现 2/4/8）
            NsfwLevel.entries.firstOrNull { it.wire == intParam("NSFW_VAL").find(html)?.groupValues?.get(1)?.toIntOrNull() }
                ?: NsfwLevel.R18
        }
        val noConditional = boolParam("OPTION_NO_CONDITIONAL_SHOW").find(html)?.groupValues?.get(1) == "true"
        val visibility = if (noConditional) {
            ShowVisibility.ANYONE
        } else {
            // SHOW_LIMIT_VAL 7/12/13（推特系/列表）App 未接入，保守归到"仅登录可看"
            when (intParam("SHOW_LIMIT_VAL").find(html)?.groupValues?.get(1)?.toIntOrNull()) {
                6 -> ShowVisibility.FOLLOWER
                else -> ShowVisibility.POIPIKU_LOGIN
            }
        }

        return EditPageData(
            kind = kind,
            categoryCd = categoryCd,
            description = DESCRIPTION.find(html)?.groupValues?.get(1)?.let(::decode).orEmpty(),
            tags = TAGS.find(html)?.groupValues?.get(1)?.let(::decode).orEmpty()
                .split(Regex("\\s+"))
                .map { it.removePrefix("#") }
                .filter { it.isNotBlank() }
                .joinToString(" "),
            publish = boolParam("OPTION_PUBLISH").find(html)?.groupValues?.get(1) != "false",
            nsfw = nsfw,
            visibility = visibility,
            // 密码值服务端不回显，只回显开关状态
            passwordEnabled = boolParam("OPTION_NO_PASSWORD").find(html)?.groupValues?.get(1) == "false",
            showRecent = boolParam("OPTION_RECENT").find(html)?.groupValues?.get(1) != "false",
            showFirstOnly = boolParam("OPTION_SHOW_FIRST").find(html)?.groupValues?.get(1) == "true",
            notTimeLimited = boolParam("OPTION_NOT_TIME_LIMITED").find(html)?.groupValues?.get(1) != "false",
            timeLimitedStart = strParam("TIME_LIMITED_START").find(html)?.groupValues?.get(1).orEmpty(),
            timeLimitedEnd = strParam("TIME_LIMITED_END").find(html)?.groupValues?.get(1).orEmpty(),
            novelDirection = intParam("NOVEL_DIRECTION_VAL").find(html)?.groupValues?.get(1)?.toIntOrNull() ?: 0,
            title = NOVEL_TITLE.find(html)?.groupValues?.get(1)?.let(::decode).orEmpty(),
            body = NOVEL_BODY.find(html)?.groupValues?.get(1)?.let(::decode).orEmpty(),
        )
    }

    /** HTML 实体解码（textarea 内容与 value 属性共用）；保留换行，不剥标签 */
    private fun decode(raw: String): String = raw
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")
}
