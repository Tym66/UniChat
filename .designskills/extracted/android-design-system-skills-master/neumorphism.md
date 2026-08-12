---
name: neumorphism
description: 纯新拟态设计系统——Android Jetpack Compose 完整 UI 库。经典浮雕风格：柔和阴影、凹凸按钮、无边框卡片、极简配色。含BlurMaskFilter双色光影引擎、深浅色自适应、按压凹陷动画、触觉反馈、圆角系统，以及 Button/Input/SearchBar/NavigationBar/Dialog/BottomSheet/DatePicker 等常用控件适配规则。触发词：新拟态、neumorphism、浮雕风格、柔和UI、soft ui、neumorphic design、软UI设计、凸起按钮、凹陷输入框。
---

# Neumorphism 纯新拟态设计系统

Android Jetpack Compose 经典新拟态（Neumorphism / Soft UI）设计系统。与背景融为一体的柔和浮雕效果，通过亮暗双色阴影模拟凸起和凹陷。

## 设计特征

- **双色光影引擎**：`BlurMaskFilter` 驱动的左上亮面高光 + 右下暗面投影
- **极致柔和**：从背景色推导阴影色，组件与背景完美融合（"background-colored" 美学）
- **深浅色自适应**：从背景色自动计算亮/暗阴影色对
- **按压凹陷反馈**：按钮按下 convex→concave 切换，松开恢复
- **触觉反馈**：标准 `TextHandleMove` / `LongPress` 触觉
- **纯净视觉**：无描边、无分割线、无涟漪——只用阴影表达层级关系
- **圆角系统**：外层 24dp → 中层 18dp → 内层 12dp

## 使用前提

此 skill 专为 **Android Jetpack Compose 新项目** 设计。当用户要求：
- "做一套新拟态设计系统"
- "用 Soft UI 风格"
- "做浮雕风格的 Android UI"
- "不要 Material Design，要柔和的新拟态风"

## 目录结构

```
ui/designsystem/
├── theme/
│   ├── NeumColors.kt        ← 新拟态色板 + 阴影色
│   ├── NeumTypography.kt    ← 排版
│   ├── NeumSpacing.kt       ← 间距/圆角
│   ├── NeumShadows.kt       ← convex/concave 阴影 Modifier
│   └── NeumTheme.kt         ← 根主题
├── token/
│   └── NeumInteractions.kt  ← 点击交互（光影联动 + 缩放）
└── component/
    ├── NeumButton.kt        ← 按钮
    ├── NeumCard.kt          ← 卡片
    ├── NeumSwitch.kt        ← 开关
    ├── NeumSlider.kt        ← 滑动器
    ├── NeumInput.kt         ← 输入框
    └── NeumDialog.kt        ← 对话框
```

---

## Step 1: 依赖

```kotlin
// app/build.gradle.kts
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
}
```

---

## Step 2: NeumColors.kt

```kotlin
package com.example.app.designsystem.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 新拟态色板 —— 关键：所有颜色都从 background 推导
 */
data class NeumColors(
    val background: Color,
    val surface: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
)

// ═══ 浅色色板（柔和暖灰调） ═══
val LightNeumColors = NeumColors(
    background = Color(0xFFEEF0F4),
    surface = Color(0xFFEEF0F4),
    textPrimary = Color(0xFF2D3436),
    textSecondary = Color(0xFF636E72),
    textTertiary = Color(0xFFB2BEC3),
    accent = Color(0xFF6C5CE7),
    success = Color(0xFF00B894),
    warning = Color(0xFFFDCB6E),
    error = Color(0xFFFF7675),
)

// ═══ 深色色板 ═══
val DarkNeumColors = NeumColors(
    background = Color(0xFF1E1F26),
    surface = Color(0xFF1E1F26),
    textPrimary = Color(0xFFF0F0F3),
    textSecondary = Color(0xFFA0A3B1),
    textTertiary = Color(0xFF5A5D6E),
    accent = Color(0xFFA29BFE),
    success = Color(0xFF55EFC4),
    warning = Color(0xFFFFEAA7),
    error = Color(0xFFFF8A80),
)

val LocalNeumColors = staticCompositionLocalOf { LightNeumColors }
```

---

## Step 3: NeumTypography.kt

```kotlin
package com.example.app.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val NeumTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 34.sp, letterSpacing = 0.sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = 0.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, letterSpacing = 0.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 16.sp, letterSpacing = 0.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 14.sp, letterSpacing = 0.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 14.sp, letterSpacing = 0.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.sp),
)
```

