# ai-gateway Specification

## Purpose
TBD - created by archiving change ai-abstraction-layer. Update Purpose after archive.
## Requirements
### Requirement: AiGateway provides a single entry point for all AI calls

系统 MUST 提供 `AiGateway` 接口(实现为 `CoreAiGateway`),暴露:
- `listProviders(): List<ProviderDescriptor>` — 已注册的 provider(含 id / displayName / models)
- `streamWritingOp(op, sourceText, providerId, apikey, modelName, systemPrompt): Flow<AiStreamEvent>` — 执⾏扩写/润色/整理,流式返回事件;**apikey 由 caller 提供,gateway 不持有凭证**
- `ping(providerId, apikey, modelName): String?` — 验证连通性;apikey 由 caller 提供;返回 `null` 表示成功,非 null 返回 provider `AiError.summary()` 字符串供 UI 直接展示

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

### Requirement: AiGateway does not depend on SecureApiKeyStore

`CoreAiGateway` 构造 MUST **不** 注入 `SecureApiKeyStore` / `ProviderPrefsStore`;apikey 由 caller(`AiActionViewModel` / `ModelManagementViewModel`)在调 gateway 前通过 `SecureApiKeyStore.get(providerId)` 取得,`null` → caller 自行 emit `ProviderNotConfigured` Failed,非 null → 透传进 `AiGateway.streamWritingOp(..., apikey = apikey)` / `AiGateway.ping(..., apikey = apikey)`。理由:gateway 保持单一职责(只做 protocol 路由),凭证获取属于业务侧职责;`SecureApiKeyStore` 是业务设施(走 Hilt @ApplicationContext 注入),不应该被 `core/ai/api/AiGateway` 抽象依赖。

#### Scenario: CoreAiGateway 构造参数只有 AiProviders + history
- **WHEN** 读 `core/ai/CoreAiGateway.kt` 构造签名
- **THEN** 形参列表只含 `providers: Map<String, AiProvider>` + `historyRepo: Lazy<AiHistoryRepository>`,**不**包含 `SecureApiKeyStore` / `ProviderPrefsStore` / `Context` / `SharedPreferences`

#### Scenario: caller 拿不到 apikey 不调 gateway
- **WHEN** `AiActionViewModel.start(...)` 调用,`secureApiKeyStore.get(providerId)` 返回 `null`
- **THEN** ViewModel 内部 emit `Failed(op, ProviderNotConfigured)`,**不**调 `aiGateway.streamWritingOp(...)`(0 次调用),UI 显示"请先在设置 → 模型管理配置"

### Requirement: AiProvider SPI is data-driven via ProviderConfig

系统 MUST 定义 `AiProvider` 接口,所有 provider(包括 `FakeAiProvider` 和 `AnthropicCompatibleAdapter`)实现此接口:
```kotlin
interface AiProvider {
    val id: String
    val displayName: String
    fun stream(request: AiRequest, credentials: AiCredentials): Flow<AiStreamEvent>
}
```

对真实 provider,创建实例时传入 `ProviderConfig`,由 `ProviderConfig` 决定 HTTP 行为(认证方式 / URL / 模型列表),**禁止**在 `AiProvider` 实现中硬编码 URL 或 header 名。

#### Scenario: Anthropic adapter reads config for auth
- **WHEN** `AnthropicCompatibleAdapter`(config=`ProviderConfig(authStyle=X_API_KEY, ...)`) 发起 HTTP 请求
- **THEN** 请求头包含 `x-api-key: $apikey`,URL 为 `$baseUrl/$endpointPath`

#### Scenario: Custom header auth
- **WHEN** `AnthropicCompatibleAdapter`(config=`ProviderConfig(authStyle=CUSTOM_HEADER, customHeaderName="api-key", ...)`) 发起 HTTP 请求
- **THEN** 请求头包含 `api-key: $apikey`

### Requirement: ProviderConfig for three preset providers

系统 MUST 在 `core/ai/provider/{deepseek,minimax,mimo}/` 提供三家预置 provider 的 `ProviderConfig` 数据(纯 Kotlin object,零逻辑):

| Provider | baseUrl | endpointPath | authStyle | defaultModel |
| --- | --- | --- | --- | --- |
| deepseek | `https://api.deepseek.com` | `/anthropic/v1/messages` | X_API_KEY | `deepseek-v4-flash` |
| minimax | `https://api.minimaxi.com` | `/anthropic/v1/messages` | AUTHORIZATION | `MiniMax-M2.7-highspeed` |
| mimo | `https://api.xiaomimimo.com` | `/anthropic/v1/messages` | CUSTOM_HEADER(api-key) | `mimo-v2.5-flash` |

#### Scenario: Deepseek config is accessible
- **WHEN** 代码引用 `com.yy.writingwithai.core.ai.provider.deepseek.DeepseekConfig.config`
- **THEN** 返回 `ProviderConfig(id="deepseek", baseUrl="https://api.deepseek.com", endpointPath="/anthropic/v1/messages", authStyle=X_API_KEY, defaultModel="deepseek-v4-flash")`

### Requirement: AnthropicCompatibleAdapter sends Anthropic-compatible messages API requests

系统 MUST 提供 `AnthropicCompatibleAdapter : AiProvider`,由 `ProviderConfig` 驱动,向 `$baseUrl/$endpointPath` 发 `POST` 请求,请求体走 Anthropic Messages API 格式:
```json
{
  "model": "...",
  "max_tokens": 2048,
  "stream": true,
  "system": "<system prompt from template>",
  "messages": [{"role": "user", "content": "<sourceText>"}]
}
```
Body 构造由 `AnthropicCompatibleAdapter` 内部完成,调用方只传 `AiRequest`(op / sourceText / model)。

