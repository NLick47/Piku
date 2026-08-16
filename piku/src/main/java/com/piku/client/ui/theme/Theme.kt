package com.piku.client.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** 当前生效的深色模式（跟随系统或用户手动选择）。由 [PoipikuTheme] 提供。 */
val LocalDarkTheme = staticCompositionLocalOf { false }

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFE0E0E0),
    onPrimary = Color(0xFF1A1A1A),
    primaryContainer = Color(0xFF3A3A3A),
    onPrimaryContainer = Color(0xFFF5F5F5),
    secondary = Color(0xFFBDBDBD),
    tertiary = Color(0xFF9E9E9E),
    background = LoginBackgroundDark,
    onBackground = LoginTextPrimaryDark,
    surface = LoginCardDark,
    onSurface = LoginTextPrimaryDark,
    surfaceVariant = Color(0xFF3A3834),
    onSurfaceVariant = LoginTextSecondaryDark,
    outline = PillBorderDark,
)

private val LightColorScheme = lightColorScheme(
    primary = AccentPurple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF0F0F0),
    onPrimaryContainer = Color(0xFF1A1A1A),
    secondary = Color(0xFF757575),
    tertiary = Color(0xFF9E9E9E),
    background = LoginBackgroundLight,
    onBackground = LoginTextPrimaryLight,
    surface = LoginCardLight,
    onSurface = LoginTextPrimaryLight,
    surfaceVariant = Color(0xFFF1EFEA),
    onSurfaceVariant = LoginTextSecondaryLight,
    outline = PillBorderLight,
)

@Composable
fun PoipikuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}