---

## Step 4: NeumSpacing.kt

```kotlin
package com.example.app.designsystem.theme

import androidx.compose.ui.unit.dp

object NeumSpacing {
    val screenPadding = 20.dp
    val cardGap = 16.dp
    val cardCorner = 24.dp        // 外层卡片
    val innerCorner = 18.dp       // 内层区域
    val elementCorner = 12.dp     // 小型元素
    val buttonHeight = 56.dp
    val buttonCorner = 16.dp
    val inputHeight = 56.dp
    val chipHeight = 36.dp
    val switchWidth = 56.dp
    val switchHeight = 30.dp
    val elementPadding = 16.dp
}
```

---

## Step 5: NeumShadows.kt — 核心引擎

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

/** 从背景色推导亮/暗阴影色 */
private fun deriveShadowColors(bg: Color): Pair<Color, Color> {
    // 判断深浅：取亮度
    val luminance = 0.299f * bg.red + 0.587f * bg.green + 0.114f * bg.blue
    return if (luminance > 0.5f) {
        // 浅色背景：纯白高光 + 偏冷灰阴影
        Color(0xFFFFFFFF) to Color(0xFFC8CDD8)
    } else {
        // 深色背景：微亮高光 + 极暗阴影
        Color(0xFF2E303A) to Color(0xFF0A0A0F)
    }
}

/**
 * 凸起浮雕面（raised / convex）
 * 左上亮 + 右下暗 → 模拟浮出背景
 */
@Composable
fun Modifier.neumConvex(
    cornerRadius: Dp = 24.dp,
    elevation: Dp = 8.dp,
    bgColor: Color? = null,
): Modifier {
    val bg = bgColor ?: LocalNeumColors.current.background
    val (light, dark) = deriveShadowColors(bg)

    return this
        .clip(RoundedCornerShape(cornerRadius))
        .background(bg)
        .drawBehind {
            drawIntoCanvas { canvas ->
                val paint = Paint().apply {
                    asFrameworkPaint().apply {
                        isAntiAlias = true
                        maskFilter = BlurMaskFilter(elevation.toPx(), BlurMaskFilter.Blur.NORMAL)
                    }
                }

                // 右下暗影
                paint.color = dark
                val d = elevation.toPx()
                canvas.drawRoundRect(
                    d * 0.5f, d * 0.5f,
                    size.width + d * 0.5f, size.height + d * 0.5f,
                    cornerRadius.toPx(), cornerRadius.toPx(),
                    paint
                )

                // 左上高光
                paint.color = light
                canvas.drawRoundRect(
                    -d * 0.5f, -d * 0.5f,
                    size.width - d * 0.5f, size.height - d * 0.5f,
                    cornerRadius.toPx(), cornerRadius.toPx(),
                    paint
                )
            }
        }
}

/**
 * 凹陷浮雕面（sunken / concave）
 * 左上暗内影 + 右下亮内影 → 模拟压入背景
 */
