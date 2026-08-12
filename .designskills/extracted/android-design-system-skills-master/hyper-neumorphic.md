---
name: hyper-neumorphic
description: HyperOS/澎湃 风格新拟态+玻璃态双引擎设计系统——Android Jetpack Compose 完整 UI 库。含新拟态双色浮雕阴影（BlurMaskFilter凸/凹）、玻璃态Mesh光斑+半透叠加、一键切换引擎（LocalAppSkin）、深浅色自适应、detectTapGestures短按长按动画、触觉反馈、28dp圆角系统，以及 Button/Input/SearchBar/NavigationBar/Dialog/BottomSheet/DatePicker 等 40+ 常用控件规范。触发词：HyperOS风格、澎湃风格、新拟态、neumorphic、浮雕按钮、小米风格、玻璃态、glassmorphism、android设计系统、compose ui库、android ui kit、通用组件库、ui组件展示。
---

# Hyper-Neumorphic 设计系统

Android Jetpack Compose 完整设计系统，融合 HyperOS/澎湃 OS 设计语言与新拟态（Neumorphism）浮雕美学。

## 设计特征

- **双色浮雕阴影**：`BlurMaskFilter` 驱动，亮面高光（左上）+ 暗面投影（右下），模拟 3D 凸起/凹陷
- **深浅色自适应**：`HyperColors` 色板（Light/Dark 各 27 色），含页面渐变、卡片渐变、语义色
- **无涟漪点击**：按下 scale 0.95 → 松开弹性恢复 1.0，150ms FastOutSlowInEasing，无水波纹
- **光影联动**：按钮按下凸→凹切换，松开凹→凸恢复
- **触觉反馈**：所有交互按钮携带 `HapticFeedbackType.TextHandleMove`
- **28dp 圆角系统**：卡片 28dp → 对话框 24dp → 按钮 26dp → 输入框 16dp → 芯片 14dp
- **6 套主题色**：默认蓝/深海蓝/樱花粉/薄荷绿/薰衣草紫 + 动态取色（Android 12+）

## 使用前提

此 skill 专为 **Android Jetpack Compose 新项目** 设计。当用户要求：
- "用 HyperOS 风格做 UI"
- "做一套新拟态设计系统"
- "生成和 MR-Linker 一样的 UI 风格"
- "做小米澎湃风格的 Compose 组件库"

## 目录结构

在项目中创建以下文件（包名 `com.example.app.designsystem`，可按需调整）：

```
ui/designsystem/
├── theme/
│   ├── Color.kt          ← HyperColors 色板 + 新拟态阴影色
│   ├── Type.kt           ← Material 3 Typography
│   ├── Spacing.kt        ← 间距/圆角/图标尺寸常量
│   ├── NeumorphicModifiers.kt  ← convex/concave 阴影 Modifier
│   └── Theme.kt          ← AppTheme 根 Composable（深浅切换）
├── token/
│   ├── HyperosClick.kt   ← HyperOS 无涟漪点击 Modifier
│   └── NeumorphicInteraction.kt  ← 新拟态光影联动点击
└── component/
    ├── NeumorphicButton.kt   ← 主按钮
    ├── NeumorphicSwitch.kt   ← 开关
    ├── NeumorphicSlider.kt   ← 滑动器
    ├── NeumorphicInputField.kt ← 输入框
    └── HyperOSDialog.kt      ← 对话框
```

---

## Step 1: 依赖

`app/build.gradle.kts` 确认已有 Compose BOM + Material 3：

```kotlin
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.core:core-ktx:1.13.1")
}
```

---

## Step 2: Color.kt

```kotlin
package com.example.app.designsystem.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ═══ HyperColors 色板 ═══
data class HyperColors(
    val background: Color,
    val cardBackground: Color,
    val pageGradientTop: Color,
    val pageGradientMid: Color,
    val pageGradientBottom: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val primary: Color,
    val primaryVariant: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val rangeStart: Color,
    val rangeEnd: Color,
    val rangeAccent: Color,
    val tempStart: Color,
    val tempEnd: Color,
    val tempAccent: Color,
    val tripGradStart: Color,
    val tripGradEnd: Color,
    val healthGradStart: Color,
    val healthGradEnd: Color,
    val iconBackground: Color,
    val badgeBackground: Color,
    val badgeText: Color,
    val border: Color,
    val divider: Color,
    val liquidScreenBg: Color,
    val liquidCardBg: Color,
    val liquidEditOverlayBg: Color,
    val liquidDangerBg: Color,
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
    rangeStart = Color(0xFFD7F2DE),
    rangeEnd = Color(0xFFE8FFE8),
    rangeAccent = Color(0xFF32BB78),
    tempStart = Color(0xFFD9F0FF),
    tempEnd = Color(0xFFEAF7FF),
    tempAccent = Color(0xFF1976D2),
    tripGradStart = Color(0xFFE2EBFF),
    tripGradEnd = Color(0xFFF1F5FF),
    healthGradStart = Color(0xFFD7F2DE),
    healthGradEnd = Color(0xFFEEFBF1),
    iconBackground = Color(0xFFF2F4F8),
    badgeBackground = Color(0xFFE8F4FF),
    badgeText = Color(0xFF007AFF),
    border = Color(0xFFE5E5E5),
    divider = Color(0xFFF0F0F0),
    liquidScreenBg = Color(0xFFF2F4F8),
    liquidCardBg = Color(0xFFFFFFFF),
    liquidEditOverlayBg = Color(0xCC1F2937),
    liquidDangerBg = Color(0xFFFF4D4F),
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
    rangeStart = Color(0xFF1B3A24),
    rangeEnd = Color(0xFF223D2A),
    rangeAccent = Color(0xFF30D158),
    tempStart = Color(0xFF1A2E3D),
    tempEnd = Color(0xFF1E3344),
    tempAccent = Color(0xFF82B1FF),
    tripGradStart = Color(0xFF1A2340),
    tripGradEnd = Color(0xFF1E2845),
    healthGradStart = Color(0xFF1B3A24),
    healthGradEnd = Color(0xFF1F3D28),
    iconBackground = Color(0xFF2A2A2A),
    badgeBackground = Color(0xFF0A2A4A),
    badgeText = Color(0xFF0A84FF),
    border = Color(0xFF2D2D2D),
    divider = Color(0xFF262626),
    liquidScreenBg = Color(0xFF1A1B1E),
    liquidCardBg = Color(0xFF1A1D22),
    liquidEditOverlayBg = Color(0xCC0F1115),
    liquidDangerBg = Color(0xFFFF6B6E),
)

val LocalHyperColors = staticCompositionLocalOf { LightHyperColors }

// 新拟态阴影色
val NeumLightHighlight = Color(0xFFFFFFFF)
val NeumLightShadow = Color(0xFFD1D9E6)
val NeumDarkLight = Color(0xFF26282C)
val NeumDarkDark = Color(0xFF0D0E11)

// 小米品牌色
val MiBlue40 = Color(0xFF4C8EFF)
val MiGreen40 = Color(0xFF6DD400)
val MiBlue80 = Color(0xFFADC9FF)
val MiGreen80 = Color(0xFFB5F37F)
```

