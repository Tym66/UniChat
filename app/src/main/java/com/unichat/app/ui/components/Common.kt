package com.unichat.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unichat.app.ui.theme.GraySecondary
import com.unichat.app.ui.theme.InkBlack
import com.unichat.app.ui.theme.SearchBarFill

/** 浅灰色圆角搜索框:内置放大镜图标 + 占位文字 */
@Composable
fun UniSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(SearchBarFill)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = "搜索",
            tint = GraySecondary,
            modifier = Modifier.size(20.dp)
        )
        Box(modifier = Modifier.padding(start = 8.dp)) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    color = GraySecondary,
                    fontSize = 14.sp
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = InkBlack, fontSize = 14.sp),
                cursorBrush = SolidColor(InkBlack),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** 板块小标题:浅灰色小号文字,无分割线 */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = GraySecondary,
        fontSize = 12.sp,
        modifier = modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp)
    )
}

@Composable
fun Spacer16() {
    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
}
