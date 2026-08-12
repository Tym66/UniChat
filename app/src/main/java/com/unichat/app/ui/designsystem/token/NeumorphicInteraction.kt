package com.unichat.app.ui.designsystem.token

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 新拟态轻触:缩放动画 + 触觉反馈,无水波纹。
 * 用于已有自身样式的元素(卡片/列表项/芯片)。
 */
@Composable
fun Modifier.neumorphicTap(
    enabled: Boolean = true,
    scalePressed: Float = 0.96f,
    durationMs: Int = 150,
    hapticType: HapticFeedbackType = HapticFeedbackType.TextHandleMove,
    onClick: () -> Unit
): Modifier {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val scaleAnim = remember { Animatable(1f) }

    return this
        .scale(scaleAnim.value)
        .pointerInput(enabled) {
            if (enabled) detectTapGestures(
                onPress = {
                    scope.launch { scaleAnim.snapTo(scalePressed) }
                    tryAwaitRelease()
                    scope.launch {
                        delay(60)
                        scaleAnim.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing))
                    }
                },
                onTap = { haptic.performHapticFeedback(hapticType); onClick() }
            )
        }
}
