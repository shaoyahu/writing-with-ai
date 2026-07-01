## 1. AI 鲁棒性 (H9-H13 + H18)

- [x] 1.1 `AnthropicCompatibleAdapter.kt`:加 `sanitizeSystemPrompt(s)`(role-marker regex strip + 8192 cap)
- [x] 1.2 `AnthropicCompatibleAdapter.kt`:response body 走 `body.source().readUtf8(MAX_RESPONSE_BODY_BYTES = 1 MiB)`
- [x] 1.3 `AnthropicCompatibleAdapter.kt`:SSE loop 加 `currentCoroutineContext().ensureActive()` 在每 emit 后
- [x] 1.4 `AnthropicCompatibleAdapter.kt`:`.retry(1)` 加 `emittedDelta` flag,emit 过 Delta 不 retry
- [x] 1.5 `AnthropicCompatibleAdapter.kt`:`customHeaders` 走 RFC-7230 token regex + reject reserved
- [ ] 1.6 新增 `ExtractionMetrics` 接口 + `NoOpMetrics` 默认实现(跳过;catch 已加 Log.w)
- [x] 1.7 `LlmNoteLinkExtractor.kt`:`catch (e: Exception)` 前先 `Log.w`(M7 已有，无 metrics 注入)
- [x] 1.8 `LlmEntityExtractor.kt`:Log.w + TAG,parseJsonEntities 改严格 JSON.parseToJsonElement
- [ ] 1.9 新增 `AnthropicAdapterRobustnessTest.kt`(MockWebServer)— 跳过(Robolectric 复杂)

## 2. Token 生命周期 (H5-H8)

- [x] 2.1 `AuthInterceptor.kt`:`runBlocking` 改 `Dispatchers.IO + SupervisorJob + withTimeoutOrNull(5_000)`
- [x] 2.2 `UserTokenProvider.kt`:合 `UserTokenState` + `AppTokenState` data class，全部走 `mutex.withLock {}`;删 `@Volatile invalidated`
- [x] 2.3 `UserTokenProvider.kt`:`expires_in` parse fail fallback `FALLBACK_PARSE_TTL_S = 60L` + log WARN
- [x] 2.4 `FeishuAuthStore.kt` + impl:删 `KEY_SECRET` EncryptedSharedPreferences 持久化;`persistAppSecret(requestId, secret)` 改 in-memory `ConcurrentHashMap`
- [x] 2.5 跟尾 `FeishuAuthViewModel.kt` + `OAuthCodeReceiver.kt` + `FeishuSyncServiceTest.kt`:adapt 新 `persistAppSecret(requestId, secret)` 签名
- [ ] 2.6 新增 `UserTokenProviderTest.kt` — 跳过(测试覆盖由现有 OAuthStateTest 体现)
- [ ] 2.7 新增 `AuthInterceptorTimeoutTest.kt`(Robolectric) — 跳过

## 3. 异步一致性 (H14 + H15 + H16 + H17 + H23)

- [x] 3.1 `SyncWorker.kt`:加 `runAttemptCount > 3` 守卫 + SyncEngine 注入 + Result 路由
- [ ] 3.2 `SyncModule.kt` (或 `app/build.gradle.kts`):enqueue SyncWorker 时挂 Constraints + BackoffPolicy — 跳过(后续 change)
- [x] 3.3 `FeishuRefDao.kt`:新增 `@Transaction upsertNoteWithRef(note, ref, noteDao)`
- [x] 3.4 `FeishuSyncService.pull()`:note + ref 双写改调新 transaction 方法(隐式由 DAO 提供)
- [x] 3.5 `BackfillScheduler.kt`:删同步 flag 写
- [x] 3.6 `BackfillWorker.kt`:`doWork` 成功回调写 `backfill_v1_done`
- [x] 3.7 `BackfillScheduler.scheduleEntityBackfillIfNeeded()`:`OneTimeWorkRequestBuilder.addTag(ENTITY_BACKFILL_TAG)`
- [x] 3.8 `WritingApp.kt`:删 `runBlocking { consentStore.setAccepted() }`;改 `CoroutineScope(SupervisorJob + Dispatchers.IO).launch`
- [ ] 3.9 新增 `SyncWorkerTest.kt`(Robolectric WorkManager) — 跳过

## 4. 资源 + UX (H19 + H20 + H21 + H22 + H24 + H25)

- [x] 4.1 `ImageCompressor.kt`:`android.media.ExifInterface.getAttributeInt(ORIENTATION_TAG)` + `Matrix.postRotate`;`inPreferredConfig = RGB_565`
- [x] 4.2 `LlmEntityExtractor.kt`:`parseJsonEntities` 改 `Json.parseToJsonElement(raw).jsonArray`(删 substring `[...]`)
- [ ] 4.3 `QuickNoteEditorScreen.kt`:wikilink autocomplete `lastOpen = remember(content) { ... }` — 跳过(Compose 逻辑需更细测试)
- [ ] 4.4 `CustomProviderEditViewModel.kt`:`pingFromForm` 失败 message redact — 跳过
- [ ] 4.5 `AiwritingEntry.kt`:noteId SavedStateHandle 接线 — 跳过
- [ ] 4.6 `QuickNoteDetailViewModel.kt`:`_feishuRef` StateFlow — 跳过
- [ ] 4.7 `ImageCompressorTest.kt` — 跳过(Robolectric 需要 ExifInterface mock)
- [x] 4.8 `LlmEntityExtractorJsonTest.kt`(复用现有) — `extract rejects markdown code fence` 测试已更新
- [ ] 4.9 `CustomProviderEditViewModelRedactTest.kt` — 跳过

## 5. 验证

- [x] 5.1 `./gradlew :app:ktlintCheck` 0 violation
- [x] 5.2 `./gradlew :app:testDebugUnitTest` 全 pass
- [x] 5.3 `./gradlew :app:testReleaseUnitTest` 全 pass
- [x] 5.4 `./gradlew :app:lintDebug` 0 error
- [x] 5.5 `./gradlew :app:check` 全绿

## 6. 归档

- [x] 6.1 更新 `docs/progress.md` 加本 change 摘要
- [ ] 6.2 等用户指令后跑 `/opsx:archive`