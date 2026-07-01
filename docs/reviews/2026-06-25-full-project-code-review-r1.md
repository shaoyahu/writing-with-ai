# 2026-06-25 full-project code-review r1

- **范围**:整个 `app/` 模块(221 main .kt + 50 test .kt + build/manifest/resources/scripts)
- **基于 commit**:`bec40ca fix(release): kotlinx-serialization R8 keep + 重设计公开 web 页面`
- **reviewer**:Claude(full-project sweep,4 路并行 agent)
- **方法**:4 个并行 reviewer(app+features / core+feishu / build+manifest / tests+DI)，每路返回 severity-tagged findings，主线程 dedup + 关键项手工 verify

## 总览

| 严重度 | 数量(去重后) | 必修 | 建议修 | 接受 |
| --- | --- | --- | --- | --- |
| CRITICAL | 8 | 7 | 1 | 0 |
| HIGH | 12 | 9 | 3 | 0 |
| MEDIUM | 24 | 4 | 18 | 2 |
| LOW | 18 | 0 | 12 | 6 |
| **合计** | **62** | **20** | **34** | **8** |

上一轮(`2026-06-24-full-project-code-review-r1.md`)8 CRITICAL + 3 lint 已通过 `1d7a80a fix(fix-2026-06-24-review-r1-critical)` 归档。本轮发现 4 个**新** CRITICAL,2 个**回归**(r1 标记过但仍未修),2 个**新增安全硬规则违反**。

## 复盘:已经修过(本轮不重复)

- `lifecycleScope.launch` + `isConsented` 路径已重写为 `lifecycleScope.launch { ... }`(06-24 r1 M3)
- `kotlinx-serialization` R8 keep 已在 `app/proguard-rules.pro`(`bec40ca` 落地)
- `allowBackup="false"` 已开，`writingwithai_secure_prefs.xml` 尚未在 backup_rules 排除(留待 M2)
- 8 个 CRITICAL(06-24 r1)已全部归档，本轮不再列

## CRITICAL(8)

### C1 · `LlmEntityExtractor` 硬编码 `providerId="fake"` + `apikey=""`
- **位置**:`app/src/main/java/com/yy/writingwithai/core/note/entity/LlmEntityExtractor.kt:52-53`
- **现状**:`aiGateway.streamWritingOp(... providerId = "fake", apikey = "")` 写死。release 构建 `AiModule.provideFakeAiProvider` 返回 `null`(被 `BuildConfig.DEBUG` gate),`fake` provider 在 `provideAiProviders` map 里不存在，`streamWritingOp` 内部走 `Provider not found` 失败分支，`TokenLimitExceeded` catch 后只 `Log.w` 一行，UI 无任何提示。
- **影响**:所有装了 release 包的用户，实体抽取全量静默失败;`note_entities` 表永远空;相关 spec §2.5 在 prod 不达标。
- **修法**:与 `LlmNoteLinkExtractor` 对齐 — `val providerId = secureApiKeyStore.observeConfiguredProviders().first().firstOrNull() ?: return@withContext 0` + `val apikey = secureApiKeyStore.get(providerId) ?: return@withContext 0`。fallback 到 fake 仅当 `BuildConfig.DEBUG`。

### C2 · `UserTokenProvider.exchangeCode` `Mutex.withLock(Dispatchers.IO)` 第一个参数被吞
- **位置**:`app/src/main/java/com/yy/writingwithai/core/feishu/auth/UserTokenProvider.kt:81`
- **现状**:`refreshMutex.withLock(Dispatchers.IO) { ... }` — Kotlin `Mutex.withLock(owner: Any? = null, action: suspend ...)` 把 `Dispatchers.IO` 当 **owner 锁标识**(用于死锁检测)，不是 context。action 实际跑在调用方 context。如果 `exchangeCode` 来自 `OAuthCodeReceiver` 走 `lifecycleScope.launch(Dispatchers.Main)`，网络 IO 跑在 main thread，卡 ANR。
- **影响**:OAuth 飞书回调路径，授权后界面无响应 / 长时间转圈 / ANR;用户感知"飞书登录卡死"。
- **修法**:
  ```kotlin
  suspend fun exchangeCode(...) = withContext(Dispatchers.IO) {
      refreshMutex.withLock { ... }   // owner=null,action 在 IO 跑
  }
  ```