---

## Step 3: Type.kt

```kotlin
package com.example.app.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        letterSpacing = 0.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        letterSpacing = 0.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.sp
    ),
)
```

---

## Step 4: Spacing.kt

```kotlin
package com.example.app.designsystem.theme

import androidx.compose.ui.unit.dp

object AppSpacing {
    val safeHorizontal = 24.dp
    val cardGap = 12.dp
    val cardCornerRadius = 28.dp
    val pillCornerRadius = 999.dp
    val cardInnerPadding = 16.dp
    val cardInnerGap = 12.dp
    val controlButtonHeight = 48.dp
    val chipHeight = 32.dp
    val iconSizeSm = 16.dp
    val iconSizeMd = 20.dp
    val iconSizeLg = 24.dp
    val fabSize = 48.dp
}
```

---

## Step 5: NeumorphicModifiers.kt — 核心浮雕阴影引擎

```kotlin
package com.example.app.designsystem.theme

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 获取当前深浅模式下正确的浮雕阴影色 */
@Composable
fun getNeumorphicColors(): Triple<Color, Color, Color> {
    val colors = LocalHyperColors.current
    val isDark = colors === DarkHyperColors
    val bgColor = colors.liquidScreenBg
    val lightShadow = if (isDark) Color(0xFF26282C) else Color(0xFFFFFFFF)
    val darkShadow = if (isDark) Color(0xFF0D0E11) else Color(0xFFD1D9E6)
    return Triple(bgColor, lightShadow, darkShadow)
}

/**
 * 凸起面：新拟态用浮雕阴影，玻璃态用半透叠加。
 * isOverlay=true（弹窗/底部面板等独立浮层）→ 玻璃态用 glassConvexOverlay（自带 mesh）
 */
@Composable
fun Modifier.neumorphicConvex(
    cornerRadius: Dp = 24.dp,
    elevation: Dp = 6.dp,
    isOverlay: Boolean = false,
): Modifier {
    if (LocalAppSkin.current == AppSkin.GLASS) {
        return if (isOverlay) this.glassConvexOverlay(cornerRadius, LocalGlassTokens.current)
        else this.glassConvex(cornerRadius, LocalGlassTokens.current)
    }
    val (bgColor, lightShadow, darkShadow) = getNeumorphicColors()

    return this
        .clip(RoundedCornerShape(cornerRadius))
        .background(bgColor)
        .drawBehind {
            drawIntoCanvas { canvas ->
                val composePaint = Paint().apply {
                    asFrameworkPaint().apply {
                        isAntiAlias = true
                        maskFilter = BlurMaskFilter(elevation.toPx(), BlurMaskFilter.Blur.NORMAL)
                    }
                }
                // 右下暗影
                composePaint.color = darkShadow
                canvas.drawRoundRect(
                    left = elevation.toPx() * 0.5f,
                    top = elevation.toPx() * 0.5f,
                    right = size.width + elevation.toPx() * 0.5f,
                    bottom = size.height + elevation.toPx() * 0.5f,
                    radiusX = cornerRadius.toPx(),
                    radiusY = cornerRadius.toPx(),
                    paint = composePaint
                )
                // 左上高光
                composePaint.color = lightShadow
                canvas.drawRoundRect(
                    left = -elevation.toPx() * 0.5f,
                    top = -elevation.toPx() * 0.5f,
                    right = size.width - elevation.toPx() * 0.5f,
                    bottom = size.height - elevation.toPx() * 0.5f,
                    radiusX = cornerRadius.toPx(),
                    radiusY = cornerRadius.toPx(),
                    paint = composePaint
                )
            }
        }
}

/**
 * 凹陷新拟态：内阴影模拟凹陷感，isOverlay 同上。
 */
@Composable
fun Modifier.neumorphicConcave(
    cornerRadius: Dp = 24.dp,
    elevation: Dp = 4.dp,
    isOverlay: Boolean = false,
): Modifier {
    if (LocalAppSkin.current == AppSkin.GLASS) {
        return if (isOverlay) this.glassConcaveOverlay(cornerRadius, LocalGlassTokens.current)
        else this.glassConcave(cornerRadius, LocalGlassTokens.current)
    }
    val (bgColor, lightShadow, darkShadow) = getNeumorphicColors()

    return this
        .clip(RoundedCornerShape(cornerRadius))
        .background(bgColor)
        .drawWithContent {
            drawIntoCanvas { canvas ->
                val radiusPx = cornerRadius.toPx()
                val elevationPx = elevation.toPx()
                val blurFilter = BlurMaskFilter(elevationPx, BlurMaskFilter.Blur.NORMAL)

                // 左上角暗内阴影
                val darkPaint = Paint().apply {
                    color = darkShadow.copy(alpha = 0.8f)
                    asFrameworkPaint().apply {
                        isAntiAlias = true
                        maskFilter = blurFilter
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = elevationPx * 2
                    }
                }
                canvas.drawRoundRect(
                    left = -elevationPx * 0.5f,
                    top = -elevationPx * 0.5f,
                    right = size.width,
                    bottom = size.height,
                    radiusX = radiusPx,
                    radiusY = radiusPx,
                    paint = darkPaint
                )

                // 右下角亮内高光
                val lightPaint = Paint().apply {
                    color = lightShadow
                    asFrameworkPaint().apply {
                        isAntiAlias = true
                        maskFilter = blurFilter
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = elevationPx * 2
                    }
                }
                canvas.drawRoundRect(
                    left = 0f,
                    top = 0f,
                    right = size.width + elevationPx * 0.5f,
                    bottom = size.height + elevationPx * 0.5f,
                    radiusX = radiusPx,
                    radiusY = radiusPx,
                    paint = lightPaint
                )
            }
            drawContent()
        }
}

/** 合并 convex + 背景的快捷方法（同 neumorphicConvex，别名） */
@Composable
fun Modifier.neumorphic3D(cornerRadius: Dp = 28.dp, elevation: Dp = 8.dp, isOverlay: Boolean = false): Modifier =
    this.neumorphicConvex(cornerRadius, elevation, isOverlay)
```

---

## Step 6: HyperosClick.kt — 无涟漪点击动画

```kotlin
package com.example.app.designsystem.token

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput

/**
 * HyperOS 风格点击修饰符
 * 按下 scale=0.95 → 松开弹性恢复 1.0，无水波纹，150ms FastOutSlowInEasing
 */
fun Modifier.hyperosClickable(
    enabled: Boolean = true,
    onClick: () -> Unit = {}
): Modifier = composed {
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.95f else 1f,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "hyperos_scale"
    )

    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .pointerInput(enabled) {
            if (enabled) {
                detectTapGestures(
                    onPress = { isPressed = true; tryAwaitRelease(); isPressed = false },
                    onTap = { onClick() }
                )
            }
        }
}
```