#### Scenario: Stream request body is well-formed
- **WHEN** `AnthropicCompatibleAdapter.stream(AiRequest(op=EXPAND, sourceText="你好", model="deepseek-v4-flash"), credentials=AiCredentials(apikey="sk-test"))` 执行
- **THEN** HTTP POST body 的 `messages[0].content` 为 `"你好"`,`stream=true`,`system` 来自 ExpandTemplate

### Requirement: SSE parser turns OkHttp streaming body into Flow<SseEvent>

系统 MUST 提供 `SseParser`,接受 OkHttp `Response` 的 `BufferedSource`,返回 `Flow<SseEvent>`:
```kotlin
fun parse(source: BufferedSource): Flow<SseEvent>
sealed interface SseEvent {
    data class Data(val content: String) : SseEvent
    data object Done : SseEvent
    data class Error(val t: Throwable) : SseEvent
}
```
解析逻辑:逐行读 `readUtf8Line()`,检测 `data: ` 前缀,聚合多行 → 一个 `Data`;检测 `[DONE]` → `Done`;IO 异常 → `Error`;30s 无新行 → `Error(SocketTimeoutException)`。

#### Scenario: Normal SSE stream parsed
- **WHEN** `SseParser.parse(source)` 接收连续行 `data: {"delta":{"text":"hello"}}\n\ndata: {"delta":{"text":"  world"}}\n\ndata: [DONE]\n\n`
- **THEN** Flow emits: `Data("{\"delta\":{\"text\":\"hello\"}}")` → `Data("{\"delta\":{\"text\":\"  world\"}}")` → `Done`

### Requirement: AnthropicCompatibleAdapter maps SseEvent to AiStreamEvent

系统 MUST 在 `AnthropicCompatibleAdapter.stream()` 内部把 `SseEvent.Data` parse 为 JSON,提取 `type` 字段,映射为 `AiStreamEvent`:
- `message_start` / `content_block_start` → `Started`
- `content_block_delta` → `Delta(text=delta.text)`
- `message_delta`(含 `usage`) → `Usage(inputTokens=..., outputTokens=...)`
- `message_stop` → `Done`
- 任意 JSON parse 失败 → `Failed(Deserialization(reason))`
- 非 200 HTTP status → `Failed(Auth/Network/InsufficientBalance/...)`

#### Scenario: Delta events accumulate in consumer
- **WHEN** server 返回 `content_block_delta` delta.text="hello" → delta.text=" world" → `message_stop`
- **THEN** AiGateway 下游收到: `Started` → `Delta("hello")` → `Delta(" world")` → `Done`

### Requirement: FakeProvider returns configurable fixed text with configurable delay and error

系统 MUST 提供 `FakeAiProvider : AiProvider`,支持通过 `FakeConfigHolder` 在运行时配置:
- `text: String` — 模拟 AI 逐 token 输出的完整文本
- `delayMs: Long` — 每个 `Delta` 事件之间的间隔
- `errorAfterTokens: Int?` — 若 non-null,在 emit 指定 token 数后 emit `Failed`(模拟中断)
- `tokenCounts: AiUsage` — Usage 事件的 token 数

`FakeAiProvider.stream()` MUST 返回 `flow { ... }`,emit `Started` → 按 token 粒度 emit `Delta`(带 `delayMs` 延迟) → emit `Usage(...)` → `Done`。

#### Scenario: FakeProvider normal flow
- **WHEN** `FakeConfigHolder.text="hello world"`, `delayMs=0`, `errorAfterTokens=null`
- **THEN** Flow emits:`Started` → `Delta("hello world")` → `Usage(...)` → `Done`

#### Scenario: FakeProvider error injection
- **WHEN** `FakeConfigHolder.errorAfterTokens=1`
- **THEN** Flow emits:`Started` → `Delta("h")` → `Failed(AiError.Network(...), recoverable=true)`

### Requirement: Prompt templates exist for three writing operations

系统 MUST 在 `core/ai/prompt/` 提供三类操作的 system prompt 模板(`ExpandPrompt.kt` / `PolishPrompt.kt` / `OrganizePrompt.kt`),每个 `internal object`:
- `const val SYSTEM: String` — system prompt 纯常量,**不**接受用户输入
- `fun userMessage(sourceText: String): String` — 构造 user 消息

#### Scenario: Expand prompt builds user message
- **WHEN** `ExpandPrompt.userMessage("晨跑")` 调用
- **THEN** 返回的字符串包含 `"晨跑"`,不包含 system prompt 片段

#### Scenario: System prompt does not accept user input
- **WHEN** `ExpandPrompt.SYSTEM` 被引用
- **THEN** 返回纯常量字符串,其内容不 包含 `$` 或可注入的占位符

### Requirement: Custom provider config supports user-defined Anthropic-compatible providers

系统 MUST 在 `ProviderConfig` 中定义 `customHeaders: Map<String, String>` 字段(M2 留空,预留给 M3/M4 用户自定义 provider)。自定义 provider 走的仍是 `AnthropicCompatibleAdapter`,由用户填的配置构造 ProviderConfig。

#### Scenario: Custom provider with extra headers
- **WHEN** `ProviderConfig(authStyle=CUSTOM_HEADER, customHeaders=mapOf("api-key" to "sk-test", "X-Custom" to "foo"))` 传给 adapter
- **THEN** adapter 合并 `customHeaders` 到请求头中(`api-key: sk-test`, `X-Custom: foo`)



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
