---
name: glassmorphism
description: 玻璃态/Glassmorphism 设计系统——Android Jetpack Compose 完整 UI 库。毛玻璃/磨砂玻璃效果：多层径向渐变 Mesh 背景、半透明叠加、渐变描边、顶部内高光、柔和外阴影。含深浅色双套玻璃令牌、凸起/凹陷玻璃面 Modifier、玻璃按钮/卡片/输入框/对话框，以及 SearchBar/NavigationBar/BottomSheet/DatePicker 等常用控件的玻璃态适配规则。触发词：玻璃态、glassmorphism、毛玻璃、磨砂玻璃、frosted glass、glass ui、通透感、半透明UI、blur background、玻璃拟态。
---

# Glassmorphism 玻璃态设计系统

Android Jetpack Compose 玻璃态/毛玻璃设计系统。通过多层径向渐变 Mesh 背景 + 半透明 tint 叠加 + 渐变描边 + 内高光，实现通透有深度的磨砂玻璃效果。

## 设计特征

- **Mesh 多层光斑背景**：4-5 个径向渐变光斑（不同位置/颜色/alpha），模拟透过玻璃看到的彩色环境光
- **对位 mesh 透出**：每个玻璃面通过 `positionInWindow()` 精准对位全局 mesh，卡片移动时透出的光斑也随之变化
- **半透叠加**：`tintConvex`（浅白透）用于凸起面，`tintConcave`（暗透）用于凹陷面
- **渐变描边**：`borderHi`/`borderLo` 上下渐变 1dp 边框，模拟玻璃边缘的反光
- **顶部内高光**：1dp 白色高光线在卡片顶部，模拟光源反射
- **柔和外阴影**：仅下方漏一线阴影，轻量不突兀
- **深浅双套令牌**：`DefaultGlassTokens`（深色炫彩）和 `LightGlassTokens`（通透奶白）

## 设计理念

玻璃态不是简单的"模糊+透明"。核心是 **"透过玻璃看到什么"**——mesh 光斑模拟了玻璃背后多彩环境光在磨砂表面的散射。卡片移动时，对位 mesh 让每个面透出不同的光斑区域，产生"玻璃厚度"和"空间深度"的错觉。

## 使用前提

- **Android Jetpack Compose 新项目**
- 用户要求 "毛玻璃风格" / "glassmorphism" / "通透 UI" / "磨砂玻璃"

## 目录结构

```
ui/designsystem/
├── theme/
│   ├── GlassTokens.kt      ← 玻璃令牌（mesh光斑+半透色+边框色+文字色）
│   ├── GlassMesh.kt         ← Mesh 背景 + Glass 面 Modifier
│   ├── GlassColors.kt       ← 辅助色板
│   ├── GlassTypography.kt   ← 排版
│   └── GlassTheme.kt        ← 根主题（集成 mesh 背景）
└── component/
    ├── GlassButton.kt       ← 玻璃按钮
    ├── GlassCard.kt         ← 玻璃卡片
    ├── GlassInput.kt        ← 玻璃输入框
    └── GlassDialog.kt       ← 玻璃对话框
```

---

## Step 1: 依赖

```kotlin
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
}
```

---

## Step 2: GlassTokens.kt — 核心令牌

