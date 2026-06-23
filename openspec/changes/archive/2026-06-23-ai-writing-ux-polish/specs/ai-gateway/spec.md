# ai-gateway Delta Spec

## MODIFIED Requirements

### Requirement: WritingOp 枚举新增 SUMMARIZE / TRANSLATE

`WritingOp` 枚举 MUST 新增两个值：
- `SUMMARIZE` — 摘要操作
- `TRANSLATE` — 翻译操作（v1 自动检测源语言方向：中文→英文 / 英文→中文）

`DefaultPrompts.forOp()` MUST 新增对应的 system prompt，含 few-shot example + 输出格式约束。

#### Scenario: SUMMARIZE prompt 包含格式约束
- **WHEN** `DefaultPrompts.forOp(SUMMARIZE)` 被调用
- **THEN** 返回的 system prompt 包含 few-shot example + "直接输出摘要，不加前缀"格式约束

#### Scenario: TRANSLATE prompt 自动检测方向
- **WHEN** `DefaultPrompts.forOp(TRANSLATE)` 被调用
- **THEN** 返回的 system prompt 指示"检测输入语言：中文输入则翻译为英文，英文输入则翻译为中文，直接输出译文不加前缀"

#### Scenario: 新 WritingOp 可通过 AiGateway 调用
- **WHEN** `AiGateway.streamWritingOp(SUMMARIZE, sourceText="长文...", providerId="deepseek", apikey="sk-xxx")`
- **THEN** gateway 正常路由到对应 provider，请求体 `system` 字段为 SUMMARIZE 对应 prompt
