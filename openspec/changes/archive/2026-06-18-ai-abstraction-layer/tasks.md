## 1. 通用类型:core/ai/api

- [x] 1.1 新建 `core/ai/api/AiStreamEvent.kt` — sealed interface(`Started / Delta(text) / Usage(inputTokens,outputTokens,totalTokens) / Failed(error,recoverable) / Done`);`data class AiUsage(val inputTokens: Int,val outputTokens: Int,val totalTokens: Int)`
- [x] 1.2 新建 `core/ai/api/AiError.kt` — sealed interface(`Network(code,detail) / Auth(code,detail) / InsufficientBalance(detail) / ContentModeration(detail) / Timeout(message) / Deserialization(message) / Unknown(code?,detail)`);`toString()` 输出摘要供落库
- [x] 1.3 新建 `core/ai/api/AiRequest.kt` — `data class AiRequest(val op: WritingOp, val sourceText: String, val model: String)`
- [x] 1.4 新建 `core/ai/api/WritingOp.kt` — `enum class WritingOp { EXPAND, POLISH, ORGANIZE }`
- [x] 1.5 新建 `core/ai/api/AiCredentials.kt` — `data class AiCredentials(val apikey: String, val baseUrlOverride: String? = null)`
- [x] 1.6 新建 `core/ai/api/AiProvider.kt` — `interface AiProvider { val id: String; val displayName: String; fun stream(request: AiRequest, credentials: AiCredentials): Flow<AiStreamEvent> }`
- [x] 1.7 新建 `core/ai/api/AiGateway.kt` — `interface AiGateway { suspend fun listProviders(): List<ProviderDescriptor>; fun streamWritingOp(op: WritingOp, sourceText: String, providerId: String, modelName: String?): Flow<AiStreamEvent>; suspend fun ping(providerId: String, modelName: String): Boolean }`
- [x] 1.8 新建 `core/ai/api/ProviderDescriptor.kt` — `data class ProviderDescriptor(val id: String, val displayName: String, val models: List<String>, val isConfigured: Boolean)`

## 2. ProviderConfig 数据驱动

- [x] 2.1 新建 `core/ai/provider/AuthStyle.kt` — `enum class AuthStyle { AUTHORIZATION, X_API_KEY, CUSTOM_HEADER }`
- [x] 2.2 新建 `core/ai/provider/ProviderConfig.kt` — `data class ProviderConfig(val id: String, val displayName: String, val baseUrl: String, val endpointPath: String, val authStyle: AuthStyle, val customAuthHeaderName: String? = null, val defaultModel: String, val supportedModels: List<String>, val customHeaders: Map<String, String> = emptyMap())`
- [x] 2.3 新建 `core/ai/provider/deepseek/DeepseekConfig.kt` — `internal object` + `val config = ProviderConfig(id="deepseek", displayName="DeepSeek", baseUrl="https://api.deepseek.com", endpointPath="/anthropic/v1/messages", authStyle=X_API_KEY, defaultModel="deepseek-v4-flash", supportedModels=listOf("deepseek-v4-flash","deepseek-v4-pro"))`
- [x] 2.4 新建 `core/ai/provider/minimax/MinimaxConfig.kt` — `internal object` + `ProviderConfig(... authStyle=AUTHORIZATION, defaultModel="MiniMax-M2.7-highspeed")`
- [x] 2.5 新建 `core/ai/provider/mimo/MimoConfig.kt` — `internal object` + `ProviderConfig(... authStyle=CUSTOM_HEADER, customAuthHeaderName="api-key", defaultModel="mimo-v2.5-flash")`

## 3. AnthropicCompatibleAdapter

- [x] 3.1 新建 `core/ai/provider/AnthropicCompatibleAdapter.kt` — `class @Inject constructor(config: ProviderConfig, client: OkHttpClient) : AiProvider`,实现 `stream()`:
  - 构造 POST body:{`model`,`max_tokens:2048`,`stream:true`,`system`,`messages:[user(content=sourceText)]`}
  - 用 `OkHttpClient` 发请求 → 读 `response.body.source()` → 交给 `SseParser.parse()`
  - map `SseEvent` → `AiStreamEvent`(Data→parse JSON→Delta/Usage/Started/Done;Done→Done;Error→Failed)
  - map HTTP status code → AiError
  - 认证头由 `ProviderConfig.authStyle` 驱动(`X_API_KEY`→`x-api-key`, `AUTHORIZATION`→`Authorization: Bearer`, `CUSTOM_HEADER`→`$customAuthHeaderName`)