```kotlin
package com.example.app.designsystem.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** mesh 单个光斑 */
data class MeshSpot(
    val xFraction: Float,     // 光斑中心 X（占屏幕宽比例，0~1）
    val yFraction: Float,     // 光斑中心 Y（占屏幕高比例，0~1）
    val color: Color,         // 光斑颜色（含 alpha）
    val radiusFraction: Float,// 光斑半径（占 min(W,H) 比例）
)

/**
 * 玻璃令牌 —— 一套完整的玻璃态视觉参数
 */
data class GlassTokens(
    val meshBase: Color,           // mesh 底色
    val meshSpots: List<MeshSpot>, // 光斑列表（4-5 个）
    val tintConvex: Color,         // 凸起面半透叠加
    val tintConcave: Color,        // 凹陷面半透叠加
    val borderHi: Color,           // 渐变边框高亮端
    val borderLo: Color,           // 渐变边框暗端
    val innerHighlight: Color,     // 顶部内高光线
    val outerShadow: Color,        // 外阴影色
    val textPrimary: Color,        // 主文字色
    val textSecondary: Color,      // 次文字色
    val textTertiary: Color,       // 三级文字色
)

// ═══ 深色玻璃（炫彩暗底） ═══
val DarkGlassTokens = GlassTokens(
    meshBase = Color(0xFF0F1320),
    meshSpots = listOf(
        MeshSpot(0.20f, 0.25f, Color(0x8C4A6EAA), 0.55f), // 左上：蓝紫
        MeshSpot(0.84f, 0.16f, Color(0x73786096), 0.52f), // 右上：灰紫
        MeshSpot(0.30f, 0.90f, Color(0x663C7878), 0.55f), // 左下：青绿
        MeshSpot(0.90f, 0.82f, Color(0x6B5A548C), 0.55f), // 右下：紫蓝
    ),
    tintConvex = Color(0x1FFFFFFF),  // 12% 白透 → 凸起面微亮
    tintConcave = Color(0x24000000), // 14% 黑透 → 凹陷面微暗
    borderHi = Color(0x8CFFFFFF),    // 55% 白 → 上边框亮
    borderLo = Color(0x1FFFFFFF),    // 12% 白 → 下边框暗
    innerHighlight = Color(0x73FFFFFF), // 45% 白 → 顶部高光线
    outerShadow = Color(0x47000000),    // 28% 黑 → 外阴影
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xC7FFFFFF),
    textTertiary = Color(0x8CFFFFFF),
)

// ═══ 浅色玻璃（通透奶白） ═══
val LightGlassTokens = GlassTokens(
    meshBase = Color(0xFFF3EFEA),  // 暖调浅底（非纯白，有纸张温度）
    meshSpots = listOf(
        MeshSpot(0.18f, 0.22f, Color(0x735AA0F0), 0.62f), // 左上：蓝
        MeshSpot(0.86f, 0.15f, Color(0x669C8AE6), 0.60f), // 右上：淡紫
        MeshSpot(0.28f, 0.90f, Color(0x6B4FB8C9), 0.62f), // 左下：青
        MeshSpot(0.90f, 0.84f, Color(0x617E86E0), 0.60f), // 右下：蓝紫
        MeshSpot(0.52f, 0.46f, Color(0x4F7FC0E8), 0.82f), // 中央：宽柔青蓝（消除白板感）
    ),
    tintConvex = Color(0x2EFFFFFF),  // 18% 白透
    tintConcave = Color(0x12000000), // 7% 黑透（浅色下凹陷不能太暗）
    borderHi = Color(0xCCFFFFFF),    // 80% 白
    borderLo = Color(0x1F000000),    // 12% 黑
    innerHighlight = Color(0x80FFFFFF),
    outerShadow = Color(0x33737D99), // 蓝灰调外阴影（匹配 mesh 色系）
    textPrimary = Color(0xFF1A1B1E),
    textSecondary = Color(0xB31A1B1E),
    textTertiary = Color(0x801A1B1E),
)

val LocalGlassTokens = staticCompositionLocalOf { DarkGlassTokens }
```

---

## Step 3: GlassMesh.kt — Mesh 背景 + 玻璃面 Modifier

