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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.unichat.app.data.Contact
import com.unichat.app.data.Platform
import com.unichat.app.ui.theme.GraySecondary
import com.unichat.app.ui.theme.InkBlack

/** 聊天列表联系人卡片:头像 + 名称 + 平台角标 + 未读红点 */
@Composable
fun ContactCard(
    contact: Contact,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = MutableInteractionSource()
            )
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 头像(首字母)
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Color(0xFF1677FF)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = contact.name.take(1).ifBlank { "?" },
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = contact.name.ifBlank { "未知联系人" },
                    color = InkBlack,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(6.dp))
                PlatformBadge(contact.platforms)
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = contact.lastMessage.ifBlank { "暂无消息" },
                color = GraySecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (contact.unreadCount > 0) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF3B30)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (contact.unreadCount > 99) "99+" else contact.unreadCount.toString(),
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun PlatformBadge(platforms: String) {
    val list = platforms.split(",").filter { it.isNotBlank() }
    if (list.isEmpty()) return
    Row {
        list.forEach { p ->
            val label = when (p) {
                Platform.WECHAT -> "微信"
                Platform.DOUYIN -> "抖音"
                else -> p
            }
            Text(
                text = label,
                color = Color(0xFF1677FF),
                fontSize = 9.sp,
                modifier = Modifier
                    .padding(end = 4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFEAF2FF))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }
    }
}
