## Context

M1 落地了 `Note` / `NoteTagCrossRef` Room schema(v1)和 `Note.lastAiOp` / `lastAiAt` 占位字段(始终 null)。M0 已引入 OkHttp(`4.12.0`)+ kotlinx-serialization(1.7.3)+ Hilt。

需求进入 M2:搭建 AI 抽象层 —— `AiGateway` 统一入口 + `AnthropicCompatibleAdapter`(**唯一**的 `AiProvider` 实现,三家 provider 数据驱动,不写独立 adapter)+ `FakeProvider` 端到端,**不接真 provider**(roadmap §15.1 拍板,真联调推迟 M5)。M2 验收用 FakeProvider 模拟流式出结果 → 落 AiHistory + 写 Note.lastAiOp。

roadmap 拍板项(2026-06-18):
- 三家 provider 全走 Anthropic Messages API 兼容,共用 **一个** `AnthropicCompatibleAdapter`;差异点通过 `ProviderConfig` 数据驱动(§6.3)
- `FakeProvider` 返回固定文本 + 可配 token 用量 + 可控延迟和错误注入(§15.1)
- okhttp `4.12.0` + 自写 SSE 解析,不用 Retrofit(§4.2)
- prompt 注入防御:用户文本只放 user 消息 content,不参与 system prompt 拼接(§6.5)
- 错误降级必须(§6.6):无网络/401/402/超时 → UI fallback 不能白屏
- 成本可观测:每次调用记录 token 消耗,落 `ai_history` 表(§3.2)

## Goals / Non-Goals

**Goals:**
- `AiGateway` / `AiProvider` 接口定形,业务侧不直接构造 HTTP 请求
- `AnthropicCompatibleAdapter` 实现,由 `ProviderConfig` 驱动认证/URL/字段,支持三家 provider
- SSE 解析(`core/ai/stream/SseParser.kt`)统一为 `Flow<SseEvent>`,映射 `AiStreamEvent`
- FakeProvider 端到端走通(构建 → 流式 → 落 AiHistory + 写 Note.lastAiOp)
- `AiHistoryEntity` Room 表 + DAO + Repository,每次 AI 调用自动落库
- Prompt 模板目录 `core/ai/prompt/`(三类操作的 system prompt),用户文本不拼 system

**Non-Goals:**
- 真 provider 联调(推迟到 M5 / M3 使用真实 apikey 时)
- 用户同意页 / apikey 加密存储(M4 onboarding)
- AI 操作 UI(扩写/润色/整理按钮 + 流式面板 — M3 `ai-writing-actions`)
- 设置页"模型管理" / "用量统计" UI(M3/M4)
- 自定义 Anthropic 兼容 provider 的用户输入 UI(M3/M4)
- 非 Anthropic 协议(OpenAI 兼容 / 私有协议 — v2+)
- `n > 1` 并行采样、对话历史、多轮对话(v2+)

## Decisions

### 1. 单一 `AnthropicCompatibleAdapter`(不 多 adapter)
**Why**: roadmap §6.3 拍板 — 三家都走 Anthropic Messages API 兼容,差异在 `ProviderConfig` 数据驱动(auth header 名、base URL、endpoint path、模型列表)。
**替代方案**:每家独立 adapter — 增加重复;新增 provider 成本高。

### 2. `ProviderConfig` 数据结构
```kotlin
data class ProviderConfig(
    val id: String,           // "deepseek" / "minimax" / "mimo" / "fake"
    val displayName: String,  // 给 UI 展示
    val baseUrl: String,      // "https://api.deepseek.com"
    val endpointPath: String, // "/anthropic/v1/messages"
    val authStyle: AuthStyle,
    val defaultModel: String, // "deepseek-v4-flash"
    val supportedModels: List<String>,
)
enum class AuthStyle {
    AUTHORIZATION,   // "Authorization: Bearer $apikey"
    X_API_KEY,       // "x-api-key: $apikey"
    CUSTOM_HEADER,   // mapOf("api-key" to apikey)
}
```
- mimo 用 `CUSTOM_HEADER`(header 名为 `api-key`)
- 自定义 provider 扩展点:`ProviderConfig` 加 `customHeaders: Map<String, String>` 字段

