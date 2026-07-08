## MODIFIED Requirements

### Requirement: AI 调用不允许走 fake 兜底(debug 包同 release 行为)

`BuildConfig.DEBUG` **不**再是「无 AI provider apikey 时回退到 `FakeAiProvider`」的合法豁免。debug 与 release 行为一致:

- 用户未配置任何真实 provider apikey → 任何 AI 调用路径(LlmEntityExtractor 拆解、AiActionViewModel 扩写/润色/整理/摘要/翻译 等) MUST 走「无 provider」错误分支(DecomposeState.ApiKeyMissing / AiError.ProviderNotConfigured / Snackbar 「请先配置 AI 模型」),**不**允许返回 fake 固定文本
- `FakeAiProvider` 类**保留在代码库**(JVM 单测用),但**禁止**在 `app/src/main/` 下任何 main / androidTest 代码里被当作 default provider 注册或调用
- 不允许的反例(必须删):`providers.firstOrNull() ?: if (BuildConfig.DEBUG) "fake" else return 0` / `val hasProvider = providers.isNotEmpty() || BuildConfig.DEBUG` / `if (BuildConfig.DEBUG) FakeAiProvider() else null` / `if (fake != null) put("fake", fake)` 之类分支

#### Scenario: debug 包无 apikey 走 ApiKeyMissing 错误
- **WHEN** debug 包跑在真机/模拟器,用户未配置任何 AI provider apikey,触发任意 AI 调用(拆解 / 扩写 / 润色 等)
- **THEN** 调用走「无 provider」错误分支(DecomposeState.ApiKeyMissing / Snackbar「请先配置 AI 模型」 + 「去设置」按钮);**不**返回 fake 文本,UI 不展示任何"Fake AI response for testing" 之类固定内容

#### Scenario: debug 包有 apikey 走真 provider
- **WHEN** debug 包跑在真机/模拟器,用户已配置 deepseek / minimax / mimo 任一家真实 apikey
- **THEN** 调用走真 provider HTTP 路径,SSE 流式返回;debug 与 release 行为一致

#### Scenario: JVM 单测仍可用 FakeAiProvider
- **WHEN** `app/src/test/` 单测通过 `FakeConfigHolder.set(text = ..., delayMs = ..., ...)` 注入固定响应
- **THEN** `FakeAiProvider` 正常 emit Delta 流,单测断言通过 — FakeAiProvider 仅 JVM 单测用,不在 main 代码路径出现

#### Scenario: grep 验证 main 无 BuildConfig.DEBUG 兜底 fake
- **WHEN** `grep -rE "(BuildConfig.DEBUG.*fake|\"fake\".*BuildConfig.DEBUG)" app/src/main/`
- **THEN** 0 匹配(无任何「DEBUG + fake」组合分支)