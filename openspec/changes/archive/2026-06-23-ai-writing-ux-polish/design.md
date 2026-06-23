## Context

当前 AI 写作功能(M3)已端到端走通：ActionSheet → AiActionViewModel → AiGateway → SSE → StreamingPanel。但体验粗糙：流式输出只看文本累加无动画反馈、错误态只有关闭无重试、Prompt 模板写死通用提示词、只支持 3 种操作。本 change 在不改架构的前提下打磨 UX。

关键现状：
- `WritingOp` 是 enum class `EXPAND / POLISH / ORGANIZE`，被 `ActionSheet` / `AiActionViewModel` / `DefaultPrompts` / `StreamingPanel` 引用
- `AiActionUiState.Done` 只有 `op + finalText + usage`，无 `originalText`
- `StreamingPanel` 的 `ScrollableBody` 用 `Text(partialText)` 渲染，无动画
- Failed 态只渲染 `toDisplayMessage()` + "关闭"按钮
- `DefaultPrompts.forOp()` 返回 system prompt，目前无 few-shot / 输出格式约束

## Goals / Non-Goals

**Goals:**
- Streaming 态有 typing indicator 视觉反馈
- Done 态 diff 高亮让用户一眼看到新增内容
- Failed 态可重试 / 快捷跳转设置
- WritingOp 新增 SUMMARIZE / TRANSLATE
- DefaultPrompts 加 few-shot + 输出格式约束提升输出质量

**Non-Goals:**
- 不改 AiGateway / AnthropicCompatibleAdapter 的 SSE 协议
- 不引入 Markdown 渲染库（编辑器改造是 B6b）
- 不做 provider 自动切换（只提示用户手动切）
- 不做 prompt A/B 测试框架

## Decisions

### D1: typing indicator 用 Compose 动画

Lottie 需加依赖(~200KB)只为 3 个点不值得。Compose `infiniteTransition` + `animateFloat` 即可，零额外依赖。

### D2: diff 高亮用 AnnotatedString + SpanStyle

Done 态持有 `originalText`，用 LCS 找新增片段，标 `SpanStyle(background = primaryContainer)`。笔记通常 <5000 字，LCS 可接受。

### D3: retry() 复用上次参数

与 `regenerate()` 对称：retry 在 Failed 态，regenerate 在 Done 态。都复用 `lastOp / lastSourceText / lastNoteId`。

### D4: WritingOp 枚举直接加值

加 SUMMARIZE / TRANSLATE 枚举值，编译器强制 `when` 穷举补全。`DefaultPrompts` 新增对应 prompt，translate v1 自动检测方向。

### D5: "去设置"用 callback

StreamingPanel 通过 callback `onNavigateToSettings: () -> Unit` 从 QuickNoteDetailScreen 传入 `navController.navigate(Settings)`。

## Risks / Trade-offs

- [LCS 性能] → 笔记 <5000 字时 O(n²) 可接受；若后续笔记变长可降级为只标记"新增"
- [枚举扩展破坏性] → 编译器强制补全是好事，非运行时风险
- [translate 无目标语言选择] → v1 自动检测，后续可加语言选择 UI
