package com.piku.client.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix

val Pink80 = Color(0xFFFFB1C2)
val PinkGrey80 = Color(0xFFCCC2DC)
val Purple80 = Color(0xFF9A7FC9)

val Pink40 = Color(0xFF7D1D36)
val PinkGrey40 = Color(0xFF625B71)
val Purple40 = Color(0xFF5E4B8B)

val LoginBackgroundLight = Color(0xFFF5F3F0)
val LoginCardLight = Color(0xFFFFFFFF)
val LoginCardBorderLight = Color(0xFFE8E4DE)
val LoginTextPrimaryLight = Color(0xFF2C2C2C)
val LoginTextSecondaryLight = Color(0xFFA09A92)
val LoginDividerLight = Color(0xFFD4D0C8)
val LoginTextFaintLight = Color(0xFFC5C0B8)

val LoginBackgroundDark = Color(0xFF1C1A18)
val LoginCardDark = Color(0xFF262421)
val LoginCardBorderDark = Color(0xFF46405A)
val LoginTextPrimaryDark = Color(0xFFE8E4DE)
val LoginTextSecondaryDark = Color(0xFF9A948C)
val LoginDividerDark = Color(0xFF46405A)
val LoginTextFaintDark = Color(0xFF5C5852)

val AccentPurple = Color(0xFF2C2C2C)

val HomeBgTopLight = Color(0xFFF5F3F0)
val HomeBgBottomLight = Color(0xFFF0EFED)
val HomeBgTopDark = Color(0xFF1C1A18)
val HomeBgBottomDark = Color(0xFF232323)

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
val PillBorderDark = Color(0xFF46405A)
val WorkCardBgDark = Color(0xCC2E2936)
val WorkCardInfoBgDark = Color(0xF22B2633)
val WorkCardPlaceholderDark = Color(0xFF2E2936)
val WorkCardBorderDark = Color(0x8C46405A)

private val WhiteTameGain = 0.86f
private val WhiteTameOffset = -20f
val TameWhiteColorFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            WhiteTameGain, 0f, 0f, 0f, WhiteTameOffset,
            0f, WhiteTameGain, 0f, 0f, WhiteTameOffset,
            0f, 0f, WhiteTameGain, 0f, WhiteTameOffset,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),
)


val ViewerBackgroundDark = Color(0xFF141312)
