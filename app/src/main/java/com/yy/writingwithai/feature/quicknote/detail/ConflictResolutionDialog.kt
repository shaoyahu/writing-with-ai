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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yy.writingwithai.R

/**
 * feishu-bidir-sync · 冲突解决对话框(tasks §5.1)。
 *
 * feishu-sync-end-to-end §3:3 处硬编码中文(标题/保留本地/保留飞书)替换为 stringResource,
 * 走 `R.string.feishu_conflict_*` 双语键。
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
        title = { Text(stringResource(R.string.feishu_conflict_title)) },
        text = {
            Column {
                Text(stringResource(R.string.feishu_conflict_body))
                Spacer(modifier = Modifier.padding(top = 12.dp))
                Text(
                    text = stringResource(R.string.feishu_conflict_local_label),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = localPreview.take(200),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.padding(top = 8.dp))
                Text(
                    text = stringResource(R.string.feishu_conflict_remote_label),
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
                Text(stringResource(R.string.feishu_conflict_keep_remote))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.feishu_conflict_cancel))
                }
                Spacer(modifier = Modifier.padding(start = 8.dp))
                TextButton(onClick = onResolveKeepLocal) {
                    Text(stringResource(R.string.feishu_conflict_keep_local))
                }
            }
        }
    )
}
