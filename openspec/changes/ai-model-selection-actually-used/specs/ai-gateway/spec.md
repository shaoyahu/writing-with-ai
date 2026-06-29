# ai-gateway Delta Spec

## MODIFIED Requirements

### Requirement: AiGateway provides a single entry point for all AI calls

系统 MUST 提供 `AiGateway` 接口(实现为 `CoreAiGateway`),暴露:
- `listProviders(): List<ProviderDescriptor>` — 已注册的 provider(含 id / displayName / models)
- `streamWritingOp(op, sourceText, providerId, apikey, modelName, systemPrompt): Flow<AiStreamEvent>` — 执⾏扩写/润色/整理,流式返回事件;**apikey 由 caller 提供,gateway 不持有凭证**
- `ping(providerId, apikey, modelName): String?` — 验证连通性;apikey 由 caller 提供;返回 `null` 表示成功,非 null 返回 provider `AiError.summary()` 字符串供 UI 直接展示

业务代码(ViewModel / Repository)**禁止**直接调 OkHttp / 构造 HTTP 请求;所有 AI 调用必须经过 `AiGateway`。

`streamWritingOp` 与 `ping` 在 `modelName == null` 时 MUST fallback 到 `provider.defaultModel`,**不**得用 `provider.supportedModels.firstOrNull()`(避免列表顺序副作用)。`provider.defaultModel` 同样为 `null` 或 blank 时,MUST emit `AiStreamEvent.Failed(AiError.ProviderNotConfigured, recoverable=false)`,不静默发「unknown」字面量到 provider。

#### Scenario: Gateway routes to provider by id
- **WHEN** 调用 `AiGateway.streamWritingOp(op=EXPAND, sourceText="hello", providerId="fake", apikey="<任意非空>")`
- **THEN** 系统从内部 `Map<String, AiProvider>` 取 `"fake"` → `FakeAiProvider`,并返回该 provider 的 `stream()` 结果 Flow

#### Scenario: Unknown provider id returns immediate Failed
- **WHEN** 调用 `AiGateway.streamWritingOp(providerId="nonexistent", apikey="<任意>")`
- **THEN** 返回 `Flow` 的首个事件为 `AiStreamEvent.Failed(AiError.Unknown(code=null, detail="provider not found"), recoverable=false)`

#### Scenario: Apikey passed through to provider credentials
- **WHEN** 调用 `AiGateway.streamWritingOp(providerId="deepseek", apikey="sk-real-123")`
- **THEN** `CoreAiGateway` 内部用 `apikey="sk-real-123"` 构造 `AiCredentials` 传给 `AnthropicCompatibleAdapter.stream()`;**不**硬编码 `"fake-apikey"` 等占位字面量

#### Scenario: modelName null fallback to provider.defaultModel
- **WHEN** 调用 `AiGateway.streamWritingOp(providerId="deepseek", apikey="sk-real-123", modelName=null)`,`DeepseekConfig.config.defaultModel == "deepseek-v4-flash"`
- **THEN** gateway 内部用 `model = "deepseek-v4-flash"` 构造 `AiRequest`,传给 `AnthropicCompatibleAdapter.stream()`;**不**用 `provider.supportedModels.firstOrNull()`(若列表顺序变化,行为不变)

#### Scenario: modelName null + defaultModel null emits ProviderNotConfigured
- **WHEN** 调用 `AiGateway.streamWritingOp(providerId="x", apikey="sk-1", modelName=null)`,且 `resolveProvider("x")` 返回的 `provider.defaultModel` 为 null 或 blank
- **THEN** 返回 `Flow` 的首个事件为 `AiStreamEvent.Failed(AiError.ProviderNotConfigured, recoverable=false)`,**不**调 `provider.stream(...)`(0 次 adapter 调用)