## 4. SSE 解析器

- [x] 4.1 新建 `core/ai/stream/SseEvent.kt` — `sealed interface SseEvent { data class Data(val content: String) : SseEvent; data object Done : SseEvent; data class Error(val t: Throwable) : SseEvent }`
- [x] 4.2 新建 `core/ai/stream/SseParser.kt` — `internal object` + `fun parse(source: BufferedSource): Flow<SseEvent>`;逐行读,聚合 `data: ` 行,emit `Data`;检测 `[DONE]` → `Done`;IO 异常 → `Error`;30s 无新行 → `Error(SocketTimeoutException)`

## 5. FakeProvider

- [x] 5.1 新建 `core/ai/fake/FakeConfig.kt` — `data class FakeConfig(val text: String, val delayMs: Long = 0L, val errorAfterTokens: Int? = null, val tokenCounts: AiUsage = AiUsage(0,0,0))`;`object FakeConfigHolder { var config: FakeConfig = ...; fun set(...); fun reset() }`(non-thread-safe,单测/UI 验收用)
- [x] 5.2 新建 `core/ai/fake/FakeAiProvider.kt` — `class @Inject constructor() : AiProvider`,实现 `stream()`:emit `Started` → tokenize text(按 space/sentence split → emit Delta with delay) → emit `Usage` → `Done`;若 `errorAfterTokens` 触发 → emit `Failed`
- [x] 5.3 `FakeAiProvider.ping()` — 直接 return true

## 6. Prompt 模板

- [x] 6.1 新建 `core/ai/prompt/ExpandPrompt.kt` — `internal object` + `const val SYSTEM = "..."`(扩写专用 system prompt)+ `fun userMessage(sourceText: String): String`
- [x] 6.2 新建 `core/ai/prompt/PollishPrompt.kt` — 润色 system prompt
- [x] 6.3 新建 `core/ai/prompt/OrganizePrompt.kt` — 整理 system prompt
- [x] 6.4 系统 prompt 纯常量,不接收用户输入;由 `AnthropicCompatibleAdapter` 根据 `WritingOp` 选择对应模板的 SYSTEM 常量

## 7. CoreAiGateway 实现

- [x] 7.1 新建 `core/ai/CoreAiGateway.kt` — `class @Inject @Singleton constructor(providers: Map<String, @JvmSuppressWildcards AiProvider>, historyRepo: AiHistoryRepository, noteRepo: NoteRepository) : AiGateway`
- [x] 7.2 `streamWritingOp(...)` 实现:取 provider → 调 `provider.stream(AiRequest(...),credentials)` → `onEach` 收集 Delta 文本 + token 计数 → `onCompletion` 落库(`AiHistoryRepository.record(...)`) + 若为 Done 调 `NoteRepository.updateAiMetadata(noteId,op,now)`
- [x] 7.3 `listProviders()` — 从 Hilt `Set<AiProvider>` 提取 `ProviderDescriptor`
- [x] 7.4 `ping()` — 委托 provider 的 ping(发 min 请求);FakeProvider 直接 true
- [x] 7.5 `CoreAiGateway` 处理 `credentials` 不存在时(apikey 未配置):`Failed(AiError.Auth(401, "api key not configured"))`

## 8. AiHistory 持久化

- [x] 8.1 新建 `core/data/db/entity/AiHistoryEntity.kt` — Room Entity,字段 id / noteId? / providerId / model / op / inputTokens / outputTokens / totalTokens / durationMs / createdAt / inputSnapshot / outputSnapshot / truncated / error?;indices = [Index("noteId"), Index("createdAt")]
- [x] 8.2 新建 `core/data/db/AiHistoryDao.kt` — `@Dao` 接口,暴露 `insert / observeByNoteId / observeAll / deleteOlderThan(cutoffMs) / getTotalTokens`
- [x] 8.3 新建 `core/data/model/AiHistory.kt` — UI 领域模型(data class,不带 Room 注解)
- [x] 8.4 新建 `core/data/mapper/AiHistoryMapper.kt` — `AiHistoryEntity ↔ AiHistory`
- [x] 8.5 新建 `core/data/repo/AiHistoryRepository.kt` — `@Singleton` class,构造函数注入 `AiHistoryDao`:`fun record(entity)`(自动截断 input/output 到 10k) + `fun prune(olderThanMs)`(清理 90 天前)
- [x] 8.6 更新 `core/data/db/AppDatabase.kt` — 加 `AiHistoryEntity` 到 entities,version→2,加 `Migration(1,2)`(CREATE TABLE ai_history + 索引)
- [x] 8.7 更新 `core/data/di/DataModule.kt` — `@Provides` `AiHistoryDao`

