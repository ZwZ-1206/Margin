package com.liubai.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.liubai.core.designsystem.theme.LocalExtraColors

/**
 * 六位解锁密码设置弹窗：输两次，校验位数与一致性。
 * 用于：设置页设密码、添加限额时补设密码。
 */
@Composable
fun PinSetupDialog(
    title: String = "设置解锁密码",
    subtitle: String = "六位数字。达到限额后锁定应用时，需要输入它才能继续使用。",
    onConfirm: (pin: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val extras = LocalExtraColors.current
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    val firstOk = first.length == 6
    val secondOk = second.length == 6
    val mismatch = firstOk && secondOk && first != second

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = extras.textSecondary,
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = first,
                    onValueChange = { first = it.filter { c -> c.isDigit() }.take(6) },
                    label = { Text("输入六位密码") },
                    singleLine = true,
                    isError = mismatch,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = second,
                    onValueChange = { second = it.filter { c -> c.isDigit() }.take(6) },
                    label = { Text("再输一次") },
                    singleLine = true,
                    isError = mismatch,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (mismatch) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "两次输入不一致",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "密码只以哈希形式存在本机。忘了它，可以在「设置 → 干预」随时修改，不需要旧密码。",
                    style = MaterialTheme.typography.labelSmall,
                    color = extras.textTertiary,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = firstOk && secondOk && !mismatch,
                onClick = { onConfirm(first) },
            ) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/**
 * 添加每日限额后的询问弹窗：达到限额后是否锁定该应用。
 *
 * 硬锁未就绪（总开关关着 / 没设密码）时也能点「锁定」——
 * 由调用方按「开总开关 → 设密码 → 落库」的顺序引导，不把用户丢在半路。
 */
@Composable
fun HardLockAskDialog(
    appLabel: String,
    hardLockReady: Boolean,
    onLock: () -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
) {
    val extras = LocalExtraColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("达到限额后锁定「$appLabel」？") },
        text = {
            Column {
                Text(
                    "锁定后：今日用量达到限额，再打开这个应用就会弹出解锁界面，" +
                        "输入六位密码才能继续用。解锁后有 10 分钟宽限，过期再次达到限额会重新锁定。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = extras.textSecondary,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "选「只提醒」则和以前一样：到点发一条通知，不拦截、不锁屏。",
                    style = MaterialTheme.typography.bodySmall,
                    color = extras.textTertiary,
                )
                if (!hardLockReady) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "硬锁还没有就绪：需要先在「设置 → 干预」打开总开关并设置六位密码。" +
                            "点「锁定」会一步步引导你完成。",
                        style = MaterialTheme.typography.labelSmall,
                        color = extras.warning,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "诚实说明：卸载留白、清除留白数据、在系统设置里关掉无障碍服务，都能绕过硬锁。" +
                        "留白不承诺它做不到的事。",
                    style = MaterialTheme.typography.labelSmall,
                    color = extras.textTertiary,
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onSkip) { Text("只提醒") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onLock) { Text("锁定") }
            }
        },
    )
}