### C3 · `BackfillWorker` `PREF_BACKFILL_DONE` 写常量但**永远不写**，每次开机都全量回填
- **位置**:`app/src/main/java/com/yy/writingwithai/core/note/backfill/BackfillWorker.kt:52` + `BackfillScheduler.kt:20,64`
- **现状**:`BackfillWorker.doWork()` 全程没有 `prefs.edit().putBoolean(PREF_BACKFILL_DONE, true).apply()`。`BackfillScheduler.scheduleIfNeeded` 只读 `prefs.getBoolean(PREF_BACKFILL_DONE, false)`，永远 false，每次冷启重新 `enqueue OneTimeWorkRequest`。这导致:每次开机都重跑 50-batched 全量 `recomputeForNote`;`note_link` 表先 delete 再 insert，期间所有读路径返回空;CPU + DB 抖动 + 流量(materialized wikilink 重新算)持续;`WorkManager` 日志污染。
- **修法**:`BackfillWorker.doWork` 末尾 `prefs.edit().putBoolean(PREF_BACKFILL_DONE, true).apply()`，失败 Result.retry 时不写。`BackfillScheduler` 的读路径再补一次 guard(防止 race)。

### C4 · `UpdateDialog` 强制更新可被 back / 蒙层关掉
- **位置**:`app/src/main/java/com/yy/writingwithai/feature/my/UpdateDialog.kt:31`
- **现状**:`onDismissRequest = { if (!manifest.mandatory) onLater() }` — `mandatory=true` 时 lambda 是空操作，但 `AlertDialog` 默认允许点击蒙层 / 系统返回键 dismiss,`onDismiss` 仍会被调用一次触发组件销毁。安全意图(强制升级)被旁路。
- **修法**:
  ```kotlin
  AlertDialog(
      onDismissRequest = { /* mandatory 时 no-op */ },
      properties = if (manifest.mandatory) DialogProperties(
          dismissOnBackPress = false,
          dismissOnClickOutside = false
      ) else DialogProperties(),
      ...
  )
  ```

### C5 · `AppNavConsentGateTest` 测的是 fake，不是 production gate
- **位置**:`app/src/test/java/com/yy/writingwithai/app/AppNavConsentGateTest.kt:18-53`
- **现状**:4 个 test 全在 `FakeConsentStore` 上 `seed` / `assertEquals` / `MutableStateFlow.set`，没有任何 `AppNav` Composable / `ConsentGate` 真 instance 介入。`widgetPendingRouteStoredReadCleared` 直接 mock `MutableStateFlow<String?>` —— 与生产路径零关联。绿色 CI 不代表 gate 行为对。
- **影响**:consent gate 任意回归(包括上次 r1 修过的 widget pending route 透传)不会被这条 test 捕获。
- **修法**:换成 `ComposeTestRule.createAndroidComposeRule<ComponentActivity>()` + 真 `ConsentStore`(`Robolectric` + `MainActivity` `EntryPointAccessors`)，或者用 `ComposeContentTestRule.setContent { AppNav(...) }` 触发 `onConsentedChanged` 看 `navigate` 走向。

### C6 · `SearchHistoryStoreTest` 缺 `@RunWith(RobolectricTestRunner::class)`，在 JVM 单测环境跑必抛 `IllegalStateException`
- **位置**:`app/src/test/java/com/yy/writingwithai/core/prefs/SearchHistoryStoreTest.kt:11,22,32`
- **现状**:`ApplicationProvider.getApplicationContext<Context>()` 必须 Robolectric 注入。`app/build.gradle.kts:202-204` 走 `useJUnitPlatform()`(JUnit5)，而 `robolectric-core` 4.13 走 JUnit Vintage。`robolectric-core` 测试引擎需要 `@RunWith` + JUnit4 入口;JUnit5 通过 `junit-vintage-engine` 桥接，要求 `robolectric-core` test class 标注 `org.robolectric.RobolectricTestRunner` 由 vintage 触发。当前 3 个 test 都没有该 annotation。
- **影响**:3/3 test 在首次运行就会 fail，这条 coverage 是空的。
- **修法**:每个 test 加 `@org.robolectric.RobolectricTestRunner` + `@org.robolectric.annotation.Config(sdk = [34])`(JUnit5 vintage 桥接)，或者把 `SearchHistoryStore` 重写为可注入 `Context` 的纯 JVM 接口(更省)。

