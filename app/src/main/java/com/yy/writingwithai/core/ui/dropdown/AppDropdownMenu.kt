package com.yy.writingwithai.core.ui.dropdown

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.yy.writingwithai.app.ui.theme.LocalCornerRadius

/**
 * unify-dropdown-menu-style · 统一下拉框组件。
 *
 * 样式与 ActionSheet / SectionCard 对齐:
 * - containerColor = surfaceContainerHigh(浮起感)
 * - shape = RoundedCornerShape(cornerRadius.md) = 12dp
 * - shadowElevation = 2dp(微阴影)
 *
 * @see AppActionDropdown 操作菜单(三点菜单 / 长按菜单)
 * @see AppSelectionDropdown 选择菜单(ExposedDropdownMenuBox 封装)
 */

/**
 * 操作菜单项数据类。
 *
 * @param text 菜单项文字
 * @param onClick 点击回调
 * @param leadingIcon 左侧图标(可选)
 * @param enabled 是否可用
 * @param isDestructive 破坏性操作(如"删除")，自动应用 error 色
 */
data class AppActionItem(
    val text: String,
    val onClick: () -> Unit,
    val leadingIcon: ImageVector? = null,
    val enabled: Boolean = true,
    val isDestructive: Boolean = false
)

/**
 * 统一操作菜单(三点菜单 / 长按菜单)。
 *
 * 样式与 ActionSheet 对齐: surfaceContainerHigh 容器色 + 12dp 圆角 + 2dp 微阴影。
 * 每项: icon + bodyLarge 文字。
 * [isDestructive]=true 时文字/图标自动变 error 色(如"删除")。
 *
 * @param expanded 是否展开
 * @param onDismissRequest 关闭回调
 * @param items 菜单项列表
 * @param modifier Modifier
 */
@Composable
fun AppActionDropdown(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    items: List<AppActionItem>,
    modifier: Modifier = Modifier
) {
    val cornerRadius = LocalCornerRadius.current

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius.md),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 2.dp,
        tonalElevation = 0.dp
    ) {
        items.forEach { item ->
            val contentColor = when {
                !item.enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                item.isDestructive -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface
            }
            val iconColor = when {
                !item.enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                item.isDestructive -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }

            DropdownMenuItem(
                text = {
                    Text(
                        text = item.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = contentColor
                    )
                },
                onClick = item.onClick,
                leadingIcon = if (item.leadingIcon != null) {
                    {
                        Icon(
                            imageVector = item.leadingIcon,
                            contentDescription = null,
                            tint = iconColor
                        )
                    }
                } else {
                    null
                },
                enabled = item.enabled,
                colors = MenuDefaults.itemColors()
            )
        }
    }
}

/**
 * 统一选择菜单(ExposedDropdownMenuBox 封装)。
 *
 * 样式与 AppActionDropdown 对齐: surfaceContainerHigh 容器色 + 12dp 圆角 + 2dp 微阴影。
 * 选中项: trailing Check icon(primary)。
 * 内部自动管理 expanded 状态，调用方无需维护。
 *
 * @param T 选项类型(必须可比较以判断选中态)
 * @param options 全部可选项
 * @param selected 当前选中项
 * @param onSelected 选中回调
 * @param label 输入框 label
 * @param optionLabel 选项显示文字转换
 * @param modifier Modifier
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> AppSelectionDropdown(
    options: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    label: @Composable () -> Unit,
    optionLabel: (T) -> String,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val cornerRadius = LocalCornerRadius.current

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = optionLabel(selected),
            onValueChange = {},
            readOnly = true,
            label = label,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(cornerRadius.md),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 2.dp,
            tonalElevation = 0.dp
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = optionLabel(option),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                    trailingIcon = if (isSelected) {
                        {
                            // review-2026-07-02 finding-3:为选中态 Check icon 加
                            // contentDescription="已选中",让 TalkBack 读出选中状态,
                            // 而非读 null(会跳过此 icon,只读 row 文字)。
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        null
                    },
                    colors = if (isSelected) {
                        MenuDefaults.itemColors(
                            textColor = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        MenuDefaults.itemColors()
                    }
                )
            }
        }
    }
}
