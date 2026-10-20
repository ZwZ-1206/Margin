package com.liubai.core.designsystem.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * 权限说明弹窗：统一的「说明用途 → 去授权 / 稍后」交互。
 * 每次用户主动点击权限入口都会先弹它，而不是直接跳系统设置——
 * 让用户知道为什么要这个权限，比直接甩一个设置页更容易被接受。
 */
@Composable
fun PermissionRationaleDialog(
    title: String,
    message: String,
    confirmText: String = "去授权",
    dismissText: String = "稍后",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissText) } },
    )
}