---

## Step 7: NeumorphicInteraction.kt — 光影联动点击

```kotlin
package com.example.app.designsystem.token

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.app.designsystem.theme.neumorphicConcave
import com.example.app.designsystem.theme.neumorphicConvex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 统一新拟态点击：缩放 + 触觉 + 无涟漪 + 光影联动（按下凹，抬起凸）
 * 使用 detectTapGestures 替代 MutableInteractionSource，确保短按长按均有动画反馈。
 */
@Composable
fun Modifier.neumorphicClickable(
    enabled: Boolean = true,
    cornerRadius: Dp = 24.dp,
    convexElevation: Dp = 6.dp,
    concaveElevation: Dp = 4.dp,
    scalePressed: Float = 0.95f,
    durationMs: Int = 150,
    hapticType: HapticFeedbackType = HapticFeedbackType.TextHandleMove,
    onClick: () -> Unit
): Modifier {
    val haptic = LocalHapticFeedback.current
    var isPressed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val scaleAnim = remember { Animatable(1f) }

    return this
        .scale(scaleAnim.value)
        .then(
            if (isPressed && enabled) Modifier.neumorphicConcave(cornerRadius, concaveElevation)
            else Modifier.neumorphicConvex(cornerRadius, convexElevation)
        )
        .pointerInput(enabled) {
            if (enabled) detectTapGestures(
                onPress = {
                    isPressed = true
                    scope.launch { scaleAnim.snapTo(scalePressed) }
                    tryAwaitRelease()
                    isPressed = false
                    scope.launch { delay(60); scaleAnim.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing)) }
                },
                onTap = { haptic.performHapticFeedback(hapticType); onClick() }
            )
        }
}

/**
 * 简化版：仅缩放 + 触觉，不改变阴影状态（用于已有自己样式的元素如选中态芯片）
 */
@Composable
fun Modifier.neumorphicTap(
    enabled: Boolean = true,
    scalePressed: Float = 0.95f,
    durationMs: Int = 150,
    hapticType: HapticFeedbackType = HapticFeedbackType.TextHandleMove,
    onClick: () -> Unit
): Modifier {
    val haptic = LocalHapticFeedback.current
    var isPressed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val scaleAnim = remember { Animatable(1f) }

    return this
        .scale(scaleAnim.value)
        .pointerInput(enabled) {
            if (enabled) detectTapGestures(
                onPress = {
                    isPressed = true
                    scope.launch { scaleAnim.snapTo(scalePressed) }
                    tryAwaitRelease()
                    isPressed = false
                    scope.launch { delay(60); scaleAnim.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing)) }
                },
                onTap = { haptic.performHapticFeedback(hapticType); onClick() }
            )
        }
}
```

---

## Step 8: NeumorphicButton.kt

```kotlin
package com.example.app.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.app.designsystem.theme.DarkHyperColors
import com.example.app.designsystem.theme.LocalHyperColors
import com.example.app.designsystem.theme.neumorphicConcave
import com.example.app.designsystem.theme.neumorphicConvex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 通用新拟态按钮
 * - 默认态：凸起（convex）
 * - 按下态：凹陷（concave）+ scale 0.95
 * - 禁用态：扁平凹陷（elevation=1.5dp），文字 30% 透明度
 * - 加载态：替换文字为 CircularProgressIndicator
 * - 使用 detectTapGestures 确保短按动画即时响应
 */
@Composable
fun NeumorphicButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isPrimary: Boolean = true,
    isLoading: Boolean = false,
    height: Dp = 52.dp,
    cornerRadius: Dp = 26.dp,
    icon: (@Composable () -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    val colors = LocalHyperColors.current
    val isDark = colors === DarkHyperColors

    // 使用 pointerInput + detectTapGestures 确保短按也有动画
    var isPressed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val scaleAnim = remember { Animatable(1f) }

    fun pressDown() { isPressed = true; scope.launch { scaleAnim.snapTo(0.95f) } }
    fun pressUp() { isPressed = false; scope.launch { delay(60); scaleAnim.animateTo(1f, tween(200, easing = FastOutSlowInEasing)) } }

    val textColor = when {
        !enabled -> (if (isDark) Color.White else Color.Black).copy(alpha = 0.3f)
        isPrimary -> colors.primary
        else -> colors.textSecondary
    }
    val tintColor = textColor.copy(alpha = 0.03f)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(height)
            .scale(scaleAnim.value)
            .then(
                when {
                    !enabled -> Modifier.neumorphicConcave(cornerRadius, elevation = 1.5.dp)
                    isPressed -> Modifier.neumorphicConcave(cornerRadius)
                    else -> Modifier.neumorphicConvex(cornerRadius)
                }
            )
            .clip(RoundedCornerShape(cornerRadius))
            .background(tintColor)
            .pointerInput(enabled) {
                if (enabled && !isLoading) detectTapGestures(
                    onPress = { pressDown(); tryAwaitRelease(); pressUp() },
                    onTap = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() }
                )
            }
            .padding(horizontal = 24.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = colors.primary,
                strokeWidth = 2.dp
            )
        } else if (icon != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon()
                Spacer(Modifier.width(6.dp))
                Text(
                    text = text,
                    color = textColor,
                    fontSize = 16.sp,
                    fontWeight = if (isPrimary) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        } else {
            Text(
                text = text,
                color = textColor,
                fontSize = 16.sp,
                fontWeight = if (isPrimary) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}
```

---

## Step 9: NeumorphicSwitch.kt

```kotlin
package com.example.app.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.example.app.designsystem.theme.DarkHyperColors
import com.example.app.designsystem.theme.LocalHyperColors
import com.example.app.designsystem.theme.neumorphicConcave
import com.example.app.designsystem.theme.neumorphicConvex

/**
 * 新拟态开关
 * 轨道：凹陷槽（52×28dp，圆角14dp）
 * 滑块：凸起圆球（20dp），offset 4dp↔24dp 动画 250ms
 * 激活色：深色=primary，浅色=success
 */
@Composable
fun NeumorphicSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val colors = LocalHyperColors.current
    val isDark = colors === DarkHyperColors

    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 24.dp else 4.dp,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "switch_thumb"
    )

    val trackColor by animateColorAsState(
        targetValue = if (checked) {
            if (isDark) colors.primary else colors.success
        } else {
            Color.Transparent
        },
        animationSpec = tween(250),
        label = "switch_track"
    )

    Box(
        modifier = modifier
            .width(52.dp)
            .height(28.dp)
            .neumorphicConcave(cornerRadius = 14.dp, elevation = 1.5.dp)
            .background(trackColor, RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onCheckedChange(!checked)
                }
            )
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .align(Alignment.CenterStart)
                .size(20.dp)
                .neumorphicConvex(cornerRadius = 10.dp, elevation = 1.5.dp)
        )
    }
}
```

