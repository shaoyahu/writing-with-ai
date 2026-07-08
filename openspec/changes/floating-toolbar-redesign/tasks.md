# floating-toolbar-redesign · tasks

## 1. SelectionFloatingToolbar 视觉重做

- [x] 1.1 删 `feature/quicknote/detail/SelectionFloatingToolbar.kt` 顶部硬编码 `Color(0xFFFFE0B2)` 和 `Color(0xFFE65100)` 引用
- [x] 1.2 外层 `Surface` 改 `color = MaterialTheme.colorScheme.surfaceContainerHigh`,`tonalElevation = 6.dp`,`shadowElevation = 8.dp`
- [x] 1.3 `shape = RoundedCornerShape(LocalCornerRadius.current.xl)`(24dp)
- [x] 1.4 删 `BorderStroke`,改用 tonalElevation 表达浮层
- [x] 1.5 "加入实体" 按钮改 `IconButton`(去 FilledTonalButton):
  - 默认:`Icon(Icons.Outlined.StarOutline, contentDescription = 加入实体)`
  - 已添加:`Icon(Icons.Filled.Star, tint = MaterialTheme.colorScheme.primary)`
- [x] 1.6 "加入实体" IconButton 下方加 `Text(加入实体, style = labelSmall)`
- [x] 1.7 "AI" 触发改 `AssistChip(icon = AutoAwesome, label = AI)`(M3 AssistChip,默认色),点击展开 DropdownMenu;Chip 不可点击时禁用(`enabled = isAiEnabled`)
- [x] 1.8 内部 Row 用 `Arrangement.spacedBy(LocalSpacing.current.md)` + `padding(LocalSpacing.current.sm)` 排版

## 2. DropdownMenu 视觉同步

- [x] 2.1 现有 `AiMenuItem` 增加 `leadingIcon` 参数,默认 `null`
- [x] 2.2 5 项 AI 操作分别配 icon:
  - 扩写:`Icons.AutoMirrored.Outlined.ShortText`
  - 润色:`Icons.Outlined.Brush`
  - 整理:`Icons.Outlined.AccountTree`
  - 摘要:`Icons.Outlined.Summarize`
  - 翻译:`Icons.Outlined.Translate`
- [x] 2.3 DropdownMenu 容器颜色走 `MaterialTheme.colorScheme.surfaceContainerHigh`(跟 Surface 一致,DropdownMenu 默认即可)
- [x] 2.4 MenuItem `TextStyle = bodyMedium`(从 bodyLarge 调到 bodyMedium,与 MenuItem 默认字号对齐)

## 3. strings.xml 检查

- [x] 3.1 确认 `selection_toolbar_add_entity` 和 `selection_toolbar_ai` 已存在(grep `values/strings.xml`)
- [x] 3.2 不新增 string key

## 4. 编译 + lint 验证

- [x] 4.1 `./gradlew :app:ktlintCheck` 0 violation
- [x] 4.2 `./gradlew :app:assembleDebug` BUILD SUCCESSFUL

## 5. 真机验证

- [x] 5.1 installDebug 到 Pixel_7_API_35
- [ ] 5.2 详情屏长按选中一段文字 → 工具栏浮出,确认背景 / 圆角 / 边框视觉跟 App 主题对齐
- [ ] 5.3 点击 [加入实体] → 星切实心 + 主色 tint;再选别的段 → 星恢复空心
- [ ] 5.4 点击 [AI] AssistChip → DropdownMenu 弹出,5 项各带 leading icon
- [ ] 5.5 未配 AI provider 时 [AI] chip 灰色 disabled,点不开

## 6. spec 同步 + 归档

- [ ] 6.1 跑 `/opsx:sync floating-toolbar-redesign` 合入主 spec(`quick-note` 加 SelectionFloatingToolbar 视觉规则;`design-system-v2` 加 surface overlay 组件 token 规则)
- [ ] 6.2 跑 `/opsx:archive floating-toolbar-redesign` 收口
- [ ] 6.3 `docs/progress.md` 加 1 条"M5 polish 续 · 浮选工具栏视觉重做"进度条目
