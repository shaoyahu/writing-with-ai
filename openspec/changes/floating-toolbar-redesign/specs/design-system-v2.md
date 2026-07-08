## ADDED Requirements

### Requirement: Surface overlay components use surfaceContainerHigh + tonalElevation, not BorderStroke

Material 3 浮层组件(Floating toolbar / ModalBottomSheet / DropdownMenu container / Dialog)MUST 使用 `color = MaterialTheme.colorScheme.surfaceContainerHigh` + `tonalElevation` 表达浮层,MUST NOT 使用 `BorderStroke`(BorderStroke 是卡片类组件的视觉语言)。`surfaceContainerHigh` 是 M3 1.2+ ColorScheme 自动派生的 token,业务代码 MUST NOT 硬编码 hex 替代。

#### Scenario: SelectionFloatingToolbar 用 surfaceContainerHigh
- **WHEN** 浮选工具栏渲染
- **THEN** 背景为 `MaterialTheme.colorScheme.surfaceContainerHigh`,`tonalElevation = 6.dp`,`shadowElevation = 8.dp`;MUST NOT 出现 `BorderStroke` 或 `Color(0x...)` 字面量

#### Scenario: grep 验证无 BorderStroke 用于浮层
- **WHEN** `grep -r "BorderStroke" app/src/main/java/com/yy/writingwithai/feature/quicknote/detail/SelectionFloatingToolbar.kt`
- **THEN** 0 匹配

#### Scenario: grep 验证浮层组件无硬编码 hex
- **WHEN** `grep -rE "Color\(0x" app/src/main/java/com/yy/writingwithai/feature/quicknote/detail/`
- **THEN** 0 匹配(浮层组件走 token)

### Requirement: Floating toolbar icon button pattern uses IconButton + label below, not FilledTonalButton

浮选工具栏的操作区 MUST 使用 `IconButton` + 下方 `Text(style = labelSmall)` 排版,而非 `FilledTonalButton`(FilledTonalButton 视觉过重,会"压住"下方内容)。已添加态 MUST 仅切换 `Icon` 类型与 `tint`,MUST NOT 切换按钮底色。

#### Scenario: 操作区是 IconButton 不是 FilledTonalButton
- **WHEN** 工具栏渲染操作区
- **THEN** `grep "FilledTonalButton" app/src/main/java/com/yy/writingwithai/feature/quicknote/detail/SelectionFloatingToolbar.kt` 返回 0 匹配

#### Scenario: 已添加态不变底色
- **WHEN** `isEntityAdded = true`
- **THEN** ⭐IconButton 内 `Icon` 切到 `Icons.Filled.Star` + `tint = MaterialTheme.colorScheme.primary`;IconButton 底色 MUST NOT 改变
