## Why

AI 写作是本 App 核心体验，当前 M3 落地的流式 UI/错误处理/prompt 存在体验短板：Streaming 态无视觉反馈、Failed 态只有"关闭"无法重试、Prompt 模板缺少 few-shot 约束、WritingOp 只支持 3 种操作。这些改进直接影响日常使用感受。

## What Changes

- **流式 UI 动画增强**：Streaming 态加 typing indicator；文本增长加 `animateContentSize`；Done 态 diff 高亮（`AnnotatedString` 标记新增与原文差异）
- **Failed 态错误降级增强**：网络/超时加"重试"按钮；Auth 加"去设置"快捷入口；InsufficientBalance 加 provider 切换建议
- **Prompt 模板迭代**：`DefaultPrompts` 加 few-shot + 输出格式约束；新增 `SUMMARIZE` / `TRANSLATE` WritingOp 及对应 prompt
- **ActionSheet 扩展**：菜单项 4 → 6（新增摘要/翻译）；i18n 补全

## Capabilities

### New Capabilities
- `ai-streaming-ux`: 流式面板 UI 增强规范（typing indicator / animateContentSize / diff 高亮）

### Modified Capabilities
- `ai-actions`: 新增 SUMMARIZE/TRANSLATE；Failed 态加重试/快捷跳转；Done 态加 originalText；ActionSheet 扩展
- `ai-gateway`: WritingOp 新增 SUMMARIZE/TRANSLATE；DefaultPrompts 迭代

## Impact

- `StreamingPanel.kt` / `AiActionViewModel.kt` / `ActionSheet.kt` / `WritingOp.kt` / `DefaultPrompts.kt` / strings.xml
