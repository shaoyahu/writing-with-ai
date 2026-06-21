# ai-gateway Delta Spec (fix-m5-blockers)

## MODIFIED Requirements

### Requirement: AiGateway provides a single entry point for all AI calls

系统 MUST 提供 `AiGateway` 接口(实现为 `CoreAiGateway`),暴露:
- `listProviders(): List<ProviderDescriptor>` — 已注册的 provider(含 id / displayName / models)
- `streamWritingOp(op, sourceText, providerId, **apikey**, modelName, systemPrompt): Flow<AiStreamEvent>` — 执⾏扩写/润色/整理,流式返回事件;**apikey 由 caller 提供,gateway 不持有凭证**
- `ping(providerId, **apikey**, modelName): Boolean` — 验证连通性;apikey 由 caller 提供

业务代码(ViewModel / Repository)**禁止**直接调 OkHttp / 构造 HTTP 请求;所有 AI 调用必须经过 `AiGateway`。

#### Scenario: Gateway routes to provider by id
- **WHEN** 调用 `AiGateway.streamWritingOp(op=EXPAND, sourceText="hello", providerId="fake", apikey="<任意非空>")`
- **THEN** 系统从内部 `Map<String, AiProvider>` 取 `"fake"` → `FakeAiProvider`,并返回该 provider 的 `stream()` 结果 Flow

#### Scenario: Unknown provider id returns immediate Failed
- **WHEN** 调用 `AiGateway.streamWritingOp(providerId="nonexistent", apikey="<任意>")`
- **THEN** 返回 `Flow` 的首个事件为 `AiStreamEvent.Failed(AiError.Unknown(code=null, detail="provider not found"), recoverable=false)`

#### Scenario: Apikey passed through to provider credentials
- **WHEN** 调用 `AiGateway.streamWritingOp(providerId="deepseek", apikey="sk-real-123")`
- **THEN** `CoreAiGateway` 内部用 `apikey="sk-real-123"` 构造 `AiCredentials` 传给 `AnthropicCompatibleAdapter.stream()`;**不**硬编码 `"fake-apikey"` 等占位字面量

## ADDED Requirements

### Requirement: AiGateway does not depend on SecureApiKeyStore

`CoreAiGateway` 构造 MUST **不** 注入 `SecureApiKeyStore` / `ProviderPrefsStore`;apikey 由 caller(`AiActionViewModel` / `ModelManagementViewModel`)在调 gateway 前通过 `SecureApiKeyStore.get(providerId)` 取得,`null` → caller 自行 emit `ProviderNotConfigured` Failed,非 null → 透传进 `AiGateway.streamWritingOp(..., apikey = apikey)` / `AiGateway.ping(..., apikey = apikey)`。理由:gateway 保持单一职责(只做 protocol 路由),凭证获取属于业务侧职责;`SecureApiKeyStore` 是业务设施(走 Hilt @ApplicationContext 注入),不应该被 `core/ai/api/AiGateway` 抽象依赖。

#### Scenario: CoreAiGateway 构造参数只有 AiProviders + history
- **WHEN** 读 `core/ai/CoreAiGateway.kt` 构造签名
- **THEN** 形参列表只含 `providers: Map<String, AiProvider>` + `historyRepo: Lazy<AiHistoryRepository>`,**不**包含 `SecureApiKeyStore` / `ProviderPrefsStore` / `Context` / `SharedPreferences`

#### Scenario: caller 拿不到 apikey 不调 gateway
- **WHEN** `AiActionViewModel.start(...)` 调用,`secureApiKeyStore.get(providerId)` 返回 `null`
- **THEN** ViewModel 内部 emit `Failed(op, ProviderNotConfigured)`,**不**调 `aiGateway.streamWritingOp(...)`(0 次调用),UI 显示"请先在设置 → 模型管理配置"