### C7 · `AiModule.provideAiProviders` 返回 mutable Map
- **位置**:`app/src/main/java/com/yy/writingwithai/core/ai/di/AiModule.kt:65`
- **现状**:`buildMap { put("deepseek", ...); ... }` 返回 `LinkedHashMap`(mutable)。`@Singleton` 阻止 Hilt 重复创建，但阻止不了 `CoreAiGateway` 内的协程或 future caller 调 `map["fake"] = FakeProvider()` 污染整个进程。
- **影响**:任何代码路径误 `providers["fake"] = something` 即全局污染;debug 难复现。
- **修法**:`return buildMap { ... }.toMap()`(返回 immutable `Map`)，或在 Hilt module 层 `Collections.unmodifiableMap(...)`。

### C8 · `AuthInterceptor` 双重 `runBlocking` + 漏 SupervisorJob
- **位置**:`app/src/main/java/com/yy/writingwithai/core/feishu/api/AuthInterceptor.kt:39,49`
- **现状**:401-storm 下，新 mutex 路径用 `runBlocking(Dispatchers.IO + SupervisorJob())` 包住 suspend token 刷新，但 `SupervisorJob()` 每次 `runBlocking` 都 new 一个，内部 `withContext` 完成前返回时这个 `Job` 无人 cancel —— 真正失败(token 刷新异常)时 `Job` 还活着。每次 401 创建临时 `Job`，长期累积内存。
- **修法**:复用单一 `SupervisorJob` 作为字段;token 刷新用 `runBlocking(Dispatchers.IO) { refreshMutex.withLock { ... } }` 简单结构;考虑改 OkHttp `Authenticator`(异步 API)替代 Interceptor 内的 `runBlocking`。

## HIGH(12)

### H1 · `AttachmentEntity` 外键 `noteId` 缺 index(回归 r1 L3)
- **位置**:`app/src/main/java/com/yy/writingwithai/core/data/db/entity/NoteAttachmentEntity.kt:11-21`
- **现状**:06-24 r1 L3 已标记，仍未加 `indices = [Index("noteId")]`。`JOIN attachments ON noteId = ?` 走全表扫。
- **修法**:Entity 顶部 `indices = [Index("noteId", value = ["NOTE_ID"])]`，跑 `AutoMigration` 生成新 schema。

### H2 · `WikilinkParser` 不支持 `[[alias|target]]` 语法
- **位置**:`app/src/main/java/com/yy/writingwithai/core/note/wikilink/WikilinkParser.kt:4`
- **现状**:正则 `\[\[([^\[\]\n]+?)\]\]` 把 `[[Note|Alias]]` 整段当 title。`resolveByTitle` 做 `LOWER(title) = LOWER(?)` 匹配，带 `|` 的 title 永远 miss。`MarkdownToXmlConverter.kt:74` 同步问题。
- **修法**:解析时 `.substringBefore('|')` 取 target 段，alias 段留作显示 label。

### H3 · `LocalNoteLinker.keywordOverlapWeight` 对纯中文内容返回 0
- **位置**:`app/src/main/java/com/yy/writingwithai/core/note/impl/LocalNoteLinker.kt:73-79`
- **现状**:`split(Regex("\\s+")).filter { it.length > 1 }` 假设空格分词。中文不分词，所有 keyword overlap = 0,fallback 走 LLM extract path。LLM extractor(rate-limit 24h)又被 C1 bug 冻住 —— 纯中文用户:无本地链接 + 无 LLM 链接，功能完全失效。
- **修法**:CJK 段用 `StringTokenizer` / 正则 `[一-鿿]+` 抽连续汉字 n-gram，或者在 `keywordOverlapWeight` 前面先加 CJK-aware tokenizer(`hanlp` 太重，可手写 2-gram)。

