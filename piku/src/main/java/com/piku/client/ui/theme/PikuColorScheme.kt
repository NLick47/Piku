package com.piku.client.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter

data class PikuColorScheme(
    val textPrimary: Color,
    val textSecondary: Color,
    val textFaint: Color,
    val border: Color,
    /** 卡片底（纯白）。亮色 #FFFFFF，暗色 #262421。 */
    val surface: Color,
    /** 卡片底（浅灰 #EAE8E3）。暗色同为 #262421。 */
    val surfaceMuted: Color,
    /** 卡片底（米灰 #F1EFEA，详情页内容块衬底）。暗色同为 #262421。 */
    val surfaceSoft: Color,
    /** 选中/强调态颜色。暗色 #E8E4DE，亮色 #5F584F。 */
    val accent: Color,
    /** 控件强调色（按钮/滑块等）。暗色 #E0E0E0，亮色 #5F584F。 */
    val controlAccent: Color,
    /** 错误/警示色。暗色 #E08A8A，亮色 #C24B4B。 */
    val error: Color,
    /** 去色滤镜（仅暗色模式需 TameWhite，亮色为 null）。 */
    val tameWhiteFilter: ColorFilter?,
)

internal val LightPikuColors = PikuColorScheme(
    textPrimary = LoginTextPrimaryLight,
    textSecondary = LoginTextSecondaryLight,
    textFaint = LoginTextFaintLight,
    border = PillBorderLight,
    surface = LoginCardLight,
    surfaceMuted = Color(0xFFEAE8E3),
    surfaceSoft = Color(0xFFF1EFEA),
    accent = AccentDark,
    controlAccent = ControlAccentLight,
    error = ErrorRedLight,
    tameWhiteFilter = null,
)

internal val DarkPikuColors = PikuColorScheme(
    textPrimary = LoginTextPrimaryDark,
    textSecondary = LoginTextSecondaryDark,
    textFaint = LoginTextFaintDark,
    border = PillBorderDark,
    surface = LoginCardDark,
    surfaceMuted = LoginCardDark,
    surfaceSoft = LoginCardDark,
    accent = LoginTextPrimaryDark,
    controlAccent = ControlAccentDark,
    error = ErrorRedDark,
    tameWhiteFilter = TameWhiteColorFilter,
)

/** 当前生效的配色表，由 [PoipikuTheme] 提供。 */
val LocalPikuColors = staticCompositionLocalOf { LightPikuColors }

/** 读取当前配色表，例如 `PikuColors.textPrimary`。需要 @Composable 上下文。 */
val PikuColors: PikuColorScheme
    @Composable
    @ReadOnlyComposable
    get() = LocalPikuColors.current
