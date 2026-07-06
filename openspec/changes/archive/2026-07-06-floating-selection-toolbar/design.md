## Context

笔记详情页 (`QuickNoteDetailScreen`) 当前使用 `Scaffold` 的 `bottomBar` 承载两个常驻按钮: 分享 / AI 操作。这两个按钮存在 UX 问题:

1. **功能重复**: 右上角三点菜单中已有 "分享" 入口,底部再放一个造成认知负担。
2. **意图不精准**: AI 操作按钮无论是否选中文本都可点击,用户容易在无选区时触发,导致 AI 处理全文而非用户期望的片段。
3. **风格不统一**: 底部固定按钮与项目 Material 3 设计语言不协调。

`note-decompose-highlight` 已建立了 "实体" 概念(`note_entities` 表 + LLM 抽取),但用户缺少快速将自己认为重要的文字标记为实体的入口。

## Goals / Non-Goals

**Goals:**
- 移除底部固定操作栏,改用文本选中时浮现的浮动工具栏
- 浮动工具栏仅在用户选中文本后出现,精准对齐用户意图
- 工具栏两个入口职责清晰: ⭐ 加入实体 (本地) / ✨ AI (依赖 provider)
- AI 按钮在未配置 provider 时与现有 "拆解" 按钮禁用策略一致 (淡灰色不可点击)
- 工具栏 UI 沿用 `AppActionDropdown` 已有样式 token (surfaceContainerHigh + 12dp 圆角 + 2dp 阴影)

**Non-Goals:**
- 浮动工具栏位置自适应 (MVP 简化为选中区域正上方,后续可优化)
- "加入实体" 的实际数据持久化 (由后续 change 处理,本 change 仅 UI 占位 + onClick 回调暴露)
- 首次使用引导 (由另一个独立 change 处理)
- 实体类型选择弹窗 (按用户决策不做,一键加入即视为 CONCEPT 实体)

## Decisions

### D1 · 浮动工具栏的触发与消失

**方案**: 利用 `BasicTextField` 已有的 `selection: TextRange` 状态。

```kotlin
// 在 detail 屏顶层维护 selection
var selection by remember { mutableStateOf(TextRange(0)) }

BasicTextField(
    value = TextFieldValue(text = ..., selection = selection),
    onValueChange = { selection = it.selection },
    ...
)

// 浮动栏可见性 = selection 非 collapsed
val showFloatingToolbar = !selection.collapsed
```

**消失时机**:
- 用户点击正文外部 → `selection` 仍存在 (Android 系统行为) → **不自动隐藏** (避免误判)
- 用户拖动 selection handle 收缩到 0 → `selection.collapsed = true` → 自动隐藏
- 用户滚动正文 → 通过 `Modifier.scrolled` 检测 scroll 事件 → 隐藏

**Why**: selection 是 Compose 标准状态,管理简单;MVP 阶段先实现"选区变化即浮现/消失",后续可加点击外部隐藏。

### D2 · 浮动工具栏组件拆分

**方案**: 新建独立 Composable `SelectionFloatingToolbar.kt`,放 `feature/quicknote/detail/` 目录。

```kotlin
@Composable
fun SelectionFloatingToolbar(
    modifier: Modifier = Modifier,
    isAiEnabled: Boolean,        // 未配置 AI 时 false
    onAddEntity: () -> Unit,     // 用户点击 "加入实体"
    onAiExpand: () -> Unit,      // AI 扩写
    onAiPolish: () -> Unit,      // AI 润色
    onAiOrganize: () -> Unit,    // AI 整理
    onAiSummarize: () -> Unit,   // AI 总结
    onAiTranslate: () -> Unit,   // AI 翻译
    onAiCopy: () -> Unit         // 复制
)
```

**Why**: 组件职责单一 (工具栏 UI + AI dropdown),与 detail 屏解耦,便于测试和未来复用。

### D3 · 工具栏样式: 沿用 AppActionDropdown 风格

**方案**: 复用 `AppActionDropdown` 的视觉风格但作为独立组件实现。