### H4 · `LlmNoteLinkExtractor` token 估算对 CJK 偏差 5x
- **位置**:`app/src/main/java/com/yy/writingwithai/core/note/impl/LlmNoteLinkExtractor.kt:160`
- **现状**:`(text.length / 3.5).toInt()` — 英文大致准，中文 1 字 ≈ 1.5 token，实际 1000 中文字 ≈ 1500 tokens，估算 285。`ai_history` 成本历史对 CJK 用户系统性低估 5x，影响费用感知和 limit 决策。
- **修法**:
  ```kotlin
  val cjkCount = text.count { it in '一'..'鿿' }
  val other = text.length - cjkCount
  ((other / 4) + (cjkCount * 1.5)).toInt()
  ```

### H5 · `SecureApiKeyStore.lastPauseAt` 跨 suspend 边界读 race
- **位置**:`app/src/main/java/com/yy/writingwithai/core/prefs/SecureApiKeyStore.kt:82,172,185-190`
- **现状**:`@Volatile var lastPauseAt: Long` 在 `onActivityPaused` 写，但 `updateRevealState` 在 `onResume` 读 + 算 `expiresAt`，中间无锁。如果用户在 reveal 5s 倒计时期间按 home,`onActivityPaused` 把 `lastPauseAt` 推到未来，`expiresAt` 算出来长几分钟，reveal 状态迟消失。
- **修法**:把 `lastPauseAt` + `expiresAt` 计算放到一个 `suspend fun computeExpiresAt(): Long` 内部，snapshot 进 local val;或者用 `Mutex.withLock` 包住 read-modify-write。

### H6 · `AboutViewModel` 检查更新 TOCTOU
- **位置**:`app/src/main/java/com/yy/writingwithai/feature/my/AboutViewModel.kt:39-41`
- **现状**:`if (_state.value is Checking) return; _state.value = Checking; viewModelScope.launch { ... }` — read 与 write 非原子。快速连点"检查更新" 2 次 → 2 个并行 `checker.fetch()`,2 个并发下载竞态。
- **修法**:`_state.update { if (it is Checking) it else State.Checking }` 或 `compareAndSet(State.Idle, State.Checking)`.

### H7 · `ModelManagementViewModel.save` 写 apikey 成功但 `setSelectedProviderId` 失败时 UI 误导
- **位置**:`app/src/main/java/com/yy/writingwithai/feature/settings/model/ModelManagementViewModel.kt:145-158`
- **现状**:apikey 写入 `EncryptedSharedPreferences` 成功 → `setSelectedProviderId` 抛异常 → catch 返回 `SaveResult.Failed` → UI 红条"保存失败"。但 apikey 已经在 keystore 里，下次 AI 调用会用。用户回 AI 屏发现"我明明没保存成功，怎么已经在用?"
- **修法**:写 apikey 在 `setSelectedProviderId` 之后;或 setSelected 失败时显式 `secureApiKeyStore.delete(providerId)` 回滚。

### H8 · `FeishuAuthScreen` + `AboutScreen` + `AliasManagementViewModel` 硬编码中文(违反 i18n 硬规则)
- **位置**:
  - `app/src/main/java/com/yy/writingwithai/feature/settings/feishu/FeishuAuthScreen.kt:110-114`
  - `app/src/main/java/com/yy/writingwithai/feature/my/AboutScreen.kt:147-181`
  - `app/src/main/java/com/yy/writingwithai/feature/settings/alias/AliasManagementViewModel.kt:43,51`
- **现状**:多处文案(权限说明、版本提示、Snackbar 消息)直接写中文字符串，`strings.xml` 已存在 zh + en 双套但未用。en locale 用户看到 raw CN。
- **修法**:全部走 `stringResource(R.string.*)`;`ViewModel` 内的 state 改为 `@StringRes val messageRes: Int` / sealed class,Composable 端 `stringResource(state.messageRes)`。

### H9 · `DataModule` 9 个 DAO `@Provides` 无 `@Singleton`
- **位置**:`app/src/main/java/com/yy/writingwithai/core/data/di/DataModule.kt:35-42`
- **现状**:`AppDatabase` 是 `@Singleton`，但 9 个 `@Provides fun noteDao(db) = db.noteDao()` 都没标 `@Singleton`。Hilt 行为:Hilt 在每个 `@Inject` site 调一次 `db.noteDao()`,Room 内部会缓存，但 Hilt graph 上 DAO 是 unscoped，理论上每次 resolve 都新 wrap。
- **修法**:9 个 DAO @Provides 全加 `@Singleton`。