---

## Step 10: NeumorphicSlider.kt

```kotlin
package com.example.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.app.designsystem.theme.LocalHyperColors
import com.example.app.designsystem.theme.neumorphicConcave
import com.example.app.designsystem.theme.neumorphicConvex

/**
 * 新拟态滑动器
 * 轨道：凹陷槽（14dp高），激活段着色，支持刻度线标记
 * 滑块：32dp 凸起圆球 + 8dp 内色点
 */
@Composable
fun NeumorphicSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    activeColor: Color,
    limitValue: Float? = null,
    modifier: Modifier = Modifier,
) {
    val range = valueRange.endInclusive - valueRange.start
    val fraction = ((value - valueRange.start) / range).coerceIn(0f, 1f)
    val currentOnValueChange by rememberUpdatedState(onValueChange)

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth().height(32.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val thumbRadiusDp = 16.dp
        val thumbRadiusPx = with(LocalDensity.current) { thumbRadiusDp.toPx() }
        val maxDragPx = widthPx - thumbRadiusPx * 2

        fun updateValueFromPx(px: Float) {
            val newFraction = ((px - thumbRadiusPx) / maxDragPx).coerceIn(0f, 1f)
            currentOnValueChange(valueRange.start + newFraction * range)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(constraints.maxWidth) {
                    detectDragGestures { change, _ -> updateValueFromPx(change.position.x) }
                }
                .pointerInput(constraints.maxWidth) {
                    detectTapGestures { offset -> updateValueFromPx(offset.x) }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            // 轨道
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .neumorphicConcave(cornerRadius = 7.dp, elevation = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = fraction)
                        .background(activeColor.copy(alpha = 0.8f), RoundedCornerShape(7.dp))
                )
            }

            // 刻度线
            if (limitValue != null) {
                val limitFraction = ((limitValue - valueRange.start) / range).coerceIn(0f, 1f)
                val limitPx = limitFraction * maxDragPx + thumbRadiusPx
                val limitDp = with(LocalDensity.current) { limitPx.toDp() }
                Box(
                    modifier = Modifier
                        .offset(x = limitDp - 1.5.dp)
                        .width(3.dp).height(24.dp)
                        .background(
                            LocalHyperColors.current.textPrimary.copy(alpha = 0.3f),
                            RoundedCornerShape(1.5.dp)
                        )
                )
            }

            // 滑块
            val thumbOffsetPx = fraction * maxDragPx
            val thumbOffsetDp = with(LocalDensity.current) { thumbOffsetPx.toDp() }
            Box(
                modifier = Modifier
                    .offset(x = thumbOffsetDp)
                    .size(thumbRadiusDp * 2)
                    .neumorphicConvex(cornerRadius = thumbRadiusDp, elevation = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.size(8.dp).background(activeColor, CircleShape))
            }
        }
    }
}
```

---

## Step 11: NeumorphicInputField.kt

```kotlin
package com.example.app.designsystem.component

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.app.designsystem.theme.LocalHyperColors
import com.example.app.designsystem.theme.neumorphicConcave

/**
 * 新拟态输入框
 * 凹陷槽（56dp高，16dp圆角），聚焦时 primary 色描边
 */
@Composable
fun NeumorphicInputField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    isFocused: Boolean = false,
) {
    val colors = LocalHyperColors.current

    Box(
        modifier = modifier
            .height(56.dp)
            .fillMaxWidth()
            .neumorphicConcave(cornerRadius = 16.dp, elevation = 2.dp)
            .then(
                if (isFocused) Modifier.border(1.dp, colors.primary, RoundedCornerShape(16.dp))
                else Modifier
            )
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (leadingIcon != null) leadingIcon()
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                textStyle = LocalTextStyle.current.copy(
                    color = colors.textPrimary,
                    fontSize = 16.sp
                ),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = colors.textSecondary,
                            fontSize = 16.sp
                        )
                    }
                    inner()
                }
            )
            if (trailingContent != null) trailingContent()
        }
    }
}
```

---

## Step 12: HyperOSDialog.kt

```kotlin
package com.example.app.designsystem.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.app.designsystem.theme.neumorphic3D

/**
 * HyperOS 风格对话框
 * 320dp 宽，24dp 圆角，8dp 最高浮雕 elevation
 */
@Composable
fun HyperOSDialog(
    title: String,
    message: String,
    confirmText: String,
    cancelText: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .width(320.dp)
                .wrapContentHeight()
                .neumorphic3D(cornerRadius = 24.dp, elevation = 8.dp, isOverlay = true)  // 弹窗用 isOverlay，玻璃态自带 mesh
        ) {
            Column(
                modifier = Modifier.padding(top = 32.dp, bottom = 24.dp, start = 24.dp, end = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = message,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))
                Row(
                    modifier = Modifier.width(272.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    NeumorphicButton(
                        text = cancelText,
                        onClick = onCancel,
                        isPrimary = false,
                        modifier = Modifier.weight(1f)
                    )
                    NeumorphicButton(
                        text = confirmText,
                        onClick = onConfirm,
                        isPrimary = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
```

---

## Step 13: Theme.kt — 根主题

```kotlin
package com.example.app.designsystem.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(LocalContext.current)
        else dynamicLightColorScheme(LocalContext.current)
    } else {
        if (darkTheme) darkColorScheme(primary = MiBlue80, secondary = MiGreen80)
        else lightColorScheme(primary = MiBlue40, secondary = MiGreen40)
    }

    val hyperColors = if (darkTheme) DarkHyperColors else LightHyperColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalHyperColors provides hyperColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content
        )
    }
}
```

---

## 使用示例

```kotlin
// MainActivity.kt
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = LocalHyperColors.current.background
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 凸起卡片
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .neumorphicConvex(cornerRadius = 28.dp, elevation = 6.dp)
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("新拟态卡片", style = MaterialTheme.typography.titleLarge)
                        }

                        // 主按钮
                        NeumorphicButton(text = "确认", onClick = { /* ... */ })

                        // 开关
                        var checked by remember { mutableStateOf(false) }
                        NeumorphicSwitch(checked = checked, onCheckedChange = { checked = it })

                        // 输入框
                        var text by remember { mutableStateOf("") }
                        NeumorphicInputField(value = text, onValueChange = { text = it }, placeholder = "请输入")
                    }
                }
            }
        }
    }
}
```

---

## 定制选项

### 圆角层级

| 元素 | 圆角 | elevation |
|------|------|-----------|
| 页面卡片 | 28dp | 6dp |
| 对话框 | 24dp | 8dp |
| 按钮 | 26dp | 6dp (默认) / 4dp (按下) |
| 输入框 | 16dp | 2dp |
| 开关轨道 | 14dp | 1.5dp |
| 芯片/Chip | 14dp | 3dp |
| 图标按钮 | 18dp | 3dp |

