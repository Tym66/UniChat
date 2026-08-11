package com.unichat.app.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unichat.app.data.ModuleCategory
import com.unichat.app.data.ModuleInfo
import com.unichat.app.ui.components.CircleIconButton
import com.unichat.app.ui.components.ModuleCard
import com.unichat.app.ui.components.SectionTitle
import com.unichat.app.ui.components.UniSearchBar

/** 模块搜索页:大标题居中 + 右上刷新 + 分类切换 + 搜索 + 模块卡片列表(顶栏避让摄像头) */
@Composable
fun ModuleSearchScreen(
    modules: List<ModuleInfo>,
    query: String,
    onQueryChange: (String) -> Unit,
    category: String,
    onCategoryChange: (String) -> Unit,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onModuleClick: (ModuleInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        // 顶部:居中大标题 + 右上角圆形刷新按钮(避让摄像头)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 12.dp)
        ) {
            Text(
                text = "模块",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            CircleIconButton(
                icon = Icons.Rounded.Refresh,
                contentDescription = "刷新",
                onClick = onRefresh,
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 20.dp)
            )
        }
        UniSearchBar(
            value = query,
            onValueChange = onQueryChange,
            placeholder = "搜索模块",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        // 分类切换
        Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
            CategoryChip("LSPosed 模块", ModuleCategory.LSPOSED, category, onCategoryChange)
            Spacer(modifier = Modifier.width(10.dp))
            CategoryChip("Magisk 模块", ModuleCategory.MAGISK, category, onCategoryChange)
        }
        when {
            loading && modules.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                }
            }
            error != null && modules.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "点击右上角重试",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 8.dp).clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onRefresh
                            )
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 20.dp, end = 20.dp, top = 4.dp, bottom = 96.dp
                    )
                ) {
                    item {
                        SectionTitle(
                            text = if (query.isNotBlank()) "搜索结果" else
                                if (category == ModuleCategory.LSPOSED) "LSPosed / Xposed 模块" else "Magisk 模块"
                        )
                    }
                    if (modules.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "暂无数据\n点击右上角刷新从 GitHub 拉取",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        items(modules, key = { it.id }) { module ->
                            ModuleCard(module = module, onClick = { onModuleClick(module) })
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(
    label: String,
    value: String,
    current: String,
    onSelect: (String) -> Unit
) {
    val selected = current == value
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onSelect(value) }
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