```kotlin
package com.example.app.designsystem.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ═══ Mesh 绘制核心 ═══

/** 在 DrawScope 中绘制 mesh（以 origin 为坐标系偏移） */
private fun DrawScope.drawMesh(tokens: GlassTokens, fullSize: Size, origin: Offset) {
    drawRect(tokens.meshBase)
    val minDim = minOf(fullSize.width, fullSize.height)
    for (spot in tokens.meshSpots) {
        val center = Offset(
            x = spot.xFraction * fullSize.width - origin.x,
            y = spot.yFraction * fullSize.height - origin.y,
        )
        val radius = (spot.radiusFraction * minDim).coerceAtLeast(1f)
        // 径向渐变：光斑色 → 透明
        // 注意：用 drawRect（非 drawCircle）让光斑覆盖全屏互相融合
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(spot.color, Color.Transparent),
                center = center,
                radius = radius,
            )
        )
    }
}

// ═══ 全屏 Mesh 背景 ═══

@Composable
fun GlassMeshBackground(tokens: GlassTokens, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind { drawMesh(tokens, fullSize = size, origin = Offset.Zero) }
    )
}

// ═══ 玻璃凸起面 ═══

/**
 * 玻璃凸起面：对位 mesh 透出 + 半透浅色叠加 + 渐变描边 + 顶部内高光 + 外阴影
 * 用于：卡片、按钮默认态、对话框
 */
@Composable
fun Modifier.glassConvex(
    cornerRadius: Dp,
    tokens: GlassTokens = LocalGlassTokens.current,
): Modifier {
    var winOffset by remember { mutableStateOf(Offset.Zero) }
    val shape = RoundedCornerShape(cornerRadius)
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val fullSize = with(density) {
        Size(configuration.screenWidthDp.dp.toPx(), configuration.screenHeightDp.dp.toPx())
    }
    val radiusPx = with(density) { cornerRadius.toPx() }

    return this
        .onGloballyPositioned { winOffset = it.positionInWindow() }
        // 外阴影（仅在下方露出一线）
        .drawBehind {
            drawRoundRect(
                color = tokens.outerShadow,
                topLeft = Offset(0f, 2.dp.toPx()),
                size = size,
                cornerRadius = CornerRadius(radiusPx, radiusPx),
            )
        }
        // 裁剪 + mesh + tint
        .clip(shape)
        .drawBehind {
            drawMesh(tokens, fullSize, winOffset)
            drawRect(tokens.tintConvex)
        }
        // 顶部内高光
        .drawWithContent {
            drawContent()
            drawRect(
                color = tokens.innerHighlight,
                size = Size(size.width, 1.dp.toPx()),
            )
        }
        // 渐变边框
        .drawBehind {
            drawRoundRect(
                brush = Brush.linearGradient(listOf(tokens.borderHi, tokens.borderLo)),
                cornerRadius = CornerRadius(radiusPx, radiusPx),
                style = Stroke(width = 1.dp.toPx()),
            )
        }
}

// ═══ 玻璃凹陷面 ═══

/**
 * 玻璃凹陷面：对位 mesh + 暗半透叠加 + 渐变描边
 * 用于：输入框、开关轨道、按钮按下态
 */
@Composable
fun Modifier.glassConcave(
    cornerRadius: Dp,
    tokens: GlassTokens = LocalGlassTokens.current,
): Modifier {
    var winOffset by remember { mutableStateOf(Offset.Zero) }
    val shape = RoundedCornerShape(cornerRadius)
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val fullSize = with(density) {
        Size(configuration.screenWidthDp.dp.toPx(), configuration.screenHeightDp.dp.toPx())
    }
    val radiusPx = with(density) { cornerRadius.toPx() }

    return this
        .onGloballyPositioned { winOffset = it.positionInWindow() }
        .clip(shape)
        .drawBehind {
            drawMesh(tokens, fullSize, winOffset)
            drawRect(tokens.tintConcave)
        }
        .drawBehind {
            drawRoundRect(
                brush = Brush.linearGradient(listOf(tokens.borderHi, tokens.borderLo)),
                cornerRadius = CornerRadius(radiusPx, radiusPx),
                style = Stroke(width = 1.dp.toPx()),
            )
        }
}

// ═══ Overlay 变体（弹窗/底部面板用，自带完整 mesh） ═══

@Composable
fun Modifier.glassConvexOverlay(cornerRadius: Dp, tokens: GlassTokens = LocalGlassTokens.current): Modifier {
    var winOffset by remember { mutableStateOf(Offset.Zero) }
    val fullSize = with(LocalDensity.current) { Size(LocalConfiguration.current.screenWidthDp.dp.toPx(), LocalConfiguration.current.screenHeightDp.dp.toPx()) }
    val radiusPx = with(LocalDensity.current) { cornerRadius.toPx() }
    return this
        .onGloballyPositioned { winOffset = it.positionInWindow() }
        .drawBehind { drawRoundRect(color = tokens.outerShadow, topLeft = Offset(0f, 2.dp.toPx()), size = size, cornerRadius = CornerRadius(radiusPx, radiusPx)) }
        .clip(RoundedCornerShape(cornerRadius))
        .drawBehind { drawMesh(tokens, fullSize, winOffset); drawRect(tokens.tintConvex) }
        .drawWithContent { drawContent(); drawRect(color = tokens.innerHighlight, size = Size(size.width, 1.dp.toPx())) }
        .drawBehind { drawRoundRect(brush = Brush.linearGradient(listOf(tokens.borderHi, tokens.borderLo)), cornerRadius = CornerRadius(radiusPx, radiusPx), style = Stroke(1.dp.toPx())) }
}

@Composable
fun Modifier.glassConcaveOverlay(cornerRadius: Dp, tokens: GlassTokens = LocalGlassTokens.current): Modifier {
    var winOffset by remember { mutableStateOf(Offset.Zero) }
    val fullSize = with(LocalDensity.current) { Size(LocalConfiguration.current.screenWidthDp.dp.toPx(), LocalConfiguration.current.screenHeightDp.dp.toPx()) }
    val radiusPx = with(LocalDensity.current) { cornerRadius.toPx() }
    return this
        .onGloballyPositioned { winOffset = it.positionInWindow() }
        .clip(RoundedCornerShape(cornerRadius))
        .drawBehind { drawMesh(tokens, fullSize, winOffset); drawRect(tokens.tintConcave) }
        .drawBehind { drawRoundRect(brush = Brush.linearGradient(listOf(tokens.borderHi, tokens.borderLo)), cornerRadius = CornerRadius(radiusPx, radiusPx), style = Stroke(1.dp.toPx())) }
}
```

