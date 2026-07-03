package com.yy.writingwithai.feature.quicknote.detail

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yy.writingwithai.R

/**
 * feishu-sync-feedback · 5 个 typed 失败 Dialog 的 Composable 集合。
 *
 * Conflict / FolderMigration 在 [QuickNoteDetailScreen] 里直接复用现有的
 * [ConflictResolutionDialog] / [FolderMigrationDialog](功能已含 3 选项交互)，
 * 不在本文件重复实现。
 *
 * 命名空间约定:object SyncFailureDialogs + 6 个静态 Composable 函数。
 */
object SyncFailureDialogs {

    /**
     * 远端文档已删除 → 提示用户"重新同步为新文档"。
     *
     * @param onRecreate 调 viewModel.recreateFeishuDoc() 删旧 ref + push 新文档
     * @param onDismiss 调 viewModel.clearSyncMessage() 清状态
     */
    @Composable
    fun RemoteDeleted(onRecreate: () -> Unit, onDismiss: () -> Unit) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.feishu_sync_dialog_remote_deleted_title)) },
            text = { Text(stringResource(R.string.feishu_sync_dialog_remote_deleted_body)) },
            confirmButton = {
                TextButton(onClick = onRecreate) {
                    Text(stringResource(R.string.feishu_sync_dialog_remote_deleted_action_recreate))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.feishu_sync_action_close))
                }
            }
        )
    }

    /**
     * 飞书端为空 → 仅提示,不允许覆盖本地。
     */
    @Composable
    fun Empty(onDismiss: () -> Unit) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.feishu_sync_dialog_empty_title)) },
            text = { Text(stringResource(R.string.feishu_sync_dialog_empty_body)) },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.feishu_sync_action_close))
                }
            }
        )
    }

    /**
     * 网络异常 → "重试"按钮调 viewModel.retryLastSync()。
     */
    @Composable
    fun Network(onRetry: () -> Unit, onDismiss: () -> Unit) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.feishu_sync_dialog_network_title)) },
            text = { Text(stringResource(R.string.feishu_sync_dialog_network_body)) },
            confirmButton = {
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.feishu_sync_action_retry))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.feishu_sync_action_close))
                }
            }
        )
    }

    /**
     * 飞书服务 5xx → "重试"按钮调 viewModel.retryLastSync()。
     */
    @Composable
    fun Server(onRetry: () -> Unit, onDismiss: () -> Unit) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.feishu_sync_dialog_server_title)) },
            text = { Text(stringResource(R.string.feishu_sync_dialog_server_body)) },
            confirmButton = {
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.feishu_sync_action_retry))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.feishu_sync_action_close))
                }
            }
        )
    }

    /**
     * 飞书限流(429) → 显示具体 retryAfterSeconds,"我知道了"关 dialog。
     */
    @Composable
    fun RateLimited(retryAfterSeconds: Int, onAck: () -> Unit) {
        AlertDialog(
            onDismissRequest = onAck,
            title = { Text(stringResource(R.string.feishu_sync_dialog_rate_limited_title)) },
            text = {
                Text(stringResource(R.string.feishu_sync_dialog_rate_limited_body, retryAfterSeconds))
            },
            confirmButton = {
                TextButton(onClick = onAck) {
                    Text(stringResource(R.string.feishu_sync_action_ack))
                }
            }
        )
    }

    /**
     * 未分类异常 → "复制错误"把 cause 写到剪贴板,方便用户贴给开发者排查。
     */
    @Composable
    fun Unknown(cause: String, onCopyError: () -> Unit, onDismiss: () -> Unit) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.feishu_sync_dialog_unknown_title)) },
            text = { Text(stringResource(R.string.feishu_sync_dialog_unknown_body, cause)) },
            confirmButton = {
                TextButton(onClick = onCopyError) {
                    Text(stringResource(R.string.feishu_sync_action_copy_error))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.feishu_sync_action_close))
                }
            }
        )
    }
}
