# ai-actions Delta Spec

## MODIFIED Requirements

### Requirement: ActionSheet shows available AI operations on selection

ActionSheet 菜单项从 4 个扩展为 6 个：

| 菜单项 | 文案 key | 触发行为 |
| --- | --- | --- |
| 扩写 | `aiwriting_action_expand` | `AiActionViewModel.start(op=EXPAND, sourceText, noteId)` |
| 润色 | `aiwriting_action_polish` | `AiActionViewModel.start(op=POLISH, sourceText, noteId)` |
| 整理 | `aiwriting_action_organize` | `AiActionViewModel.start(op=ORGANIZE, sourceText, noteId)` |
| 摘要 | `aiwriting_action_summarize` | `AiActionViewModel.start(op=SUMMARIZE, sourceText, noteId)` |
| 翻译 | `aiwriting_action_translate` | `AiActionViewModel.start(op=TRANSLATE, sourceText, noteId)` |
| 复制 | `aiwriting_action_copy` | `ClipboardManager.setPrimaryClip(sourceText)` |

#### Scenario: 6 个菜单项齐全
- **WHEN** 详情屏用户长按选中 5 个字符，点击 AutoAwesome FAB
- **THEN** DropdownMenu 显示 6 个菜单项（扩写/润色/整理/摘要/翻译/复制）

### Requirement: AiActionViewModel owns the streaming state machine

`AiActionUiState.Done` 新增 `originalText: String` 字段。`AiActionViewModel` 新增 `retry()` 公开方法，行为：复用 `lastOp / lastSourceText / lastNoteId` 重新调 `start()`，与 `regenerate()` 对称（retry 在 Failed 态，regenerate 在 Done 态）。

#### Scenario: retry 从 Failed 态重试
- **WHEN** state = `Failed(op=EXPAND, error=Network(500))`，用户点"重试"
- **THEN** `retry()` 被调用，内部调 `start(EXPAND, sourceText=<上次>, noteId=<上次>)`，state 进入 Streaming

#### Scenario: Done 携带 originalText
- **WHEN** `start(EXPAND, sourceText="晨跑", noteId="n1")` 执行后 Done
- **THEN** `Done(op=EXPAND, originalText="晨跑", finalText=<AI输出>, usage=...)`