### 动画参数

| 动画 | 时长 | 曲线 |
|------|------|------|
| 按钮按下/释放 | 150ms/200ms | FastOutSlowInEasing |
| 开关滑动 | 250ms | FastOutSlowInEasing |
| 交错入场 (每个元素) | 1000ms | FastOutSlowInEasing |

### 交错入场动画（可选）

```kotlin
fun Modifier.staggeredEntrance(visible: Boolean, delayMillis: Int): Modifier = composed {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(1000, delayMillis, FastOutSlowInEasing),
        label = "entrance_alpha"
    )
    val offsetY by animateFloatAsState(
        targetValue = if (visible) 0f else 60f,
        animationSpec = tween(1000, delayMillis, FastOutSlowInEasing),
        label = "entrance_offset"
    )
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.92f,
        animationSpec = tween(1000, delayMillis, FastOutSlowInEasing),
        label = "entrance_scale"
    )
    this
        .graphicsLayer { this.alpha = alpha; translationY = offsetY; scaleX = scale; scaleY = scale }
}
// 用法：Column 中每个子元素传入递进的 delayMillis（0, 150, 250, 350, 450...）
```

---

## Step 14: GlassTokens.kt — 玻璃态令牌

```kotlin
package com.example.app.designsystem.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class MeshSpot(val xFraction: Float, val yFraction: Float, val color: Color, val radiusFraction: Float)

data class GlassTokens(
    val meshBase: Color, val meshSpots: List<MeshSpot>,
    val tintConvex: Color, val tintConcave: Color,
    val borderHi: Color, val borderLo: Color,
    val innerHighlight: Color, val outerShadow: Color,
    val textPrimary: Color, val textSecondary: Color, val textTertiary: Color,
)

val DarkGlassTokens = GlassTokens(
    meshBase = Color(0xFF0F1320),
    meshSpots = listOf(
        MeshSpot(0.20f, 0.25f, Color(0x8C4A6EAA), 0.55f), MeshSpot(0.84f, 0.16f, Color(0x73786096), 0.52f),
        MeshSpot(0.30f, 0.90f, Color(0x663C7878), 0.55f), MeshSpot(0.90f, 0.82f, Color(0x6B5A548C), 0.55f),
    ),
    tintConvex = Color(0x1FFFFFFF), tintConcave = Color(0x24000000),
    borderHi = Color(0x8CFFFFFF), borderLo = Color(0x1FFFFFFF),
    innerHighlight = Color(0x73FFFFFF), outerShadow = Color(0x47000000),
    textPrimary = Color(0xFFFFFFFF), textSecondary = Color(0xC7FFFFFF), textTertiary = Color(0x8CFFFFFF),
)

val LightGlassTokens = GlassTokens(
    meshBase = Color(0xFFF3EFEA),
    meshSpots = listOf(
        MeshSpot(0.18f, 0.22f, Color(0x735AA0F0), 0.62f), MeshSpot(0.86f, 0.15f, Color(0x669C8AE6), 0.60f),
        MeshSpot(0.28f, 0.90f, Color(0x6B4FB8C9), 0.62f), MeshSpot(0.90f, 0.84f, Color(0x617E86E0), 0.60f),
        MeshSpot(0.52f, 0.46f, Color(0x4F7FC0E8), 0.82f),
    ),
    tintConvex = Color(0x2EFFFFFF), tintConcave = Color(0x12000000),
    borderHi = Color(0xCCFFFFFF), borderLo = Color(0x1F000000),
    innerHighlight = Color(0x80FFFFFF), outerShadow = Color(0x33737D99),
    textPrimary = Color(0xFF1A1B1E), textSecondary = Color(0xB31A1B1E), textTertiary = Color(0x801A1B1E),
)

val LocalGlassTokens = staticCompositionLocalOf { DarkGlassTokens }
enum class AppSkin { NEUMORPHISM, GLASS }
val LocalAppSkin = staticCompositionLocalOf { AppSkin.NEUMORPHISM }
fun glassTokensFor(isDark: Boolean) = if (isDark) DarkGlassTokens else LightGlassTokens
```

## Step 15: GlassSurface.kt — 全局 mesh 背景 + 玻璃面半透明叠加

> **关键设计**：全局只有 `GlassMeshBackground` 绘制 mesh 光斑。`glassConvex/Concave` 不再重复绘制 mesh，只做半透明 tint 叠加——底下的全局 mesh 自然透出，形成统一的玻璃质感。

