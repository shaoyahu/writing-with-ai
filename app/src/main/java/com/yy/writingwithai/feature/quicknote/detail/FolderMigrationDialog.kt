package com.yy.writingwithai.feature.quicknote.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yy.writingwithai.R

/**
 * feishu-folder-migration · 文件夹变更迁移对话框。
 *
 * 当 push 检测到当前设置的 folder token 与已同步文档的 folder token 不一致时弹出，
 * 让用户选择:
 * - 删除旧文档 + 在新文件夹创建
 * - 在原位置更新(忽略 folder token 变更)
 * - 取消
 *
 * 参照 [ConflictResolutionDialog] 的 stateless + callback 模式。
 *
 * 按钮排版:用自定义 Row 而非 Material AlertDialog 的 confirmButton/dismissButton 槽,
 * 是因为后者各只能放 1 个按钮,放 3 个(确认 + 次要 + 取消)会溢出或被截断。
 */
@Composable
fun FolderMigrationDialog(
    oldLocation: String,
    newLocation: String,
    onDeleteAndRecreate: () -> Unit,
    onUpdateInPlace: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.feishu_folder_migration_title)) },
        text = {
            Column {
                Text(stringResource(R.string.feishu_folder_migration_body))
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.feishu_folder_migration_old_location, oldLocation),
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.feishu_folder_migration_new_location, newLocation),
                    style = MaterialTheme.typography.titleSmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDeleteAndRecreate) {
                Text(stringResource(R.string.feishu_folder_migration_delete_and_recreate))
            }
        },
        dismissButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(end = 8.dp)
            ) {
                TextButton(onClick = onUpdateInPlace) {
                    Text(stringResource(R.string.feishu_folder_migration_update_in_place))
                }
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.feishu_folder_migration_cancel))
                }
            }
        }
    )
}

/**
 * feishu-folder-migration · 把 FeishuAuthStore 里的 folder token 截断成 UI 友好的 display token。
 *
 * 长 token 显示前 12 字符 + `…`,避免在 AlertDialog 里溢出。
 *
 * 注:实际 i18n 字符串("默认空间(根目录)" / "文件夹 %s")在 [describeFolderLocation]
 * Composable 里走 stringResource 拿 — 这个纯函数只负责 token 长度处理,
 * 便于单元测试且不依赖 Composable context。
 *
 * 例:`fldcnABC123XYZ456abc` → `fldcnABC123X…`
 */
internal fun formatFolderLocationToken(token: String?): String? {
    if (token == null) return null
    return if (token.length > 12) token.take(12) + "…" else token
}

/**
 * feishu-folder-migration · 把 FeishuAuthStore 里的 folder token 渲染成 UI label。
 *
 * - null → `R.string.feishu_folder_location_default`(默认空间/根目录)
 * - 非 null → `R.string.feishu_folder_location_named`(文件夹 %s),长 token 截断避免溢出
 *
 * 把字符串模板放 Composable 里走 stringResource,而不是硬编码在 helper 函数里 —
 * 符合 CLAUDE.md"字符串一律走 strings.xml"的约定,英文 locale 也能自动切换。
 */
@Composable
internal fun describeFolderLocation(token: String?): String {
    val display = formatFolderLocationToken(token)
    return if (display == null) {
        stringResource(R.string.feishu_folder_location_default)
    } else {
        stringResource(R.string.feishu_folder_location_named, display)
    }
}

@Preview(name = "FolderMigrationDialog", showBackground = true)
@Composable
private fun FolderMigrationDialogPreview() {
    MaterialTheme {
        FolderMigrationDialog(
            oldLocation = describeFolderLocation("fldcnABC123XYZ456"),
            newLocation = describeFolderLocation("fldcnFolderB"),
            onDeleteAndRecreate = {},
            onUpdateInPlace = {},
            onCancel = {}
        )
    }
}
