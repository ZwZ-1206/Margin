package com.liubai.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.liubai.core.designsystem.component.ScreenHeader
import com.liubai.core.designsystem.component.SectionCard
import com.liubai.core.designsystem.theme.LocalExtraColors

private data class HelpItem(val q: String, val a: String)

private val HELP_ITEMS = listOf(
    HelpItem(
        "屏幕数据为什么有时不准？",
        "系统只在使用事件发生时才记账，且各家厂商对后台有不同节流策略；部分厂商还会杀掉后台。遇到缺口时，统计页会标注为估算值——宁可告诉你不确定，也不假装精确。打开「精确监控」能显著减少缺口。",
    ),
    HelpItem(
        "专注进行中被系统杀掉怎么办？",
        "引擎每秒把计时状态写进本地数据库，同时登记了一个精确闹钟。即使进程被回收，重新打开或闹钟触发时会从断点继续并归档本次专注。",
    ),
    HelpItem(
        "为什么要「使用情况访问」权限？",
        "这是 Android 读取各应用使用时长的唯一官方途径，系统会在授权页明确提示。可以拒绝：拒绝后专注、标签、白噪音全部照常，只有屏幕洞察不可用。",
    ),
    HelpItem(
        "限额和挑战为什么不那么及时？",
        "默认每 15 分钟检查一次（这是安卓后台任务的系统下限），亮屏与解锁时也会顺带检查。想要更及时？在设置页打开「精确监控」，会常驻一条通知、每 60 秒检查一次——代价是常驻通知与更多耗电。即便如此，它仍只是提醒，不会拦截或锁屏。",
    ),
    HelpItem(
        "数据存在哪？会上传吗？",
        "全部在你手机的本地数据库里。没有账号、没有服务器、没有分析 SDK。备份导出的是单个 JSON 文件，由你用系统文件选择器自己保存。",
    ),
    HelpItem(
        "换手机怎么迁移？",
        "旧手机设置页「导出备份」得到 JSON，传到新手机后用「导入备份」选择该文件即可。同一份文件可以重复导入，重复记录会被自动跳过。",
    ),
    HelpItem(
        "白噪音为什么这么小？",
        "六种音色都是实时合成的（雨声、溪流、森林、海浪、咖啡馆、棕噪音），没有打包任何音频文件，所以安装包只有 2.7MB。",
    ),
    HelpItem(
        "赞助后会解锁什么？",
        "什么都不会。所有功能本来就是免费的——赞助只是如果你想请开发者喝杯咖啡。",
    ),
)

@Composable
fun HelpScreen(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    val extras = LocalExtraColors.current
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            ScreenHeader(
                title = "帮助",
                subtitle = "留白想做的事很简单：把时间看明白，然后放下手机。",
                onBack = onBack,
            )
        }
        items(HELP_ITEMS.size) { i ->
            val item = HELP_ITEMS[i]
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column {
                        Text(item.q, style = MaterialTheme.typography.titleLarge, color = extras.textPrimary)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            item.a,
                            style = MaterialTheme.typography.bodyMedium,
                            color = extras.textSecondary,
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