```kotlin
package com.example.app.designsystem.theme

import androidx.compose.foundation.layout.Box; import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable; import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip; import androidx.compose.ui.draw.drawBehind; import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius; import androidx.compose.ui.geometry.Offset; import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush; import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope; import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity; import androidx.compose.ui.unit.Dp; import androidx.compose.ui.unit.dp

private fun DrawScope.drawMesh(tokens: GlassTokens, fullSize: Size) {
    drawRect(tokens.meshBase)
    val minDim = minOf(fullSize.width, fullSize.height)
    for (spot in tokens.meshSpots) {
        val c = Offset(spot.xFraction * fullSize.width, spot.yFraction * fullSize.height)
        drawRect(Brush.radialGradient(listOf(spot.color, Color.Transparent), center = c, radius = (spot.radiusFraction * minDim).coerceAtLeast(1f)))
    }
}

@Composable
fun GlassMeshBackground(tokens: GlassTokens, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().drawBehind { drawMesh(tokens, fullSize = size) })
}

// glassConvex: 只做半透叠加 + 渐变描边 + 顶部高光 + 外阴影，不重复画 mesh
@Composable
fun Modifier.glassConvex(cornerRadius: Dp, tokens: GlassTokens = LocalGlassTokens.current): Modifier {
    val rp = with(LocalDensity.current) { cornerRadius.toPx() }
    return this
        .drawBehind { drawRoundRect(tokens.outerShadow, Offset(0f, 2.dp.toPx()), size, CornerRadius(rp, rp)) }
        .clip(RoundedCornerShape(cornerRadius))
        .drawBehind { drawRect(tokens.tintConvex) }
        .drawWithContent { drawContent(); drawRect(tokens.innerHighlight, Size(size.width, 1.dp.toPx())) }
        .drawBehind { drawRoundRect(Brush.linearGradient(listOf(tokens.borderHi, tokens.borderLo)), CornerRadius(rp, rp), style = Stroke(1.dp.toPx())) }
}

// glassConcave: 半透暗色叠加 + 渐变描边
@Composable
fun Modifier.glassConcave(cornerRadius: Dp, tokens: GlassTokens = LocalGlassTokens.current): Modifier {
    val rp = with(LocalDensity.current) { cornerRadius.toPx() }
    return this
        .clip(RoundedCornerShape(cornerRadius))
        .drawBehind { drawRect(tokens.tintConcave) }
        .drawBehind { drawRoundRect(brush = Brush.linearGradient(listOf(tokens.borderHi, tokens.borderLo)), cornerRadius = CornerRadius(rp, rp), style = Stroke(1.dp.toPx())) }
}

/** 玻璃凸起面（弹窗/浮层用）：自带 mesh，不透明 */
@Composable
fun Modifier.glassConvexOverlay(cornerRadius: Dp, tokens: GlassTokens = LocalGlassTokens.current): Modifier {
    var o by remember { mutableStateOf(Offset.Zero) }
    val d = LocalDensity.current; val cfg = LocalConfiguration.current
    val fs = with(d) { Size(cfg.screenWidthDp.dp.toPx(), cfg.screenHeightDp.dp.toPx()) }
    val rp = with(d) { cornerRadius.toPx() }
    return this
        .onGloballyPositioned { o = it.positionInWindow() }
        .drawBehind { drawRoundRect(color = tokens.outerShadow, topLeft = Offset(0f, 2.dp.toPx()), size = size, cornerRadius = CornerRadius(rp, rp)) }
        .clip(RoundedCornerShape(cornerRadius))
        .drawBehind { drawMesh(tokens, fs, o); drawRect(tokens.tintConvex) }
        .drawWithContent { drawContent(); drawRect(color = tokens.innerHighlight, size = Size(size.width, 1.dp.toPx())) }
        .drawBehind { drawRoundRect(brush = Brush.linearGradient(listOf(tokens.borderHi, tokens.borderLo)), cornerRadius = CornerRadius(rp, rp), style = Stroke(1.dp.toPx())) }
}

/** 玻璃凹陷面（弹窗/浮层用）：自带 mesh */
@Composable
fun Modifier.glassConcaveOverlay(cornerRadius: Dp, tokens: GlassTokens = LocalGlassTokens.current): Modifier {
    var o by remember { mutableStateOf(Offset.Zero) }
    val d = LocalDensity.current; val cfg = LocalConfiguration.current
    val fs = with(d) { Size(cfg.screenWidthDp.dp.toPx(), cfg.screenHeightDp.dp.toPx()) }
    val rp = with(d) { cornerRadius.toPx() }
    return this
        .onGloballyPositioned { o = it.positionInWindow() }
        .clip(RoundedCornerShape(cornerRadius))
        .drawBehind { drawMesh(tokens, fs, o); drawRect(tokens.tintConcave) }
        .drawBehind { drawRoundRect(brush = Brush.linearGradient(listOf(tokens.borderHi, tokens.borderLo)), cornerRadius = CornerRadius(rp, rp), style = Stroke(1.dp.toPx())) }
}
```

## Step 16: 双引擎切换

Theme.kt 更新为同时提供 `LocalAppSkin` + `LocalGlassTokens`，并在 GLASS 时铺 mesh 背景：

```kotlin
@Composable
fun AppTheme(darkTheme: Boolean = isSystemInDarkTheme(), skin: AppSkin = AppSkin.NEUMORPHISM, content: @Composable () -> Unit) {
    // ... (同 Step 13 的 colorScheme / hyperColors / 透明状态栏逻辑)
    val glassTokens = glassTokensFor(darkTheme)

    CompositionLocalProvider(
        LocalHyperColors provides hyperColors,
        LocalGlassTokens provides glassTokens,
        LocalAppSkin provides skin,
    ) {
        if (skin == AppSkin.GLASS) {
            Box(modifier = Modifier.fillMaxSize()) {
                GlassMeshBackground(glassTokens)
                MaterialTheme(colorScheme = colorScheme, typography = AppTypography, content = { content() })
            }
        } else {
            MaterialTheme(colorScheme = colorScheme, typography = AppTypography, content = content)
        }
    }
}
```

`NeumorphicModifiers.kt` 中的 `neumorphicConvex/Concave` 已在 Step 5 添加了 `if (LocalAppSkin.current == AppSkin.GLASS)` 分支，自动路由到 `glassConvex/glassConcave`。所有组件无需修改即可同时支持两种皮肤。

**切换用法**：
```kotlin
var skin by remember { mutableStateOf(AppSkin.NEUMORPHISM) }
AppTheme(skin = skin) {
    Column {
        NeumorphicSwitch(checked = skin == AppSkin.GLASS, onCheckedChange = {
            skin = if (skin == AppSkin.NEUMORPHISM) AppSkin.GLASS else AppSkin.NEUMORPHISM
        })
        NeumorphicButton("按钮自动适配当前皮肤", onClick = {})
        // 所有组件通过 neumorphicConvex/Concave 自动适配
    }
}
```

**`isOverlay` 使用规则**：
- `isOverlay = false`（默认）：页面内组件（按钮、卡片、输入框等）——玻璃态下半透叠加，全局 mesh 透出
- `isOverlay = true`：独立浮层（Dialog、BottomSheet 等）——玻璃态下自带完整 mesh，不透明
- 新拟态模式下 `isOverlay` 无影响，两种模式渲染相同

**触觉反馈**：所有可交互组件必须携带触觉反馈。`NeumorphicButton`/`FAB`/`Checkbox`/`RadioButton`/`Slider`/`Chip`/`Tabs`/`ListItem` 使用 `TextHandleMove`，`NeumorphicSwitch` 使用 `LongPress`。实现方式：`detectTapGestures.onTap` 中调用 `haptic.performHapticFeedback()`。

---

## 扩展组件库

以下组件均基于 `neumorphicConvex/Concave` 和 `neumorphicTap`，自动适配新拟态/玻璃态双皮肤。生成真实页面时优先补齐本节控件，不要只提供 Button/Card/Input 的最小集合。

### 控件命名与通用 API

| 类型 | 命名 | 必备参数 |
|------|------|----------|
| 基础操作 | `NeumorphicButton` / `NeumorphicIconButton` / `NeumorphicFAB` | `onClick`、`enabled`、`modifier`、`content` 或 `text` |
| 选择控件 | `NeumorphicCheckbox` / `NeumorphicRadioButton` / `NeumorphicSwitch` | `checked/selected`、`onCheckedChange/onClick`、`label` |
| 输入控件 | `NeumorphicInputField` / `NeumorphicPasswordField` / `NeumorphicSearchBar` / `NeumorphicTextArea` | `value`、`onValueChange`、`placeholder`、`enabled`、`isError` |
| 数值控件 | `NeumorphicSlider` / `NeumorphicRangeSlider` / `NeumorphicStepper` / `NeumorphicRatingBar` | `value`、`valueRange`、`onValueChange` |
| 导航控件 | `NeumorphicTopAppBar` / `NeumorphicTabs` / `NeumorphicNavigationBar` / `NeumorphicNavigationRail` | `selectedIndex`、`onSelect`、`items` |
| 反馈控件 | `HyperOSDialog` / `NeumorphicBottomSheet` / `NeumorphicSnackbar` / `NeumorphicProgress` | `visible`、`onDismiss`、`state/progress` |
| 数据展示 | `NeumorphicCard` / `NeumorphicListItem` / `NeumorphicGridTile` / `NeumorphicStatisticCard` | `content`、`leading`、`trailing`、`onClick` |