@Composable
fun Modifier.neumConcave(
    cornerRadius: Dp = 24.dp,
    elevation: Dp = 4.dp,
    bgColor: Color? = null,
): Modifier {
    val bg = bgColor ?: LocalNeumColors.current.background
    val (light, dark) = deriveShadowColors(bg)

    return this
        .clip(RoundedCornerShape(cornerRadius))
        .background(bg)
        .drawWithContent {
            drawIntoCanvas { canvas ->
                val ep = elevation.toPx()
                val blur = BlurMaskFilter(ep, BlurMaskFilter.Blur.NORMAL)

                // 左上暗内影
                val darkP = Paint().apply {
                    color = dark.copy(alpha = 0.7f)
                    asFrameworkPaint().apply {
                        isAntiAlias = true; maskFilter = blur
                        style = android.graphics.Paint.Style.STROKE; strokeWidth = ep * 2f
                    }
                }
                canvas.drawRoundRect(
                    -ep * 0.5f, -ep * 0.5f, size.width, size.height,
                    cornerRadius.toPx(), cornerRadius.toPx(), darkP
                )

                // 右下亮内影
                val lightP = Paint().apply {
                    color = light
                    asFrameworkPaint().apply {
                        isAntiAlias = true; maskFilter = blur
                        style = android.graphics.Paint.Style.STROKE; strokeWidth = ep * 2f
                    }
                }
                canvas.drawRoundRect(
                    0f, 0f, size.width + ep * 0.5f, size.height + ep * 0.5f,
                    cornerRadius.toPx(), cornerRadius.toPx(), lightP
                )
            }
            drawContent()
        }
}
```

---

## Step 6: NeumInteractions.kt — 点击交互

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
import com.example.app.designsystem.theme.neumConcave
import com.example.app.designsystem.theme.neumConvex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 新拟态点击：缩放 + 触觉 + 光影联动
 * 使用 detectTapGestures 确保短按长按均有动画反馈。
 * 按下：scale 0.96 + concave；抬起：scale 1.0 + convex
 */
@Composable
fun Modifier.neumClickable(
    enabled: Boolean = true,
    cornerRadius: Dp = 24.dp,
    elevation: Dp = 6.dp,
    onClick: () -> Unit,
): Modifier {
    val haptic = LocalHapticFeedback.current
    var isPressed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val scaleAnim = remember { Animatable(1f) }

    return this
        .scale(scaleAnim.value)
        .then(
            if (isPressed && enabled) neumConcave(cornerRadius, elevation / 2)
            else neumConvex(cornerRadius, elevation)
        )
        .pointerInput(enabled) {
            if (enabled) detectTapGestures(
                onPress = {
                    isPressed = true
                    scope.launch { scaleAnim.snapTo(0.96f) }
                    tryAwaitRelease()
                    isPressed = false
                    scope.launch { delay(50); scaleAnim.animateTo(1f, tween(180, easing = FastOutSlowInEasing)) }
                },
                onTap = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() }
            )
        }
}
```

---

## Step 7: NeumButton.kt

```kotlin
package com.example.app.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.app.designsystem.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun NeumButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    height: Dp = 56.dp,
    cornerRadius: Dp = 16.dp,
    icon: (@Composable () -> Unit)? = null,
) {
    val colors = LocalNeumColors.current
    var isPressed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val scaleAnim = remember { Animatable(1f) }

    fun pressDown() { isPressed = true; scope.launch { scaleAnim.snapTo(0.96f) } }
    fun pressUp() { isPressed = false; scope.launch { delay(50); scaleAnim.animateTo(1f, tween(200, easing = FastOutSlowInEasing)) } }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(height)
            .scale(scaleAnim.value)
            .then(
                if (isPressed && enabled) neumConcave(cornerRadius, elevation = 3.dp)
                else neumConvex(cornerRadius, elevation = 6.dp)
            )
            .clip(RoundedCornerShape(cornerRadius))
            .pointerInput(enabled) {
                if (enabled && !isLoading) detectTapGestures(
                    onPress = { pressDown(); tryAwaitRelease(); pressUp() },
                    onTap = { onClick() }
                )
            }
            .padding(horizontal = 28.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = colors.accent,
                strokeWidth = 2.dp
            )
        } else if (icon != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon()
                Spacer(Modifier.width(8.dp))
                Text(text, color = colors.accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Text(text, color = colors.accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
```

---

## Step 8: NeumCard.kt

```kotlin
package com.example.app.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.app.designsystem.theme.neumConvex

@Composable
fun NeumCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    elevation: Dp = 8.dp,
    padding: Dp = 20.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .neumConvex(cornerRadius = cornerRadius, elevation = elevation)
            .padding(padding),
        content = { content() }
    )
}
```

---

## Step 9: NeumSwitch.kt

```kotlin
package com.example.app.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
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
import com.example.app.designsystem.theme.LocalNeumColors
import com.example.app.designsystem.theme.neumConcave
import com.example.app.designsystem.theme.neumConvex

@Composable
fun NeumSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val colors = LocalNeumColors.current

    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 26.dp else 4.dp,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "thumb"
    )

    val trackColor by animateColorAsState(
        targetValue = if (checked) colors.accent else Color.Transparent,
        animationSpec = tween(250),
        label = "track"
    )

    Box(
        modifier = modifier
            .width(56.dp).height(30.dp)
            .neumConcave(cornerRadius = 15.dp, elevation = 2.dp)
            .background(trackColor, RoundedCornerShape(15.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onCheckedChange(!checked)
            }
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .align(Alignment.CenterStart)
                .size(22.dp)
                .neumConvex(cornerRadius = 11.dp, elevation = 2.dp)
        )
    }
}
```

