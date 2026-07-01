package com.yy.writingwithai.feature.quicknote.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import com.yy.writingwithai.R
import com.yy.writingwithai.app.ui.theme.LocalSpacing

/**
 * Tag 输入行:已挂 tag 用 [InputChip] 渲染(可删除)，末尾 [OutlinedTextField] 接收新 tag。
 *
 * - 逗号或回车触发 [onAddTag]
 * - chip 删除图标触发 [onRemoveTag]
 * - `input` / `onInputChange` 由外部 VM 控制(解决用户不按逗号/回车就点保存丢失输入的问题)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagInputRow(
    tags: List<String>,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    modifier: Modifier = Modifier,
    input: String = "",
    onInputChange: (String) -> Unit = {}
) {
    val spacing = LocalSpacing.current

    FlowRow(
        modifier =
        modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.md, vertical = spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm / 2)
    ) {
        tags.forEach { tag ->
            InputChip(
                selected = false,
                onClick = { onRemoveTag(tag) },
                label = { Text("#$tag") },
                trailingIcon = {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.quicknote_tag_remove_cd, tag)
                    )
                }
            )
        }
        OutlinedTextField(
            value = input,
            onValueChange = { value ->
                if (value.contains(',')) {
                    value.split(',').forEach { segment ->
                        onAddTag(segment)
                    }
                    onInputChange("")
                } else {
                    onInputChange(value)
                }
            },
            placeholder = { Text(stringResource(R.string.quicknote_tag_input_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions =
            KeyboardActions(onDone = {
                if (input.isNotBlank()) {
                    onAddTag(input)
                    onInputChange("")
                }
            }),
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = spacing.sm / 2),
            textStyle = MaterialTheme.typography.bodyMedium
        )
    }
}