通用要求：
- 所有可点控件必须带 `enabled`，禁用态使用 `alpha(0.48f)`，并阻止触觉反馈和点击回调。
- 所有图标按钮必须提供 44dp 以上点击目标；视觉尺寸可为 36-40dp，但外层 `minimumInteractiveComponentSize()` 或等价 padding 不可省。
- 所有输入类控件必须支持 `isError`、`supportingText`、`leadingIcon`、`trailingIcon`，错误态只改变文字/描边/辅助色，不破坏凹陷面。
- 列表、导航、Chip、Tab 的选中态统一使用凹陷或内嵌高亮；默认态使用轻凸起或透明承载面。
- 玻璃态浮层控件必须传 `isOverlay = true`，例如 Dialog、BottomSheet、Dropdown、Snackbar。

### 常用控件覆盖矩阵

| 分类 | 必做控件 | 视觉规则 |
|------|----------|----------|
| 操作 | Button、OutlinedButton、TextButton、IconButton、FAB、SplitButton | 主操作凸起，按下凹陷；次操作降低 alpha，不额外加 Material elevation |
| 输入 | InputField、PasswordField、SearchBar、TextArea | 默认凹陷，聚焦时增加 1dp accent 内描边或柔光 |
| 数值 | Slider、RangeSlider、Stepper、RatingBar | 轨道凹陷，thumb 凸起；拖动开始触觉反馈 |
| 选择 | Checkbox、RadioButton、Switch、Chip、DropdownMenu、SegmentedControl | 选中态凹陷或 accent 填充，未选中态轻凸起 |
| 导航 | TopAppBar、Tabs、NavigationBar、NavigationRail、Breadcrumb、PagerIndicator | 当前项凸起/凹陷明确，未选中只用文字色区分 |
| 展示 | Card、ListItem、GridTile、Avatar、Badge、Tag、StatisticCard、Timeline | 容器少嵌套；小徽标用 accent 低透明背景 |
| 反馈 | Dialog、BottomSheet、Snackbar、ToastHost、Progress、Spinner、Skeleton、EmptyState、ErrorState | 浮层独立承载，loading 使用低对比 shimmer |
| 容器 | Section、SettingsGroup、ExpandablePanel、Carousel、PullRefreshContainer | Section 不要做卡片套卡片，组内条目用轻分隔或间距 |

### Checkbox
```kotlin
@Composable
fun NeumorphicCheckbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String = "", modifier: Modifier = Modifier) {
    val colors = LocalHyperColors.current
    val bg by animateColorAsState(if (checked) colors.primary else Color.Transparent, tween(200), label = "cb")
    Row(modifier.neumorphicTap(onClick = { onCheckedChange(!checked) }), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(24.dp).then(if (checked) neumorphicConcave(6.dp, 2.dp) else neumorphicConvex(6.dp, 2.dp)).background(bg, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
            if (checked) Text("✓", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.background)
        }
        if (label.isNotEmpty()) { Spacer(Modifier.width(10.dp)); Text(label, fontSize = 15.sp, color = colors.textPrimary) }
    }
}
```

### RadioButton + RadioGroup
```kotlin
@Composable
fun NeumorphicRadioButton(selected: Boolean, onClick: () -> Unit, label: String = "") { /* 22dp 圆, 选中时Center 10dp 色点 */ }
@Composable
fun NeumorphicRadioGroup(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) { /* 垂直排列 RadioButton */ }
```

### IconButton / ToggleButton / SegmentedControl
```kotlin
@Composable
fun NeumorphicIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, selected: Boolean = false, content: @Composable BoxScope.() -> Unit) { /* 44dp 点击目标, selected/pressed 用 concave */ }

@Composable
fun NeumorphicToggleButton(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String, leadingIcon: (@Composable () -> Unit)? = null) { /* checked 凹陷 + accent 文本 */ }

@Composable
fun NeumorphicSegmentedControl(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) { /* 外层凹陷胶囊, 选中段凸起滑块 */ }
```

### Progress (Linear / Circular / Spinner)
```kotlin
@Composable
fun NeumorphicLinearProgress(progress: Float, color: Color, trackHeight: Dp = 8.dp, showLabel: Boolean = true) { /* 凹陷轨道 + animateFloatAsState 填充 */ }
@Composable
fun NeumorphicCircularProgress(progress: Float, size: Dp = 64.dp, color: Color, strokeWidth: Dp = 6.dp) { /* 圆形凸起容器 + 中心百分比文字 */ }
@Composable
fun NeumorphicLoadingSpinner(size: Dp = 40.dp, color: Color) { /* 凸起圆形 + Material CircularProgressIndicator */ }
```

### Chip + ChipGroup
```kotlin
@Composable
fun NeumorphicChip(label: String, selected: Boolean, onClick: () -> Unit, leadingIcon: (@Composable () -> Unit)? = null) { /* 选中凸起, 未选中平坦 + neumorphicTap */ }
@Composable
fun NeumorphicChipGroup(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) { /* 水平排列 Chip */ }
```

### SearchBar / PasswordField / TextArea
```kotlin
@Composable
fun NeumorphicSearchBar(query: String, onQueryChange: (String) -> Unit, placeholder: String = "Search", onClear: (() -> Unit)? = null) { /* leading 搜索图标 + trailing 清除按钮 + 凹陷输入面 */ }

@Composable
fun NeumorphicPasswordField(value: String, onValueChange: (String) -> Unit, visible: Boolean, onVisibilityChange: (Boolean) -> Unit, isError: Boolean = false) { /* trailing 眼睛 IconButton, 支持 PasswordVisualTransformation */ }

@Composable
fun NeumorphicTextArea(value: String, onValueChange: (String) -> Unit, minLines: Int = 3, maxLines: Int = 6, supportingText: String? = null) { /* 高度自适应, 凹陷容器, supportingText 放外部 */ }
```

### Stepper / RangeSlider / RatingBar
```kotlin
@Composable
fun NeumorphicStepper(value: Int, onValueChange: (Int) -> Unit, range: IntRange, step: Int = 1, label: String? = null) { /* 减号 IconButton + 数值凹陷读数 + 加号 IconButton */ }

@Composable
fun NeumorphicRangeSlider(start: Float, end: Float, valueRange: ClosedFloatingPointRange<Float>, onValueChange: (Float, Float) -> Unit) { /* 双 thumb, active 区间 accent 填充 */ }

@Composable
fun NeumorphicRatingBar(value: Int, onValueChange: (Int) -> Unit, max: Int = 5) { /* IconButton 横排, 选中 accent, 支持半星时改 Float */ }
```

