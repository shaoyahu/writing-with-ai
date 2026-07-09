# Code Review: 全量项目代码审查

**Reviewed**: 2026-07-09
**Scope**: 全量 main source (270 Kotlin files)
**Decision**: REQUEST CHANGES

## Summary

7 模块并行扫描，135 findings (2 CRITICAL / 21 HIGH / 73 MEDIUM / 39 LOW)。安全类问题集中在：logcat 泄露敏感信息(apikey/token)、更新模块缺少输入校验、Room FK 未启用导致数据孤儿。正确性类问题集中在：事务边界缺失、SSE 解析遗漏、选区索引偏移。需修复所有 CRITICAL + HIGH 后方可继续。

---

## CRITICAL (2)

### C1. UpdateError.ChecksumMismatch 泄露 SHA-256 哈希
- **File**: [UpdateError.kt:19](app/src/main/java/com/yy/writingwithai/core/update/UpdateError.kt#L19)
- **Category**: security
- **Detail**: `ChecksumMismatch(val expected: String, val actual: String)` 将两个 SHA-256 哈希写入 exception message `"checksum mismatch: expected=$expected actual=$actual"`。若此异常被 log 或展示，攻击者可利用 expected hash 验证篡改后的 APK 是否匹配服务端预期。
- **Fix**: 改为通用消息 `"checksum mismatch"`，hash 值仅在 debug build 通过安全 logger 输出。

### C2. AppUpdateManifest 无反序列化后校验
- **File**: [AppUpdateManifest.kt:14](app/src/main/java/com/yy/writingwithai/core/update/AppUpdateManifest.kt#L14)
- **Category**: security
- **Detail**: `@Serializable data class AppUpdateManifest` 接受任意字段值：`apkSha256` 可为空串/非 hex、`apkUrl` 可为非 HTTPS、`versionCode` 可为 0/负数。被篡改的 manifest 可静默通过反序列化，在 ApkDownloader 深处才崩溃。
- **Fix**: 加 `init` 块校验：`apkSha256` 匹配 `Regex("^[0-9a-fA-F]{64}$")`、`apkUrl` 以 `https://` 开头、`versionCode > 0`、`versionName.isNotBlank()`。校验失败抛 `UpdateError.Parse`。

---

## HIGH (21)

### H1. Log.d 输出未脱敏的 rawDetail — 可能泄露 apikey/Bearer token
- **File**: [AnthropicCompatibleAdapter.kt:188](app/src/main/java/com/yy/writingwithai/core/ai/provider/AnthropicCompatibleAdapter.kt#L188)
- **Category**: security
- **Detail**: `Log.d("AnthropicAdapter", "POST $url → $code body=${rawDetail.take(200)}")` 使用 `rawDetail` 而非已脱敏的 `detail`。Provider 5xx 错误页可能回显 Authorization header。
- **Fix**: 改为 `detail.take(200)` 或 `sanitizeErrorDetail(rawDetail).take(200)`。

### H2. RESERVED_HEADERS 缺少 x-api-key — customHeaders 可覆盖 apikey
- **File**: [AnthropicCompatibleAdapter.kt:554](app/src/main/java/com/yy/writingwithai/core/ai/provider/AnthropicCompatibleAdapter.kt#L554)
- **Category**: security
- **Detail**: `RESERVED_HEADERS` 不含 `"x-api-key"`。OkHttp `header()` last-writer-wins，customHeaders 中的 `x-api-key` 会覆盖 AuthStyle.X_API_KEY 设置的真实 apikey。
- **Fix**: 将 `"x-api-key"` 加入 `RESERVED_HEADERS`。同时动态检查 `customAuthHeaderName` 冲突。

### H3. NoteRepository.delete() 不清理指向被删笔记的 note_links (dstNoteId)
- **File**: [NoteRepository.kt:196](app/src/main/java/com/yy/writingwithai/core/data/repo/NoteRepository.kt#L196)
- **Category**: correctness
- **Detail**: 删除笔记时清理了 attachments/tags/ai_history/note，但 note_links 中 `dstNoteId = 被删笔记` 的行未清理。因 Room FK 未启用(见 H4)，CASCADE 不生效，导致孤儿链接。
- **Fix**: 在 `db.withTransaction` 块中加 `noteLinkDao.deleteByDstNoteId(id)`。

### H4. Room 外键约束未启用 — CASCADE 静默失效
- **File**: [DataModule.kt:35](app/src/main/java/com/yy/writingwithai/core/data/di/DataModule.kt#L35)
- **Category**: correctness
- **Detail**: `Room.databaseBuilder()` 未调用 `setForeignKeyConstraintsEnabled(true)`。NoteLinkEntity/NoteAttachmentEntity/NoteEntityRow 的 `ForeignKey(CASCADE)` 全部被 SQLite 忽略。
- **Fix**: 在 builder callback 中 `db.setForeignKeyConstraintsEnabled(true)`。需验证现有数据无 FK 违规后再启用。

### H5. FeishuApiClientImpl Log.d 泄露 API 请求体
- **File**: [FeishuApiClientImpl.kt:133](app/src/main/java/com/yy/writingwithai/core/feishu/api/FeishuApiClientImpl.kt#L133)
- **Category**: security
- **Detail**: `Log.d("FeishuApi", "createBlock body: $body")` 在 main source (release 也会执行)输出完整请求体，含 image token。
- **Fix**: 移除或包裹 `BuildConfig.DEBUG`。

### H6. UserTokenProvider.postJson Log 泄露完整 raw 响应
- **File**: [UserTokenProvider.kt:210](app/src/main/java/com/yy/writingwithai/core/feishu/auth/UserTokenProvider.kt#L210)
- **Category**: security
- **Detail**: `Log.w(TAG, "postJson: feishu business error code=$code msg=$msg body=$raw")` 输出完整 raw body，可能含 access_token/refresh_token。
- **Fix**: 截断或脱敏 `raw`，至少 redact `access_token`/`refresh_token` 字段。

### H7. AuthInterceptor token 超时 fallback 发无 auth 请求触发 401 循环
- **File**: [AuthInterceptor.kt:87](app/src/main/java/com/yy/writingwithai/core/feishu/api/AuthInterceptor.kt#L87)
- **Category**: correctness
- **Detail**: `firstToken == null` 时构造无 Authorization 请求发给飞书 → 401 → 业务层重试 → 再次超时 → 空转 10s。代码注释说"fail-fast"但实际仍发请求。
- **Fix**: `firstToken == null` 时直接抛 `FeishuError.AuthExpired`，不发请求。

### H8. FeishuImportService.upsert 在事务外
- **File**: [FeishuImportService.kt:256](app/src/main/java/com/yy/writingwithai/core/feishu/sync/FeishuImportService.kt#L256)
- **Category**: correctness
- **Detail**: `noteRepository.upsert()` 在 `db.withTransaction` 块外调用，破坏原子性。
- **Fix**: 将 upsert 移入事务块。

### H9. WikilinkParser regex 不支持多管道 wikilink
- **File**: [WikilinkParser.kt:17](app/src/main/java/com/yy/writingwithai/core/note/wikilink/WikilinkParser.kt#L17)
- **Category**: correctness
- **Detail**: `[[A|B|C]]` 只匹配 `A|B` 为 display text，`C` 被丢弃。
- **Fix**: group2 改为贪婪匹配 `([^\[\]\n]+)`。

### H10. LlmEntityExtractor delete+upsert 不在事务中
- **File**: [LlmEntityExtractor.kt:101](app/src/main/java/com/yy/writingwithai/core/note/entity/LlmEntityExtractor.kt#L101)
- **Category**: correctness
- **Detail**: `deleteByNoteId` + `upsertAll` 不在事务中，进程杀死会丢失所有 entity 数据。
- **Fix**: 包裹在 `db.withTransaction { }` 中。

### H11. NoteEntityMatcher.matchAndPersist 不删除旧行
- **File**: [NoteEntityMatcher.kt:58](app/src/main/java/com/yy/writingwithai/core/note/entity/NoteEntityMatcher.kt#L58)
- **Category**: correctness
- **Detail**: upsert 前未 delete stale rows，编辑后的笔记保留旧 entity spans。
- **Fix**: upsert 前加 `deleteByNoteId`，包裹在事务中。

### H12. QuickNoteDetailScreen AI callback 选区偏移用 raw content 索引
- **File**: [QuickNoteDetailScreen.kt:947](app/src/main/java/com/yy/writingwithai/feature/quicknote/detail/QuickNoteDetailScreen.kt#L947)
- **Category**: correctness
- **Detail**: AI callback 的 selection offsets 使用 raw content 索引而非 annotated string 索引，entity highlight 导致偏移错位。
- **Fix**: 转换 annotated string offset 到 content offset。

### H13. isEntityAdded 比较 annotated-string offset 与 content-space offset
- **File**: [QuickNoteDetailScreen.kt:905](app/src/main/java/com/yy/writingwithai/feature/quicknote/detail/QuickNoteDetailScreen.kt#L905)
- **Category**: correctness
- **Detail**: 与 H12 同源问题，offset 空间不一致导致 entity 添加判断错误。
- **Fix**: 统一 offset 空间。

### H14. StreamingPanel delta 累积依赖 composition 期间写 MutableState
- **File**: [StreamingPanel.kt:130](app/src/main/java/com/yy/writingwithai/feature/aiwriting/streaming/StreamingPanel.kt#L130)
- **Category**: correctness
- **Detail**: delta 推送依赖 composition 期间写 MutableState 触发 snapshotFlow，脆弱且可能丢失 delta。
- **Fix**: 改用 `LaunchedEffect` key on `state.delta` 推送。

### H15. UpdateDownloadReceiver log 泄露 attacker-controlled apkName
- **File**: [UpdateDownloadReceiver.kt:101](app/src/main/java/com/yy/writingwithai/core/update/UpdateDownloadReceiver.kt#L101)
- **Category**: security
- **Detail**: `Log.w(TAG, "unsafe apkName: $apkName")` 输出 manifest 中的原始 apkName，可被服务端注入。
- **Fix**: 只 log "unsafe apkName, using fallback"，不输出原始值。

### H16. ApkDownloader require() 消息泄露完整 apkUrl
- **File**: [ApkDownloader.kt:34](app/src/main/java/com/yy/writingwithai/core/update/ApkDownloader.kt#L34)
- **Category**: security
- **Detail**: `require(apkUrl.startsWith("https://")) { "apkUrl must use HTTPS: $apkUrl" }` 将完整 URL 写入异常消息。
- **Fix**: 改为通用消息 `"apkUrl must use HTTPS"`。

### H17. grantUriPermission 只针对 com.android.packageinstaller
- **File**: [UpdateDownloadReceiver.kt:124](app/src/main/java/com/yy/writingwithai/core/update/UpdateDownloadReceiver.kt#L124)
- **Category**: security
- **Detail**: Samsung/Xiaomi 等厂商使用自定义 installer，URI permission 未授予导致安装失败。
- **Fix**: 增加已知 installer 包名：`com.google.android.packageinstaller`、`com.samsung.android.packageinstaller` 等。

### H18. UpdateManifestStore.put() 从未被调用 — download-manifest 关联断裂
- **File**: [ApkDownloader.kt:50](app/src/main/java/com/yy/writingwithai/core/update/ApkDownloader.kt#L50)
- **Category**: correctness
- **Detail**: `UpdateManifestStore.put(downloadId, manifest)` 从未调用，下载完成后无法通过 downloadId 找到 manifest。
- **Fix**: 在 `dm.enqueue(request)` 后调用 `manifestStore.put(downloadId, manifest)`。

### H19. WritingApp 使用私有 appScope 而非 Hilt 注入的 @ApplicationScope
- **File**: [WritingApp.kt:50](app/src/main/java/com/yy/writingwithai/app/WritingApp.kt#L50)
- **Category**: correctness
- **Detail**: `private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)` 绕过 Hilt 的 `@ApplicationScope`，导致两个独立 CoroutineScope 并存。
- **Fix**: 注入 `@ApplicationScope CoroutineScope` 替代私有 scope。

### H20. UpdateDownloadReceiver.onReceive 重 IO 无 goAsync() — ANR 风险
- **File**: [UpdateDownloadReceiver.kt:39](app/src/main/java/com/yy/writingwithai/core/update/UpdateDownloadReceiver.kt#L39)
- **Category**: correctness
- **Detail**: `onReceive()` 内做 DB 查询 + SHA-256 校验，无 `goAsync()`，超时会被系统杀进程。
- **Fix**: 开头 `val pendingResult = goAsync()`，所有退出路径 `pendingResult.finish()`。

### H21. COLUMN_URI 优先于 COLUMN_LOCAL_URI — SHA-256 校验用远程 URL
- **File**: [UpdateDownloadReceiver.kt:70](app/src/main/java/com/yy/writingwithai/core/update/UpdateDownloadReceiver.kt#L70)
- **Category**: correctness
- **Detail**: 优先取 `COLUMN_URI`(远程 URL)而非 `COLUMN_LOCAL_URI`(本地文件路径)做 SHA-256 校验，导致校验失败。
- **Fix**: 交换优先级：先检查 localIdx >= 0，uriIdx 作为 fallback。

---

## MEDIUM (73) — 按类别摘要

### Security (6)
| # | File | Summary |
|---|------|---------|
| M1 | ProviderConfig.kt:38 | RESERVED_AUTH_HEADERS 与 AnthropicCompatibleAdapter.RESERVED_HEADERS 不一致 |
| M2 | AnthropicCompatibleAdapter.kt:127 | sourceText 未用 SafePromptTemplate 包裹 |
| M3 | NoteAssociationPrompt.kt:22 | candidate note title/summary 未用 SafePromptTemplate 包裹 |
| M4 | AiModule.kt:32 | TLS 证书 pinning 未启用，apikey/prompt 明文传输 |
| M5 | ZipHelper.kt:75 | readZip() 无 zip bomb 防护 |
| M6 | UpdateDownloadReceiver.kt:123 | grantUriPermission 安装后未撤销 |

### Correctness (33)
| # | File | Summary |
|---|------|---------|
| M7 | AnthropicCompatibleAdapter.kt:62 | stream() 函数 ~257 行，远超 50 行 guideline |
| M8 | SseParser.kt:55 | 忽略 SSE `event:` 字段，Anthropic `message_stop` 等事件被丢弃 |
| M9 | AnthropicCompatibleAdapter.kt:232 | SSE error event (event:error) 被静默丢弃 |
| M10 | CoreAiGateway.kt:169 | outputBuilder 无大小上限，OOM 风险 |
| M11 | EntityDetailViewModel.kt:88 | 删除 entity 不清理 entity_aliases 孤儿行 |
| M12 | NoteExporter.kt:43 | 硬编码 appVersion "0.4.0" 而非 BuildConfig.VERSION_NAME |
| M13 | NoteImporter.kt:181 | REPORT_TS_FORMAT 缺 timezone |
| M14 | SearchHistoryStore.kt:14 | 非 DI 对象，Context 逐方法传入 |
| M15 | MarkdownToDocxConverterImpl.kt:178 | isFenceOpen 拒绝合法缩进 code fence |
| M16 | MarkdownToDocxConverterImpl.kt:195 | parseRuns 不处理重叠/嵌套 inline marker |
| M17 | MarkdownToXmlConverter.kt:118 | convertInline 误解析连续 bold/italic marker |
| M18 | FeishuDocService.kt:259 | getBlocks 解析逻辑重复，retry 覆盖不一致 |
| M19 | FeishuSyncService.kt:328 | pull() 返回硬编码中文字符串而非结构化结果 |
| M20 | FeishuImageDownloader.kt:142 | 图片下载无流大小上限，恶意服务器可无限写磁盘 |
| M21 | FeishuSyncService.kt:121 | TOCTOU race: remote revision check 与 updateDoc 无条件更新 |
| M22 | MarkdownToXmlConverter.kt:50 | 只处理 h1-h3，h4-h6 渲染为段落 |
| M23 | NoteEntityMatcher.kt:43 | 只匹配 surface form 首次出现，遗漏后续匹配 |
| M24 | EntityBacklinker.kt:72 | 手动 JSON 拼接，含引号/反斜杠的 entity key 产出非法 JSON |
| M25 | LocalNoteLinker.kt:123 | 手动 JSON 拼接，同 M24 |
| M26 | WikilinkIndexer.kt:21 | N 次单独 DAO 查询而非批量 resolveTitles |
| M27 | BackfillWorker.kt:27 | 加载全部 NoteEntity 到内存取 ID — OOM 风险 |
| M28 | LlmBackfillWorker.kt:27 | 同 M27 |
| M29 | LlmEntityExtractor.kt:106 | prompt 注入检测用简单子串匹配，易绕过 |
| M30 | LlmEntityExtractor.kt:169 | collectText 忽略 AiStreamEvent.Failed，LLM 失败未传播 |
| M31 | QuickNoteDetailScreen.kt:307 | uiSelection 在 noteId 变更时未重置 |
| M32 | WikilinkAutocomplete.kt:58 | 硬编码中文字符串而非 stringResource |
| M33 | QuickNoteDetailScreen.kt:923 | onAddEntity 内重复调用 buildEntityAnnotatedString |
| M34 | QuickNoteEditorViewModel.kt:166 | save() 无事务保证，onSaved 在 attachment commit 失败时仍触发 |
| M35 | QuickNoteDetailViewModel.kt:127 | delete() 在 NonCancellable 外调 onDeleted() |
| M36 | FolderImportViewModel.kt:36 | 硬编码中文错误字符串 |
| M37 | NoteAssociationSettingsViewModel.kt:88 | init 中同步调用 DataStore — 可能阻塞/读到旧值 |
| M38 | NoteAssociationSettingsViewModel.kt:124 | 同 M37 |
| M39 | EntityDetailViewModel.kt:64 | N+1 查询：逐 note 查 DB |

### Maintainability (25)
| # | File | Summary |
|---|------|---------|
| M40 | AnthropicCompatibleAdapter.kt:57 | DEFAULT_MAX_TOKENS=2048 因 encodeDefaults=false 从未发送 |
| M41 | FakeAiProvider.kt:19 | 在 main source 而非 test source，@Singleton/@Inject 可被误用 |
| M42 | AnthropicCompatibleAdapter.kt:352 | 多个 parse 函数无单测 |
| M43 | NoteAssociationPrompt.kt:9 | prompt 构造无单测 |
| M44 | NoteAttachmentDao.kt:68 | observeFirstImageForNotes public 但空 list 崩溃 |
| M45 | FeishuApiClientImpl.kt:228 | catch(Throwable) 包 OOM/StackOverflow 为 NetworkError |
| M46 | QuickNoteDetailViewModel.kt:171 | 5 个方法重复 FeishuError→SyncMessage catch 块 (~70 行) |
| M47 | AiActionViewModel.kt:201 | 死代码: `apikey ?: ""` 在 null check 后 |
| M48 | CopyText.kt:13 | copyToClipboard 在 AiwritingEntry 和 CopyText.kt 重复 |
| M49 | SettingsLanguageScreen.kt:67 | 硬编码英文 Toast 字符串 |
| M50 | AnimationDetailViewModel.kt:70 | isReduceMotionEnabled() 反射方法在两个 VM 重复 |
| M51 | FeishuAuthViewModel.kt:119 | catch(Throwable) 过宽，应 catch Exception |
| M52 | EntityManagementViewModel.kt:104 | search 无 debounce，快速输入触发 N 并发查询 |
| M53 | Shape.kt:13 | Shape.kt 与 CornerRadius.kt 重复值需手动同步 |
| M54 | QuickNoteWidgetUpdater.kt:26 | 1x4 widget 未刷新 |
| M55 | WidgetState.kt:17 | 3 个未使用字段 |
| M56 | ApkDownloader.kt:61 | DEFAULT_APK_NAME/SNIPPET_LEN 常量重复 |
| M57 | WidgetStateSerializer.kt:17 | 两套 Json 配置碎片化 |
| M58 | AppDropdownMenu.kt:213 | 硬编码英文 contentDescription |
| M59 | AppUpdateManifest.kt:20 | 4 个字段从未读取 |
| M60 | QuickNote1x1Widget.kt:47 | 硬编码 16.dp 而非 token 常量 |
| M61 | Theme.kt:21 | 使用已废弃 LocalLifecycleOwner |
| M62 | AppNav.kt:86 | 315 行 mega-composable |
| M63 | UpdateDownloadReceiver.kt:39 | onReceive() 67 行混合职责 |
| M64 | update/ | 无单测 |

### Pattern (5)
| # | File | Summary |
|---|------|---------|
| M65 | PromptTemplateStore.kt:50 | PromptTemplates 遗漏 SUMMARIZE/TRANSLATE |
| M66 | ConsentStore.kt:64 | 自建 CoroutineScope 而非注入 @ApplicationScope |
| M67 | FeishuDocService.kt:228 | xmlEscape 与 MarkdownToXmlConverter.escape 重复 |
| M68 | FeishuApiClientImpl.kt:211 | executeRequest 100 行 |
| M69 | SyncWorker.kt:27 | SyncWorker/SyncEngine 死代码未集成 |

### Performance (4)
| # | File | Summary |
|---|------|---------|
| M70 | LruBitmapCache.kt:32 | maxMemory/8 无上限，大堆设备可达 64MB+ |
| M71 | ImageCompressor.kt:42 | inSampleSize=1 时解码全分辨率 bitmap |
| M72 | QuickNoteDetailScreen.kt:136 | 1245 行 mega-composable 过大重组范围 |
| M73 | SettingsDataViewModel.kt:164 | 导入报告 zip 全量 ByteArray — 大导入 OOM |

---

## LOW (39)

略。主要包含：死代码(FeishuCommandPrompt、QuicknoteList route)、硬编码字符串、未使用变量、DataStore 同步调用风险、Widget 硬编码 dp 值、catch(Throwable) 过宽等。详见 Workflow 完整输出。

---

## 按模块分布

| 模块 | CRITICAL | HIGH | MEDIUM | LOW | Total |
|------|----------|------|--------|-----|-------|
| core/ai | 0 | 2 | 8 | 7 | 17 |
| core/data+prefs+security | 0 | 2 | 5 | 6 | 13 |
| core/feishu+sync | 0 | 3 | 10 | 9 | 22 |
| core/note+editor+media | 0 | 3 | 10 | 8 | 21 |
| feature/quicknote | 0 | 2 | 5 | 7 | 14 |
| feature/other | 0 | 0 | 6 | 5 | 11 |
| app+di+ui+widget+update | 2 | 9 | 29 | 7 | 47 |

---

## 修复优先级建议

1. **立即修** (CRITICAL + 安全 HIGH): C1, C2, H1, H2, H5, H6, H15, H16 — 所有 logcat 泄露 + 更新模块安全
2. **尽快修** (正确性 HIGH): H3, H4, H8, H10, H11, H18, H20, H21 — 事务边界 + 数据完整性
3. **本迭代修** (关键 MEDIUM): M5(zip bomb), M8/M9(SSE 解析), M10(OOM cap), M24/M25(JSON 拼接), M29(prompt 注入), M52(search debounce)
4. **后续迭代**: 其余 MEDIUM + LOW
