# Full Project Code Review R3

**日期**: 2026-06-26
**范围**: 全项目代码（core/ai, core/data, core/note, core/feishu, core/widget, core/prefs, feature/*, app/*）
**触发**: 用户指令 "review 整个项目代码"
**方法**: 4 个并行 review agent 覆盖子系统,验证 R2 修复 + 发现新问题
**基线**: HEAD = `e9465c0` (R2 fix) + `ac559e7` (R2 report),working tree clean

## 修复总结

### CRITICAL (5 个)

| # | 文件:行 | 问题 | 修复建议 |
|---|---------|------|----------|
| C1 | `core/data/db/dao/FeishuRefDao.kt:upsertNoteWithRef` | default interface method 跨 DAO 调用,绕过 Room 事务包裹,实际**非原子** | 改 `@Transaction` 标注 method,或内联到单一 DAO `@Query` |
| C2 | `core/feishu/auth/OAuthCodeReceiver.kt` | `GlobalScope` + `NonCancellable` 持有 OAuth 回调,进程被杀时丢 token | 改用 `WorkManager` 或 `ActivityResultRegistry` 持久化 pending state |
| C3 | `core/feishu/auth/FeishuAuthStore.kt:appSecretCache` | in-memory `appSecret` cache + OAuthCodeReceiver 立即读 → 进程重启窗口期 `cache=null` 静默失败 | 改用 EncryptedSharedPreferences 落盘,或加显式 `preflight()` 检查 |
| C4 | `feature/quicknote/edit/QuickNoteEditorScreen.kt:137-156` | `lastOpen` 闭包捕获旧 content offset,AI acceptReplace 后 `state.content` 变但 `lastOpen` 不变 → onSelect 写错位 | 用 offset 快照 + 失效检查,或 `LaunchedEffect` 内重算 |
| C5 | `core/widget/QuickNoteWidget.kt:268` + `WidgetIntentHelpers.kt:25-33` | **R2 H7 修后回归**。`createNoteIntent` 不清栈,`launchWithTaskStack` 清栈 → 两条 widget 入口 back 行为不一致 | 统一走 `launchWithTaskStack` |

### HIGH (34 个,按子系统分组)

#### core/ai + net (4)

- **H1** `AnthropicCompatibleAdapter.kt:123-203` — response.close() 分散在两处(failure line 141 + success line 203),取消路径下 body source 泄漏。把 `response` 提到 split 前的 `val resp`,统一 `try-finally close`
- **H2** `CoreAiGateway.kt:175` — `ping()` 抛 `IllegalStateException` 违反契约(`@return null 表示成功`)。改 `return AiError.ProviderNotConfigured.summary()`(String)
- **H3** `AnthropicCompatibleAdapter.kt:130-140` — 早期 body read 的 `catch (Throwable)` 吞 `CancellationException`。先 `catch (CancellationException) { throw e }`
- **H4** `CoreAiGateway.kt:117` — `provider.supportedModels.firstOrNull() ?: "unknown"` 静默发 "unknown" 到 provider。空列表时 emit `Failed(ProviderNotConfigured)`

#### core/data + core/note (4)

- **H5** `core/data/repo/NoteRepository.kt:144` — `delete()` 文件清理在 DB 事务前,中间崩溃 → DB 行指向不存在的 `localPath`。先 DB 后文件,或补偿回滚
- **H6** `core/note/backfill/EntityBackfillWorker.kt:33-35` — `catch (Exception)` 静默吞 `IOException`/`JsonDecodingException`,`ok` 不增,失败不可见。加 `Log.w` + `failed` 计数
- **H7** `core/data/di/DataModule.kt:36-40` — DEBUG 模式 `fallbackToDestructiveMigration` → 装旧版 debug 静默抹掉用户数据。改为 addMigrations 默认,DEBUG 也不抹
- **H8** `core/note/backfill/LlmBackfillWorker.kt:23-33` — 主循环无 per-note try/catch,单条 poisoned note abort 整个 worker。逐条包 try/catch + skip

#### core/feishu (14,选关键)

- **H9** `core/feishu/api/FeishuApiClientImpl.kt` — 1 MiB cap 限制 string 分配,但 okio `BufferedSource` 仍缓存完整 body 在 buffer。改 `source.readByteArray(maxBytes)` 流式截断
- **H10** `core/feishu/sync/MarkdownToDocxConverterImpl.kt` — code-fence close 检测不区分 indented code line(`   \`\`\`rust` 误判为 close)。要求行首无空白
- **H11** `core/feishu/sync/MarkdownToXmlConverter.kt` — code block content 未转义 → HTML 嵌入产出 malformed XML。CDATA 包裹或 XML-escape
- **H12** `core/feishu/sync/FeishuSyncService.kt:pull` — 每次 pull 都 `localRevision = System.currentTimeMillis()` → 下次 conflict 检测永远 LOCAL > REMOTE。改为只更新 lastSyncedAt,localRevision 来自业务 upsert
- **H13** `core/feishu/sync/FeishuConflictResolver.kt` — `REMOTE_DELETED` enum 值存在但无任何 set 路径(死代码)。要么实现,要么删 enum
- **H14** `core/feishu/sync/FeishuApiClient.kt:extractDocIdFromUrl` — 拒绝带 query param 的 URL(`?from=copy`)。split `?` 再解析

#### feature + app + widget + prefs (12)

- **H15** `core/prefs/SecureApiKeyStore.kt:97-211` — **R2 H12 修后新 race**。`reveal()` fire-and-forget launch,`clear()` 同步改 Hidden;reveal 协程内 `prefs.getString` IO 期间 clear 完成 → 协程恢复时仍 emit `Revealed` 覆盖 Hidden。检查 `flow.value is Revealed` 才进入 timer
- **H16** `feature/settings/model/CustomProviderEditViewModel.kt:107-124` — `onDisplayNameChanged` 用 `[^a-z0-9\\s-]` 正则,中英混合输入把中文段全删,id 变空。fallback 短随机后缀
- **H17** `feature/settings/model/CustomProviderEditViewModel.kt:233-235` — `save()` 先 `customProviderStore.save(config)` 再 `secureApiKeyStore.save(id, key)`,后步失败 → config 已落库 + key 缺失,UI 假成功。改顺序 + 失败回滚
- **H18** `feature/aiwriting/streaming/AiActionViewModel.kt:177-236` — **R2 H1 修后新 bug**。`acceptReplace` 失败时 `return@withContext` 只跳出内层 NonCancellable,外层继续 `_state.value = Replaced` 覆盖 `Failed`。把 state 写也放进内层,或外层 return
- **H19** `feature/quicknote/detail/QuickNoteDetailViewModel.kt:269-297` — `addAttachment` `imageCompressor.compress` 抛异常时 `sourceFile.delete()` 不执行 → cache 累积。`try-finally delete`
- **H20** `feature/quicknote/detail/QuickNoteDetailViewModel.kt:209-213` — `resolveConflictKeepRemote` catch 块未重置 `_showConflictDialog.value = false`,用户解决失败时 dialog 仍开。catch 内补一行
- **H21** `feature/aiwriting/streaming/AiActionViewModel.kt:128-167` — 每次 Delta 整段 `partialText` emit + `stateIn.collectAsState` 重组 + recompose 全部内容,O(n²) 内存 + recompose。改增量 emit + UI 累加
- **H22** `feature/quicknote/list/QuickNoteListViewModel.kt:95-106` — `uiState.collect { feishuSyncService.getRefsForNotes(ids) }` 无防抖,搜索抖动时连发。加 `distinctUntilChangedBy` + `debounce(300ms)`
- **H23** `feature/settings/model/ModelManagementViewModel.kt:132-195` — `saveProvider` 失败回滚时 rollback 自己 try-catch 静默吞,且 `setSelectedProviderId` 早已持久化到 DataStore。先 backup → 内存态 → 全成功才持久化
- **H24** `core/widget/QuickNoteWidgetRepository.kt:18` + `QuickNoteWidgetUpdater.kt:25-30` — Glance widget host process vs 主进程的 `updateAll` race 风险。实测确认,必要时去掉 withContext
- **H25** `core/widget/QuickNoteWidget.kt:43` — `provideGlance` 内 `observeConfiguredProviders` 单次 `.first()` 拉 Room,每次 widget update 都触发 Room 读。加 <N 秒缓存
- **H26** `feature/quicknote/edit/QuickNoteEditorViewModel.kt:165` — debug 包 `Log.d("EditorVM", "save noteId=${note.id} ...")` 打印用户数据 UUID。删日志或去掉 noteId

## MEDIUM 摘要 (80 个,按子系统聚合)

| 子系统 | 数量 | 主题 |
|--------|------|------|
| core/ai | 11 | retry 范围太宽(SSL/UnknownHost)、`summary()` 不 escape Markdown、OpenAI usage 字段 nullable、`Retry-After` 不支持 HTTP-date、`fireInvalidate` 吞 Cancellation |
| core/data+note | 11 | `LocalNoteLinker.sanitizeForSearch` 先 take 后 escape 顺序可疑、CJK 单字符 unigram 缺失、`GROUP BY` 缺 id 列、`recomputeFlow` 静默 drop、`NoteExporter` 双重 combine、`ai_history` import 失败无日志、`BackfillScheduler` magic constants 重复、`recomputeAll` 死 SPI、TypeConverter 失败 closed 无测试、`recomputeFlow` collector 吞 Cancellation |
| core/feishu | 30 | OAuth state 持久化、token refresh 错误映射、conflict 边界(mid-stream cancel、concurrent edit)、encryption 边界、HTML 转义不全、empty `aliasRows` 跳过、eager fetch |
| feature+app+widget+prefs | 28 | Compose `remember` 缺 key、`SimpleDateFormat` 每次重组新建、wikilink `lastOpen` 缓存失效、`SearchHistoryStore` 死代码、selection VM 双份、`streamJob.cancel` 旧状态 race、`OnboardingScreen` total=1 死锁、`StreamingPanel` diff O(n)、`QuickNoteListScreen` 搜索历史 UI 不刷新、tag color hash 不均、a11y `contentDescription` 缺失、`BuildConfig.APPLICATION_ID` 假设 |

## LOW 摘要 (54 个)

dead code(`CompositeNoteLinker.recomputeAll`、`EXPECTED_JSON_SHAPE` const)、magic numbers、注释 TODO、测试断言 implementation detail、String concatenation 性能、locale fallback 链缺失、`@Suppress` 死字段。

## 测试覆盖 gap (R2 + R3 共 41 个)

**HIGH 优先级(回归测试)**:
- `AnthropicCompatibleAdapter` 401/403/429/5xx 错误映射(H4 R2 修无回归测试)
- `CoreAiGateway.ping` CancellationException rethrow(H5 R2 修)
- `AiActionViewModel.acceptReplace` 三种情况(完全匹配/不匹配/多处匹配)— R2 H1 引入的 H18 bug 需先修后测
- `NoteRepository.delete` 部分失败(file deleted before DB delete)— R2 H15 引入的 H5 bug
- `WikilinkAutocomplete` SQL LIKE 注入 + 转义 — 无单测
- `FeishuConflictResolver` `lastSyncedAt=0L` 边界
- `WidgetStateStore.incrementNoteIndex` 并发 — R2 修
- `SecureApiKeyStore.reveal` vs `clear` race — R2 H12 引入的 H15
- `BackfillScheduler.cancelEntityBackfill` 按 tag cancel — 无测试
- `CustomProviderEditViewModel.save` 失败回滚 — H17

**MEDIUM**:`AiHistoryRepository.record` apikey 脱敏、`LocalNoteLinker.sanitizeForSearch` `\` `%` `_` round-trip、`AiHistoryRepository.prune`、`AiHistoryDao.deleteOlderThan`、`OnboardingScreen` total=1 死锁、`SimpleMarkdown` 嵌套 `**`、`AboutViewModel` 状态机。

## R2 修复回归检查

| R2 修复 | 状态 | 备注 |
|---------|------|------|
| H1 `AiActionViewModel` runBlocking 移除 | ⚠️ **引入 H18** | acceptReplace Failed 路径下 Replaced 覆盖 |
| H2 `QuickNoteDetailViewModel` CancellationException | ✅ Correct | |
| H3 `SseParser` data: 大小写 | ✅ Correct | |
| H4 `AnthropicCompatibleAdapter` 错误映射 | ✅ Correct | 但缺回归测试 |
| H5 `CoreAiGateway.ping` rethrow | ✅ Correct | 但缺回归测试 |
| H6 `SafePromptTemplate` BEGIN/END | ✅ Correct | |
| H7 `QuickNoteWidget` 空列表守卫 | ⚠️ **引入 C5** | 两条 Intent 路径 flag 不一致 |
| H8 `FeishuSyncService.pull` docId | ✅ Correct | |
| H9 `EntityBackfillWorker` 完成标志 | ✅ Correct | 但 `catch (Exception)` 静默吞(H6) |
| H10 `MainActivity` hasRoute | ✅ Correct | |
| H11 `AppNav` consentFlow.first() | ✅ Correct | |
| H12 `SecureApiKeyStore` ConcurrentHashMap | ⚠️ **引入 H15** | reveal-vs-clear 竞态 |
| H13 `Note+NoteMapper` sync 字段 | ✅ Correct | |
| H14 `ZipHelper` relativeToOrNull | ✅ Correct | |
| H15 `NoteRepository.delete` 附件清理 | ⚠️ **引入 H5** | delete 顺序反了 |
| H16 `FeishuConflictResolver` localRev | ✅ Correct | |
| M1 `OAuthLauncher` persist 同步 | ⚠️ (C2/C3 加剧) | OAuthCodeReceiver 用 GlobalScope+NonCancellable,进程死即丢 |
| M2 `AppNav` context cast | ✅ Correct | |
| M3 `QuickNoteListViewModel` StateFlow | ✅ Correct | |
| M4 `WikilinkAutocomplete` rememberUpdatedState | ✅ Correct | |
| M5 `WikilinkAutocomplete` CancellationException | ✅ Correct | |

**R2 净结果**: 16 HIGH + 5 MEDIUM 修对,但 5 处(H1/H7/H12/H15/M1)各自引入新 HIGH/CRITICAL。R3 净发现 5 CRITICAL + 29 NEW HIGH + 80 MEDIUM + 54 LOW。

## 建议 R4 修复优先级

**P0(必修,影响数据/安全)**:
- C1-C5(5 CRITICAL)
- H5/H6/H7/H8(core/data 4 HIGH)
- H12/H13/H14(core/feishu conflict 3 HIGH)
- H17/H18(feature 2 HIGH — 用户感知)

**P1(应修,影响体验/性能)**:
- H1/H2/H3/H4(core/ai 4 HIGH)
- H9-H11(core/feishu 3 HIGH)
- H15/H16/H19/H20/H21/H22/H23/H26(feature 8 HIGH)
- HIGH 优先级测试覆盖 10 项

**P2(M5 polish 范畴)**:
- MEDIUM + LOW + 非关键测试

## 验证

- ⏸️ R3 修复前未重跑 build/test(避免噪声)
- R2 修复已 verify:`./gradlew :app:assembleDebug` / `ktlintCheck` / `testDebugUnitTest` 234 tests 通过
- R4 修复后建议重跑全套

## 未在本 review 深入

- `core/net/` 目录不存在(CLAUDE.md 提及但项目未创建)
- `feature/settings/feishu/` 实际由 feishu 子系统 agent 覆盖
- `app/src/main/res/` 资源文件(layout / strings / drawables)
- `app/src/main/AndroidManifest.xml`
- Gradle / Hilt module 配置
- 性能/内存深度 profiling(仅静态分析)
