package com.unichat.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = AccentBlue,
    onPrimary = Color.White,
    background = PureWhite,
    onBackground = InkBlack,
    surface = PureWhite,
    onSurface = InkBlack,
    surfaceVariant = SearchBarFill,
    onSurfaceVariant = GraySecondary,
    outline = GrayTertiary,
    outlineVariant = Color(0xFFEEEEEE),
    secondary = GraySecondary,
    onSecondary = Color.White,
    error = Color(0xFFE53935)
)

/** 夜间模式配色:深灰背景 + 浅色文字,保持同风格 */
private val DarkColors = darkColorScheme(
    primary = Color(0xFF4C9AFF),
    onPrimary = Color(0xFF001B36),
    background = Color(0xFF121212),
    onBackground = Color(0xFFEDEDED),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFAAAAAA),
    outline = Color(0xFF555555),
    outlineVariant = Color(0xFF333333),
    secondary = Color(0xFFAAAAAA),
    onSecondary = Color(0xFF121212),
    error = Color(0xFFEF5350)
)

@Composable
fun UniChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content
    )
}
