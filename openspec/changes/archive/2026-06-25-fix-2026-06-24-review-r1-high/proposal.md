# fix-2026-06-24-review-r1-high Proposal

## Why

`docs/reviews/2026-06-24-full-project-code-review-r1.md` 全量扫描发现 **8 项 CRITICAL + 25 项 HIGH**。CRITICAL + lint 3 error 已在 `fix-2026-06-24-review-r1-critical` 归档(`./gradlew :app:check` 绿)。本 change 收剩余 **22 项 HIGH**(H1/H2/H3 与 r1-critical fix 重叠,跳过)。

HIGH 集中在 4 个领域:
- **AI/Stream 鲁棒性**:AnthropicAdapter SSE ensureActive 缺失 / body cap / retry 重放 / customHeaders 无校验 / prompt 注入
- **Token 生命周期**:AuthInterceptor runBlocking 死锁 / UserTokenProvider @Volatile 分裂 / expires_in 7000s fallback / appSecret 无 TTL
- **异步一致性**:SyncWorker stub / FeishuSyncService 网络+DAO 无 @Transaction / BackfillScheduler flag 顺序错 + tag no-op / WritingApp runBlocking onCreate
- **资源 + UX**:ImageCompressor EXIF+OOM / wikilink prefix race / LLM 静默 catch / pingFromForm apikey 泄漏 / AiwritingEntry noteId 形参丢失 / feishuRef 一次性 get 不观察

不收 MEDIUM/LOW(后续 change 分批)。

## What Changes

- **H4** `UpdateDownloadReceiver.sha256()`:改 `goAsync()` + `Dispatchers.IO`,主线程不再跑 SHA
- **H5** `AuthInterceptor`:删除 `runBlocking { getToken() }`,改 `withContext(Dispatchers.IO) { withTimeout(5s) { mutex.withLock { ... } } }`
- **H6** `UserTokenProvider.invalidated`:`@Volatile Boolean` → mutex 内状态,避免并发分裂
- **H7** `UserTokenProvider`:解析 `expires_in` 失败时 fallback 60s + log warn(不再 7000s 静默)
- **H8** `FeishuAuthStore`:appSecret 持久化加 TTL(`KEY_SECRET_TTL`,30 分钟过期自动 clear);或仅内存 LRU(选 LRU)
- **H9** `AnthropicCompatibleAdapter`:用户 systemPrompt 走 `SafePromptTemplate` strip role-marker 关键词 + 长度 cap 8192
- **H10** `AnthropicCompatibleAdapter`:response body 读取走 `source().request(MAX_BODY = 1 MiB)`,不再 `body.string()` 整 body
- **H11** `AnthropicCompatibleAdapter`:SSE consume loop 加 `currentCoroutineContext().ensureActive()`,stall 时协作取消
- **H12** `AnthropicCompatibleAdapter`:`.retry(1)` 仅在 emit 过 Failed 时 retry;emit 过 Delta 不 retry
- **H13** `AnthropicCompatibleAdapter`:`customHeaders` 走 `Headers.checkName()` + 拒绝 reserved(`Host`/`Authorization`/`Content-Length`)
- **H14** `SyncWorker`:加 `NetworkType.CONNECTED` Constraints + `ExponentialBackoff` + `runAttemptCount <= 3` 守卫,`doWork` 调真正 sync 逻辑
- **H15** `FeishuSyncService.pull`:`note + ref` 双写包进 `@Transaction` DAO 方法,防孤儿
- **H16** `BackfillScheduler.scheduleIfNeeded`:flag 写入移到 Worker `doWork` 成功后;enqueue 失败 flag 不写
- **H17** `BackfillScheduler`:Entity backfill enqueue 加 `.addTag(ENTITY_BACKFILL_TAG)`,cancel tag 真正生效
- **H18** `LlmNoteLinkExtractor` + `LlmEntityExtractor`:catch `Exception` 前先 `log` + 失败计数(可通过 metrics 接口)
- **H19** `ImageCompressor`:`ExifInterface.getAttributeInt(ORIENTATION_TAG)` → `Matrix.postRotate`;`inSampleSize` 按目标维度算,跳过中间全尺寸 bitmap
- **H20** `LlmEntityExtractor.parseJsonEntities`:用 `Json.parseToJsonElement(...).jsonArray` 严格解析(替代 substring `[...]`)
- **H21** `QuickNoteEditorScreen` wikilink autocomplete:`lastOpen` 在 recompose 时 snap,onSelect 时从 *当前* content 重新定位 prefix
- **H22** `CustomProviderEditViewModel.pingFromForm`:`e.message` 渲染前 redact query string 中的 apikey
- **H23** `WritingApp.onCreate`:`runBlocking { consentStore... }` → `CoroutineScope(SupervisorJob + Dispatchers.IO).launch`
- **H24** `AiwritingEntry`:`noteId` 形参通过 `extras` 传 `SavedStateHandle["noteId"]`,VM 读 SavedStateHandle
- **H25** `QuickNoteDetailViewModel._feishuRef`:`getRef` → `refDao.observeForNote(id).stateIn(scope)`,UI 实时刷新

