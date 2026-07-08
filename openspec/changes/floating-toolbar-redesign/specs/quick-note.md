## MODIFIED Requirements

### Requirement: Detail screen shows floating selection toolbar (M5+ floating-selection-toolbar,替换原固定底部 Share+AI 栏)

详情屏 MUST 在用户长按文本选中局部选区(非空 `TextRange`)时,浮出 `SelectionFloatingToolbar`,提供 "加入实体" 和 "AI" 两个入口;选区为空时工具栏隐藏。工具栏 MUST 浮在 Scaffold 顶层(避免被 `verticalScroll` Column / when 分支吞掉),固定在屏幕底部、避开系统导航栏(`WindowInsets.navigationBars`),不弹系统 IME。

工具栏 MUST 遵循 floating-toolbar-redesign 的视觉规则:

- 外层 `Surface` 用 `color = MaterialTheme.colorScheme.surfaceContainerHigh`、`tonalElevation = 6.dp`、`shadowElevation = 8.dp`、`shape = RoundedCornerShape(cornerRadius.xl)`(24dp);MUST NOT 画 `BorderStroke`(tonalElevation 已表达浮层)
- 操作区 MUST 使用 `IconButton` + 下方 `Text(style = labelSmall)` 排版,MUST NOT 使用 `FilledTonalButton`(视觉过重)
- "AI" 触发 MUST 是 `AssistChip(icon = AutoAwesome, label = "AI")`,MUST NOT 是 Button
- 已添加态 MUST 仅切换 `Icon` 类型(`Icons.Filled.Star` vs `Icons.Outlined.StarOutline`)和 `tint = MaterialTheme.colorScheme.primary`,MUST NOT 切换底色

#### Scenario: 长按文字出现局部选区 + 浮动工具栏
- **WHEN** 详情屏用户长按笔记正文中一个词,拖动 selection handle 选中非空范围
- **THEN** UI 显示 selection handles + 屏幕底部浮出 `SelectionFloatingToolbar`,包含 ⭐加入实体 按钮(始终 enabled)和 ✨AI chip(展开后是扩写/润色/整理/摘要/翻译 5 项 dropdown)
- **AND** 系统 IME MUST NOT 弹出(`LocalSoftwareKeyboardController.hide()` 在 `onValueChange` 触发)

#### Scenario: 工具栏视觉走 surfaceContainerHigh 浮层色
- **WHEN** 工具栏在浅色主题下渲染
- **THEN** 背景色为 `MaterialTheme.colorScheme.surfaceContainerHigh`(M3 1.2+ 派生 token,非硬编码 hex),边框由 `tonalElevation = 6.dp` 表达,MUST NOT 出现 `Color(0x...)` 字面量

#### Scenario: 已添加态仅切 icon + tint
- **WHEN** 当前选区对应文字已被加入实体(`isEntityAdded = true`)
- **THEN** ⭐加入实体 IconButton 内 `Icon` 切到 `Icons.Filled.Star` 且 `tint = MaterialTheme.colorScheme.primary`;工具栏底色 / 边框 MUST NOT 变化

#### Scenario: AI Chip 不可用时禁用
- **WHEN** 用户未配置任何 AI provider apikey(`isAiEnabled = false`)
- **THEN** ✨AI AssistChip MUST 处于 disabled 灰色态,点击 MUST NOT 展开 dropdown

#### Scenario: 无选区时工具栏隐藏
- **WHEN** 详情屏无文本选区(`selection.collapsed == true`)
- **THEN** `SelectionFloatingToolbar` MUST NOT 渲染(早返回);Screen 底部不留空白占位

#### Scenario: 选区变化后工具栏 UI 同步
- **WHEN** 用户拖动 selection handle 调整选区起点 / 终点
- **THEN** `BasicTextField.onValueChange` 触发,UI 层 `uiSelection: TextRange` 同步更新,工具栏按钮回调(`onAddEntity` / `onAiExpand` 等)使用最新选区 `uiSelection.min` / `uiSelection.max`

#### Scenario: 工具栏位置避开导航栏
- **WHEN** 详情屏在带底部导航栏 / gesture handle 的设备上显示
- **THEN** `SelectionFloatingToolbar` 通过 `Modifier.windowInsetsPadding(WindowInsets.navigationBars)` 上推,MUST 完全可见不被裁剪

#### Scenario: 加入实体按钮不受 AI 配置影响
- **WHEN** 用户未配置任何 AI provider apikey
- **THEN** ⭐加入实体 IconButton MUST 保持 enabled(本地功能,无 AI 依赖)
