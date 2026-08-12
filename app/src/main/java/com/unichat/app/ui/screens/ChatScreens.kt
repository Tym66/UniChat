package com.unichat.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unichat.app.data.Contact
import com.unichat.app.data.Direction
import com.unichat.app.data.Message
import com.unichat.app.data.MsgType
import com.unichat.app.data.Platform
import com.unichat.app.data.SyncStat
import com.unichat.app.ui.components.ContactCard
import com.unichat.app.ui.components.SectionTitle
import com.unichat.app.ui.components.UniSearchBar
import com.unichat.app.ui.designsystem.theme.LocalHyperColors
import com.unichat.app.ui.designsystem.theme.neumorphicConvex
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 聊天聚合页:顶部大标题居中 + 搜索框 + 联系人卡片列表(顶栏避让摄像头) */
@Composable
fun ChatListScreen(
    contacts: List<Contact>,
    query: String,
    syncStats: List<SyncStat>,
    syncing: Boolean,
    syncMessage: String?,
    onSync: () -> Unit,
    onQueryChange: (String) -> Unit,
    onContactClick: (Contact) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        // 顶部大标题(居中),顶部 padding 避让状态栏/摄像头
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 12.dp)
        ) {
            Text(
                text = "聊天",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        // 平台接入状态条(诊断):显示微信/抖音是否已接入、最近同步
        SyncStatusRow(
            syncStats = syncStats,
            syncing = syncing,
            syncMessage = syncMessage,
            onSync = onSync
        )
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

/** 平台接入状态条:绿点=已接入并最近同步,灰点=未检测到数据流 */
@Composable
private fun SyncStatusRow(
    syncStats: List<SyncStat>,
    syncing: Boolean,
    syncMessage: String?,
    onSync: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SyncChip(label = "微信", stat = syncStats.firstOrNull { it.platform == Platform.WECHAT })
            SyncChip(label = "抖音", stat = syncStats.firstOrNull { it.platform == Platform.DOUYIN })
            Spacer(modifier = Modifier.weight(1f))
            // 立即同步按钮(HyperOS 风格胶囊)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onSync
                    )
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "立即同步",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (syncing) "同步中…" else "同步",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        syncMessage?.let {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = it,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SyncChip(label: String, stat: SyncStat?) {
    val active = stat?.hookInstalled == true
    val dotColor = if (active) Color(0xFF34C759) else MaterialTheme.colorScheme.outlineVariant
    val text = when {
        stat == null -> "$label 未接入"
        !active -> "$label 未接入"
        stat.msgCount > 0 -> "$label 已同步 ${stat.msgCount} 条"
        else -> "$label 已接入"
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 单聊详情:跨平台消息按时间线合并展示,支持返回键 */
@Composable
fun ChatDetailScreen(
    contact: Contact?,
    messages: List<Message>,
    onBack: () -> Unit,
    onMarkRead: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val name = contact?.name ?: "联系人"
    // 系统返回键支持
    BackHandler(onBack = onBack)

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // 顶栏:返回按钮 + 居中标题(避让摄像头)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 4.dp, end = 8.dp)
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(26.dp)
                )
            }
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = name,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                contact?.let { c ->
                    if (c.platforms.isNotBlank()) {
                        Text(
                            text = c.platforms.split(",").joinToString(" / ") {
                                if (it == Platform.WECHAT) "微信" else "抖音"
                            } + " 聚合",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(48.dp))
        }
        androidx.compose.material3.HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant
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
    val colors = LocalHyperColors.current
    val bubbleColor = if (isOut) colors.primary else colors.cardBackground
    val textColor = if (isOut) Color.White else colors.textPrimary
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOut) Arrangement.End else Arrangement.Start
    ) {
        Column(horizontalAlignment = if (isOut) Alignment.End else Alignment.Start) {
            Text(
                text = "$platformTag · ${formatTime(msg.timestamp)}",
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
            Box(
                modifier = Modifier
                    .then(
                        if (isOut) Modifier
                        else Modifier.neumorphicConvex(cornerRadius = 14.dp, elevation = 3.dp)
                    )
                    .clip(RoundedCornerShape(14.dp))
                    .background(bubbleColor)
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Text(
                    text = displayContent(msg),
                    fontSize = 14.sp,
                    color = textColor,
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
                tint = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
private fun formatTime(ts: Long): String = timeFmt.format(Date(ts))
