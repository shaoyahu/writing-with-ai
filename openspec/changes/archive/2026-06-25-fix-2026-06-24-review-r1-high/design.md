# fix-2026-06-24-review-r1-high Design

## Context

`fix-2026-06-24-review-r1-critical`(已归档)收 8 CRITICAL + lint 3，本 change 收剩余 22 项 HIGH。HIGH 集中在 4 类:**AI 鲁棒性** + **Token 生命周期** + **异步一致性** + **资源/UX**。每项 fix 遵循最小-diff 原则，只动相关文件;不改公共 API(仅 `FeishuAuthStore` 内部实现 + `QuickNoteDetailViewModel._feishuRef` 改 StateFlow)。

## Goals / Non-Goals

**Goals**:
- 22 项 HIGH 全收，`./gradlew :app:check` 保持绿
- 不引入新依赖，继续用 kotlinx.coroutines + OkHttp + WorkManager + Room + ExifInterface
- H4 goAsync / H5 Dispatchers.IO 等异步修复沿用现有 dispatcher(`core/common/di/DispatcherModule`)
- 每项配独立单测(防御回归)

**Non-Goals**:
- 不收 MEDIUM/LOW(后续 change 分批)
- 不重构 AnthropicCompatibleAdapter / FeishuSyncService 等大文件结构
- 不改 Room schema、不加 Hilt module
- 不修 KSP/编译警告

## Decisions

### D1 — H5 `AuthInterceptor.runBlocking` 删除

`AuthInterceptor.intercept(chain)` 当前在 OkHttp dispatcher 线程上 `runBlocking { tokenProvider.getToken() }`。改:

```kotlin
val tokenSnapshot = runBlocking(Dispatchers.IO + SupervisorJob()) {
    withTimeoutOrNull(5_000) { mutex.withLock { /* refresh + return token */ } }
}
```

**替代方案**:改用 OkHttp Authenticator(异步 401 重试)— 拒绝，需大改契约。`runBlocking` 在 OkHttp interceptor 是 OkHttp 官方允许的 pattern;只移 dispatcher + 加 timeout。

### D2 — H6 `UserTokenProvider.invalidated` 移入 mutex

```kotlin
private data class TokenState(val token: String?, val expiresAt: Long, val invalidated: Boolean)
private val mutex = Mutex()
private var state: TokenState = TokenState(null, 0L, false)
```

替换 `@Volatile invalidated: Boolean`。所有读 / 写 / 失效都走 `mutex.withLock { ... }`。无锁外读 → 消除并发分裂。

### D3 — H7 `expires_in` fallback 60s + log

```kotlin
private val FALLBACK_PARSE_TTL_S = 60L
val ttl = data.str("expires_in")?.toLongOrNull()
    ?: run { Log.w(TAG, "expires_in parse failed, fallback to ${FALLBACK_PARSE_TTL_S}s"); FALLBACK_PARSE_TTL_S }
```

### D4 — H8 appSecret 内存 LRU(不持久化)

`FeishuAuthStore.persistAppSecret`:删持久化，改 `ConcurrentHashMap<requestId, Pair<secret, expiresAt>>`。`getAppSecretSnapshot` 按 requestId 取。**理由**:持久化到 EncryptedSharedPreferences 不安全 + 难 TTL;exchange 用一次清一次，内存足够。

### D5 — H9/H10/H11/H12/H13 AnthropicAdapter 鲁棒性

```kotlin
// H9:strip role-marker
private val ROLE_MARKERS = Regex("""(?i)\b(role|system|assistant|user)\s*:\s*""")
fun sanitizeSystemPrompt(s: String): String =
    s.take(MAX_SYSTEM_PROMPT_LEN).replace(ROLE_MARKERS, "[redacted]:")

// H10:body cap
const val MAX_RESPONSE_BODY_BYTES = 1L shl 20  // 1 MiB
val body = response.body ?: return@flow
val raw = body.source().request(MAX_RESPONSE_BODY_BYTES)

// H11:ensureActive
while (currentCoroutineContext().isActive) { emit(...) }

// H12:retry only when Failed not Delta
var emittedDelta = false
flow.retry(1) { e -> !emittedDelta && e is IOException && e !is SocketTimeoutException }

// H13:Headers.checkName
headers.forEach { (k, v) ->
    require(!RESERVED_HEADERS.contains(k.lowercase())) { "reserved header: $k" }
    builder.header(Headers.checkName(k), v)
}
```

