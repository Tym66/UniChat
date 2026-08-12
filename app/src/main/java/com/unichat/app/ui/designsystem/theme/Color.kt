package com.unichat.app.ui.designsystem.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * HyperOS 新拟态设计系统色板(深浅色)。
 * 取自 android-design-system-skills / hyper-neumorphic。
 */
data class HyperColors(
    val background: Color,        // 页面底色(浅灰,舒适)
    val cardBackground: Color,    // 卡片底色(白/深)
    val pageGradientTop: Color,
    val pageGradientMid: Color,
    val pageGradientBottom: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val primary: Color,           // 品牌主色
    val primaryVariant: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val iconBackground: Color,
    val badgeBackground: Color,
    val badgeText: Color,
    val border: Color,
    val divider: Color,
)

val LightHyperColors = HyperColors(
    background = Color(0xFFF2F4F8),
    cardBackground = Color(0xFFFFFFFF),
    pageGradientTop = Color(0xFFEAF1FF),
    pageGradientMid = Color(0xFFF5F6FB),
    pageGradientBottom = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF111111),
    textSecondary = Color(0xFF6F7280),
    textTertiary = Color(0xFFB5B5C0),
    primary = Color(0xFF007AFF),
    primaryVariant = Color(0xFF0062CC),
    success = Color(0xFF32BB78),
    warning = Color(0xFFFFB300),
    error = Color(0xFFFF5252),
    iconBackground = Color(0xFFF2F4F8),
    badgeBackground = Color(0xFFE8F4FF),
    badgeText = Color(0xFF007AFF),
    border = Color(0xFFE5E5E5),
    divider = Color(0xFFF0F0F0),
)

val DarkHyperColors = HyperColors(
    background = Color(0xFF1A1B1E),
    cardBackground = Color(0xFF1E1E1E),
    pageGradientTop = Color(0xFF0D1117),
    pageGradientMid = Color(0xFF12161C),
    pageGradientBottom = Color(0xFF1A1E25),
    textPrimary = Color(0xFFF5F5F5),
    textSecondary = Color(0xFFB3B3B3),
    textTertiary = Color(0xFF808080),
    primary = Color(0xFF0A84FF),
    primaryVariant = Color(0xFF0070E0),
    success = Color(0xFF30D158),
    warning = Color(0xFFFFB74D),
    error = Color(0xFFFF8A80),
    iconBackground = Color(0xFF2A2A2A),
    badgeBackground = Color(0xFF0A2A4A),
    badgeText = Color(0xFF0A84FF),
    border = Color(0xFF2D2D2D),
    divider = Color(0xFF262626),
)

/** 当前深浅色板(CompositionLocal) */
val LocalHyperColors = staticCompositionLocalOf { LightHyperColors }

// ═══ 新拟态阴影色(浮雕) ═══
val NeumLightHighlight = Color(0xFFFFFFFF)
val NeumLightShadow = Color(0xFFD1D9E6)
val NeumDarkLight = Color(0xFF26282C)
val NeumDarkDark = Color(0xFF0D0E11)
