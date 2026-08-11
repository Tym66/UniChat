package com.unichat.app.ui.screens

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.unichat.app.BuildConfig
import com.unichat.app.R
import com.unichat.app.ui.components.CircleIconButton

/**
 * 《关于》页面
 *
 * UI 规格:
 * - 左上角大号粗体标题"关于",右上角齿轮设置图标
 * - 居中蓝色矢量艺术字母 YM Logo + 软件名 UniChat + slogan + 版本号
 * - 圆角信息卡片:开发者(GitHub 头像+链接)、设备系统信息
 * - 底部:打赏卡片(微信收款码)
 * - 纯白/纯黑背景、大量留白、扁平化、微弱圆角、无阴影,颜色跟随主题
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

        // ===== 顶部:左上标题 + 右上齿轮(避让摄像头) =====
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp)
                .padding(vertical = 14.dp)
        ) {
            Text(
                text = "关于",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            CircleIconButton(
                icon = Icons.Rounded.Settings,
                contentDescription = "设置",
                onClick = onSettingsClick,
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ===== 居中 YM Logo =====
            Spacer(modifier = Modifier.height(24.dp))
            YmLogo()
            Spacer(modifier = Modifier.height(18.dp))

            // ===== 软件名称 =====
            Text(
                text = "UniChat",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(6.dp))

            // ===== slogan =====
            Text(
                text = "聚合微信 · 抖音,一个会话搞定",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))

            // ===== 版本号 =====
            Text(
                text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(28.dp))

            // ===== 开发者信息卡片 =====
            InfoCard(title = "开发者") {
                // GitHub 头像(方形圆角)
                AsyncImage(
                    model = "https://avatars.githubusercontent.com/Tym66",
                    contentDescription = "开发者头像",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Spacer(modifier = Modifier.height(10.dp))
                InfoRow(label = "作者", value = "Tym66")
                InfoRow(label = "GitHub", value = "github.com/Tym66/UniChat", url = "https://github.com/Tym66/UniChat")
                InfoRow(label = "开源协议", value = "MIT License")
            }
            Spacer(modifier = Modifier.height(14.dp))

            // ===== 设备系统信息卡片 =====
            InfoCard(title = "设备信息") {
                InfoRow(label = "机型", value = "${Build.MANUFACTURER} ${Build.MODEL}")
                InfoRow(label = "系统", value = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                InfoRow(label = "架构", value = Build.SUPPORTED_ABIS.firstOrNull() ?: "未知")
            }
            Spacer(modifier = Modifier.height(14.dp))

            // ===== 功能信息卡片 =====
            InfoCard(title = "功能") {
                InfoRow(label = "聊天聚合", value = "微信 + 抖音 同人会话")
                InfoRow(label = "模块搜索", value = "Magisk / LSPosed 仓库")
            }
            Spacer(modifier = Modifier.height(14.dp))

            // ===== 打赏卡片(位于页面最底部) =====
            InfoCard(title = "打赏支持") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Favorite,
                        contentDescription = null,
                        tint = Color(0xFFE53950),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "如果 UniChat 对你有帮助,可以请作者喝杯咖啡 ☕",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                // 微信收款码
                Image(
                    painter = painterResource(R.drawable.pay_qr),
                    contentDescription = "微信收款码",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
            }
            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "仅供学习研究,请勿用于违规用途\n微信、抖音为各自公司商标,本工具与其无任何关联",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** 蓝色矢量艺术字母 YM Logo */
@Composable
fun YmLogo(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(88.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0xFF1677FF)),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Y",
                fontSize = 40.sp,
                fontWeight = FontWeight.Black,
                color = Color.White
            )
            Text(
                text = "M",
                fontSize = 40.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFFBBD8FF)
            )
        }
    }
}

/** 圆角信息卡片(跟随主题) */
@Composable
private fun InfoCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Text(
            text = title,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(10.dp))
        content()
    }
}

/** 信息行:label 左 / value 右 */
@Composable
private fun InfoRow(
    label: String,
    value: String,
    url: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            fontSize = 13.sp,
            color = if (url != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (url != null) FontWeight.Medium else FontWeight.Normal,
            textAlign = TextAlign.End
        )
    }
}
