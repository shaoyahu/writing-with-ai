package com.yy.writingwithai.feature.quicknote.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * feishu-bidir-sync · 冲突解决对话框(tasks §5.1)。
 *
 * spec: openspec/changes/feishu-bidir-sync/specs/feishu-bidir-sync/spec.md
 * "Conflict detection and resolution"
 *
 * 三选项:保留本地 / 保留飞书(默认)/ 取消。
 */
@Composable
fun ConflictResolutionDialog(
    localPreview: String,
    remotePreview: String,
    onResolveKeepLocal: () -> Unit,
    onResolveKeepRemote: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("飞书同步冲突") },
        text = {
            Column {
                Text("本地和飞书两端都被修改过,请选择保留哪一版:")
                Spacer(modifier = Modifier.padding(top = 12.dp))
                Text(
                    text = "本地版本:",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = localPreview.take(200),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.padding(top = 8.dp))
                Text(
                    text = "飞书版本:",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = remotePreview.take(200),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onResolveKeepRemote) {
                Text("保留飞书")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onCancel) {
                    Text("取消")
                }
                Spacer(modifier = Modifier.padding(start = 8.dp))
                TextButton(onClick = onResolveKeepLocal) {
                    Text("保留本地")
                }
            }
        }
    )
}