## Capabilities

### Modified Capabilities

- **ai-gateway**:H9 + H10 + H11 + H12 + H13 — AnthropicAdapter prompt/body cap / SSE ensureActive / retry 策略 / header 校验
- **feishu-auth**:H5 + H6 + H7 + H8 — AuthInterceptor 异步 / Token 状态一致性 / expires_in fallback / appSecret LRU
- **feishu-bidir-sync**:H15 — `pull` `note + ref` 双写 `@Transaction`
- **release-readiness**:H4 — UpdateDownloadReceiver SHA `goAsync`
- **note-entity-link**:H18 — 失败可观测
- **note-entity-extraction**:H18 + H20 — 失败可观测 + 严格 JSON 解析
- **quick-note**:H21 — wikilink prefix race fix
- **writing-app**:H23 — `WritingApp.onCreate` 异步化
- **aiwriting**:H22 + H24 — apikey redact + noteId 形参
- **quicknote-list**:H25 — feishuRef observe Flow
- **media-attachment-infrastructure**:H19 — ImageCompressor EXIF + OOM
- **note-association** (backfill 子 spec):H16 + H17 — BackfillScheduler flag 顺序 + tag 修复
- **sync** (现有 spec):H14 — SyncWorker Constraints + BackoffPolicy

(具体 capability 名查 `openspec/specs/` 已存在 24 个 spec,按上面映射)

## Impact

- **API**:
  - `UpdateDownloadReceiver`:`sha256(context, uri)` 签名不变,改内部 IO 模型
  - `AuthInterceptor.intercept(chain)`:签名不变,移除 `runBlocking`
  - `UserTokenProvider.exchangeCode` / `getToken` / `invalidate`:签名不变
  - `FeishuAuthStore`:移除 `KEY_SECRET` 持久化,改 LRU(影响 `persistAppSecret/clearAppSecret` 内部实现)
  - `AnthropicCompatibleAdapter`:签名不变
  - `SyncWorker.doWork`:签名不变
  - `FeishuSyncService.pull`:签名不变
  - `BackfillScheduler.scheduleIfNeeded`:签名不变
  - `ImageCompressor`:签名不变
  - `LlmEntityExtractor.parseJsonEntities`:private,无外部影响
  - `WritingApp.onCreate`:签名不变
  - `AiwritingEntry`:Composable 形参列表不变(noteId 走 SavedStateHandle)
  - `QuickNoteDetailViewModel._feishuRef`:暴露 StateFlow 替代单次 get(UI 内部 collect)
- **Tests**:补 `AuthInterceptorTest`(Robolectric MockWebServer)、`LlmEntityExtractor parseJsonEntities 严格解析测试`、`ImageCompressor EXIF` 等;`SyncWorker` / `BackfillScheduler` 现有测试跟进
- **DB migration**:无
- **CI**: `./gradlew :app:check` 必须保持绿

## Not-In-Scope (后续 change)

- MEDIUM 50+ 项(extractDocId fallback / Compose 资源 token / shell injection / runBlocking catch Cancellation / `PaddingValues(vertical=12.dp)` 等)
- LOW 25+ 项(字符串硬编码 / 过时 import / 一些 deprecation warning)
- KSP/编译警告(FK 索引 / FlowPreview / AutoMirrored icons)
- lint-baseline.xml 129 个 whitelisted 待审计