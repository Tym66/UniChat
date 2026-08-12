package com.unichat.app.ui.designsystem.theme

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

/** 获取当前深浅模式下的新拟态浮雕阴影色 */
@Composable
private fun neumorphicShadowColors(): Triple<Color, Color, Color> {
    val isDark = LocalHyperColors.current === DarkHyperColors
    val bg = if (isDark) DarkHyperColors.background else LightHyperColors.background
    val light = if (isDark) NeumDarkLight else NeumLightHighlight
    val dark = if (isDark) NeumDarkDark else NeumLightShadow
    return Triple(bg, light, dark)
}

/**
 * 凸起面:双色浮雕阴影(左上高光 + 右下暗影),模拟 3D 凸起。
 */
@Composable
fun Modifier.neumorphicConvex(
    cornerRadius: Dp = 24.dp,
    elevation: Dp = 6.dp,
): Modifier {
    val (bgColor, lightShadow, darkShadow) = neumorphicShadowColors()
    return this
        .clip(RoundedCornerShape(cornerRadius))
        .background(bgColor)
        .drawBehind {
            drawIntoCanvas { canvas ->
                val paint = Paint().apply {
                    asFrameworkPaint().apply {
                        isAntiAlias = true
                        maskFilter = BlurMaskFilter(elevation.toPx(), BlurMaskFilter.Blur.NORMAL)
                    }
                }
                // 右下暗影
                paint.color = darkShadow
                canvas.drawRoundRect(
                    left = elevation.toPx() * 0.5f,
                    top = elevation.toPx() * 0.5f,
                    right = size.width + elevation.toPx() * 0.5f,
                    bottom = size.height + elevation.toPx() * 0.5f,
                    radiusX = cornerRadius.toPx(),
                    radiusY = cornerRadius.toPx(),
                    paint = paint
                )
                // 左上高光
                paint.color = lightShadow
                canvas.drawRoundRect(
                    left = -elevation.toPx() * 0.5f,
                    top = -elevation.toPx() * 0.5f,
                    right = size.width - elevation.toPx() * 0.5f,
                    bottom = size.height - elevation.toPx() * 0.5f,
                    radiusX = cornerRadius.toPx(),
                    radiusY = cornerRadius.toPx(),
                    paint = paint
                )
            }
        }
}

/** 凹陷面:内阴影模拟凹陷 */
@Composable
fun Modifier.neumorphicConcave(
    cornerRadius: Dp = 24.dp,
    elevation: Dp = 4.dp,
): Modifier {
    val (_, lightShadow, darkShadow) = neumorphicShadowColors()
    return this
        .clip(RoundedCornerShape(cornerRadius))
        .background(LocalHyperColors.current.cardBackground)
        .drawWithContent {
            drawIntoCanvas { canvas ->
                val radiusPx = cornerRadius.toPx()
                val elevationPx = elevation.toPx()
                val blur = BlurMaskFilter(elevationPx, BlurMaskFilter.Blur.NORMAL)

                // 左上暗内阴影
                val darkPaint = Paint().apply {
                    color = darkShadow.copy(alpha = 0.8f)
                    asFrameworkPaint().apply {
                        isAntiAlias = true
                        maskFilter = blur
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = elevationPx * 2
                    }
                }
                canvas.drawRoundRect(
                    left = -elevationPx * 0.5f, top = -elevationPx * 0.5f,
                    right = size.width, bottom = size.height,
                    radiusX = radiusPx, radiusY = radiusPx,
                    paint = darkPaint
                )
                // 右下亮内高光
                val lightPaint = Paint().apply {
                    color = lightShadow
                    asFrameworkPaint().apply {
                        isAntiAlias = true
                        maskFilter = blur
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = elevationPx * 2
                    }
                }
                canvas.drawRoundRect(
                    left = 0f, top = 0f,
                    right = size.width + elevationPx * 0.5f,
                    bottom = size.height + elevationPx * 0.5f,
                    radiusX = radiusPx, radiusY = radiusPx,
                    paint = lightPaint
                )
            }
            drawContent()
        }
}

/** 便捷别名:凸起卡片(28dp 大圆角) */
@Composable
fun Modifier.neumorphic3D(
    cornerRadius: Dp = 28.dp,
    elevation: Dp = 8.dp,
): Modifier = this.neumorphicConvex(cornerRadius, elevation)
