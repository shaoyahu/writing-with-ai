package com.yy.writingwithai.feature.quicknote.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yy.writingwithai.R

/**
 * note-list-card-actions · 长按菜单「添加已有标签」弹出的选择 dialog。
 *
 * - 列出 [allTags] 全表 tag
 * - 已挂([currentTags] 包含)的 tag 显示 Check icon + clickable.enabled = false
 * - 未挂 tag 可点 → 触发 [onTagSelected] 后 dialog 自动关闭
 * - [allTags] 空时显示空态文案,仍可关闭
 *
 * feature 自包含,放 feature/quicknote/list/,不跨 feature 引用。
 */
@Composable
fun AddExistingTagDialog(
    allTags: List<String>,
    currentTags: List<String>,
    onTagSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quicknote_list_add_tag_dialog_title)) },
        text = {
            if (allTags.isEmpty()) {
                Text(stringResource(R.string.quicknote_list_add_tag_dialog_empty))
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(items = allTags, key = { it }) { tag ->
                        val isCurrent = tag in currentTags
                        ListItem(
                            headlineContent = { Text("#$tag") },
                            trailingContent = if (isCurrent) {
                                {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = stringResource(
                                            R.string.quicknote_list_add_tag_dialog_current_cd,
                                            tag
                                        )
                                    )
                                }
                            } else {
                                null
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isCurrent) {
                                    onTagSelected(tag)
                                    onDismiss()
                                }
                                .padding(vertical = 4.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_close))
            }
        }
    )
}
