## 1. 资源 — i18n 字符串

- [ ] 1.1 在 `app/src/main/res/values/strings.xml` 新增 7 条 `selection_toolbar_*` 字符串(中文)
- [ ] 1.2 在 `app/src/main/res/values-en/strings.xml` 同步英文翻译

## 2. UI 组件 — SelectionFloatingToolbar

- [ ] 2.1 新建文件 `app/src/main/java/com/yy/writingwithai/feature/quicknote/detail/SelectionFloatingToolbar.kt`
- [ ] 2.2 实现 Composable 函数签名:`SelectionFloatingToolbar(modifier, isAiEnabled, onAddEntity, onAiExpand, onAiPolish, onAiOrganize, onAiSummarize, onAiTranslate, onAiCopy)`
- [ ] 2.3 实现 ⭐ "加入实体" 按钮(始终 enabled,使用 `FilledTonalButton` + 图标 + 文字)
- [ ] 2.4 实现 ✨ "AI" 按钮 + 内部 `DropdownMenu`(根据 `isAiEnabled` 决定是否禁用)
- [ ] 2.5 应用样式: `surfaceContainerHigh` 背景 + `LocalCornerRadius.current.md` 圆角 + 2dp 阴影
- [ ] 2.6 所有 spacing 走 `LocalSpacing.current` token,不写裸 `.dp`

## 3. QuickNoteDetailScreen 集成

- [ ] 3.1 在 `QuickNoteDetailScreen.kt` 移除 `bottomBar` lambda(整个 Surface + Row + FilledTonalButton 块)
- [ ] 3.2 在 detail 屏顶层添加 `var selection by remember { mutableStateOf(TextRange(0)) }`
- [ ] 3.3 修改 `BasicTextField` 的 `value` 参数为 `TextFieldValue(text, selection)`,`onValueChange` 更新 selection
- [ ] 3.4 在 Scaffold content 区增加 `SelectionFloatingToolbar` 渲染条件:`if (!selection.collapsed)`
- [ ] 3.5 连接 `hasAiProvider` 到 `SelectionFloatingToolbar.isAiEnabled` 参数
- [ ] 3.6 暴露 `onAddEntity` 回调(本 change 仅打 log 占位,后续 change 实现持久化)
- [ ] 3.7 连接 `aiVm.start(WritingOp.EXPAND, ...)` 等回调到 toolbar 的 `onAiExpand` 等参数

## 4. 编译 + 静态检查

- [ ] 4.1 `./gradlew :app:assembleDebug` 通过
- [ ] 4.2 `./gradlew :app:ktlintCheck` 通过
- [ ] 4.3 `./gradlew :app:testDebugUnitTest` 全绿

## 5. 人工验证

- [ ] 5.1 安装到模拟器
- [ ] 5.2 进入笔记详情页,确认底部固定栏已移除
- [ ] 5.3 长按选中正文一段文字,确认浮动工具栏出现
- [ ] 5.4 检查 ⭐ "加入实体" 按钮始终可点击
- [ ] 5.5 未配置 AI 时,确认 ✨ "AI" 按钮为淡灰色不可点击
- [ ] 5.6 配置 AI 后,确认 ✨ "AI" 按钮可点击并能展开 dropdown 菜单

## 6. 文档 + archive

- [ ] 6.1 `docs/progress.md` 追加本次重构条目
- [ ] 6.2 运行 `/opsx:archive` 归档本 change