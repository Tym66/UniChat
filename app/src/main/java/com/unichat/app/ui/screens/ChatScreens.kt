package com.unichat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unichat.app.data.Contact
import com.unichat.app.data.Direction
import com.unichat.app.data.Message
import com.unichat.app.data.MsgType
import com.unichat.app.data.Platform
import com.unichat.app.ui.components.ContactCard
import com.unichat.app.ui.components.SectionTitle
import com.unichat.app.ui.components.UniSearchBar
import com.unichat.app.ui.theme.GraySecondary
import com.unichat.app.ui.theme.InkBlack
import com.unichat.app.ui.theme.SearchBarFill
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 聊天聚合页:顶部大标题居中 + 搜索框 + 联系人卡片列表 */
@Composable
fun ChatListScreen(
    contacts: List<Contact>,
    query: String,
    onQueryChange: (String) -> Unit,
    onContactClick: (Contact) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        // 顶部大标题(居中)
        Box(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Text(
                text = "聊天",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = InkBlack,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        UniSearchBar(
            value = query,
            onValueChange = onQueryChange,
            placeholder = "搜索联系人",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp, end = 20.dp, top = 4.dp, bottom = 96.dp
            )
        ) {
            item { SectionTitle(text = "聚合会话") }
            if (contacts.isEmpty()) {
                item {
                    EmptyHint("暂无会话\n微信/抖音收到新消息后会自动聚合到这里")
                }
            } else {
                items(contacts, key = { it.id }) { contact ->
                    ContactCard(contact = contact, onClick = { onContactClick(contact) })
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}

/** 单聊详情:跨平台消息按时间线合并展示 */
@Composable
fun ChatDetailScreen(
    contact: Contact?,
    messages: List<Message>,
    onBack: () -> Unit,
    onMarkRead: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val name = contact?.name ?: "联系人"
    Column(modifier = modifier.fillMaxSize().background(Color.White)) {
        // 顶栏:返回 + 居中标题 + 平台信息
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.TextButton(onClick = onBack) {
                Text("‹", fontSize = 28.sp, color = InkBlack)
            }
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = InkBlack)
                contact?.let { c ->
                    if (c.platforms.isNotBlank()) {
                        Text(
                            text = c.platforms.split(",").joinToString(" / ") {
                                if (it == Platform.WECHAT) "微信" else "抖音"
                            } + " 聚合",
                            fontSize = 10.sp,
                            color = GraySecondary
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(48.dp))
        }
        androidx.compose.material3.HorizontalDivider(
            thickness = 0.5.dp,
            color = Color(0xFFEEEEEE)
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp
            )
        ) {
            if (messages.isEmpty()) {
                item { EmptyHint("暂无消息\n跨平台消息会按时间线显示在这里") }
            } else {
                items(messages, key = { it.id }) { msg ->
                    MessageBubble(msg)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: Message) {
    val isOut = msg.direction == Direction.OUT
    val platformTag = if (msg.platform == Platform.WECHAT) "微信" else "抖音"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOut) androidx.compose.foundation.layout.Arrangement.End else androidx.compose.foundation.layout.Arrangement.Start
    ) {
        Column(horizontalAlignment = if (isOut) Alignment.End else Alignment.Start) {
            Text(
                text = "$platformTag · ${formatTime(msg.timestamp)}",
                fontSize = 9.sp,
                color = GraySecondary,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 14.dp, topEnd = 14.dp,
                            bottomStart = if (isOut) 14.dp else 4.dp,
                            bottomEnd = if (isOut) 4.dp else 14.dp
                        )
                    )
                    .background(if (isOut) Color(0xFF1677FF) else Color(0xFFF2F3F5))
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Text(
                    text = displayContent(msg),
                    fontSize = 14.sp,
                    color = if (isOut) Color.White else InkBlack,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

private fun displayContent(msg: Message): String = when (msg.type) {
    MsgType.IMAGE -> "🖼️ [图片]"
    MsgType.VOICE -> "🎤 [语音]"
    MsgType.VIDEO -> "🎬 [视频]"
    MsgType.FILE -> "📎 [文件] ${msg.content}"
    MsgType.LINK -> "🔗 [链接] ${msg.content}"
    MsgType.SYSTEM -> msg.content.ifBlank { "[系统消息]" }
    else -> msg.content
}

@Composable
private fun EmptyHint(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(top = 80.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                tint = Color(0xFFDDDDDD),
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = text,
                color = GraySecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
private fun formatTime(ts: Long): String = timeFmt.format(Date(ts))