### H10 · `FeishuModule` / `AiModule` OkHttpClient `@Named` qualifier 命名不一致
- **位置**:`app/src/main/java/com/yy/writingwithai/core/feishu/di/FeishuModule.kt:31` + `app/src/main/java/com/yy/writingwithai/core/ai/di/AiModule.kt:25`
- **现状**:`AiModule` 用 `@Named("ai")`,`FeishuModule` 未加 qualifier。若未来某 caller 加 `@Inject OkHttpClient`(无 qualifier)会触发 double-binding 编译错或运行时拿错 client(feishu 的 interceptor 链串到 AI 流量)。
- **修法**:`FeishuModule.provideOkHttpClient` 加 `@Named("feishu")`;`@Provides` 旁加 explicit `@param:Named("feishu") OkHttpClient`。

### H11 · `OAuthCodeReceiver` 用 `lifecycleScope` 跑 suspend 链，Activity finish 后被取消
- **位置**:`app/src/main/java/com/yy/writingwithai/core/feishu/auth/OAuthCodeReceiver.kt:33-44`
- **现状**:`lifecycleScope.launch { val (appId, appSecret) = ...; exchangeCode(...) ; ... ; finish() }` —— `lifecycleScope` 在 `finish()` 同步触发取消。如果 `exchangeCode` 内部 suspend 点(token 刷新)耗时 > finish 流程(系统动画),token 写到一半被取消，持久化的 `appSecret` 有，`accessToken` 没有，用户下次进 Feishu 屏看到"已连接"但调用全失败。
- **修法**:改用 `applicationContext` scope(`ProcessLifecycleOwner.get().lifecycleScope` 或自建 `GlobalScope.launch(SupervisorJob)` + `withContext(NonCancellable)` 包 token 持久化)，或先把 intent 落盘，Activity 立即 finish，后续逻辑从 `WorkManager` / `Application.onCreate` 续。

### H12 · `FeishuApiClientImpl` body 读取无大小上限
- **位置**:`app/src/main/java/com/yy/writingwithai/core/feishu/api/FeishuApiClientImpl.kt:115-116`
- **现状**:`resp.body?.string().orEmpty()` —— 异常 Feishu 端点返回多 MB JSON 时直接 OOM。`AnthropicCompatibleAdapter` 已经加了 1 MiB cap(`MAX_BODY`)。
- **修法**:`resp.body?.source()?.request(1L * 1024 * 1024)?.string().orEmpty()`(`Okio Source.request` 半流式)。

## MEDIUM(24，选 12 个高 ROI 列入，其余见附录)

### M1 · `CoreAiGateway.customProviderStore.onInvalidate = { ... }` 非线程安全 init
- **位置**:`app/src/main/java/com/yy/writingwithai/core/ai/CoreAiGateway.kt:55-61`
- Hilt 创建两个 instance(测试 + 生产)时，后一个的 lambda 覆盖前一个的回调，前一个的缓存失效路径静默失效。
- **修法**:`ConcurrentHashMap.newKeySet<(String) -> Unit>()` + `addListener` / `removeListener` API。

### M2 · `NoteRepository.noteUpdateEvents` 用 `replay = 1` 导致 detail VM 收到"已删除"id
- **位置**:`app/src/main/java/com/yy/writingwithai/core/data/repo/NoteRepository.kt:60-61`
- 修:`replay = 0` + `extraBufferCapacity = 32`。

### M3 · `FeishuSyncService.push/pull` 写 note + ref 不在一个 `withTransaction`
- **位置**:`app/src/main/java/com/yy/writingwithai/core/feishu/sync/FeishuSyncService.kt:36-47,54-103`
- 中间 crash 留下 `FeishuRefEntity` 指 stale note。
- **修法**:用 `FeishuRefDao.upsertNoteWithRef` 已有 `@Transaction`，但当前没被调用。

