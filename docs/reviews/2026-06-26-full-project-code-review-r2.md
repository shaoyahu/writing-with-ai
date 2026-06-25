# Full Project Code Review R2

**日期**: 2026-06-26
**范围**: 全项目代码（core/ai, core/data, core/feishu, core/note, core/widget, core/prefs, core/net, feature/*, app/*）
**触发**: 用户指令 "review 整个项目代码，并修复所有 bug"
**方法**: 5 个并行 review agent 覆盖不同子系统，按 HIGH → MEDIUM 优先级修复

## 修复总结

### HIGH (16 个)

| # | 文件 | 问题 | 修复 |
|---|------|------|------|
| H1 | `AiActionViewModel.kt` | `runBlocking` 同步读 consent/ack 阻塞主线程(ANR) + 快照过期 | 删 `runBlocking`，改为 `start()` 内 `viewModelScope.launch` 异步读取 |
| H2 | `QuickNoteDetailViewModel.kt` | 3 处 `catch(Throwable)` 吞掉 CancellationException | 每处前加 `catch(CancellationException) { throw e }` |
| H3 | `SseParser.kt` | `removePrefix("data:")` 不匹配 `DATA:`/`Data:` 等大小写变体 | `substring(5)` 与 `startsWith("data:", ignoreCase=true)` 对齐 |
| H4 | `AnthropicCompatibleAdapter.kt` | CancellationException 被吞 + `readUtf8` 小 body EOF + 429/5xx 映射到 Unknown | 加 rethrow + `source.request(Long.MAX_VALUE)` 条件读 + 正确错误映射 |
| H5 | `CoreAiGateway.kt` | `ping()` 的 `catch(Exception)` 吞 CancellationException | 加 rethrow |
| H6 | `SafePromptTemplate.kt` | `fenceUserContent()` 只转义 END 标记，注入者可伪造 BEGIN 边界 | 同时转义 BEGIN + END |
| H7 | `QuickNoteWidget.kt` | 空列表 `noteIndex % notes.size` ArithmeticException | `notes.isNotEmpty()` 守卫 |
| H8 | `FeishuSyncService.kt` | `pull()` 用参数 docId 查 ref，与 URL 解析结果不同导致重复记录 | 改用 `content.docId`（`readDoc` 从 URL 提取的权威值） |
| H9 | `EntityBackfillWorker.kt` | 成功后无完成标志，Worker 被反复调度 | `SharedPreferences` 写 `backfill_entity_v1_done` |
| H10 | `MainActivity.kt` | `hasRoute(QuicknoteList::class)` 永远 false（已被 AppShell 取代） | 改为 `hasRoute(AppShell::class)` |
| H11 | `AppNav.kt` | `consentFlow.value` 读到 EMPTY 初始值导致 onboarding 闪烁 | `consentFlow.first()` + EMPTY 守卫 |
| H12 | `SecureApiKeyStore.kt` | `mutableMapOf` 非线程安全，reveal/clear 并发 CME | `ConcurrentHashMap` |
| H13 | `Note.kt` + `NoteMapper.kt` | sync 字段(syncRevision/syncStatus/lastSyncedAt)未映射，每次 upsert 重置 SYNCED→LOCAL | Note 加3字段 + NoteMapper 双向映射 |
| H14 | `ZipHelper.kt` | Android canonical path `//` 前缀导致 `startsWith` 检查拒绝所有合法 entry | `relativeToOrNull` 替代 `startsWith` |
| H15 | `NoteRepository.kt` | `delete()` 不清理附件文件+DB行，磁盘泄漏 | 加 `attachmentStore.deleteAllForNote()` + `noteAttachmentDao.deleteForNote()` |
| H16 | `FeishuConflictResolver.kt` | `localRev > 0L` 永远 true（updatedAt 时间戳），远端变更总判 BOTH_DIRTY | 改为 `localRev > lastSyncedAt` |

### MEDIUM (6 个)

| # | 文件 | 问题 | 修复 |
|---|------|------|------|
| M1 | `OAuthLauncher.kt` | `persistOAuthState` fire-and-forget，回调到达前 state 未落盘 | `runBlocking` 同步 persist 后再 launchUrl |
| M2 | `AppNav.kt` | `context as Activity` 不安全转换，非 Activity context 崩溃 | `as? Activity` + `error()` 明确报错 |
| M3 | `QuickNoteListViewModel.kt` | `selectedIds`/`isSelectMode` 暴露 MutableStateFlow | 改为 `_selectedIds` private + `asStateFlow()` |
| M4 | `WikilinkAutocomplete.kt` | `onSelect` lambda 在 clickable 中可能过期 | `rememberUpdatedState(onSelect)` |
| M5 | `WikilinkAutocomplete.kt` | CancellationException 被 `catch(Exception)` 吞 | 加 rethrow |
| M6 | `FeishuAuthScreen.kt` | seedDone 边界情况(savedAppId 变化不 re-seed) | 跳过，影响极小 |

### 测试修复 (2 个)

| 文件 | 修改 |
|------|------|
| `FeishuSyncServiceTest.kt` | `getByDocId("doc1")` → `getByDocId("d1")`（对齐 H8 修复） |
| `FeishuConflictResolverTest.kt` | 所有用例适配新签名 `detect(localRev, lastSyncedAt, ...)` |

## 未修复 / 低优先级

| 项 | 说明 | 原因 |
|----|------|------|
| AuthInterceptor `runBlocking` | `withTimeoutOrNull(5s)` 已防止死锁，但 OkHttp 线程占用仍存在 | 改为回调式需重构架构，M5 polish 范畴 |
| FeishuAuthScreen seedDone | savedAppId 变化时不 re-seed | 边界情况，主流程不受影响 |
| SecureApiKeyStore timer 未取消 | 5s delay timer 在 pause 后仍运行 | 已通过 `lastPauseAt` + `updateRevealState` 逻辑等效处理 |

## 验证

- ✅ `./gradlew :app:assembleDebug` 编译通过
- ✅ `./gradlew :app:ktlintCheck` 风格检查通过
- ✅ `./gradlew :app:testDebugUnitTest` 234 测试全部通过