### Tabs
```kotlin
@Composable
fun NeumorphicTabs(tabs: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) { /* 整体凹陷容器, 选中Tab凸起 + neumorphicTap */ }
```

### TopAppBar / NavigationBar / NavigationRail
```kotlin
data class NeumorphicNavItem(val label: String, val icon: @Composable () -> Unit, val badge: String? = null)

@Composable
fun NeumorphicTopAppBar(title: String, navigationIcon: (@Composable () -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) { /* 透明背景 + 底部轻阴影或浮雕分隔 */ }

@Composable
fun NeumorphicNavigationBar(items: List<NeumorphicNavItem>, selectedIndex: Int, onSelect: (Int) -> Unit) { /* 底部凸起容器, selected item 凹陷胶囊 */ }

@Composable
fun NeumorphicNavigationRail(items: List<NeumorphicNavItem>, selectedIndex: Int, onSelect: (Int) -> Unit) { /* 宽 72dp, 垂直选项, selected 凹陷圆角 */ }
```

### ListItem / Divider
```kotlin
@Composable
fun NeumorphicListItem(headline: String, supporting: String?, leading/trailing slots, onClick: (() -> Unit)?) { /* Row + neumorphicTap */ }
@Composable
fun NeumorphicDivider() { /* 1dp divider 色横线, 两侧 16dp padding */ }
```

### DropdownMenu / Tooltip / Snackbar
```kotlin
@Composable
fun NeumorphicDropdownMenu(expanded: Boolean, onDismiss: () -> Unit, items: List<String>, onSelect: (Int) -> Unit) { /* Popup/Dialog 浮层, 玻璃态传 isOverlay=true */ }

@Composable
fun NeumorphicTooltip(visible: Boolean, text: String, anchor: @Composable () -> Unit) { /* 小型凸起浮层, 12dp 圆角, 不抢主层级 */ }

@Composable
fun NeumorphicSnackbar(message: String, actionText: String? = null, onAction: (() -> Unit)? = null, onDismiss: () -> Unit) { /* 底部浮层, 凸起容器 + 可选 action */ }
```

### Avatar / Badge
```kotlin
@Composable
fun NeumorphicAvatar(content: @Composable () -> Unit, size: Dp = 44.dp) { /* 圆形 neumorphicConvex 容器 */ }
@Composable
fun NeumorphicBadge(text: String, color: Color) { /* 10dp 圆角凸起 + 12% color 背景 + color 文字 */ }
```

### StatisticCard / Timeline / GridTile
```kotlin
@Composable
fun NeumorphicStatisticCard(title: String, value: String, delta: String? = null, icon: (@Composable () -> Unit)? = null) { /* 数值大字 + 辅助趋势, 用于 dashboard */ }

@Composable
fun NeumorphicTimeline(items: List<TimelineItem>) { /* 左侧圆点凸起 + 竖线, 右侧内容卡片 */ }

@Composable
fun NeumorphicGridTile(title: String, subtitle: String? = null, icon: (@Composable () -> Unit)? = null, onClick: (() -> Unit)? = null) { /* 固定 aspectRatio, 避免网格跳动 */ }
```

### EmptyState
```kotlin
@Composable
fun NeumorphicEmptyState(icon: String = "📭", title: String, subtitle: String, action: (@Composable () -> Unit)?) { /* 居中 Column: 72dp 图标圆 + 标题 + 描述 + 操作 */ }

@Composable
fun NeumorphicErrorState(title: String, subtitle: String, retryText: String = "重试", onRetry: (() -> Unit)? = null) { /* 错误图标圆 + 文案 + 可选重试按钮 */ }
```

### Skeleton (骨架屏)
```kotlin
@Composable
fun NeumorphicSkeleton(width: Dp, height: Dp, cornerRadius: Dp) { /* rememberInfiniteTransition + shimmer 渐变 */ }
@Composable
fun NeumorphicSkeletonList(lines: Int) { /* 头像圆 + 文字条的骨架列表 */ }
```

### FAB (浮动按钮)
```kotlin
@Composable
fun NeumorphicFAB(onClick: () -> Unit, size: Dp = 56.dp, content: @Composable () -> Unit) { /* 圆形凸起, 4dp elevation */ }
```

### BottomSheet (底部面板)
```kotlin
@Composable
fun NeumorphicBottomSheet(visible: Boolean, onDismiss: () -> Unit, title: String, content: @Composable ColumnScope.() -> Unit) { /* Dialog + wrapContentHeight + neumorphicConvex + 拖拽指示条 */ }
```

### DatePicker / TimePicker
```kotlin
@Composable
fun NeumorphicDatePicker(selectedDateMillis: Long?, onDateSelected: (Long) -> Unit, onDismiss: () -> Unit) { /* 月份导航 IconButton + 日期网格, selected 日期凹陷 */ }

@Composable
fun NeumorphicTimePicker(hour: Int, minute: Int, onTimeChange: (Int, Int) -> Unit, onDismiss: () -> Unit) { /* 小时/分钟 Stepper 或表盘, 数字项保持 44dp 点击目标 */ }
```

### PullRefresh / Carousel / ExpandablePanel
```kotlin
@Composable
fun NeumorphicPullRefreshContainer(isRefreshing: Boolean, onRefresh: () -> Unit, content: @Composable () -> Unit) { /* 优先接入 Material pullRefresh 状态, 指示器使用凸起圆 */ }

@Composable
fun NeumorphicCarousel(pageCount: Int, currentPage: Int, content: @Composable (Int) -> Unit) { /* 横向 Pager + PagerIndicator, 卡片固定宽高 */ }

@Composable
fun NeumorphicExpandablePanel(title: String, expanded: Boolean, onExpandedChange: (Boolean) -> Unit, content: @Composable ColumnScope.() -> Unit) { /* header 可点, 展开内容用 AnimatedVisibility */ }
```

### 可访问性与状态清单

- `contentDescription`：纯图标操作必须提供；装饰图标传 `null`。
- `semantics`：Checkbox/Radio/Switch/Slider/Tab 要暴露 selected/checked/progress 语义。
- `minimumInteractiveComponentSize`：图标、Chip、Tab、日期格子不小于 44dp。
- `enabled/loading/error/selected/focused/pressed`：组件至少覆盖这些状态中的相关项。
- `rememberSaveable`：示例页面中的输入、Tab、筛选条件优先使用可保存状态。

---

## 禁止与注意

- **不要使用 Material 默认的 `ElevationCard` / `Surface` elevation**
- **不要用 `ripple()` indication**——使用 `indication = null`
- **不要在非凸起/凹陷面使用 `Modifier.background()` 覆盖颜色**——会破坏浮雕阴影
- **短按动画用 `detectTapGestures` 而非 `MutableInteractionSource`**——后者对快速点击有延迟
- **深色模式下阴影色对自动切换**（`NeumDarkLight`/`NeumDarkDark`）
- **`Animatable.snapTo` 用于按下瞬间, `animateTo` 用于松开回弹**