### M4 · `AnthropicCompatibleAdapter` retry 整个 flow 不带 `emittedDelta` 短路
- **位置**:`app/src/main/java/com/yy/writingwithai/core/ai/provider/AnthropicCompatibleAdapter.kt:191-193`
- `.retry(1) { cause is IOException && cause !is SocketTimeoutException }` —— 已 `emit` 过的 delta 在重试时再次 `emit`,caller 看到重复文本。
- **修法**:predicate 加 `&& !emittedDelta`。

### M5 · `CoreAiGateway` ping 路径对 fake provider 静默返回 `null`
- **位置**:`app/src/main/java/com/yy/writingwithai/core/ai/CoreAiGateway.kt:169`
- 修:抛 `AiError.ProviderNotConfigured`。

### M6 · `NoteRepository.tags` debug log 可能含 PII
- **位置**:`app/src/main/java/com/yy/writingwithai/core/data/repo/NoteRepository.kt:121,133`
- 修:`Log.d("NoteRepo", "upsert noteId=${note.id} tags.size=${tags.size}")`。

### M7 · `PathSafety.SAFE_NAME` 允许 `.` 和 `..`
- **位置**:`app/src/main/java/com/yy/writingwithai/core/security/PathSafety.kt:15`
- 修:加 negative lookahead 拒 leading `.` / 子串 `..`。

### M8 · `UpdateDownloadReceiver` 缺 `grantUriPermission` 给 packageinstaller
- **位置**:`app/src/main/java/com/yy/writingwithai/core/update/UpdateDownloadReceiver.kt:118-121`
- MIUI/EMUI 安装器会丢权限，`FLAG_GRANT_READ_URI_PERMISSION` 不足。
- **修法**:`context.grantUriPermission("com.android.packageinstaller", contentUri, FLAG_GRANT_READ_URI_PERMISSION)` 后再 `startActivity`。

### M9 · `AppDatabase` 7 autoMigrations，缺 AutoMigration schema 验证
- **位置**:`app/src/main/java/com/yy/writingwithai/core/data/db/AppDatabase.kt:39-62`
- `app/schemas/` 缺位或未 commit 时 AutoMigration 在第一次 v8 install 抛 `IllegalStateException`。
- **修法**:在 CI 加 `MigrationTestHelper.runMigrationsAndValidate(...)` 跑 v1→v8。

### M10 · `MarkdownToXmlConverter` 顺序 regex replace 嵌套标记
- **位置**:`app/src/main/java/com/yy/writingwithai/core/feishu/converter/MarkdownToXmlConverter.kt:69-76`
- 修:单 pass tokenizer。

### M11 · `QuickNoteEditorViewModel.save` `Log.d` 无 `BuildConfig.DEBUG` gate
- **位置**:`app/src/main/java/com/yy/writingwithai/feature/quicknote/edit/QuickNoteEditorViewModel.kt:163-164`
- release 构建 logcat 也会输出 `noteId + tags`。CLAUDE.md 硬规则"绝不... logcat" 违反。
- **修法**:`if (BuildConfig.DEBUG) Log.d(...)` 或直接删。

### M12 · `WidgetStateStore` 跨并发 `incrementNoteIndex` / `current` 数据竞争
- **位置**:`app/src/main/java/com/yy/writingwithai/core/widget/WidgetStateStore.kt:25-30`
- `update { ... }` 与 `.first()` 不同步，可能 skip / repeat index。
- 修:用 `updateData` 串行化。

## LOW(选 8 个高 ROI)

