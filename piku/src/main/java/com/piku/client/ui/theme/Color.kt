package com.piku.client.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix

val SurfaceVariantDark = Color(0xFF3A3834)

val LoginBackgroundLight = Color(0xFFF5F3F0)
val LoginCardLight = Color(0xFFFFFFFF)
val LoginCardBorderLight = Color(0xFFE8E4DE)
val LoginTextPrimaryLight = Color(0xFF2C2C2C)
val LoginTextSecondaryLight = Color(0xFFA09A92)
val LoginDividerLight = Color(0xFFD4D0C8)
val LoginTextFaintLight = Color(0xFFC5C0B8)

val LoginBackgroundDark = Color(0xFF1C1A18)
val LoginCardDark = Color(0xFF262421)
val LoginCardBorderDark = SurfaceVariantDark
val LoginTextPrimaryDark = Color(0xFFE8E4DE)
val LoginTextSecondaryDark = Color(0xFF9A948C)
val LoginDividerDark = SurfaceVariantDark
val LoginTextFaintDark = Color(0xFF5C5852)

val AccentDark = Color(0xFF5F584F)

val AccentSolid = Color(0xFF7D746A)


val ControlAccentLight = AccentDark
val ControlAccentDark = Color(0xFFE0E0E0)

val StarLight = Color(0xFFE0A83C)
val StarDark = Color(0xFFFFD166)
val StarTintLight = Color(0x2AE0A83C)
val StarTintDark = Color(0x33FFD166)

val FollowLight = Color(0xFF4CAF50)
val FollowDark = Color(0xFF81C784)
val FollowTintLight = Color(0x2A4CAF50)
val FollowTintDark = Color(0x3381C784)

val SwitchCheckedThumbLight = Color.White
val SwitchCheckedThumbDark = LoginBackgroundDark
val SwitchCheckedTrackLight = AccentSolid
val SwitchCheckedTrackDark = LoginTextPrimaryDark
val SwitchCheckedIconLight = AccentSolid
val SwitchCheckedIconDark = LoginTextPrimaryDark
val SwitchUncheckedThumbLight = Color.White
val SwitchUncheckedThumbDark = LoginTextSecondaryDark
val SwitchUncheckedTrackLight = Color(0xFFE6E2DB)
val SwitchUncheckedTrackDark = SurfaceVariantDark

val SoftBorderLight = Color(0x59C8C2B8)
val SoftBorderDark = Color(0x59FFFFFF)
val BadgeBgLight = Color(0xF2FFFFFF)
val BadgeBgDark = Color(0xCC5C5852)

val LoginButtonLight = Color(0xFF4A453F)
val LoginButtonDark = LoginTextPrimaryDark

val HomeBgTopLight = Color(0xFFF5F3F0)
val HomeBgBottomLight = Color(0xFFF0EFED)
val HomeBgTopDark = Color(0xFF1C1A18)
val HomeBgBottomDark = Color(0xFF232323)

val StarSkyTopDark = Color(0xFF0E1330)
val StarSkyMidDark = Color(0xFF1B1B44)
val StarSkyBottomDark = Color(0xFF29213F)
val StarSkyTopLight = Color(0xFFE7EDFA)
val StarSkyMidLight = Color(0xFFECEAF7)
val StarSkyBottomLight = Color(0xFFF5F1F1)

val GlassHeaderTintLight = Color(0xF2F5F3F0)
val GlassHeaderTintDark = Color(0xE61C1A18)
val GlassCardTintLight = Color(0xA6FFFFFF)
val GlassCardTintDark = Color(0x99FFFFFF)
val GlassCardBorderLight = Color(0x59FFFFFF)
val GlassCardBorderDark = Color(0x1FFFFFFF)
val GlassCardBgLight = Color(0x99FFFFFF)
val GlassCardBgDark = Color(0x8C262421)

val GlassIconBgDark = Color(0xCC262421)
val PillBorderLight = Color(0xFFE8E4DE)
val PillBorderDark = SurfaceVariantDark

val WorkCardBgDark = LoginCardDark.copy(alpha = 0.8f)
val WorkCardInfoBgDark = LoginCardDark.copy(alpha = 0.95f)
val WorkCardPlaceholderDark = LoginCardDark
val WorkCardBorderDark = SurfaceVariantDark.copy(alpha = 0.33f)

// 暗色下作品图统一后处理：非对称暖调矩阵（亮色不启用）
//  - 亮度：白底压到 ~67%，对齐暗色 UI 表面层级，消除网格里的"亮块"感
//  - 色温：R>G>B 非对称，高光偏暖米、暗部偏暖黑，与暖灰 UI（#262421 系）同族
//  - 保留中间调层次与原始饱和度，作品内容不被篡改
// 推演（0-255）：白 255→R172 G161 B138；中灰 128→R98 G90 B74；黑 0→R24 G18 B10
private val DimRGain = 0.58f
private val DimGGain = 0.56f
private val DimBGain = 0.50f
private val DimRLift = 24f
private val DimGLift = 18f
private val DimBLift = 10f
val TameWhiteColorFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            DimRGain, 0f, 0f, 0f, DimRLift,
            0f, DimGGain, 0f, 0f, DimGLift,
            0f, 0f, DimBGain, 0f, DimBLift,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),
)


val ViewerBackgroundDark = Color(0xFF141312)