> **`glassConvex/Concave` vs `glassConvexOverlay/ConcaveOverlay`**：
> - 页面内组件（按钮/卡片/输入框）→ `glassConvex/Concave`：仅半透 tint，全局 mesh 透出
> - 独立浮层（Dialog/BottomSheet）→ `glassConvexOverlay/ConcaveOverlay`：自带完整 mesh，不透明
> - 切换方式：给统一入口函数（如 `neumorphicConvex(cornerRadius, elevation, isOverlay = true)`）加 `isOverlay` 参数路由

---

## Step 4: GlassTypography.kt

```kotlin
package com.example.app.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val GlassTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 36.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 32.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 12.sp),
)
```

---

## Step 5: GlassColors.kt — 辅助色板

```kotlin
package com.example.app.designsystem.theme

import androidx.compose.ui.graphics.Color

/** 玻璃态下的强调色（与 mesh 色系协调） */
object GlassAccent {
    val primary = Color(0xFF7C9FF5)
    val primaryDark = Color(0xFF8AADFF)
    val success = Color(0xFF5EC4A7)
    val warning = Color(0xFFFFBF6E)
    val error = Color(0xFFFF8080)
}
```

---

## Step 6: GlassButton.kt

```kotlin
package com.example.app.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.app.designsystem.theme.GlassAccent
import com.example.app.designsystem.theme.LocalGlassTokens
import com.example.app.designsystem.theme.glassConcave
import com.example.app.designsystem.theme.glassConvex
import kotlinx.coroutines.delay

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    height: Dp = 52.dp,
    cornerRadius: Dp = 16.dp,
    icon: (@Composable () -> Unit)? = null,
) {
    val tokens = LocalGlassTokens.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scaleAnim = remember { Animatable(1f) }
    LaunchedEffect(isPressed, enabled) {
        if (isPressed && enabled) scaleAnim.snapTo(0.96f)
        else { delay(60); scaleAnim.animateTo(1f, tween(200, easing = FastOutSlowInEasing)) }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(height)
            .scale(scaleAnim.value)
            .then(
                if (isPressed && enabled) glassConcave(cornerRadius, tokens)
                else glassConvex(cornerRadius, tokens)
            )
            .clip(RoundedCornerShape(cornerRadius))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled && !isLoading
            ) { onClick() }
            .padding(horizontal = 24.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = GlassAccent.primary,
                strokeWidth = 2.dp
            )
        } else if (icon != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon()
                Spacer(Modifier.width(8.dp))
                Text(
                    text = text,
                    color = tokens.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        } else {
            Text(
                text = text,
                color = tokens.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
```