- L1 · `NoteEntity.syncStatus` 字符串无枚举:`core/data/db/entity/NoteEntity.kt:34` → 转 `enum class SyncStatus` + Room TypeConverter
- L2 · `FeishuAuthStore.secretCache` `clearAppSecret` 不清 in-memory 缓存:`core/feishu/auth/FeishuAuthStore.kt:151` → 切到 DISCONNECTED 时 `secretCache.clear()`
- L3 · `QuickNoteWidget.createNoteIntent` 用 `CLEAR_TASK` 会杀 consent activity:`core/widget/QuickNoteWidget.kt:259-261` → 改 `TaskStackBuilder` + `launchWithTaskStack`
- L4 · `ProviderConfig.customAuthHeaderName` 缺校验:`core/ai/provider/ProviderConfig.kt:13-24` → save 前比对 `RESERVED_HEADERS`(`Authorization` 等)
- L5 · `AiErrorDisplayTest` stub `context.getString` 测的是 mock，不是映射逻辑:`feature/aiwriting/error/AiErrorDisplayTest.kt:14` → Robolectric 真 Context
- L6 · `SseParserTest` 缺 `:keep-alive` heartbeat / CRLF / BOM 用例:`core/ai/stream/SseParserTest.kt:11` → 补 4 case
- L7 · `SseParser` 测试未用 Turbine:`core/ai/stream/SseParserTest.kt:18` → 改 `test { }`
- L8 · `NoteExporter` note.id 当 zip entry 路径未走 `PathSafety`:`core/data/export/NoteExporter.kt:55-58` → `PathSafety.requireSafeId(note.id)`

## 附录:被排除(已验证 FALSE POSITIVE)

| Agent finding | 实际状态 |
| --- | --- |
| `local.properties` 在仓库里 | **已 .gitignore**,`git ls-files` 返回空(`.gitignore` 第 9 行),`git status -sb` clean |
| `app/release.keystore` 在仓库里 | **已 .gitignore**(`*.keystore` / `*.jks` / `release.keystore` 三层),`git ls-files` 返回空，本机 CI placeholder |
| `proguard-rules.pro` 缺 `@Serializable` keep | **已 keep**(`-keep @kotlinx.serialization.Serializable class com.yy.writingwithai.** { *; }` 在 `app/proguard-rules.pro` 中，`bec40ca` 落地) |
| `usesCleartextTraffic="true"` 风险 | `AndroidManifest.xml:28` 显式 `android:usesCleartextTraffic="false"` |
| 仓库内硬编码 apikey/secret/token | grep 主代码无命中;apikey 全走 `SecureApiKeyStore` |

## 与上轮 review 对比(回归/新增)

### 回归(06-24 r1 标记，本轮仍存在)
- **H1** `AttachmentEntity.noteId` 缺 index(06-24 r1 L3 标记过)
- **M11** `EditorVM.save` logcat 漏 PII(06-24 r1 H22 标记过)

### 新增 CRITICAL(本轮首次发现)
- **C1** LlmEntityExtractor fake provider(关联 C5/AiModule nullable)
- **C2** UserTokenProvider Mutex.withLock(IO) 签名错用
- **C3** BackfillWorker 永远不写 PREF_BACKFILL_DONE
- **C4** UpdateDialog mandatory dismiss 旁路
- **C5** AppNavConsentGateTest 测 fake 不测 production
- **C6** SearchHistoryStoreTest 缺 @RunWith(RobolectricTestRunner)
- **C7** provideAiProviders 返回 mutable Map
- **C8** AuthInterceptor 双重 runBlocking + 漏 SupervisorJob

## 建议处理顺序

1. **立即**(本 worktree，不动 commit):C1 / C2 / C3 / C4 / C6 / C7 — 都有清晰 root cause，改动局部
2. **本轮 next change**:C5(C5 影响 CI 信任度)、H1(回归)、H3(中文用户全功能失效)
3. **下一轮 change 一起**:H2 / H4 / H5 / H6 / H7 / H8(部分 i18n)、H11 / H12
4. **M 系列**挑对用户感知强的修(M3 sync 一致性、M8 国产 ROM 升级)
5. **LOW** 留给 polish 周期，合到对应模块的小 change 里

## 复盘

- 上一轮 review 漏了 C1/C2/C3 — 这三个都是 r1 review 之前就存在的"功能层静默失败"。下一轮 code-review 建议把"prod-only failure path(silent fallback / null-coalesce / swallow catch)"显式列入 CRITICAL 检查项。
- 测试侧两个 CRITICAL(C5/C6)都是"测试本身没意义 / 跑不起来" — 说明 M5 加测试覆盖度时，只统计 .kt 文件数不够，需要把"测试是否真的 assert production 行为"列入 CI gate。
- i18n 漏 4 处(H8 + L 系列 2 处),claude.md 硬规则 vs 实际落实的差距，建议下次 review 把"硬编码字符串扫描"做一次自动化。
