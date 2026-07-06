## Why

笔记详情页底部的固定操作栏(分享 + AI 操作)存在两个 UX 问题:1) 与右上角三点菜单的"分享"功能重复;2) AI 操作按钮无文本选中状态提示,用户容易在无选区时触发,导致 AI 处理全文而非用户期望的片段。本 change 移除底部固定栏,改为**文本选中时自动浮现的浮动工具栏**,让操作精准对齐用户意图。

## What Changes

- **移除** QuickNoteDetailScreen 的 `bottomBar`(分享 + AI 两个常驻 FilledTonalButton)
- **新增** 浮动工具栏组件 `SelectionFloatingToolbar`:仅在用户选中正文文本(`selection` 非 collapsed)时浮现
- 浮动工具栏包含两个按钮:
  - **⭐ 加入实体** - 一键将选中文字标记为实体(本地功能,无 AI 依赖,始终可点击)
  - **✨ AI 操作** - 触发 AI 操作,点击展开 Dropdown(扩写/润色/整理/总结/翻译)
- **AI 操作按钮在用户未配置 AI 模型时为淡灰色不可点击**,与"拆解"按钮的禁用策略一致
- 浮动工具栏出现在选中区域**正上方**(MVP 简化版,后续可优化自适应)
- 浮动工具栏在用户**取消选择**(点击空白 / 滚动 / 文本失焦)时自动隐藏

## Capabilities

### New Capabilities

- `floating-selection-toolbar`: 笔记详情页文本选中时浮现的工具栏,提供"加入实体"和"AI 操作"两个快捷入口

### Modified Capabilities

无(本 change 不修改现有 spec 的 requirement 级别行为;实体加入的实际数据流由后续 change 处理)

## Impact

- **代码改动**:
  - `QuickNoteDetailScreen.kt`:移除 `bottomBar` lambda;在 `Scaffold` 的 content 区增加 `SelectionFloatingToolbar` 调用
  - 新增组件:`feature/quicknote/detail/SelectionFloatingToolbar.kt`(Composable 组件)
  - 可能新增:Toolbar 状态管理变量(`floatingToolbarVisible`, `floatingToolbarAnchorOffset`)
- **依赖**:无新增第三方依赖,沿用 Material 3 `FilledTonalButton`、`DropdownMenu`
- **测试影响**:现有 detail 屏测试可能需要更新,移除 bottomBar 相关断言
- **设计规范**:遵循 CLAUDE.md 中的 Material 3 主题、Spacing、CornerRadius token,不使用裸 `.dp`
- **i18n**:新组件涉及字符串(工具栏按钮文案),需在 `strings.xml` + `values-en/strings.xml` 同步