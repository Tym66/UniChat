package com.unichat.app.ui.theme

import androidx.compose.material3.MaterialTheme
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

@Composable
fun UniChatTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = Typography,
        content = content
    )
}
