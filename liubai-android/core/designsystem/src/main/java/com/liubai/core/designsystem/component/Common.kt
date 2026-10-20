package com.liubai.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.liubai.core.designsystem.theme.LocalExtraColors

/** 通用分区卡片 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val extras = LocalExtraColors.current
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = extras.card),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(modifier = Modifier.padding(20.dp), content = content)
    }
}

/** 统计小方块：数值 + 标签 */
@Composable
fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    valueColor: Color = LocalExtraColors.current.textPrimary,
) {
    val extras = LocalExtraColors.current
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(extras.window)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = valueColor)
        Text(label, style = MaterialTheme.typography.labelSmall, color = extras.textTertiary)
    }
}

/** 分段选择器（专注模式 / 统计周期等） */
@Composable
fun <T> SegmentedToggle(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
) {
    val extras = LocalExtraColors.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(extras.window)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { opt ->
            val active = opt == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onSelect(opt) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(opt),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) MaterialTheme.colorScheme.onPrimary else extras.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** 空状态：图形位 + 文案 + 可选动作 */
@Composable
fun EmptyState(
    title: String,
    desc: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val extras = LocalExtraColors.current
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
        )
        Text(title, style = MaterialTheme.typography.titleLarge, color = extras.textPrimary)
        Text(
            desc,
            style = MaterialTheme.typography.bodySmall,
            color = extras.textTertiary,
            textAlign = TextAlign.Center,
        )
        action?.invoke()
    }
}

/**
 * 二级页面标题栏：可选返回箭头 + 大标题 + 说明。
 *
 * 返回箭头用负 offset 抵消 IconButton 自带的 48dp 触控区内边距，
 * 让图标视觉边缘与页面内容的 20dp 边距对齐。
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
) {
    val extras = LocalExtraColors.current
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.offset(x = (-10).dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = extras.textPrimary,
                    )
                }
            }
            Text(
                title,
                style = MaterialTheme.typography.displaySmall,
                color = extras.textPrimary,
            )
        }
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = extras.textTertiary,
            )
        }
    }
}
