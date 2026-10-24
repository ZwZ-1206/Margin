package com.liubai.app.lock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liubai.core.common.DurationFormat
import com.liubai.core.designsystem.theme.LiubaiTheme
import com.liubai.core.designsystem.theme.LocalExtraColors
import com.liubai.data.di.ServiceLocator
import com.liubai.data.lock.AppLockManager
import com.liubai.data.usage.AppLabelResolver
import com.liubai.domain.model.AppSettings
import com.liubai.domain.model.ThemeMode
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

/**
 * 硬锁解锁界面：全屏、吞噬返回键、不进最近任务。
 *
 * 由 [AppLockService] 在被锁应用切到前台时拉起；密码正确即进入 10 分钟宽限期并关闭。
 * 若总开关被关闭或密码被清除，本界面会自动消失（不会把人关在外面）。
 */
class LockActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PACKAGE = "extra_package"

        @Volatile
        private var instance: LockActivity? = null

        @Volatile
        private var showingPkg: String? = null

        /** 锁定界面是否正在为某个应用显示 */
        fun isShowingFor(pkg: String): Boolean = showingPkg == pkg

        /** 总开关关闭等场景：直接收起锁定界面 */
        fun dismissIfOpen() {
            instance?.let { if (!it.isFinishing) it.finish() }
            showingPkg = null
            instance = null
        }
    }

    private var pkg: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pkg = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()

        setContent {
            val settings by ServiceLocator.settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = null)
            val dark = when (settings?.theme ?: ThemeMode.SYSTEM) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            LiubaiTheme(darkTheme = dark) {
                LockScreen(packageName = pkg, settings = settings)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        instance = this
        showingPkg = pkg
    }

    override fun onStop() {
        if (instance === this) {
            instance = null
            showingPkg = null
        }
        super.onStop()
    }
}

@Composable
private fun LockScreen(packageName: String, settings: AppSettings?) {
    val context = LocalContext.current
    val extras = LocalExtraColors.current
    val scope = rememberCoroutineScope()

    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var usageText by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }

    val label = remember(packageName) {
        runCatching { AppLabelResolver(context).label(packageName) }.getOrDefault(packageName)
    }

    // 用量文案 + 自检：总开关被关 / 密码被清 / 已在宽限期 → 直接放行
    LaunchedEffect(packageName) {
        val settings = runCatching { ServiceLocator.settingsRepository.current() }.getOrNull()
        if (settings == null || !settings.hardLockEnabled || settings.lockPasswordHash.isNullOrBlank()) {
            (context as? LockActivity)?.finish()
            return@LaunchedEffect
        }
        if (AppLockManager.inGrace(context, packageName)) {
            (context as? LockActivity)?.finish()
            return@LaunchedEffect
        }
        val (used, limit) = runCatching { AppLockManager.usageOf(packageName) }
            .getOrDefault(0L to 0L)
        usageText = if (limit > 0L) {
            "已使用 ${DurationFormat.chinese(used)} / 限额 ${DurationFormat.chinese(limit)}"
        } else {
            "已使用 ${DurationFormat.chinese(used)}"
        }
    }

    fun submit(entered: String) {
        if (checking) return
        checking = true
        scope.launch {
            val ok = runCatching { AppLockManager.unlock(context, packageName, entered) }
                .getOrDefault(false)
            checking = false
            if (ok) {
                (context as? LockActivity)?.finish()
            } else {
                error = true
                pin = ""
            }
        }
    }

    // 返回键吞掉：不允许「按返回绕过」
    BackHandler {}

    // 总开关被关 / 密码被清：立刻放行，不把人关在外面
    LaunchedEffect(settings?.hardLockEnabled, settings?.lockPasswordHash) {
        val s = settings ?: return@LaunchedEffect
        if (!s.hardLockEnabled || s.lockPasswordHash.isNullOrBlank()) {
            (context as? LockActivity)?.finish()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(12.dp))
            LockGlyph(color = extras.textSecondary)
            Spacer(Modifier.height(20.dp))
            Text(
                "$label 今日已达限额",
                style = MaterialTheme.typography.headlineSmall,
                color = extras.textPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                usageText,
                style = MaterialTheme.typography.bodyMedium,
                color = extras.textSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "输入六位数解锁密码继续使用",
                style = MaterialTheme.typography.bodySmall,
                color = extras.textTertiary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))

            // 六位密码点阵
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(6) { i ->
                    val filled = i < pin.length
                    Box(
                        Modifier
                            .size(if (filled) 14.dp else 12.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(
                                if (filled) MaterialTheme.colorScheme.primary
                                else extras.window
                            )
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            if (error) {
                Text(
                    "密码不对，再试一次",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                Spacer(Modifier.height(20.dp))
            }

            Spacer(Modifier.weight(1f))

            // 数字键盘
            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9")
            keys.chunked(3).forEach { row ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    row.forEach { k -> KeyPadButton(k) { pin = (pin + k).take(6); error = false } }
                }
                Spacer(Modifier.height(10.dp))
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(72.dp))
                KeyPadButton("0") { pin = (pin + "0").take(6); error = false }
                Box(
                    Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(36.dp))
                        .clickable(enabled = pin.isNotEmpty()) {
                            pin = pin.dropLast(1)
                            error = false
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "删除",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (pin.isEmpty()) extras.textTertiary else extras.textSecondary,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "解锁后 10 分钟内不再锁定。忘记密码？只能等到次日零点自动解锁——" +
                    "这是留白能给你的最后一道诚实边界。",
                style = MaterialTheme.typography.labelSmall,
                color = extras.textTertiary,
                textAlign = TextAlign.Center,
            )
        }
    }

    // 满六位自动校验
    LaunchedEffect(pin) {
        if (pin.length == 6) submit(pin)
    }
}

@Composable
private fun KeyPadButton(label: String, onClick: () -> Unit) {
    val extras = LocalExtraColors.current
    Box(
        Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(36.dp))
            .background(extras.window)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 24.sp,
            style = MaterialTheme.typography.headlineSmall,
            color = extras.textPrimary,
        )
    }
}

/** 自绘挂锁：material-icons-core 没有 Lock，不为此引入扩展图标库 */
@Composable
private fun LockGlyph(color: Color) {
    Canvas(Modifier.size(56.dp)) {
        val stroke = Stroke(width = 5f)
        val w = size.width
        val h = size.height
        // 锁梁
        drawArc(
            color = color,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.26f, h * 0.10f),
            size = Size(w * 0.48f, h * 0.48f),
            style = stroke,
        )
        // 锁体
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.16f, h * 0.44f),
            size = Size(w * 0.68f, h * 0.44f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f),
            style = stroke,
        )
    }
}