---

## Step 7: GlassCard.kt

```kotlin
package com.example.app.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.app.designsystem.theme.glassConvex

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    padding: Dp = 20.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .glassConvex(cornerRadius = cornerRadius)
            .padding(padding),
        content = { content() }
    )
}
```

---

## Step 8: GlassInput.kt

```kotlin
package com.example.app.designsystem.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.app.designsystem.theme.GlassAccent
import com.example.app.designsystem.theme.LocalGlassTokens
import com.example.app.designsystem.theme.glassConcave

@Composable
fun GlassInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    isFocused: Boolean = false,
) {
    val tokens = LocalGlassTokens.current

    Box(
        modifier = modifier
            .height(52.dp).fillMaxWidth()
            .glassConcave(cornerRadius = 14.dp, tokens)
            .then(
                if (isFocused) Modifier.drawBehind {
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            listOf(GlassAccent.primary, GlassAccent.primary.copy(alpha = 0.4f))
                        ),
                        cornerRadius = CornerRadius(14.dp.toPx()),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                } else Modifier
            )
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = LocalTextStyle.current.copy(color = tokens.textPrimary, fontSize = 15.sp),
            decorationBox = { inner ->
                if (value.isEmpty()) Text(placeholder, color = tokens.textTertiary, fontSize = 15.sp)
                inner()
            }
        )
    }
}
```

---

## Step 9: GlassDialog.kt

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
import com.example.app.designsystem.theme.LocalGlassTokens
import com.example.app.designsystem.theme.glassConvex

@Composable
fun GlassDialog(
    title: String,
    message: String,
    confirmText: String = "确认",
    cancelText: String = "取消",
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val tokens = LocalGlassTokens.current

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .width(300.dp).wrapContentHeight()
                .glassConvex(cornerRadius = 24.dp, tokens)
                .padding(top = 28.dp, bottom = 20.dp, start = 24.dp, end = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = tokens.textPrimary)
                Spacer(Modifier.height(12.dp))
                Text(
                    message, fontSize = 14.sp, lineHeight = 20.sp,
                    color = tokens.textSecondary, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GlassButton(text = cancelText, onClick = onCancel, modifier = Modifier.weight(1f))
                    GlassButton(text = confirmText, onClick = onConfirm, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
```

---

## Step 10: GlassTheme.kt — 根主题

```kotlin
package com.example.app.designsystem.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun GlassTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val tokens = if (darkTheme) DarkGlassTokens else LightGlassTokens

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = tokens.meshBase.toArgb()
            window.navigationBarColor = tokens.meshBase.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalGlassTokens provides tokens) {
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme.copy(
                background = tokens.meshBase,
                surface = tokens.meshBase,
                onBackground = tokens.textPrimary,
                onSurface = tokens.textPrimary,
                primary = GlassAccent.primary,
            ),
            typography = GlassTypography,
        ) {
            // 全屏 Mesh 背景
            Box(modifier = Modifier.fillMaxSize()) {
                GlassMeshBackground(tokens)
                content()
            }
        }
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
            GlassTheme {
                val tokens = LocalGlassTokens.current
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 玻璃卡片
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                            Column {
                                Text("玻璃态卡片", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = tokens.textPrimary)
                                Spacer(Modifier.height(8.dp))
                                Text("磨砂玻璃效果", fontSize = 14.sp, color = tokens.textSecondary)
                            }
                        }
                    }

                    // 玻璃按钮
                    item { GlassButton(text = "主要操作", onClick = { /* ... */ }, modifier = Modifier.fillMaxWidth()) }

                    // 玻璃输入框
                    item {
                        var text by remember { mutableStateOf("") }
                        GlassInput(value = text, onValueChange = { text = it }, placeholder = "输入内容...", modifier = Modifier.fillMaxWidth())
                    }

                    // 嵌套卡片的层次感
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth().height(80.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(44.dp).glassConvex(22.dp))
                                Spacer(Modifier.width(16.dp))
                                Column {
                                    Text("层次嵌套", color = tokens.textPrimary, fontWeight = FontWeight.Medium)
                                    Text("内层元素也可使用玻璃面", fontSize = 12.sp, color = tokens.textTertiary)
                                }
                            }
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

