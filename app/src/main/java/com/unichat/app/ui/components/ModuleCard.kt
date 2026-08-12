package com.unichat.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unichat.app.data.ModuleCategory
import com.unichat.app.data.ModuleInfo
import com.unichat.app.ui.designsystem.theme.neumorphic3D
import com.unichat.app.ui.designsystem.token.neumorphicTap

/**
 * 模块列表卡片:
 * 左侧方形圆角图标 / 右侧两行(加粗名称 + 浅灰包名) / 最右灰色 ">"
 * 卡片几乎无阴影,轻微圆角,大量留白。颜色跟随主题。
 */
@Composable
fun ModuleCard(
    module: ModuleInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .neumorphic3D(cornerRadius = 20.dp, elevation = 5.dp)
            .neumorphicTap(scalePressed = 0.97f, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧方形圆角图标
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(categoryColor(module.category)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = module.name.take(1).ifBlank { "?" },
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        // 右侧两行文字
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = module.name,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = module.packageName.ifBlank { module.author },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        // 最右灰色向右箭头
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = "进入",
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(22.dp)
        )
    }
}

private fun categoryColor(category: String): Color = when (category) {
    ModuleCategory.MAGISK -> Color(0xFF4CAF50)
    ModuleCategory.LSPOSED -> Color(0xFF1677FF)
    else -> Color(0xFF9E9E9E)
}
