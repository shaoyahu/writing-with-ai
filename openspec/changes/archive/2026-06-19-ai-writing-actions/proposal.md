## Why

M2 已把 AI 抽象层(AnthropicCompatibleAdapter / FakeProvider / AiHistory / AiGateway)落地并 review r1/r2 通过，但**没有 UI 验证**。M3 把基建转成用户能感知的"AI 助手"功能:详情屏选中文本 → 弹操作气泡(扩写/润色/整理 + 复制)→ 流式面板展示 AI 实时返回 → 用户接受/拒绝/再生成 → 替换原文并自动写 `Note.lastAiOp` / `lastAiAt`。M3 是 roadmap §3.2"AI 助手"主流程的首次落地，也是 v1 价值最核心的 feature。

## What Changes

- 新增 **AI 操作气泡**(`feature/aiwriting/action/ActionSheet.kt`):详情屏选中文本后弹出的 PopUp / DropdownMenu，提供 3 个 AI 操作按钮(扩写/润色/整理)+ 1 个"复制"按钮
- 新增 **AI 流式面板**(`feature/aiwriting/streaming/StreamingPanel.kt`):底部 ModalBottomSheet，显示当前操作 / 已收文本(实时滚动) / 取消按钮 / 接受按钮 / 拒绝按钮 / token 用量(小型 chip)
- 新增 **流式状态机 ViewModel**(`feature/aiwriting/streaming/AiActionViewModel.kt`):`@HiltViewModel`，注入 `AiGateway` + `NoteRepository`;暴露 `StateFlow<AiActionUiState>`(`Idle / Streaming(progress, partialText) / Done(text, usage) / Failed(error, recoverable)`);`start(op, sourceText, noteId)` 启动流 → 取消 / 接受 / 拒绝操作
- 新增 **用户操作 ViewModel**(`feature/aiwriting/action/ActionSelectionViewModel.kt`):选中文本后从 TextField 抽离 selectedText(单选 + 范围)
- 改 **详情屏**(`feature/quicknote/detail/QuickNoteDetailScreen.kt`):加 `TextField` + `selectionContainer` 替代 `SelectionContainer` 包 `Text`;检测 `selection != null` 时显示 FAB 触发 ActionSheet;Done 弹 ModalBottomSheet 展示 StreamingPanel，用户接受 → 替换正文 + `NoteRepository.upsert()` + `AiHistoryRepository` 已自动落
- 改 **编辑器屏**:`QuickNoteEditorViewModel` 增加 `setAiMetadata(op, at)`(已存在 `updateAiMetadata` 写 db;新增 read 路径让详情屏在 replace 之前读出当前正文)
- 改 **Note domain model**(`core/data/model/Note.kt`):无字段变更(M1 已预 lastAiOp / lastAiAt)
- 新增 **i18n**(`values/strings.xml` + `values-en/`):`aiwriting_action_expand` / `aiwriting_action_polish` / `aiwriting_action_organize` / `aiwriting_action_copy` / `aiwriting_panel_title` / `aiwriting_panel_cancel` / `aiwriting_panel_accept` / `aiwriting_panel_reject` / `aiwriting_panel_regenerate` / `aiwriting_panel_streaming` / `aiwriting_panel_usage_fmt` / `aiwriting_error_network` / `aiwriting_error_auth` / `aiwriting_error_unknown`(权威中文 + TODO(en) 占位)
- 改 **`AppNav.kt`**:加 `data object AiAction` route(单实例 Modal)，在 detail / editor 屏上覆盖
- 改 **`build.gradle.kts`**:加 `androidx.compose.runtime:runtime-saveable`(Saveable state for selectedText) — 已在 compose-bom 中，无需新依赖
- 改 **`strings.xml`** 双语:详尽 AI 文案 14 个 key
- 改 **`feature/quicknote/detail/QuickNoteDetailViewModel.kt`**:注入 `AiActionRepository` / `AiGateway`;`acceptReplace(aiText, op)` 写新正文 + 触发 `NoteRepository.upsert` + `updateAiMetadata`
- **BREAKING**:无
- **不引入**:真 provider 切换(M5)/ 提示词自定义(M5)/ 对话历史(单次操作为主，无 history in UI)/ n > 1 多次采样

## Capabilities

### New Capabilities
- `ai-actions`:ActionSheet(选中文本后弹)+ 流式 ModalBottomSheet(StreamingPanel)+ AiActionViewModel + ActionSelectionViewModel + 用户接受/拒绝/取消 状态机 + 错误降级 UI(无网络/超时/余额不足显示 fallback 文案)

### Modified Capabilities
- `quick-note`:详情屏 `Text` 改为可选择(selection state) + FAB 触发 ActionSheet + 接受 AI 输出后替换正文并落 `lastAiOp`;编辑器屏支持 `setAiMetadata` 写路径
- `ai-gateway`:M2 的 `AiGateway.streamWritingOp` 现在被 M3 UI 调用;**API 不变**，只增加消费方

## Impact

- **新增 package**:
  - `feature/aiwriting/action/` — ActionSheet + ActionSelectionViewModel
  - `feature/aiwriting/streaming/` — StreamingPanel + AiActionViewModel
  - `feature/aiwriting/error/` — 错误降级 UI helper(纯 composable)
- **修改**:
  - `feature/quicknote/detail/QuickNoteDetailScreen.kt` + ViewModel
  - `feature/quicknote/edit/QuickNoteEditorViewModel.kt`(setAiMetadata 触发写)
  - `app/AppNav.kt` — 接入 AiAction route(实际是 bottom sheet，不需要新 route)
  - `app/src/main/res/values/strings.xml` + `values-en/strings.xml` — AI 文案 14 个 key
- **新增依赖**:无(M2 已把 OkHttp / Coroutines / Compose / Hilt 全配齐;runtime-saveable 在 compose-bom 里)
- **风险**:
  - 流式面板中途 back 关闭 / ViewModel cleared → `withContext(NonCancellable)` 包 `NoteRepository.upsert`(M1 review 已修过类似场景);stream 本身不强制完成
  - 大文本(>10k 字符)展示性能:`LazyColumn` 滚动，只渲染可见区
  - 选中文本 vs 全文歧义:M3 默认用 `selection` 优先;无选区时弹"操作整篇"提示
  - Markdown 渲染:M3 **不**渲染 AI 输出的 Markdown(M3 仍走 plain text;Markwon 推迟 M5 polish);用户在详情屏可以再编辑时手动排版
  - 错按"接受"覆盖原稿:接受前显示 diff 预览?本 M3 简化:接受后保留 prev content 在 `inputSnapshot`(AiHistory)，用户在 AiHistory 历史回看
  - 复数 provider 同时可用:本 M3 写死 `providerId = "fake"`;M5 接真 apikey 时设置页加 provider 切换
