package com.piku.client.domain.model

/**
 * 用户主页头部信息：从用户作品列表页（IllustListPcV.jsp，即完整用户主页）HTML 中解析。
 *
 * 来源：
 * - [headerUrl]：`<head>` 内嵌 `<style>` 的 `.UserInfo {background-image: url(...)}` 规则（页头 banner 图，未设置时整条规则缺失）
 * - [avatarUrl]：`<meta name="twitter:image">`（全尺寸头像）
 * - [userName]：`<meta property="og:title">`（"XXXのポイピク | ..." 前缀）
 * - [workCount]：`<meta property="og:description">`（"XXXはポイピクにN枚のイラスト..."）
 * - [bgColorHex]/[bgImageUrl]：同 style 块中 `.UserInfo` 之外的 background 规则
 *   （Poipass 会员「背景画像/背景色」β 功能，非会员不出现；渲染格式宽容解析，未命中则为 null）
 */
data class UserPageInfo(
    val userName: String? = null,
    val avatarUrl: String? = null,
    val headerUrl: String? = null,
    val workCount: Int? = null,
    val bgColorHex: String? = null,
    val bgImageUrl: String? = null,
) {
    val hasBackground: Boolean
        get() = !bgColorHex.isNullOrBlank() || !bgImageUrl.isNullOrBlank()
}