### 深色玻璃令牌 (`DarkGlassTokens`)

| 属性 | 值 | 说明 |
|------|----|------|
| meshBase | `#0F1320` | 深邃蓝黑底 |
| tintConvex | `0x1FFFFFFF` (12%) | 微亮白透 |
| tintConcave | `0x24000000` (14%) | 微暗暗透 |
| borderHi | `0x8CFFFFFF` (55%) | 上边框亮白 |
| borderLo | `0x1FFFFFFF` (12%) | 下边框极淡 |
| innerHighlight | `0x73FFFFFF` (45%) | 顶部高光白线 |
| outerShadow | `0x47000000` (28%) | 黑色投影 |
| textPrimary | `#FFFFFF` | 纯白 |
| textSecondary | `0xC7FFFFFF` (78%) | 半透白 |
| textTertiary | `0x8CFFFFFF` (55%) | 透明白 |

### 浅色玻璃令牌 (`LightGlassTokens`)

| 属性 | 值 | 说明 |
|------|----|------|
| meshBase | `#F3EFEA` | 暖调浅底 |
| tintConvex | `0x2EFFFFFF` (18%) | 白透叠加 |
| tintConcave | `0x12000000` (7%) | 极轻暗透 |
| borderHi | `0xCCFFFFFF` (80%) | 亮白边框 |
| borderLo | `0x1F000000` (12%) | 暗边框 |
| textPrimary | `0xFF1A1B1E` | 接近纯黑 |
| textSecondary | `0xB31A1B1E` (70%) | 深灰 |
| textTertiary | `0x801A1B1E` (50%) | 中灰 |

---

## 光斑调参指南

每个光斑有 4 个参数，调出理想效果的关键：

1. **位置** (`xFraction`, `yFraction`)：分散在屏幕四角 + 中央，不要聚集
2. **颜色** (`color`)：使用中低饱和度色（如蓝紫、青绿、灰紫），alpha 控制在 `0x4F`~`0x8C` 之间
3. **半径** (`radiusFraction`)：0.5~0.85，越大越柔和，越小越集中
4. **数量**：深色 4 个足够，浅色建议 5 个（加中央宽柔光斑消除"白板"感）

调整原则：**透过玻璃看到的颜色要"微妙"而非"抢眼"**——光斑色是环境光的暗示，不是设计的主色。

---

## 扩展组件

玻璃态下所有组件的 `glassConvex/Concave` 已自动处理 mesh 对位 + 半透 + 渐变描边 + 内高光。组件 API 与新拟态版本完全一致，仅皮肤的底层渲染不同。完整组件列表（Checkbox、RadioButton、Progress、Chip、Tabs、ListItem、Avatar、Badge、EmptyState、Skeleton、FAB、BottomSheet 等）参考 `hyper-neumorphic` skill 的"扩展组件库"章节。

### 常用控件清单

纯玻璃态 Skill 至少覆盖以下控件。API 命名使用 `Glass*` 前缀，参数与 `hyper-neumorphic` 中的 `Neumorphic*` 保持同构。

| 分类 | 控件 | 玻璃态实现要点 |
|------|------|----------------|
| 操作 | Button、IconButton、FAB、SplitButton、ToggleButton、SegmentedControl | 默认 `glassConvex`，按下/选中 `glassConcave`，保持半透明 tint |
| 输入 | Input、PasswordInput、SearchBar、TextArea | `glassConcave` 输入面，聚焦时加强 borderHi 或 accent 内描边 |
| 数值 | Slider、RangeSlider、Stepper、RatingBar | 轨道凹陷，thumb/按钮凸起，active 区间用 accent 半透明填充 |
| 选择 | Checkbox、RadioButton、Switch、Chip、DropdownMenu、DatePicker、TimePicker | 选中态凹陷或 accent 低透明填充，不使用纯实色块 |
| 导航 | TopAppBar、Tabs、NavigationBar、NavigationRail、Breadcrumb、PagerIndicator | 当前项用玻璃凸起/凹陷面；导航容器可轻透但文字必须足够对比 |
| 展示 | Card、ListItem、GridTile、StatisticCard、Timeline、Avatar、Badge、Tag、Tooltip | 让全局 mesh 透出，避免大面积纯白遮罩 |
| 反馈 | Dialog、BottomSheet、Snackbar、ToastHost、Progress、Spinner、Skeleton、EmptyState、ErrorState | 浮层全部使用 overlay 版本，确保脱离页面背景也成立 |
| 容器 | Section、SettingsGroup、ExpandablePanel、Carousel、PullRefreshContainer | Section 不套 Section；用间距和透明度建立层级 |

