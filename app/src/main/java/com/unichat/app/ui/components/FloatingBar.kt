package com.unichat.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unichat.app.ui.theme.OverlayFill
import com.unichat.app.ui.designsystem.theme.LocalHyperColors
import com.unichat.app.ui.designsystem.theme.neumorphicConvex
import com.unichat.app.ui.designsystem.token.neumorphicTap

/**
 * HyperOS 新拟态底部 Dock:凸起胶囊 + 图标 + 文字 + 无涟漪触觉点击 + 选中高亮
 * @param selected 当前选中项:0=聊天 1=模块 2=关于
 */
@Composable
fun FloatingActionBar(
    selected: Int,
    onChatClick: () -> Unit,
    onModuleClick: () -> Unit,
    onAboutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalHyperColors.current
    val primary = colors.primary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .neumorphicConvex(cornerRadius = 32.dp, elevation = 8.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavItem(
            icon = Icons.Rounded.ChatBubbleOutline,
            label = "聊天",
            selected = selected == 0,
            primary = primary,
            onClick = onChatClick
        )
        NavItem(
            icon = Icons.Rounded.Extension,
            label = "模块",
            selected = selected == 1,
            primary = primary,
            onClick = onModuleClick
        )
        NavItem(
            icon = Icons.Rounded.Info,
            label = "关于",
            selected = selected == 2,
            primary = primary,
            onClick = onAboutClick
        )
    }
}

/** Dock 单项:图标 + 文字,选中时主题色高亮 + 浅色胶囊底 */
@Composable
private fun NavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    primary: Color,
    onClick: () -> Unit
) {
    val colors = LocalHyperColors.current
    val tint = if (selected) primary else colors.textSecondary
    Column(
        modifier = Modifier
            .neumorphicTap(scalePressed = 0.92f, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            color = tint,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
        )
    }
}

/** 右上角圆形功能图标按钮(跟随主题) */
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
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
