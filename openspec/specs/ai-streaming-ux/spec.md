# ai-streaming-ux Specification

## Purpose
TBD - created by archiving change ui-redesign-v2. Update Purpose after archive.
## Requirements
### Requirement: Streaming 态 typing indicator

StreamingPanel 在 `AiActionUiState.Streaming` 态 MUST 在 `partialText` 下方渲染 3 个水平排列的圆点，以 Compose `infiniteTransition` + `animateFloat` 实现交错脉冲动画（每个圆点延迟 200ms），圆点颜色用 `MaterialTheme.colorScheme.primary`，大小 6.dp，间距 4.dp。当首个 Delta 到达后圆点消失，只显示文本。

#### Scenario: 首个 Delta 前显示 typing indicator
- **WHEN** state = `Streaming(op=EXPAND, partialText="")`
- **THEN** 圆点区域可见（3 个跳动圆点），文本区域空白

#### Scenario: Delta 到达后隐藏 typing indicator
- **WHEN** state = `Streaming(op=EXPAND, partialText="你")`
- **THEN** 圆点区域不可见，文本区域显示"你"

### Requirement: Done 态 diff 高亮

`AiActionUiState.Done` MUST 新增 `originalText: String` 字段（用户选中的原始文本）。StreamingPanel 在 Done 态 MUST 对 `finalText` 与 `originalText` 做 LCS diff，新增部分用 `SpanStyle(background = MaterialTheme.colorScheme.primaryContainer)` 高亮标记，通过 `AnnotatedString` + `Text` 渲染。

#### Scenario: 扩写后新增内容高亮
- **WHEN** state = `Done(op=EXPAND, originalText="晨跑", finalText="晨跑让我精神焕发，清晨的空气格外清新")`
- **THEN** "晨跑"正常渲染，"让我精神焕发，清晨的空气格外清新"渲染为 primaryContainer 背景色

#### Scenario: 润色后替换内容高亮
- **WHEN** state = `Done(op=POLISH, originalText="今天天气很好", finalText="今日天朗气清")`
- **THEN** "今日天朗气清"中与原文不同的部分渲染为 primaryContainer 背景色

### Requirement: Failed 态重试与快捷跳转

StreamingPanel 在 `AiActionUiState.Failed` 态 MUST 根据错误类型显示额外按钮：
- `Network` / `Timeout` → 显示"重试"按钮，点击调用 `viewModel.retry()`
- `Auth` → 显示"去设置"按钮，点击调用 `onNavigateToSettings()` callback
- `InsufficientBalance` → 显示"去设置"按钮 + 文案提示"可尝试切换其他模型"
- 其他错误 → 仅显示"关闭"（行为不变）

#### Scenario: 网络错误显示重试
- **WHEN** state = `Failed(op=EXPAND, error=Network(500))`
- **THEN** 底部按钮："重试" + "关闭"

#### Scenario: Auth 错误显示去设置
- **WHEN** state = `Failed(op=POLISH, error=Auth(401))`
- **THEN** 底部按钮："去设置" + "关闭"

#### Scenario: retry 用上次参数重跑
- **WHEN** 用户点击"重试"
- **THEN** `viewModel.retry()` 被调用，内部复用 `lastOp/lastSourceText/lastNoteId` 重新调 `start()`

### Requirement: AiActionUiState.Done 新增 originalText

`AiActionUiState.Done` MUST 包含 `originalText: String` 字段（用户选中的原始文本）。

#### Scenario: Done 携带原始文本
- **WHEN** `AiActionViewModel.start(EXPAND, sourceText="晨跑", noteId="n1")` 执行后 AiGateway emit Done
- **THEN** `AiActionUiState.Done` 的 `originalText="晨跑"`，`finalText` 为 AI 输出

### Requirement: AiwritingEntry noteId reaches AiActionViewModel via SavedStateHandle

`feature/aiwriting/AiwritingEntry(noteId: String, ...)` Composable MUST pass `noteId` into `AiActionViewModel` via Hilt's `SavedStateHandle["noteId"]` (rather than only through the Composable parameter list, which `hiltViewModel()` silently drops). The ViewModel MUST read the parameter from `SavedStateHandle.get<String>("noteId")` in its constructor block.

#### Scenario: AiwritingEntry launched with noteId
- **WHEN** caller invokes `AiwritingEntry(noteId = "n1", ...)` from `QuickNoteDetailScreen`
- **THEN** the resolved `AiActionViewModel.lastNoteId` equals `"n1"` and `start(op, sourceText, noteId = "n1")` runs with the correct noteId for ai_metadata update

### Requirement: StreamingPanel uses updated design tokens
The StreamingPanel ModalBottomSheet SHALL use lg(16dp) corner radius from LocalCornerRadius. The header row SHALL use ink-green primary color. The accept button SHALL use primary (ink-green) container color. The typing indicator dots SHALL use primary color.

#### Scenario: Streaming panel uses new color tokens
- **WHEN** the streaming panel is displayed
- **THEN** the header, accept button, and typing indicator use ink-green primary color from the new design system

### Requirement: ActionSheet uses updated design tokens
The ActionSheet popup SHALL use md(12dp) corner radius from LocalCornerRadius. Menu items SHALL use primary color for icons. The arrow triangle SHALL match the surfaceContainerHigh color.

#### Scenario: Action sheet uses new corner radius
- **WHEN** the action sheet popup is displayed
- **THEN** the menu card uses 12dp corner radius