---

## Step 10: NeumSlider.kt

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
import com.example.app.designsystem.theme.LocalNeumColors
import com.example.app.designsystem.theme.neumConcave
import com.example.app.designsystem.theme.neumConvex

@Composable
fun NeumSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalNeumColors.current
    val range = valueRange.endInclusive - valueRange.start
    val fraction = ((value - valueRange.start) / range).coerceIn(0f, 1f)
    val currentOnValueChange by rememberUpdatedState(onValueChange)

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth().height(36.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val thumbR = 18.dp; val thumbRPx = with(LocalDensity.current) { thumbR.toPx() }
        val maxDragPx = widthPx - thumbRPx * 2

        fun pxToValue(px: Float) {
            val f = ((px - thumbRPx) / maxDragPx).coerceIn(0f, 1f)
            currentOnValueChange(valueRange.start + f * range)
        }

        Box(
            modifier = Modifier.fillMaxSize()
                .pointerInput(Unit) { detectDragGestures { c, _ -> pxToValue(c.position.x) } }
                .pointerInput(Unit) { detectTapGestures { pxToValue(it.x) } },
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().height(10.dp)
                    .neumConcave(cornerRadius = 5.dp, elevation = 2.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxHeight().fillMaxWidth(fraction)
                        .background(colors.accent.copy(alpha = 0.7f), RoundedCornerShape(5.dp))
                )
            }

            val thumbPx = fraction * maxDragPx
            val thumbDp = with(LocalDensity.current) { thumbPx.toDp() }
            Box(
                modifier = Modifier.offset(x = thumbDp).size(thumbR * 2)
                    .neumConvex(cornerRadius = thumbR, elevation = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.size(8.dp).background(colors.accent, CircleShape))
            }
        }
    }
}
```

---

## Step 11: NeumInput.kt

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
import com.example.app.designsystem.theme.LocalNeumColors
import com.example.app.designsystem.theme.neumConcave

@Composable
fun NeumInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leadingIcon: @Composable (() -> Unit)? = null,
    isFocused: Boolean = false,
) {
    val colors = LocalNeumColors.current

    Box(
        modifier = modifier
            .height(56.dp).fillMaxWidth()
            .neumConcave(cornerRadius = 16.dp, elevation = 2.dp)
            .then(
                if (isFocused) Modifier.border(1.5.dp, colors.accent, RoundedCornerShape(16.dp))
                else Modifier
            )
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leadingIcon != null) { leadingIcon(); Spacer(Modifier.width(10.dp)) }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                textStyle = LocalTextStyle.current.copy(color = colors.textPrimary, fontSize = 16.sp),
                decorationBox = { inner ->
                    if (value.isEmpty()) Text(placeholder, color = colors.textTertiary, fontSize = 16.sp)
                    inner()
                }
            )
        }
    }
}
```

---

## Step 12: NeumDialog.kt

```kotlin
package com.example.app.designsystem.component

import androidx.compose.foundation.layout.*
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
import com.example.app.designsystem.theme.LocalNeumColors
import com.example.app.designsystem.theme.neumConvex

@Composable
fun NeumDialog(
    title: String,
    message: String,
    confirmText: String = "确认",
    cancelText: String = "取消",
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = LocalNeumColors.current

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .width(300.dp).wrapContentHeight()
                .neumConvex(cornerRadius = 24.dp, elevation = 10.dp)
                .padding(top = 28.dp, bottom = 20.dp, start = 24.dp, end = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                Spacer(Modifier.height(12.dp))
                Text(message, fontSize = 14.sp, lineHeight = 20.sp, color = colors.textSecondary, textAlign = TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    NeumButton(
                        text = cancelText,
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        height = 48.dp,
                        cornerRadius = 14.dp
                    )
                    NeumButton(
                        text = confirmText,
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        height = 48.dp,
                        cornerRadius = 14.dp
                    )
                }
            }
        }
    }
}
```

---

## Step 13: NeumTheme.kt — 根主题

```kotlin
package com.example.app.designsystem.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun NeumTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val neumColors = if (darkTheme) DarkNeumColors else LightNeumColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = neumColors.background.toArgb()
            window.navigationBarColor = neumColors.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalNeumColors provides neumColors) {
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme.copy(
                background = neumColors.background,
                surface = neumColors.surface,
                onBackground = neumColors.textPrimary,
                onSurface = neumColors.textPrimary,
                primary = neumColors.accent,
            ),
            typography = NeumTypography,
            content = content,
        )
    }
}
```