### D6 — H14 SyncWorker Constraints + BackoffPolicy

```kotlin
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted ctx: Context, @Assisted params: WorkerParameters,
    private val syncEngine: SyncEngine
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        if (runAttemptCount > MAX_ATTEMPTS) return Result.failure()
        return when (val r = syncEngine.pull(sinceRevision = null)) {
            is SyncResult.PullSuccess -> Result.success()
            is SyncResult.Conflict, is SyncResult.Failure -> Result.retry()
            is SyncResult.Unsupported -> Result.failure()
            is SyncResult.PushSuccess -> Result.success()
        }
    }
    companion object { const val MAX_ATTEMPTS = 3 }
}
```

### D7 — H15 FeishuSyncService.pull `@Transaction`

新加 DAO 方法 `@Transaction` 包 note + ref 双写。`FeishuSyncService.pull` 调新方法。

### D8 — H16 BackfillScheduler flag 顺序

flag 写入移到 Worker `doWork` 成功回调里;enqueue 失败时 flag 不写。

### D9 — H17 BackfillScheduler tag 修复

Entity backfill enqueue 加 `.addTag(ENTITY_BACKFILL_TAG)`,cancel 真正生效。

### D10 — H18 LLM 失败可观测

加 `Metrics` 接口(默认 `NoOpMetrics`)，通过 Hilt 注入。后续可接 Crashlytics。

### D11 — H19 ImageCompressor EXIF + OOM

`ExifInterface.getAttributeInt(ORIENTATION_TAG)` → `Matrix.postRotate`;`inSampleSize` 按目标维度算，跳过中间全尺寸 bitmap。

### D12 — H20 LlmEntityExtractor 严格 JSON

`Json.parseToJsonElement(raw).jsonArray`(替代 substring `[...]`)。

### D13 — H21 wikilink prefix race fix

`val lastOpen = remember(content) { content.lastIndexOf("[[") }`(recompute on content change);onSelect 从 current content 重新定位。

### D14 — H22 apikey redact

`msg.replace(Regex("""([?&](?:api[_-]?key|access[_-]?token|token)=)([^&\s]+)"""), "$1***")`。

### D15 — H23 WritingApp.onCreate 异步化

`CoroutineScope(SupervisorJob + Dispatchers.IO).launch { consentStore... }`，主线程不阻塞。

### D16 — H24 AiwritingEntry noteId SavedStateHandle

`composable<QuicknoteDetail>` entry 读 `savedStateHandle["noteId"]`,AiActionViewModel 通过 `@AssistedInject SavedStateHandle` 取(详细由 apply 决定)。

### D17 — H25 feishuRef observe Flow

`refDao.observeForNote(id).stateIn(scope, WhileSubscribed(5_000), null)`。

## Risks / Trade-offs

- **R1 — H4 goAsync + SHA**:大 APK > 50MB + 慢盘可能 10s 超时 → Mitigation:失败 `Log.w`，不阻塞用户。
- **R2 — H5 runBlocking(Dispatchers.IO)** 仍阻塞 OkHttp dispatcher，但仅 5s 超时;长尾被 `withTimeoutOrNull` 截断。
- **R3 — H8 appSecret 改 LRU**:进程被杀丢失 secret → 用户重授权即可。
- **R4 — H14 SyncWorker Constraints**:无网络立即 retry → `BackoffPolicy.EXPONENTIAL` + `MAX_ATTEMPTS=3` 兜底。
- **R5 — H17 tag fix**:只 BackfillScheduler 自己用，无外部依赖。
- **R6 — H23 WritingApp 异步**:极小概率 crash 前未完成 setAccepted → 不影响 consent gate。

## Migration Plan

1. 提 PR `fix-2026-06-24-review-r1-high`
2. PR merge 到 main(无 DB migration)
3. AI 路径变化(failed metrics);sync 启用网络约束

回滚:每项 fix 集中 1-2 文件，revert 单 commit = 单 fix 回滚。

## Open Questions

- **Q1**:`Metrics` 是否落地 Crashlytics? → 本 change 只做 `NoOpMetrics`，后续接 SDK。
- **Q2**:H22 redact 正则覆盖度? → 覆盖常见 API key/query，边界后续 review。
- **Q3**:H14 SyncWorker OneTime 还是 Periodic? → OneTime(enqueue 由 SyncModule 决定)。
- **Q4**:H19 ImageCompressor 输出 quality? → 85(JPEG)，保留现状。

(Q1-Q4 不阻塞本 change)