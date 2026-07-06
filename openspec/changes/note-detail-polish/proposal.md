# note-detail-polish

## Why

笔记详情页 (`QuickNoteDetailScreen`) 在 v0.4.0 后仍有 3 个 UX 缺陷需要修复:

1. **长按选词时系统键盘会弹起**: `BasicTextField(readOnly = false)` 让用户在选择文本时无意识触发 IME,遮挡选区 toolbar,需要切到 readOnly 拦截 IME 路径。
2. **拆解 / 重新拆解按钮点击后无反馈**: 菜单项在 `decomposeState is Loading` 时被隐藏 (`if (decomposeState !is DecomposeState.Loading) add(...)`),用户点完菜单关闭,屏幕无任何变化,不知道任务是否开始。
3. **关联笔记筛选维度混淆**: 详情页底部"关联笔记"区按所有 signal type 展示(包含 `ENTITY_HIT` 相同实体),而"按实体查看关联"应该是实体卡片的职责。

## What Changes

- **修复 `QuickNoteDetailScreen` 长按 IME 弹起**: `BasicTextField` 切到 `readOnly = true`,系统默认不弹 IME,长按直接进入系统 selection 模式;toolbar 仍按 `selection.collapsed` 显示。
- **拆解进度展示**: TopAppBar 下方加 `LinearProgressIndicator`,只在 `decomposeState is Loading` 时显示;菜单项在 Loading 时保留并显示"拆解中…"文案,Snackbar 在 Loading 完成后提示成功/失败/无实体。
- **关联笔记筛选**: `RelatedNotesSection` 渲染列表前过滤 `LinkType.ENTITY_HIT`;实体卡片 `getRelatedByEntity` 复用 Section 拉到的 `related` 列表(避免重复 IO)。

## Capabilities

### Modified Capabilities

- `note-decompose-highlight`: 拆解状态机展示 + 关联笔记渲染规则
- `floating-selection-toolbar`: 选区 IME 行为 (readOnly)

## Impact

- **代码改动**:
  - `app/src/main/java/com/yy/writingwithai/feature/quicknote/detail/QuickNoteDetailScreen.kt`: TopAppBar 下方加 `LinearProgressIndicator`;菜单项 Loading 状态不隐藏;BasicTextField 改 `readOnly = true`;实体卡片复用 `related` 列表。
  - `app/src/main/java/com/yy/writingwithai/feature/quicknote/detail/RelatedNotesSection.kt`: 渲染前过滤 `LinkType.ENTITY_HIT`。
  - `app/src/main/java/com/yy/writingwithai/feature/quicknote/detail/QuickNoteDetailViewModel.kt`: 不变。
- **资源**:
  - 新增 `R.string.note_decompose_in_progress`("拆解中…")。
- **测试**:
  - 现有单测不破;`./gradlew :app:testDebugUnitTest` 全过。
- **依赖**: 无新依赖。
