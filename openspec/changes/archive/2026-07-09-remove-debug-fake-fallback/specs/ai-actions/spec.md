## MODIFIED Requirements

### Requirement: AI op 调用必须使用真 AI provider(debug 包同 release 行为)

`AiActionViewModel` 扩写 / 润色 / 整理 / 摘要 / 翻译 5 个 op MUST 仅在用户配置真实 AI provider apikey 时调用 LLM。`BuildConfig.DEBUG` **不**再是「无 apikey 时回退 fake」的合法理由:

- 无任何真实 provider apikey → `AiActionViewModel.start(...)` 检测 `secureApiKeyStore.observeConfiguredProviders().first().isEmpty()` → 立即 emit `AiError.ProviderNotConfigured` → UI Snackbar「请先配置 AI 模型」 + 「去设置」action,跳 AI 设置页
- `PROVIDER_ID_FAKE` 常量删除(`AiActionViewModel.kt:71`);全链路不再认 `"fake"` 作为合法 provider id
- `CoreAiGateway.stream(...)` 收到 `providerId == "fake"` 时不再走 fake 特殊路径(line 231);provider map 不含 "fake",自动 fall through 到通用"无 provider"错误处理

#### Scenario: debug 包无 apikey 触发扩写 → 请先配置
- **WHEN** debug 包跑在真机/模拟器,用户未配置 AI provider apikey,在详情屏选中文本点扩写
- **THEN** `AiActionViewModel.start(EXPAND, ...)` 立即 emit `AiError.ProviderNotConfigured`;UI Snackbar 显示「请先配置 AI 模型」 + 「去设置」按钮;点击 action 跳 `ModelManagementScreen`;**不**调用 FakeAiProvider,**不**出现 fake 文本

#### Scenario: debug 包有 apikey 触发扩写 → 真 provider 流式
- **WHEN** debug 包跑在真机/模拟器,用户已配置 deepseek apikey,选中文本点扩写
- **THEN** `AiActionViewModel` 走真 provider HTTP,SSE 流式返回 Delta,UI StreamingPanel 逐 token 渲染;debug 与 release 行为一致

#### Scenario: 扩写完成 token 落 ai_history 表
- **WHEN** 真 provider 扩写流式完成
- **THEN** ai_history 表写入一条记录(`op=expand`, `providerId="deepseek"`, `inputTokens` / `outputTokens` / `totalTokens`);debug 与 release 一致

#### Scenario: grep 验证 AiActionViewModel 无 PROVIDER_ID_FAKE 常量
- **WHEN** `grep "PROVIDER_ID_FAKE\|\"fake\"" app/src/main/java/com/yy/writingwithai/feature/aiwriting/streaming/AiActionViewModel.kt`
- **THEN** 0 匹配

#### Scenario: grep 验证 CoreAiGateway 无 fake 特殊处理
- **WHEN** `grep "FakeAiProvider" app/src/main/java/com/yy/writingwithai/core/ai/CoreAiGateway.kt`
- **THEN** 0 匹配