- 背景: `MaterialTheme.colorScheme.surfaceContainerHigh`
- 圆角: `LocalCornerRadius.current.md` (12dp)
- 阴影: `shadowElevation = 2.dp`
- 形状: `RoundedCornerShape(12.dp)`

**为什么不直接复用 `AppActionDropdown`**:
- `AppActionDropdown` 是 `DropdownMenu` 封装,依赖锚点(`expanded` + `onDismissRequest`)
- 浮动工具栏不是 dropdown,是常驻 UI 元素,触发逻辑不同
- 但视觉风格完全一致,便于用户认知

### D4 · 位置: 选中区域正上方

**方案**: 工具栏作为 Composable 渲染在 `Box` 中,放在 `BasicTextField` 之上,使用 `Modifier.offset` 根据选中区域计算位置 (MVP 简化为固定偏移,后续可优化)。

**MVP 实现**: 工具栏固定在 `BasicTextField` 顶部上方 8dp 处,不动态计算选中区域位置。

**Why**: MVP 优先保证功能可用,位置精度可后续优化。

### D5 · AI 按钮的下拉菜单

**方案**: AI 按钮内部使用 `DropdownMenu`,6 个操作项 (扩写/润色/整理/总结/翻译/复制)。

**为什么不直接触发扩写**:
- 6 个操作频率不同,直接触发扩写对想用"翻译"的用户不友好
- dropdown 让用户预览所有选项,与原有 AI 操作菜单 (`AiwritingEntry.ActionSheetRoute`) 风格一致

### D6 · 字符串 i18n

**新增 strings** (在 `values/strings.xml` + `values-en/strings.xml` 同步):
- `selection_toolbar_add_entity`: 加入实体 / Add entity
- `selection_toolbar_ai`: AI 操作 / AI actions
- `selection_toolbar_ai_expand`: 扩写 / Expand
- `selection_toolbar_ai_polish`: 润色 / Polish
- `selection_toolbar_ai_organize`: 整理 / Organize
- `selection_toolbar_ai_summarize`: 总结 / Summarize
- `selection_toolbar_ai_translate`: 翻译 / Translate

## Risks / Trade-offs

| Risk | Mitigation |
| --- | --- |
| 浮动工具栏遮挡选中文本 | MVP 简化为正上方,选中区域下方无遮挡;后续可优化为屏幕边缘避让 |
| 选中后立即操作会误触 | 工具栏需要用户主动点击,不会立即执行任何破坏性操作 |
| AI 按钮禁用状态在多 provider 切换时不同步 | 沿用 detail 屏已有的 `hasAiProvider` state (`produceState` 订阅 SecureApiKeyStore) |
| 工具栏与系统 selection handle 重叠 | 工具栏位于 selection 上方,系统 handle 在 selection 下方,空间上不冲突 |
| 滚动时工具栏不消失 | MVP 简化,后续可加 `Modifier.scrolled` 检测 scroll 事件 |
| 用户加实体后无视觉反馈 | 本 change 仅 UI 占位,onClick 回调暴露但不实现,视觉反馈由后续 change 处理 |

## Migration Plan

1. **代码改动**:
   - 新增 `SelectionFloatingToolbar.kt` 组件文件
   - 修改 `QuickNoteDetailScreen.kt`:
     - 移除 `bottomBar` lambda
     - 在 `BasicTextField` 中维护 selection state
     - 在 content 区渲染 `SelectionFloatingToolbar`(当 `!selection.collapsed` 时)
2. **资源改动**:
   - `values/strings.xml` + `values-en/strings.xml` 增加 7 条字符串
3. **验证**:
   - `./gradlew :app:assembleDebug` 通过
   - `./gradlew :app:ktlintCheck` 通过
   - `./gradlew :app:testDebugUnitTest` 全绿
4. **回滚策略**: 直接 revert commit 即可,无数据库迁移影响

## Open Questions

- 工具栏出现动画 (slide-in / fade-in)? MVP 不做,后续可加 `AnimatedVisibility`
- 工具栏位置自适应 (屏幕边缘避让)? MVP 简化为正上方
- 工具栏与现有 `AiwritingEntry.ActionSheetRoute` 关系? 本 change 直接在 toolbar 内集成 dropdown,后续可重构为复用 ActionSheetRoute