### 3. FakeProvider 设计
- `FakeConfig(data class`: `fixedText: String`, `delayMs: Long`, `errorAfterTokens: Int?`, `tokenCounts: AiUsage`)
- `FakeAiProvider : AiProvider`:每次 `stream()` 返回 `flow { ... }` → emit `Started` → 按 token 粒度 emit `Delta(text="...")`(带 `delayMs` 间隔) → emit `Usage(...)` → `Done`
- 若 `errorAfterTokens != null`,在 emit 指定 token 数后 emit `Failed(...)`,模拟网络中断
- M2/M3 所有单测和 UI 验收都用 FakeProvider;M5 真联调时换真 adapter

### 4. SSE 解析(`core/ai/stream/SseParser.kt`)
- 输入:OkHttp `Response.body.source()`(BufferedSource)
- 输出:`Flow<SseEvent>`(cold flow,collect 时开始读流)
- 实现:逐行读(`source.readUtf8Line()`),检测 `data: ` 前缀,收集多行 → 一个 `SseEvent`(emit 时 parse JSON body);`[DONE]` → emit `Done` 后 close
- 错误:connect 超时(30s)、read 超时(30s)、JSON parse 失败(`Failed(AiError.Deserialization)`)、`Response.code != 200`(map HTTP status → AiError)
- **不**做 reconnect 重试(给 UI 层控制;`AnthropicCompatibleAdapter` 把重试逻辑放在 `Flow.retry(1)`)

### 5. AiStreamEvent 类型
```kotlin
sealed interface AiStreamEvent {
    data object Started : AiStreamEvent
    data class Delta(val text: String) : AiStreamEvent
    data class Usage(val inputTokens: Int, val outputTokens: Int, val totalTokens: Int) : AiStreamEvent
    data class Failed(val error: AiError, val recoverable: Boolean) : AiStreamEvent
    data object Done : AiStreamEvent
}
```
- M2 只造类型,M3 UI 拼接 Delta + Started/Done 驱动 UI 状态机
- `Failed.recoverable` 区分"重试能好"(network timeout)和"必须修配置"(apikey invalid)

### 6. AiError 密封类型
```kotlin
sealed interface AiError {
    data class Network(val code: Int, val detail: String) : AiError
    data class Auth(val code: Int, val detail: String) : AiError            // 401/403
    data class InsufficientBalance(val detail: String) : AiError            // 402
    data class ContentModeration(val detail: String) : AiError              // provider 拦截
    data class Timeout(val message: String) : AiError
    data class Deserialization(val message: String) : AiError
    data class Unknown(val code: Int?, val detail: String) : AiError
}
```
映射:HTTP status → `AiAuth(401/403)` / `Network(5xx)` / `InsufficientBalance(402)` / `ContentModeration(400 + specific body)`;IO exception → `Network(-1)`;timeout → `Timeout`

### 7. AiHistory 表 + auto-write
- `AiHistoryEntity`(见 spec §"AiHistory entity schema"):id / noteId / provider / model / op / inputTokens / outputTokens / totalTokens / durationMs / createdAt / inputSnapshot / outputSnapshot
- `AiGateway.streamWritingOp(...)` 内部:开始记时 → emit `Started` → emit `Delta` 累积 output → 收到 `Usage` → 成功 → 在 `Done` 前落库(在 Repo 层做)
- 失败也落库:`outputSnapshot` 存部分已收文本 + error detail
- Repository 层:`AiHistoryRepository.record(...)` 在 IO dispatcher

### 8. AiGateway接口
```kotlin
interface AiGateway {
    suspend fun listProviders(): List<ProviderDescriptor>
    fun streamWritingOp(
        op: WritingOp,
        sourceText: String,
        providerId: String,
        modelName: String?,
    ): Flow<AiStreamEvent>
    suspend fun ping(providerId: String, modelName: String): Boolean
}
```
- `listProviders()` 返回当前已配 apikey 的 provider(dev 期全列出,credentials 为 fake)
- `ping` 发最小请求(带 `max_tokens: 1`)验证连通性;M2 用 FakeProvider 的 fake ping
- `WritingOp` enum:`EXPAND / POLISH / ORGANIZE`

