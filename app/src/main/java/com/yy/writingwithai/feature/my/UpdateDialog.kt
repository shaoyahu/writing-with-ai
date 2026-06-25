@file:Suppress("FunctionNaming")

package com.yy.writingwithai.feature.my

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.yy.writingwithai.R
import com.yy.writingwithai.core.update.AppUpdateManifest

/**
 * app-self-hosted-update · 新版本提示 dialog。
 *
 * 显示 versionName + releaseNotes + 下载 / 稍后 按钮。
 * 严格 spec/app-update 的「可选更新 UX」语义:不做强制。
 */
@Composable
fun UpdateDialog(manifest: AppUpdateManifest, onDownload: () -> Unit, onLater: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!manifest.mandatory) onLater() },
        properties = if (manifest.mandatory) {
            DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        } else {
            DialogProperties()
        },
        title = {
            Column {
                Text(stringResource(R.string.update_dialog_title, manifest.versionName))
                if (manifest.mandatory) {
                    Text(
                        "必须升级",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.update_dialog_size, formatSize(manifest.apkSize)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (manifest.releaseNotes.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.update_dialog_notes_header),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = manifest.releaseNotes,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDownload, modifier = Modifier.fillMaxWidth(0.5f)) {
                Text(stringResource(R.string.update_dialog_download))
            }
        },
        dismissButton = {
            if (!manifest.mandatory) {
                TextButton(onClick = onLater) {
                    Text(stringResource(R.string.update_dialog_later))
                }
            }
        }
    )
}

private fun formatSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1.0) "%.1f MB".format(mb) else "%d KB".format(bytes / 1024)
}