### 玻璃态控件适配规则

| 控件 | 核心Modifier | 注意点 |
|------|-------------|--------|
| IconButton | 44dp 点击目标 + `glassConvex(22.dp)` | 图标色使用 `textPrimary/textSecondary` |
| SegmentedControl | 外层 `glassConcave` + 选中段 `glassConvex` | 选中滑块不要完全不透明 |
| SearchBar | `glassConcave(18.dp)` + leading/trailing IconButton | 清除按钮用小型凸起圆 |
| PasswordInput | 与 Input 同构 | trailing 眼睛图标不要破坏输入凹陷面 |
| TextArea | `glassConcave(18.dp)` + minLines | 支持 supportingText/error |
| Stepper | 减号/加号 `glassConvex` + 中间读数 `glassConcave` | 边界禁用 alpha 0.48 |
| RangeSlider | 凹陷轨道 + 双凸起 thumb | track active 色 alpha 0.35-0.55 |
| RatingBar | 横排 IconButton | 选中可用 accent，未选中用 textTertiary |
| TopAppBar | 透明或轻 `glassConvex` | 若内容滚动到下方，增加 1dp 玻璃描边 |
| NavigationBar | 底部 `glassConvexOverlay` 或页面内 `glassConvex` | 独立悬浮时用 overlay |
| DropdownMenu | Popup/Dialog + `glassConvexOverlay` | 必须自带 mesh，不能依赖页面底图 |
| Snackbar | 底部 `glassConvexOverlay` | action 使用 GlassButton 小尺寸 |
| DatePicker/TimePicker | Dialog + `glassConvexOverlay` | 日期/数字格子保持 44dp 点击目标 |
| Skeleton | `glassConcave` + shimmer | shimmer alpha 保持低，避免闪烁 |
| Carousel | HorizontalPager + PagerIndicator | 卡片固定宽高，mesh 对位随卡片移动 |
| ExpandablePanel | Header `glassConvex` + AnimatedVisibility | 展开区用同一容器内 padding，不再套卡片 |

### 状态与无障碍

- 所有可交互组件提供 `enabled`，禁用时 `alpha(0.48f)` 且不触发 haptic。
- Dialog、BottomSheet、Dropdown、Snackbar、Tooltip 等独立浮层使用 `glassConvexOverlay/glassConcaveOverlay`。
- 输入、Tab、Navigation item、Slider 等必须提供 focused/selected/pressed 状态。
- 纯图标操作必须有 `contentDescription`；装饰图标传 `null`。
- 小尺寸视觉元素仍需 44dp 以上点击目标。

### 按钮短按动画修复

所有可点击组件需使用 `detectTapGestures` + `pointerInput` 模式，而非 `MutableInteractionSource`。详见 `hyper-neumorphic` skill Step 7-8 的修复后代码。

## 禁止与注意

- **GlassTheme 必须包裹在最外层**
- **不要同时用 neumorphism + glassmorphism**
- **`positionInWindow()` 是关键**：不调用会退化成纯色半透板
- **玻璃面必须使用 `clip()`**：否则 mesh 绘制到圆角外
- **tintConvex 不宜超过 0.25**：太高遮蔽 mesh 光斑
- **浅色玻璃 tintConcave 要更低**：7% 足够
- **首帧用 `LocalConfiguration.screenWidthDp/screenHeightDp`**：不能依赖 `view.width/height`
