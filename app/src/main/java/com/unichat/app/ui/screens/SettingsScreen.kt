package com.unichat.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.Icon
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
import com.unichat.app.ui.ThemeMode
import com.unichat.app.ui.designsystem.theme.neumorphicConcave

/** 设置页:主题选择(跟随系统 / 日间 / 夜间),颜色跟随主题 */
@Composable
fun SettingsScreen(
    currentMode: String,
    onModeChange: (String) -> Unit,
    onBack: () -> Unit
) {
    // 系统返回键支持
    BackHandler(onBack = onBack)

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // 顶部:返回 + 居中标题(避让摄像头)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 8.dp, end = 8.dp)
                .padding(vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.align(Alignment.CenterStart),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onBack
                        )
                )
            }
            Text(
                text = "设置",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(
                text = "主题",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp)
            )

            // 主题选项卡片
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .neumorphicConcave(cornerRadius = 20.dp, elevation = 3.dp)
                    .padding(vertical = 6.dp)
            ) {
                ThemeOption(
                    icon = Icons.Rounded.PhoneAndroid,
                    title = "跟随系统",
                    desc = "自动匹配系统深浅色",
                    selected = currentMode == ThemeMode.SYSTEM,
                    onClick = { onModeChange(ThemeMode.SYSTEM) }
                )
                ThemeOption(
                    icon = Icons.Rounded.LightMode,
                    title = "日间模式",
                    desc = "纯白浅色界面",
                    selected = currentMode == ThemeMode.LIGHT,
                    onClick = { onModeChange(ThemeMode.LIGHT) }
                )
                ThemeOption(
                    icon = Icons.Rounded.DarkMode,
                    title = "夜间模式",
                    desc = "深色护眼界面",
                    selected = currentMode == ThemeMode.DARK,
                    onClick = { onModeChange(ThemeMode.DARK) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "更多设置即将上线",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun ThemeOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    desc: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (selected) primary else MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = desc,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // 选中圆点
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (selected) primary else MaterialTheme.colorScheme.outlineVariant),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.onPrimary)
                )
            }
        }
    }
}
