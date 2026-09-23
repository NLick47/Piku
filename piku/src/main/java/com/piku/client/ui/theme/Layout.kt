package com.piku.client.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object PikuLayout {
    /** 页面内容左右边距 */
    val ScreenInset: Dp = 16.dp

    /** 网格列间距、行间距 */
    val GridGap: Dp = 12.dp

    /** 导航头像与图标按钮的可见直径 */
    val NavControl: Dp = 32.dp

    /** 导航控件命中区 */
    val NavHit: Dp = 44.dp

    /** 命中区比可见直径大出的部分均分到两侧，从行内距里扣掉 */
    val NavRowInset: Dp = ScreenInset - (NavHit - NavControl) / 2

    /** 卡片圆角，缩略图铺满卡片共用同一圆角 */
    val CardCorner: Dp = 14.dp

    /** 卡片信息区内边距 */
    val CardPadding: Dp = 12.dp

    /** 屏幕宽减去它就等于两列卡片的总宽 */
    val CardWidthChrome: Dp = ScreenInset * 2 + GridGap
}

internal fun feedCardWidthPx(screenWidthDp: Int, density: Float): Float =
    (screenWidthDp - PikuLayout.CardWidthChrome.value) / 2f * density
