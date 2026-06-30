# 全仓库代码 Review：逻辑漏洞与 Bug

**日期**: 2026-06-30
**范围**: 全仓库 `app/src/main/java/` (155 源文件)
**方法**: 5 维度并行审查 (AI层 / 数据层 / Feishu同步 / 安全与核心 / Compose UI)
**发现总数**: 32

| 严重度 | 数量 |
|--------|------|
| 🔴 Critical | 4 |
| 🟠 High | 13 |
| 🟡 Medium | 12 |
| 🟢 Low | 3 |

| 分类 | 数量 |
|------|------|
| logic-bug | 21 |
| data-loss | 4 |
| security | 3 |
| race-condition | 2 |
| error-handling | 1 |
| null-safety | 1 |

---

## 🔴 Critical (4)

### C1. CompositeNoteLinker 删除+插入无事务 — 进程崩溃导致笔记链接永久丢失
- **文件**: [CompositeNoteLinker.kt:30](app/src/main/java/com/yy/writingwithai/core/note/impl/CompositeNoteLinker.kt#L30)
- **分类**: data-loss
- **详情**: `recomputeForNote()` 调用 `noteLinkDao.deleteBySrc(noteId)` 后再 `noteLinkDao.upsertAll(capped)`，但整个操作没有包在数据库事务里。如果进程在 delete 和 upsertAll 之间被杀（OOM killer、用户强杀、系统回收），该笔记的所有出站链接永久丢失。该类未注入 AppDatabase，无法调用 `db.withTransaction`。`SemanticNoteLinker.extractAndPersist()` 也有同样风险。
- **修复**: 注入 AppDatabase，将 delete + upsertAll 包在 `db.withTransaction { ... }` 中。

### C2. FeishuConflictResolver.detect() 从未被调用 — 冲突检测是死代码，push 覆盖远端、pull 覆盖本地
- **文件**: [FeishuConflictResolver.kt:21](app/src/main/java/com/yy/writingwithai/core/feishu/sync/FeishuConflictResolver.kt#L21)
- **分类**: logic-bug
- **详情**: `FeishuConflictResolver.detect()` 已定义但全仓库无任何调用点。`FeishuSyncService.push()` 直接调 `docService.updateDoc()` 无冲突检查；`pull()` 直接覆盖本地内容不检查本地是否有修改。`FeishuRefStatus.CONFLICT` 枚举值定义了但从未赋值给任何 `FeishuRefEntity`。`QuickNoteDetailViewModel._showConflictDialog` 从未被设为 true。UI 代码检查 CONFLICT 状态并弹对话框，但状态永远到不了。结果：**push 静默覆盖远端修改，pull 静默覆盖本地编辑** — 双向数据丢失路径。
- **修复**: 在 `FeishuSyncService.push()` 和 `pull()` 中调用 `FeishuConflictResolver.detect()`；当检测到 `BOTH_DIRTY` 时设置 `FeishuRefStatus.CONFLICT` 并展示冲突对话框。

### C3. APK 下载 URL 无 HTTPS 校验 — 攻击者可控 URL 直接传给 DownloadManager
- **文件**: [ApkDownloader.kt:31](app/src/main/java/com/yy/writingwithai/core/update/ApkDownloader.kt#L31)
- **分类**: security
- **详情**: `ApkDownloader.start()` 把 `manifest.apkUrl` 直接传给 `DownloadManager.Request(Uri.parse(manifest.apkUrl))`，未校验 URL scheme。代码注释说"HTTPS-only transport"但未强制执行。若 manifest 服务器被入侵或 JSON 被篡改（pinning 之前的 MITM），apkUrl 可以是 `http://`、`file://`、`ftp://` — 允许攻击者拦截下载或通过明文提供恶意 APK。SHA-256 校验缓解了 APK 篡改但不能防止网络层对下载 URL 的拦截。`AppUpdateChecker.fetch()` 对 manifest URL 正确使用了 HTTPS，但 manifest 内的 apkUrl 从未被校验。
- **修复**: 在 `ApkDownloader.start()` 开头加 `require(manifest.apkUrl.startsWith("https://")) { "apkUrl must use HTTPS" }`。

### C4. feishu_oauth_prefs.xml 未排除在备份规则外 — OAuth token 和 appSecret 可泄露
- **文件**: [backup_rules.xml:7](app/src/main/res/xml/backup_rules.xml#L7)
- **分类**: security
- **详情**: `backup_rules.xml` 和 `data_extraction_rules.xml` 只排除了 `writingwithai_secure_prefs.xml`。`FeishuAuthStoreImpl` 把 access_token、refresh_token、appSecret、pending OAuth 交换数据、OAuth CSRF state 存在另一个 `EncryptedSharedPreferences` 文件 `feishu_oauth_prefs` 中。该文件未列在备份排除规则里。虽然 `android:allowBackup="false"` 当前阻止了所有备份，但排除规则被注释描述为"forward-looking for M5"。如果未来重新启用 allowBackup，包含 live OAuth token 和 appSecret 的 feishu_oauth_prefs.xml 将被包含在云备份和设备迁移中，泄露凭据。
- **修复**: 在 `backup_rules.xml` 和 `data_extraction_rules.xml` 中添加 `<exclude domain="sharedpref" path="feishu_oauth_prefs.xml" />`。

---

## 🟠 High (13)

### H1. SseParser cleanTermination 标志未重置 — 截断的 SSE 流被报告为正常终止
- **文件**: [SseParser.kt:80](app/src/main/java/com/yy/writingwithai/core/ai/stream/SseParser.kt#L80)
- **分类**: logic-bug
- **详情**: `cleanTermination` 在空行刷新完整事件时设为 `true`，但在读取新的 `data:` 行时**从未重置为 `false`**。导致流 `data: first\n\ndata: second`（EOF 无尾换行）的第二个事件会继承第一个事件的 `cleanTermination=true`，解析器发出 `Done` 而非 `Error(EOFException)`。下游 AI 层把不完整输出当作完整结果显示，用户看到截断的 AI 文本而无任何错误提示。
- **修复**: 在 `line.startsWith("data:")` 分支中添加 `cleanTermination = false`。

### H2. AiActionViewModel cancel()/dismiss() 未 bump streamGeneration — 旧协程可覆盖 Idle 状态
- **文件**: [AiActionViewModel.kt:353](app/src/main/java/com/yy/writingwithai/feature/aiwriting/streaming/AiActionViewModel.kt#L353)
- **分类**: race-condition
- **详情**: `cancel()`/`dismiss()` 调用 `streamJob?.cancel()` 并设 `_state.value = Idle`，但未 bump `streamGeneration`。旧协程（generation 仍匹配）在取消窗口期发出的 stale Failed/Delta/Done 事件会覆盖 Idle 状态。用户看到：按取消 → Idle → 状态又翻回 Failed 或 Done。`start()` 正确 bump 了 streamGeneration，但 cancel()/dismiss() 没有。
- **修复**: 在 `cancel()` 和 `dismiss()` 开头加 `streamGeneration++`，与 `start()` 保持一致。

### H3. CoreAiGateway onCompletion 中 DB 写入无 try-catch — 失败替换 CancellationException 破坏结构化并发
- **文件**: [CoreAiGateway.kt:168](app/src/main/java/com/yy/writingwithai/core/ai/CoreAiGateway.kt#L168)
- **分类**: error-handling
- **详情**: `streamWritingOp` 的 `onCompletion` 调用 `historyRepo.get().record()` 执行 Room DAO insert，可抛异常（约束违反、磁盘满等）。无 try-catch 保护。若 flow 被取消（CancellationException），DB 异常替换 CancellationException，破坏结构化并发 — 取消信号丢失。正常完成时，collector 看到意外异常。两种情况都可导致崩溃或不一致状态。
- **修复**: 在 onCompletion 中包裹 try-catch：`try { historyRepo.get().record(...) } catch (e: CancellationException) { throw e } catch (e: Exception) { Log.w(TAG, "history record failed", e) }`。

### H4. ExportNote 缺失 syncRevision/syncStatus/lastSyncedAt — 导出再导入丢失同步状态
- **文件**: [ExportModels.kt:11](app/src/main/java/com/yy/writingwithai/core/data/export/ExportModels.kt#L11)
- **分类**: data-loss
- **详情**: `ExportNote` 不含 `syncRevision`、`syncStatus`、`lastSyncedAt` 字段。已同步笔记导出再导入后 syncStatus 默认 LOCAL，syncRevision/lastSyncedAt 为 null。违反导出-导入 round-trip 保真保证。导入后之前已同步的笔记显示为仅本地，同步修订历史丢失。
- **修复**: 将这三个字段加入 ExportNote，更新 NoteExporter 和 NoteImporter 的映射逻辑。

### H5. NoteRepository.delete() 未清理 ai_history 孤儿行
- **文件**: [NoteRepository.kt:160](app/src/main/java/com/yy/writingwithai/core/data/repo/NoteRepository.kt#L160)
- **分类**: logic-bug
- **详情**: `NoteRepository.delete()` 不清理 `ai_history` 中 noteId 匹配的行。`AiHistoryEntity` 的 noteId 无 ForeignKey 注解，`AiHistoryDao` 无 `deleteByNoteId` 方法。删除笔记后，ai_history 行成为孤儿 — `observeByNoteId(noteId)` 仍会发射这些孤儿行，`observeTotalTokens()` 仍会计入它们。
- **修复**: 给 AiHistoryDao 加 `deleteByNoteId(noteId)` 方法，在 NoteRepository.delete() 的 `db.withTransaction` 块内调用。

### H6. AuthInterceptor 返回已关闭的 Response — use-after-close
- **文件**: [AuthInterceptor.kt:82](app/src/main/java/com/yy/writingwithai/core/feishu/api/AuthInterceptor.kt#L82)
- **分类**: logic-bug
- **详情**: token 无效时调用 `response.close()`，之后若第二次 token 获取也失败（secondToken == null），方法返回已关闭的 `response` 对象。OkHttp Response.close() 释放底层连接并标记 body 不可用。调用方尝试读取 response body 时会得到 IllegalStateException 或静默丢弃错误信息。
- **修复**: 当 secondToken 为 null 时，不要返回已关闭的 response，而是构建并返回一个新的无认证请求响应或抛出 FeishuError。

### H7. UserTokenProvider.invalidate() 在 mutex 外写 userState — 与 getToken() 竞争
- **文件**: [UserTokenProvider.kt:72](app/src/main/java/com/yy/writingwithai/core/feishu/auth/UserTokenProvider.kt#L72)
- **分类**: race-condition
- **详情**: `invalidate()` 在 `refreshMutex` 外写 `userState`（设 token=null, invalidated=true）。`getToken()` 在 mutex 内外都读 userState（line 62 mutex 内，line 67 mutex 外）。并发的 invalidate() 可在 mutex 保护的读取和外部读取之间设 token 为 null，导致 line 67 返回 null 并抛出 FeishuError.NotAuthorized，尽管 mutex 内已找到有效 token。invalidate() 的写操作与 mutex 保护的 reentrantFetchLocked() 也非原子，可导致刚刷新的 token 被丢弃。
- **修复**: 将 invalidate() 逻辑移入 `refreshMutex`，或将 line 67 的 return 移入 mutex 块内。

### H8. FeishuSyncService.pull() 静默覆盖本地内容 — 不检查本地是否已编辑
- **文件**: [FeishuSyncService.kt:84](app/src/main/java/com/yy/writingwithai/core/feishu/sync/FeishuSyncService.kt#L84)
- **分类**: logic-bug
- **详情**: `pull()` 找到 existingRef 且对应笔记存在时，直接 `existingNote.copy(content = markdown, title = title)` 静默覆盖本地编辑，不检查本地修改（localRevision > lastSyncedAt）。冲突解决器存在但从未被调用。即使用户做了大量本地修改，pull 也会用远端内容替换。这是数据丢失路径。
- **修复**: 覆盖前调用 `FeishuConflictResolver.detect()` 检查本地是否已修改；若 `BOTH_DIRTY`，展示冲突对话框而非静默覆盖。

### H9. FeishuSyncService.push() 不检查远端是否已修改就覆盖
- **文件**: [FeishuSyncService.kt:53](app/src/main/java/com/yy/writingwithai/core/feishu/sync/FeishuSyncService.kt#L53)
- **分类**: logic-bug
- **详情**: `push()` 当 existingRef 存在时，直接调 `docService.updateDoc(note, existingRef)` 而不检查远端文档自上次同步后是否被修改（比较 storedRemoteRev vs newRemoteRev）。如果另一用户或设备在飞书上编辑了文档，此次 push 会用本地内容覆盖远端修改。
- **修复**: push 前获取远端当前 revision，运行 `FeishuConflictResolver.detect()`；若 `BOTH_DIRTY` 展示冲突。

### H10. CoreAiGateway 无 consent 门控 — AI API 调用不检查用户同意
- **文件**: [CoreAiGateway.kt:106](app/src/main/java/com/yy/writingwithai/core/ai/CoreAiGateway.kt#L106)
- **分类**: logic-bug
- **详情**: `streamWritingOp()` 和 `ping()` 有有效 apikey 就继续执行，不检查 `ConsentStore.isConsented()`。同意只在导航层（AppNav.kt 的 LaunchedEffect）执行。通过 deep link、进程恢复后状态还原、或直接 ViewModel 调用到达 AI 功能时，API 调用会执行。项目 CLAUDE.md 规定"首次 AI 调用必须有用户同意"，Gateway 是所有 AI 调用的单一抽象层（项目规则），是正确的执行点。
- **修复**: 在 `streamWritingOp()` 和 `ping()` 开头加 consent 检查，未同意则返回 `AiStreamEvent.Failed(AiError.ConsentRequired)`。

### H11. LlmBackfillWorker 永远返回 Result.success() — 全部失败也不重试
- **文件**: [LlmBackfillWorker.kt:32](app/src/main/java/com/yy/writingwithai/core/note/backfill/LlmBackfillWorker.kt#L32)
- **分类**: logic-bug
- **详情**: `doWork()` 无条件返回 `Result.success(...)`。对比 `EntityBackfillWorker` 正确地在 `result.ok == 0 && result.failed > 0` 时返回 `Result.failure()`。若 LlmBackfillWorker 发生系统性失败（模型配置错误、API key 无效、网络问题），WorkManager 认为工作成功而不重试。
- **修复**: 加与 EntityBackfillWorker 相同的全失败保护：`if (result.failed > 0 && result.processed == 0) return Result.failure(...)`。

### H12. SettingsDataViewModel 吞掉 CancellationException — 破坏协程取消
- **文件**: [SettingsDataViewModel.kt:112](app/src/main/java/com/yy/writingwithai/feature/settings/data/SettingsDataViewModel.kt#L112)
- **分类**: logic-bug
- **详情**: `exportToJsonZip()` 和 `importFromZip()` 的 `catch (e: Exception)` 捕获了 CancellationException。ViewModel 被清除时（用户在导出/导入期间导航离开），协程被取消但 CancellationException 被捕获，状态被错误设为 `DataUiState.Failed` 而非允许取消传播。同文件 `saveImportReport()` 正确地先 rethrow CancellationException 再 catch Exception。
- **修复**: 在两个方法的 generic catch 前加 `catch (e: CancellationException) { throw e }`，与 saveImportReport() 保持一致。

### H13. StreamingPanel LaunchedEffect(state.delta) 丢失中间 delta — 流式显示乱码
- **文件**: [StreamingPanel.kt:110](app/src/main/java/com/yy/writingwithai/feature/aiwriting/streaming/StreamingPanel.kt#L110)
- **分类**: logic-bug
- **详情**: `LaunchedEffect(state.delta)` 在新 delta 到达时取消前一个 LaunchedEffect 再启动新的。若 delta 到达速度快于 Compose 处理帧率（SSE 流中 token 约 50ms 到一个），中间 delta 的 LaunchedEffect 被取消而其 body 未执行，导致显示文本缺失块，产生乱码输出。
- **修复**: 改用 `LaunchedEffect(Unit)` + snapshotFlow/collect 模式，或用 `derivedStateOf` 从 delta 列表计算累积文本，或让 ViewModel 发射完整累积文本。

---

## 🟡 Medium (12)

### M1. SecureApiKeyStore.save() 用 apply() 而非 commit() — 崩溃时 apikey 丢失
- **文件**: [SecureApiKeyStore.kt:135](app/src/main/java/com/yy/writingwithai/core/prefs/SecureApiKeyStore.kt#L135)
- **分类**: data-loss
- **详情**: `save()` 使用 `SharedPreferences.edit().apply()` 异步写磁盘。作为 suspend 函数，调用方期望返回时数据已持久化。若 app 在异步写入完成前崩溃或被杀，apikey 丢失。apikey 是用户输入的密钥，无法恢复。`clear()` 和 `clearAll()` 也有同样问题。
- **修复**: 将 `.apply()` 改为 `.commit()`。这些已在 `Dispatchers.IO` 上运行，同步写磁盘不会阻塞主线程。

### M2. SecureApiKeyStore.updateRevealState expiresAt 使用 IO 挂起前的时间戳
- **文件**: [SecureApiKeyStore.kt:227](app/src/main/java/com/yy/writingwithai/core/prefs/SecureApiKeyStore.kt#L227)
- **分类**: logic-bug
- **详情**: `expiresAt` 用 `now + REVEAL_TIMEOUT_MS` 计算，但 `now` 在 `withContext(Dispatchers.IO)` 挂起点之前获取。IO 读取完成后实际时间已推进 50-200ms。UI 倒计时基于 `expiresAt` 可能立即显示负值。
- **修复**: 将 `now` 和 `expiresAt` 计算移到 `withContext(Dispatchers.IO)` 完成之后。

### M3. FeishuSyncService.pull() 不设 NoteEntity.syncStatus=SYNCED
- **文件**: [FeishuSyncService.kt:86](app/src/main/java/com/yy/writingwithai/core/feishu/sync/FeishuSyncService.kt#L86)
- **分类**: logic-bug
- **详情**: pull 创建新 Note 时未设 syncStatus（默认 LOCAL），FeishuRefEntity 标记 SYNCED 但 NoteEntity.syncStatus 仍是 LOCAL。更新现有笔记时 copy() 保留了旧的 syncStatus（如 DIRTY），与 FeishuRefEntity.status 不一致。
- **修复**: 两个分支都设 `syncStatus = SyncStatus.SYNCED, lastSyncedAt = pullTimestamp`。

### M4. NoteRepository.observeRecent() 加载全部笔记再截断 — 大库 GC 压力
- **文件**: [NoteRepository.kt:207](app/src/main/java/com/yy/writingwithai/core/data/repo/NoteRepository.kt#L207)
- **分类**: logic-bug
- **详情**: `observeRecent(limit)` 调用 `noteDao.observeAll()` 加载所有笔记，再在内存中取前 limit 条。Room Flow 查询在每次表变更时重新发射完整列表。大数据库下造成不必要的内存分配和 GC 压力。
- **修复**: 在 DAO 加专用方法 `@Query("SELECT * FROM notes ORDER BY isPinned DESC, updatedAt DESC LIMIT :limit") fun observeRecent(limit: Int): Flow<List<NoteEntity>>`。

### M5. FeishuRefEntity 缺 docId 索引，FeishuSyncEventEntity 缺 noteId 索引
- **文件**: [FeishuSyncEntity.kt:14](app/src/main/java/com/yy/writingwithai/core/feishu/sync/FeishuSyncEntity.kt#L14)
- **分类**: null-safety
- **详情**: FeishuRefEntity 的 docId 列无 Room Index，每次 pull 操作全表扫描。FeishuSyncEventEntity 的 noteId 也无索引。随数据增长查询退化。
- **修复**: FeishuRefEntity 加 `indices = [Index("docId")]`，FeishuSyncEventEntity 加 `indices = [Index("noteId")]`。提供数据库迁移。

### M6. FeishuShareViewModel 是死代码
- **文件**: [FeishuShareViewModel.kt:29](app/src/main/java/com/yy/writingwithai/feature/quicknote/share/FeishuShareViewModel.kt#L29)
- **分类**: logic-bug
- **详情**: @HiltViewModel 类，全仓库无任何 Composable/Activity 引用。包含自己的 extractDocId regex（与 FeishuDocService.extractDocIdFromUrl 不同），以及与 QuickNoteDetailViewModel 重复但行为不同的冲突解决方法。会腐烂并误导开发者。
- **修复**: 删除整个 FeishuShareViewModel。

### M7. MarkdownToDocxConverterImpl 粗体正则 `[^*]+` 无法匹配嵌套粗斜体 — 数据丢失
- **文件**: [MarkdownToDocxConverterImpl.kt:205](app/src/main/java/com/yy/writingwithai/core/feishu/converter/MarkdownToDocxConverterImpl.kt#L205)
- **分类**: data-loss
- **详情**: 粗体 regex `\*\*([^*]+)\*\*` 无法匹配 `**bold *italic* bold**`，因为 `[^*]` 排除所有星号。斜体同理。MarkdownToXmlConverter 通过递归 convertInline() 正确处理嵌套，但 DocxConverterImpl 不能。含嵌套强调标记的 markdown 通过 v1 docx API 推送时内容丢失。
- **修复**: 改 regex 为 `\*\*(.+?)\*\*` 和 `\*(.+?)\*`（非贪婪，允许内部星号），或采用与 MarkdownToXmlConverter 一致的递归 tokenizer 方案。

### M8. UpdateDownloadReceiver 日志泄露 SHA-256 哈希
- **文件**: [UpdateDownloadReceiver.kt:90](app/src/main/java/com/yy/writingwithai/core/update/UpdateDownloadReceiver.kt#L90)
- **分类**: security
- **详情**: checksum 不匹配时日志输出 expected 和 actual SHA-256 哈希。生产构建 ProGuard 可能仍输出到可调试设备的 logcat。泄露验证期望和接收文件哈希可辅助攻击者理解服务了什么二进制。
- **修复**: 改为 `Log.w(TAG, "sha mismatch for downloadId=$downloadId")`，不记录 actual hash。

### M9. FeishuAuthStore.clearAll() 清除 pending OAuth 交换和 CSRF 状态
- **文件**: [FeishuAuthStore.kt:195](app/src/main/java/com/yy/writingwithai/core/feishu/auth/FeishuAuthStore.kt#L195)
- **分类**: logic-bug
- **详情**: `p.edit().clear().apply()` 擦除 feishu_oauth_prefs 文件中所有 key，包括 pending exchange state 和 OAuth CSRF state。若 clearAll() 在 OAuth 流进行中被调用（用户从设置触发登出时浏览器正打开 OAuth），pending exchange 和 CSRF state 被销毁。OAuthCodeReceiver 回调到达时找不到 pending exchange 和 state 验证，OAuth 回调静默失败。
- **修复**: 选择性删除 token/凭据 key 而非 .clear()，或检查 hasPendingExchange() 再清除。

### M10. QuickNoteEditorScreen existingAttachments Flow 非 lifecycle-aware 收集
- **文件**: [QuickNoteEditorScreen.kt:85](app/src/main/java/com/yy/writingwithai/feature/quicknote/edit/QuickNoteEditorScreen.kt#L85)
- **分类**: logic-bug
- **详情**: `LaunchedEffect(state.isNew) { viewModel.observeAttachments().collect { ... } }` 不感知 lifecycle — app 在后台时仍继续收集 Room 查询发射。同屏幕 pendingUris（line 81）和 QuickNoteDetailScreen 都用了 `collectAsStateWithLifecycle`。
- **修复**: 改用 `viewModel.observeAttachments().collectAsStateWithLifecycle(initialValue = emptyList())`。

### M11. OnboardingRoute 用 collectAsState() 非 collectAsStateWithLifecycle()
- **文件**: [OnboardingRoute.kt:28](app/src/main/java/com/yy/writingwithai/feature/onboarding/OnboardingRoute.kt#L28)
- **分类**: logic-bug
- **详情**: `action` 用 `collectAsState()` 而非 `collectAsStateWithLifecycle()`。Flow 在 app 后台时继续收集，LaunchedEffect 可在 Activity stopped 状态触发。`finishAffinity()` 可在意外生命周期状态被调用。ApikeyPromptRoute（line 20）和其他 route composable 都正确使用了 `collectAsStateWithLifecycle`。
- **修复**: 改为 `collectAsStateWithLifecycle()`。

### M12. SettingsDataScreen notesCount 用 collectAsState() 非 collectAsStateWithLifecycle()
- **文件**: [SettingsDataScreen.kt:120](app/src/main/java/com/yy/writingwithai/feature/settings/data/SettingsDataScreen.kt#L120)
- **分类**: logic-bug
- **详情**: `viewModel.notesCount.collectAsState()` 而非 `collectAsStateWithLifecycle()`。Room Flow 在 app 后台时仍触发查询，浪费资源。同屏幕其他状态收集都用了 `collectAsStateWithLifecycle`。
- **修复**: 改为 `collectAsStateWithLifecycle()`。

---

## 🟢 Low (3)

### L1. FeishuSyncEventEntity noteId 无 ForeignKey — 删除笔记留孤儿事件行
- **文件**: [FeishuSyncEntity.kt:44](app/src/main/java/com/yy/writingwithai/core/feishu/sync/FeishuSyncEntity.kt#L44)
- **分类**: logic-bug
- **详情**: FeishuSyncEventEntity 有 noteId 列但无 ForeignKey，NoteRepository.delete() 不清理 feishu_sync_event 行。孤儿行引用不存在的笔记。若无 FeishuSyncEventDao.deleteByNoteId 方法。
- **修复**: 加 ForeignKey(noteId, onDelete=CASCADE) 或 FeishuSyncEventDao.deleteByNoteId()。如孤儿事件是有意的，在实体类注释中记录决策。

### L2. QuickNoteEditorScreen pendingUris 的 LazyRow items 缺稳定 key
- **文件**: [QuickNoteEditorScreen.kt:334](app/src/main/java/com/yy/writingwithai/feature/quicknote/edit/QuickNoteEditorScreen.kt#L334)
- **分类**: logic-bug
- **详情**: `items(pendingUris)` 未提供 `key` 参数。从列表中间删除 pending URI 时 Compose 无法稳定识别哪个项被删除，可能短暂显示错误缩略图。existingAttachments（line 328）正确用了 `key = { it.id }`。
- **修复**: 用 `itemsIndexed(pendingUris)` 或给每个 pending URI 包装唯一 ID 作为 key。

### L3. StateFlow 用于一次性导航事件 — 重组时可能重发或丢失快速连续事件
- **文件**: [OnboardingViewModel.kt:38](app/src/main/java/com/yy/writingwithai/feature/onboarding/OnboardingViewModel.kt#L38)
- **分类**: logic-bug
- **详情**: OnboardingViewModel、ApikeyPromptViewModel、ResetApikeyPromptViewModel、FeishuAuthViewModel 都用 `MutableStateFlow<Action?>(null)` 做一次性导航/动作事件。两个问题：(1) 配置变更时 null 触发 LaunchedEffect 空操作，但非 null 事件在 composable 被移除前未处理则持续存在，用户返回时重发；(2) StateFlow 合并发射，两个快速连续事件第一个会丢失。ModelManagementViewModel 已使用正确模式：`MutableSharedFlow(replay=0, extraBufferCapacity=8, onBufferOverflow=DROP_OLDEST)`。
- **修复**: 将 `_action` 从 `MutableStateFlow<Action?>` 改为 `MutableSharedFlow<Action>`（replay=0, extraBufferCapacity=1, onBufferOverflow=DROP_OLDEST），与 ModelManagementViewModel 保持一致。

---

## 按模块汇总

| 模块 | Critical | High | Medium | Low | 总计 |
|------|----------|------|--------|-----|------|
| core/feishu/sync | 1 | 3 | 2 | 1 | 7 |
| core/ai | 0 | 3 | 0 | 0 | 3 |
| feature/aiwriting | 0 | 2 | 0 | 0 | 2 |
| core/note | 1 | 1 | 0 | 0 | 2 |
| core/update | 1 | 0 | 1 | 0 | 2 |
| core/prefs | 0 | 0 | 2 | 0 | 2 |
| feature/settings | 0 | 1 | 1 | 0 | 2 |
| core/data | 0 | 2 | 1 | 0 | 3 |
| feature/onboarding | 0 | 0 | 1 | 1 | 2 |
| core/feishu/auth | 0 | 1 | 1 | 0 | 2 |
| core/feishu/api | 0 | 1 | 0 | 0 | 1 |
| core/feishu/converter | 0 | 0 | 1 | 0 | 1 |
| feature/quicknote | 0 | 0 | 1 | 1 | 2 |
| res/xml | 1 | 0 | 0 | 0 | 1 |

**最需优先修复的模块**: `core/feishu/sync`（7 个问题，含 1 个 Critical），其次是 `core/ai` 和 `core/data`。

---

## 建议修复优先级

1. **立即修复** (Critical + 关键 High):
   - C1 CompositeNoteLinker 事务缺失 — 加 withTransaction
   - C2 冲突检测死代码 — 在 push/pull 中调用 FeishuConflictResolver.detect()
   - C3 APK URL HTTPS 校验 — 加 scheme 检查
   - C4 备份规则补全 — 加 feishu_oauth_prefs.xml 排除
   - H1 SseParser cleanTermination 重置 — 加 `cleanTermination = false`
   - H3 CoreAiGateway onCompletion try-catch — 保护 CancellationException

2. **尽快修复** (剩余 High):
   - H2, H4-H13 — 各自独立修复，影响用户数据安全和功能正确性

3. **计划修复** (Medium + Low):
   - M1-M12, L1-L3 — 不影响核心功能但影响健壮性和一致性
