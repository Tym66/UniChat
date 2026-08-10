package com.unichat.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.unichat.app.ui.theme.GraySecondary
import com.unichat.app.ui.theme.OverlayFill

/**
 * 底部悬浮半透明圆角操作栏:三个简约线性图标按钮(聊天/模块/关于)
 */
@Composable
fun FloatingActionBar(
    onChatClick: () -> Unit,
    onModuleClick: () -> Unit,
    onAboutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(OverlayFill)
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onChatClick) {
            Icon(
                imageVector = Icons.Rounded.ChatBubbleOutline,
                contentDescription = "聊天聚合",
                tint = GraySecondary,
                modifier = Modifier.size(26.dp)
            )
        }
        IconButton(onClick = onModuleClick) {
            Icon(
                imageVector = Icons.Rounded.Extension,
                contentDescription = "模块搜索",
                tint = GraySecondary,
                modifier = Modifier.size(26.dp)
            )
        }
        IconButton(onClick = onAboutClick) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = "关于",
                tint = GraySecondary,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

/** 右上角圆形功能图标按钮 */
@Composable
fun CircleIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFFF2F3F5)),
        contentAlignment = Alignment.Center
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = InkBlack,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private val InkBlack = Color(0xFF1A1A1A)