## 9. NoteRepository 扩展

- [x] 9.1 在 `core/data/repo/NoteRepository.kt` 新增 `suspend fun updateAiMetadata(noteId: String, op: String, at: Long)`:调 `NoteDao.updateAiMetadata(...)`(用 `@Query("UPDATE notes SET lastAiOp=:op, lastAiAt=:at WHERE id=:noteId")`),配 `NoteDao` 加此方法

## 10. DI 模块(AiModule)

- [x] 10.1 新建 `core/ai/di/AiModule.kt` — `@Module @InstallIn(SingletonComponent::class)`:
  - `@Provides @Singleton @Named("ai") fun provideAiOkHttpClient(): OkHttpClient`(connect/read 30s)
  - `@Provides fun provideFakeConfigHolder(): FakeConfigHolder`
  - `@Provides @Singleton fun provideFakeAiProvider(): FakeAiProvider`
  - `@Provides @Singleton fun provideAnthropicAdapter(@Named("ai") client, configs: List<ProviderConfig>): AnthropicCompatibleAdapter` — 取第一个非 fake config
  - `@Provides @Singleton fun provideAiProviders(fake: FakeAiProvider, adapter: AnthropicCompatibleAdapter): Map<String, AiProvider>`;map 中额外加 1 个 `AnthropicCompatibleAdapter` instance 给三家(或三个 instance,每个配不同 ProviderConfig)
  - `@Binds @Singleton fun bindAiGateway(impl: CoreAiGateway): AiGateway`

## 11. Build 配置

- [x] 11.1 `gradle/libs.versions.toml` — 确认 `kotlinx-serialization` 存在;加 `okhttp-mockwebserver` 版本(与 okhttp 同版 4.12.0) + testImplementation
- [x] 11.2 `app/build.gradle.kts` — `testImplementation(libs.okhttp.mockwebserver)`(加 library entry)

## 12. 测试

- [x] 12.1 `core/ai/stream/SseParserTest.kt` — JUnit5 + MockWebServer(StreamResponseBody):验 `data: ` 行/多行聚合/`[DONE]`/truncation/timeout
- [x] 12.2 `core/ai/provider/AnthropicCompatibleAdapterTest.kt` — MockWebServer mock 200+SSE stream → 验 Delta/Usage/Done 映射;mock 401→验 Failed(Auth);mock 500→验 Failed(Network)
- [x] 12.3 `core/ai/fake/FakeAiProviderTest.kt` — Turbine 验 `FakeConfigHolder` set text/delay/error → 流正确
- [x] 12.4 `core/ai/CoreAiGatewayTest.kt` — MockK `AiProvider` → 验 `streamWritingOp` 落库 AiHistory + 写 Note.lastAiOp
- [x] 12.5 `core/data/db/AiHistoryDaoTest.kt` — Room in-memory(需 Robolectric 或 instrumentation,因 migration 需要 Context;先用 `Room.databaseBuilder` + `allowMainThreadQueries` + `createFromAsset` 或直接 instrumentation)
- [x] 12.6 跑 `./gradlew :app:testDebugUnitTest` 全部新增测试通过

## 13. 整体验收

- [x] 13.1 `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL
- [x] 13.2 `./gradlew :app:testDebugUnitTest` → 新增 AI 模块测试全绿
- [x] 13.3 `./gradlew :app:lintDebug` → BUILD SUCCESSFUL
- [x] 13.4 `./gradlew :app:ktlintCheck` → 无新增违规(已知 Compose PascalCase follow-up 除外)
- [x] 13.5 FakeProvider 端到端走通:在 `CoreAiGatewayTest`(single test)里模拟整个流程:`FakeAiProvider` → `AiGateway.streamWritingOp(...)` → collect 事件 → 验 AiHistory 落库正确 + Note.lastAiOp 被更新

## 14. OpenSpec 收尾

- [x] 14.1 review 通过后,跑 `openspec archive ai-abstraction-layer -y`
- [x] 14.2 更新 `docs/progress.md`:M2 完成
- [x] 14.3 在 `docs/plans/writing-with-ai-mobile-roadmap.md` §13 标注 M2 完成;§15.2 标 `ai-abstraction-layer` done
