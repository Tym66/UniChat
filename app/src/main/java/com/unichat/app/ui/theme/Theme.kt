package com.unichat.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.unichat.app.ui.designsystem.theme.DarkHyperColors
import com.unichat.app.ui.designsystem.theme.LightHyperColors
import com.unichat.app.ui.designsystem.theme.LocalHyperColors

private val LightColors = lightColorScheme(
    primary = LightHyperColors.primary,
    onPrimary = Color.White,
    background = LightHyperColors.background,
    onBackground = LightHyperColors.textPrimary,
    surface = LightHyperColors.cardBackground,
    onSurface = LightHyperColors.textPrimary,
    surfaceVariant = Color(0xFFE9EDF3),
    onSurfaceVariant = LightHyperColors.textSecondary,
    outline = LightHyperColors.border,
    outlineVariant = LightHyperColors.divider,
    secondary = LightHyperColors.textSecondary,
    onSecondary = Color.White,
    error = LightHyperColors.error
)

/** 夜间模式配色:深灰背景 + 浅色文字,保持同风格 */
private val DarkColors = darkColorScheme(
    primary = DarkHyperColors.primary,
    onPrimary = Color(0xFF001B36),
    background = DarkHyperColors.background,
    onBackground = DarkHyperColors.textPrimary,
    surface = DarkHyperColors.cardBackground,
    onSurface = DarkHyperColors.textPrimary,
    surfaceVariant = Color(0xFF26282C),
    onSurfaceVariant = DarkHyperColors.textSecondary,
    outline = DarkHyperColors.border,
    outlineVariant = DarkHyperColors.divider,
    secondary = DarkHyperColors.textSecondary,
    onSecondary = Color(0xFF121212),
    error = DarkHyperColors.error
)

@Composable
fun UniChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val hyperColors = if (darkTheme) DarkHyperColors else LightHyperColors
    CompositionLocalProvider(LocalHyperColors provides hyperColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = Typography,
            content = content
        )
    }
}