---

## 使用示例

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NeumTheme {
                val colors = LocalNeumColors.current
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = colors.background
                ) {
                    LazyColumn(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 凸起卡片
                        item {
                            NeumCard(modifier = Modifier.fillMaxWidth().height(100.dp)) {
                                Text("新拟态卡片", style = MaterialTheme.typography.titleLarge)
                            }
                        }

                        // 按钮
                        item { NeumButton(text = "Primary Action", onClick = { /* ... */ }, modifier = Modifier.fillMaxWidth()) }

                        // 开关
                        item {
                            var checked by remember { mutableStateOf(false) }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("开关", modifier = Modifier.weight(1f))
                                NeumSwitch(checked = checked, onCheckedChange = { checked = it })
                            }
                        }

                        // 滑动器
                        item {
                            var sliderValue by remember { mutableFloatStateOf(50f) }
                            NeumSlider(
                                value = sliderValue,
                                valueRange = 0f..100f,
                                onValueChange = { sliderValue = it },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // 输入框
                        item {
                            var text by remember { mutableStateOf("") }
                            NeumInput(value = text, onValueChange = { text = it }, placeholder = "请输入...", modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}
```

---

## 设计参数速查

| 属性 | 浅色 | 深色 |
|------|------|------|
| 背景色 | `#EEF0F4` | `#1E1F26` |
| 亮阴影 | `#FFFFFF` | `#2E303A` |
| 暗阴影 | `#C8CDD8` | `#0A0A0F` |
| 主色调 | `#6C5CE7` | `#A29BFE` |
| 按钮圆角 | 16dp | 16dp |
| 卡片圆角 | 24dp | 24dp |
| 凸起 elevation | 6-8dp | 6-8dp |
| 凹陷 elevation | 2-4dp | 2-4dp |
| 按钮缩放 | 0.96 | 0.96 |
| 动画时长 | 180-250ms | 180-250ms |

---

## 扩展组件

所有扩展组件基于 `neumConvex/Concave` + `neumClickable/neumTap`。核心模式一致：凸起 = 默认，凹陷 = 按下/选中/输入。

### 常用控件清单

纯新拟态 Skill 至少覆盖以下控件。API 命名使用 `Neum*` 前缀，参数与 `hyper-neumorphic` 中的 `Neumorphic*` 保持同构，便于迁移。

| 分类 | 控件 | 新拟态实现要点 |
|------|------|----------------|
| 操作 | Button、IconButton、FAB、SplitButton、ToggleButton、SegmentedControl | 默认凸起，按下/选中凹陷；禁用态 alpha 0.48 |
| 输入 | Input、PasswordInput、SearchBar、TextArea | 凹陷容器，聚焦时 accent 内高光，错误态不改变层级 |
| 数值 | Slider、RangeSlider、Stepper、RatingBar | 轨道凹陷，thumb/按钮凸起，拖动开始触觉反馈 |
| 选择 | Checkbox、RadioButton、Switch、Chip、DropdownMenu、DatePicker、TimePicker | 选中态凹陷或 accent 填充，未选中轻凸起 |
| 导航 | TopAppBar、Tabs、NavigationBar、NavigationRail、Breadcrumb、PagerIndicator | 当前项用凹陷胶囊或凸起滑块，未选中降低文本色 |
| 展示 | Card、ListItem、GridTile、StatisticCard、Timeline、Avatar、Badge、Tag、Tooltip | 与背景同色，靠阴影和间距建立层级 |
| 反馈 | Dialog、BottomSheet、Snackbar、ToastHost、Progress、Spinner、Skeleton、EmptyState、ErrorState | 浮层凸起承载，进度和骨架使用低对比动画 |
| 容器 | Section、SettingsGroup、ExpandablePanel、Carousel、PullRefreshContainer | 避免卡片套卡片；组内用间距、缩进、轻分隔表达结构 |

### Checkbox / RadioButton / Progress / Chip / Tabs / ListItem / Avatar / Badge / EmptyState / Skeleton / FAB / BottomSheet

完整实现参考 `hyper-neumorphic` skill 的"扩展组件库"章节。下面列出关键模式：

| 组件 | 核心Modifier | 关键交互 |
|------|-------------|---------|
| Checkbox | 24dp 凸起/凹陷切换 6dp圆角 | neumorphicTap + animateColorAsState |
| RadioButton | 22dp 凸起圆，10dp inner色点 | clickable + animateColorAsState |
| LinearProgress | 凹陷轨道 + 着色填充 | animateFloatAsState |
| CircularProgress | 凸起圆形容器 | animateFloatAsState |
| Chip | 选中凸起/未选中平坦 16dp圆角 | neumorphicTap |
| Tabs | 整体凹陷 + 选中Tab凸起 | neumorphicTap |
| ListItem | Row + 可选 neumorphicTap | 带 headline/supporting/leading/trailing |
| Avatar | 圆形 neumorphicConvex | 内容居中 |
| Badge | 10dp 凸起 + 12% color 背景 | 小文字标签 |
| EmptyState | 居中 Column + icon圆 | 可选 action 按钮 |
| Skeleton | 凹陷 + shimmer 渐变动画 | rememberInfiniteTransition |
| FAB | 56dp 凸起圆 | clickable |
| BottomSheet | Dialog + neumorphicConvex + 拖拽条 | 底部弹出 |

### 新增常用控件模式

| 控件 | 核心Modifier | 关键交互 |
|------|-------------|---------|
| IconButton | 44dp 点击目标 + 圆形凸起/凹陷 | icon 必须有 contentDescription |
| ToggleButton | checked 时凹陷 + accent 文本 | neumTap + animateColorAsState |
| SegmentedControl | 外层凹陷胶囊 + 选中凸起滑块 | selectedIndex 切换动画 |
| SearchBar | leading 搜索图标 + trailing 清除按钮 | 输入面凹陷，清除按钮凸起 |
| PasswordInput | trailing 可见性 IconButton | PasswordVisualTransformation |
| TextArea | minLines/maxLines + supportingText | 高度自适应，辅助文案在容器外 |
| RangeSlider | 凹陷轨道 + 双凸起 thumb | active 区间 accent 填充 |
| Stepper | 减号按钮 + 凹陷数值 + 加号按钮 | 到边界时按钮 disabled |
| RatingBar | 横排 IconButton | 选中 accent，未选中 tertiary |
| TopAppBar | 透明背景 + 轻浮雕底部分隔 | navigation/actions 用 IconButton |
| NavigationBar | 底部凸起容器 | selected item 凹陷胶囊 |
| NavigationRail | 72dp 侧栏凸起容器 | selected item 凹陷圆角 |
| DropdownMenu | Popup/浮层凸起容器 | 点击外部 dismiss |
| Tooltip | 小型凸起浮层 | 12dp 圆角，短文本 |
| Snackbar | 底部凸起浮层 | 可选 action 按钮 |
| StatisticCard | Card + 数值大字 + delta | Dashboard 首选 |
| Timeline | 左侧凸起节点 + 竖线 | 右侧内容轻凸起 |
| GridTile | 固定 aspectRatio 卡片 | 避免网格高度跳动 |
| DatePicker | 月份导航 + 日期网格 | selected 日期凹陷 |
| TimePicker | Stepper 或表盘 | 数字项 44dp 点击目标 |
| PullRefreshContainer | 刷新指示器凸起圆 | 复用 pullRefresh 状态 |
| Carousel | HorizontalPager + PagerIndicator | 卡片固定宽高 |
| ExpandablePanel | Header 可点 + AnimatedVisibility | 展开内容不再套卡片 |

### 状态与无障碍

- 所有可交互组件提供 `enabled`，禁用时不触发 haptic。
- 图标操作必须提供 `contentDescription`；装饰图标使用 `null`。
- Checkbox、Radio、Switch、Slider、Tab、Navigation item 添加对应 semantics。
- 小控件视觉尺寸可小于 44dp，但点击目标不能小于 44dp。
- `loading/error/selected/focused/pressed` 状态要在控件 API 或示例中体现。


## 禁止与注意

- **背景色必须统一**：所有凸起/凹陷面使用完全相同的背景色
- **不要同时使用 Material elevation + neumorphism**
- **短按动画用 `detectTapGestures` 而非 `MutableInteractionSource`**
- **elevation 不宜过大**：6-8dp 是最佳范围
- **触觉反馈**：所有可交互组件需 `haptic.performHapticFeedback()`。Button/FAB/Checkbox/Radio/Slider/Chip/Tabs/ListItem 用 `TextHandleMove`，Switch 用 `LongPress`