### 9. Prompt 模板
- `core/ai/prompt/ExpandPrompt.kt` / `PolishPrompt.kt` / `OrganizePrompt.kt` — 每个 `internal object`
- 结构:`const val SYSTEM = "..."`, `fun buildUserMessage(sourceText: String): String`(用户文本单独放 user content)
- system prompt 纯常量,**不**接受用户输入
- M3 `ai-writing-actions` 加"自定义 system prompt"功能时改为从 DataStore 读(保留注入防御规则)

### 10. ProviderConfig 数据文件
```
core/ai/provider/
├── deepseek/DeepseekConfig.kt  → ProviderConfig + auth header "x-api-key"
├── minimax/MinimaxConfig.kt    → ProviderConfig + auth header "Authorization: Bearer" + service_tier 字段
├── mimo/MimoConfig.kt          → ProviderConfig + auth header "api-key"(CUSTOM_HEADER)
└── AuthStyle.kt                → enum
```
每个文件只是 `internal object` + `val config = ProviderConfig(...)`,**零逻辑**。

### 11. DI 模块
- `core/ai/di/AiModule.kt`:`@Module @InstallIn(SingletonComponent::class)`,`@Provides @Singleton` 提供 `OkHttpClient`(SSE 用,30s connect/read timeout) + `AnthropicCompatibleAdapter` + `AiGateway`(impl:`CoreAiGateway`)
- `CoreAiGateway`:`@Singleton` class,持有 `Map<String, AiProvider>`(由各 ProviderConfig + provider 实例填充;M2 阶段 `FakeProvider` + 三家 adapter 全注册,但三家无真实 apikey,调用时报 `AiError.Auth`)

### 12. FakeProvider 注册与生产隔离
- `FakeAiProvider`:`@Singleton` class,在 `AiModule` 中注册;非 build variant,compile time 全路径(dev 阶段)
- 提供 `FakeConfigHolder` object(非 DI,测试代码直接设置):`FakeConfigHolder.set(text, delay, error...); FakeConfigHolder.reset()`
- 用于单测和 M3 UI 验收;M5 真联调时不改 FakeProvider,直接换 `AnthropicCompatibleAdapter`(配真 credentials)

## Risks / Trade-offs

- **[Risk] Anthropic 协议字段差异未真测** → `ProviderConfig` 预留 `extraHeaders` + `extraRequestFields` map(M2 留下扩展点,不真填);M5 真联调时按实测结果补
- **[Risk] OkHttp streaming body read 内存暴** → `SseParser` 每次 `readUtf8Line()` 即流式,不缓存整 body;compose 端用 `collectAsStateWithLifecycle` 限流
- **[Risk] AiHistory 快速膨胀**(大量调用) → Room `DELETE FROM ai_history WHERE createdAt < :cutoff` 定期清理(保留 90 天),由 `AiHistoryRepository.prune(olderThanMs)` 实现
- **[Risk] Database migration 1→2 复杂度** → v1→v2 只加 1 张表(`ai_history`),手动写 Migration(不依赖 AutoMigration),在单测里跑 migration 验证

## Migration Plan

`Migration(1, 2) { db -> db.execSQL(CREATE_TABLE_AI_HISTORY) }` — 纯新建表,无旧数据迁移。

`N‌ote` 表不 动(M1 已有 `lastAiOp`/`lastAiAt` 字段),M2 只写不读。

回滚:git revert + 仓库 schema 降级到 v1(重新建库,因为 v1→v2 的 migration 没有 rollback;删 app 重装即可还原)。

## Open Questions

- **AiHistory 截断 inputSnapshot/outputSnapshot 长度?(大段笔记全文)** 倾向:M2 截断到 10k 字符;M3+ 加 `truncated: Boolean` 字段
- **FakeProvider 的 `n` engine(多候选)?** 倾向:不 做(单次 stream 只返回固定文本;再生成 = 再调一次)
- **OkHttp 是否复用已有 `OkHttpClient`(M0 引入但未用)?** 倾向:新建 `@AiOkHttpClient @Qualifier` 实例,BE 专用 30s timeout,不和未来的网络层打架
