## Tasks

### WritingOp 枚举 + Prompt 扩展
- [x] `WritingOp.kt` 新增 `SUMMARIZE` / `TRANSLATE` 枚举值
- [x] `DefaultPrompts.kt` 新增 `summarizeSystem` / `translateSystem` prompt（含 few-shot + 格式约束）
- [x] `DefaultPrompts.forOp()` 补全 SUMMARIZE / TRANSLATE 分支
- [x] `strings.xml` / `values-en/strings.xml` 新增摘要/翻译操作 i18n key

### AiActionUiState + ViewModel 扩展
- [x] `AiActionUiState.Done` 新增 `originalText: String` 字段
- [x] `AiActionViewModel.start()` 记录 `lastOp / lastSourceText / lastNoteId` 为私有字段
- [x] `AiActionViewModel.start()` Done 态携带 `originalText = sourceText`
- [x] `AiActionViewModel.retry()` 新增公开方法，复用上次参数调 `start()`
- [x] `AiActionViewModel` 所有 `when(op)` 穷举补全 SUMMARIZE / TRANSLATE

### ActionSheet 扩展
- [x] `ActionSheet.kt` / `ActionSelectionViewModel.kt` 新增摘要/翻译菜单项
- [x] 菜单项文案走 `stringResource(R.string.aiwriting_action_summarize/translate)`

### StreamingPanel UI 增强
- [x] 新增 `TypingIndicator` Composable（3 圆点脉冲动画）
- [x] `StreamingPanel` Streaming 态 `partialText.isEmpty()` 时显示 `TypingIndicator`
- [x] `ScrollableBody` 加 `animateContentSize` 平滑文本增长
- [x] 新增 `diffHighlight(original: String, modified: String): AnnotatedString` 工具函数（LCS diff）
- [x] Done 态 `ScrollableBody` 用 `diffHighlight(originalText, finalText)` 渲染高亮文本

### Failed 态错误降级
- [x] `StreamingPanel` Failed 态 `Network/Timeout` 加"重试"按钮调 `viewModel.retry()`
- [x] `StreamingPanel` Failed 态 `Auth` 加"去设置"按钮调 `onNavigateToSettings()`
- [x] `StreamingPanel` Failed 态 `InsufficientBalance` 加"去设置" + 提示文案"可尝试切换其他模型"
- [x] `StreamingPanel` 新增 `onNavigateToSettings: () -> Unit` callback 参数
- [x] `QuickNoteDetailScreen` 传入 `onNavigateToSettings = { navController.navigate(Settings) }`
- [x] 新增"重试"/"去设置"按钮 i18n key

### 验证
- [x] `./gradlew :app:check` 全绿（编译 + 169+ tests + ktlint）
- [ ] 手动验证：Streaming 态 typing indicator / Done 态 diff 高亮 / Failed 态重试按钮
