# Code Review: 全项目 r1(本次 session 全量扫描，2026-06-24)

**Reviewed**: 2026-06-24
**Branch**: main(本地未提交 5 个文件 + 1 个 untracked 归档目录)
**Scope**: app / core / di / feature / scripts 全量，5 个并行 reviewer 覆盖
**Decision**: **REQUEST CHANGES** — 8 CRITICAL + 1 lint-blocker + 大量 HIGH/MEDIUM

## Validation

| Check | Result | Detail |
|---|---|---|
| ktlintCheck | ✅ Pass | 主源 + 测试源均 0 violation |
| testDebugUnitTest | ✅ Pass | |
| testReleaseUnitTest | ✅ Pass | |
| compileReleaseKotlin | ✅ Pass | 6 warning(3 deprecate + 1 FK 缺索引 + 1 FlowPreview + 1 SharedFlow 注解) |
| lintDebug | ❌ FAIL | 3 errors + 14 warnings(129 baseline-filtered) |

**Lint 失败明细**:
1. `core/update/UpdateDownloadReceiver.kt:59` `getColumnIndex(DownloadManager.COLUMN_URI)` 返回 -1 时未做 Range 检查(Range lint) — **与下文 H1 同源**
2. `core/update/UpdateDownloadReceiver.kt:60` 同上，`COLUMN_LOCAL_URI`
3. `feature/settings/data/SettingsDataScreen.kt:79` `R.string.settings_data_save_failed` 不是合法 format string 却传 `String.format`(StringFormatInvalid)

不修这 3 个，CI `:app:check` 永远红。

## 本地未提交 diff(M1 之外)

```
M docs/progress.md
D openspec/changes/app-self-hosted-update/  (整个 change 目录删除)
?? openspec/changes/archive/2026-06-24-app-self-hosted-update/  (新归档目录)
```

不是代码改动，是 OpenSpec 归档动作(proposal/design/specs/tasks 搬到 archive)。Review 不覆盖。

## CRITICAL(8 项，需阻断)

#### C1 — `core/update/UpdateDownloadReceiver.kt:79` 安装 Intent filename 来自服务器 URL，可路径穿越

- 用 `uriStr.substringAfterLast('/')` 取 `DownloadManager.COLUMN_URI`(**服务器 http URL**，不是本地路径)拼到 `getExternalFilesDir(DOWNLOADS)/app-update/$filename`。
- 攻击者控制 manifest 的 `apkUrl` 路径段即可在 app 沙盒内写任意子路径。
- 修复:`val safe = manifest.apkName.takeIf { it.matches(Regex("^[A-Za-z0-9._-]+$")) } ?: "update.apk"`，不要从 URI 派生。

#### C2 — `core/feishu/auth/OAuthLauncher.kt:45` + `OAuthCodeReceiver.kt:29` OAuth 无 state / nonce，任意 code 可注入

- `OAuthLauncher` 启 `https://xiaozha.nananxue.cn/callback?code=...` 跳到宿主页 JS bridge,`OAuthCodeReceiver` 直接从 `intent.data` 读 `code` + `error`。
- 任何能打开浏览器到 `com.yy.writingwithai://feishu/callback?code=ATTACKER` 的恶意 app / 网页，都能塞任意 code 进 app 完成 token 交换。
- 修复:`OAuthLauncher` 生成随机 `state`(PKCE/UUID)塞进授权 URL,`OAuthCodeReceiver` 校验 `state` 与原值相等后再交换。

#### C3 — `core/data/export/ZipHelper.kt:32` `readZip` 信任 `entry.name`,Zip Slip 路径穿越

- 导入 zip 时直接 `File(targetDir, entry.name)`，恶意 zip 的 entry 名 `../../xxx` 逃出 targetDir。
- 修复:resolve → `canonicalPath` → 必须以 `targetDir.canonicalPath` 前缀开头，否则抛 `ImportRejected`。

#### C4 — `core/media/AttachmentStore.kt:21` `getAttachmentFile` 无 id 校验，沙盒内任意文件读取

- `noteId / attachmentId / extension` 直接拼到 `attachmentsDir/noteId/attachmentId.ext`，恶意 sync 客户端可传 `../<...>` 读到其他 app 文件。
- 修复:`val safe = Regex("^[A-Za-z0-9_-]{1,64}$")`，三段都校验;resolve 后 canonical 必须 inside attachmentsDir。

#### C5 — `core/sync/WebDavSyncEngine.kt:14,18` push/pull 无条件返回 `SyncResult.Failure`

- 整个 WebDavSyncEngine 是 stub，任何调用方都会以为远程写入失败但本地已写入 — 静默数据漂移。
- 修复:throw `NotImplementedError("B5b")` 或返回 typed `SyncResult.Unsupported`，强制 UI 显示"未启用"。

#### C6 — `core/note/impl/LlmNoteLinkExtractor.kt:57` 用户笔记内容直接拼进 LLM prompt，提示注入

- `src.content`(来自任意用户笔记、可能含"忽略上面所有指令"等恶意文本)直接 interpolate 到 system prompt。
- 修复:用 chat 角色 `user` 段或 fenced block(`<<<USER_NOTE>>>...<<<END>>>`)包住，system prompt 写明"只解析 fenced 内容"。

#### C7 — `core/note/impl/LlmNoteLinkExtractor.kt:70` 收集 `gateway.streamWritingOp` 无 token/字符上限

- 失控 / 循环 / 恶意的 LLM 输出会无限 append,OOM + 计费炸。
- 修复:`collect { if (sb.length > MAX_CHARS) throw TokenLimitExceeded; ... }`,MAX_CHARS = 16384。

#### C8 — `core/ai/di/AiModule.kt:32-33` `FakeAiProvider` 用 `@Singleton` 注册到 prod 的 `Map<String, AiProvider>`

- `ProviderPrefsStore.DEFAULT_PROVIDER_ID = "fake"`，新装 app 首次 AI 操作直接走 fake provider，所有"AI 扩写"看起来成功但其实是 mock 输出 — 真用户无法分辨。
- 修复:`@Provides` 加 `if (BuildConfig.DEBUG)` 分支，prod 注册器只放真实 provider;`DEFAULT_PROVIDER_ID` 改成 `null` 或首个真实 provider。

## HIGH(精选 25 项，按修复优先级)

#### H1 — `core/update/UpdateDownloadReceiver.kt:59,60` `cursor.getColumnIndex()` 返回 -1 直接 `getString/getLong` — 即 lint Range error 同源

- 修复:`val idx = cursor.getColumnIndexOrThrow(COLUMN_URI); if (idx < 0) return@cursor null`。

#### H2 — `core/update/ApkDownloader.kt:35` `manifest.apkUrl.substringAfterLast('/')` 当文件名，同 C1 镜像

- 修复:同 C1 safe-name 校验，或写死 `update.apk`。

#### H3 — `core/update/UpdateDownloadReceiver.kt:67` SHA-256 算 cursor URI 不是固定 File

- 恶意 app 注册 content provider 把 `COLUMN_LOCAL_URI` 指向它的字节，SHA 通过但装的是恶意 APK。
- 修复:anchor 到 `File(getExternalFilesDir(...)/app-update/<safeName>)`，算这个固定 File。

#### H4 — `core/update/UpdateDownloadReceiver.kt:39` `BroadcastReceiver.onReceive` 主线程跑 SHA-256,APK > 50MB ANR

- 修复:`goAsync()` + `Dispatchers.IO` 算 hash，完成后 `pendingResult.finish()`。

#### H5 — `core/feishu/api/AuthInterceptor.kt:32` `runBlocking` 等 token fetch 卡 OkHttp dispatcher

- 慢请求 + token refresh 同时来时同 `refreshMutex` 死锁风险。
- 修复:`withContext(Dispatchers.IO) { withTimeout(...) { mutex.withLock { ... } } }`。

#### H6 — `core/feishu/auth/UserTokenProvider.kt:43` `invalidated: @Volatile Boolean` 与 mutex 状态分裂

- 并发 `invalidate()` + `reentrantFetch()` 后 `invalidated=true` 永不重置，所有后续请求绕过缓存走网络。
- 修复:`invalidated` 放进 mutex 内 set/clear。

#### H7 — `core/feishu/auth/UserTokenProvider.kt:120` `expires_in` 解析失败 fallback 到 7000s，静默接受 2 小时 token

- 修复:解析失败 log + 保守 TTL(60s)强制下一次 refresh。

#### H8 — `core/feishu/auth/FeishuAuthStore.kt:126` `appSecret` 持久化到 EncryptedSharedPreferences，无 TTL，永远 at rest

- 注释自称"transient"，但 KEY_SECRET 从不主动清。
- 修复:存到 `KEY_SECRET_<requestId>`,exchange 成功后 clear;或内存 LRU cache。

#### H9 — `core/ai/provider/AnthropicCompatibleAdapter.kt:91-107` prompt 注入表面 + `systemPrompt` 用户可改

- 用户自定义 prompt 模板 + raw `sourceText` → 无校验送进 `system` 角色。
- 修复:role-marker 关键词 denylist + 长度 cap + JSON 转义验证。

#### H10 — `core/ai/provider/AnthropicCompatibleAdapter.kt:119` `response.body?.string()` 整 body 入内存，无 cap

- 多 GB body → OOM。
- 修复:`source().request(MAX_BODY)`，默认 1 MiB。

#### H11 — `core/ai/provider/AnthropicCompatibleAdapter.kt:53-177` SSE consume loop 无 `ensureActive()`,stall 时不可协作取消

- 修复:`currentCoroutineContext().ensureActive()` 每次 emit 后;或 `coroutineContext.isActive` + timeout。

#### H12 — `core/ai/provider/AnthropicCompatibleAdapter.kt:179-181` `.retry(1)` 对已 emit Delta 的流重放，UI 重复

- 修复:emit 过 Delta 后不 retry。

#### H13 — `core/ai/provider/AnthropicCompatibleAdapter.kt:204-206` `customHeaders` 无 header-name 校验，可覆盖 `Host/Authorization/Content-Length`

- 修复:`Headers.checkName(name)` + 拒绝 reserved header。

#### H14 — `core/sync/SyncWorker.kt:11,15` 没 Constraints / 没 BackoffPolicy / `doWork` 直接 `Result.success()`(stub)

- 修复:`NetworkType.CONNECTED` + `ExponentialBackoff` + `runAttemptCount <= 3`。

#### H15 — `core/feishu/sync/FeishuSyncService.kt:68,88` pull 网络读 doc 后 DAO upsert 无 `@Transaction`,crash 中段留孤儿 note

- 修复:`@Transaction` DAO 方法包住 note + refDao 双写。

#### H16 — `core/note/backfill/BackfillScheduler.kt:34,54` 先 `prefs.putBoolean(FLAG, true)` 再 `enqueueUniqueWork`,enqueue 失败时 flag 已写任务没排

- 修复:flag 写入移到 Worker `doWork` 成功回调里;或 enqueue result 校验。

#### H17 — `core/note/backfill/BackfillScheduler.kt:46` `cancelEntityBackfill()` 用 `cancelAllWorkByTag(ENTITY_BACKFILL_TAG)` 但 enqueue 时没加这个 tag → cancel no-op

- 修复:`OneTimeWorkRequestBuilder<...>().addTag(ENTITY_BACKFILL_TAG).build()`。

#### H18 — `core/note/impl/LlmNoteLinkExtractor.kt:109` / `LlmEntityExtractor.kt:74` 所有失败 catch 后静默返回 0/[]

- 计费 / 速率限制指标全错。
- 修复:log + increment 失败计数器 + emit 到 telemetry 通道。

#### H19 — `core/media/ImageCompressor.kt:35,37` `decodeFile` 没 EXIF orientation + 中间全尺寸 bitmap → 旋转错 + OOM

- 修复:`ExifInterface.getAttributeInt(ORIENTATION_TAG)` → `Matrix` 旋转;`inSampleSize` 按目标维度算;`inPreferredConfig = RGB_565`(无 alpha 时)。

#### H20 — `core/note/entity/LlmEntityExtractor.kt:86` `parseJsonEntities` 取首 `[` 末 `]`，中间所有内容当 JSON 解析 → 垃圾容忍

- 修复:`JsonArray` 流式解析或 bracket-match 严格验证。

#### H21 — `feature/quicknote/edit/QuickNoteEditorScreen.kt:104-114` wikilink 自动补全 `lastOpen` 在非 composable body 重算，onSelect 用 captured 值 → state.content 变化后索引脱同步，保存时 corrupt content

- 修复:`lastOpen` 在 recompute 时刻 snap,onSelect 时从 *当前* content 重新定位 prefix。

#### H22 — `feature/settings/model/CustomProviderEditViewModel.kt:177-200` `pingFromForm` 把原始 apikey 通过 `e.message` 渲染进 Toast → URL 含 apikey 时直接漏给用户

- 修复:`String.format` 时过滤 query string + 把 apikey redact 成 `***`。

#### H23 — `app/src/main/java/.../WritingApp.kt:52` `runBlocking` 在 `Application.onCreate` 主线程等 DataStore IO，冷启动阻塞几百 ms

- 修复:`CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { ... }`。

#### H24 — `feature/aiwriting/AiwritingEntry.kt:34-36` `noteId` 形参被 `hiltViewModel()` 忽略，VM 实际取 SavedStateHandle，多 note 共享 lastNoteId

- 修复:要么 `extras` 传 `noteId` 进 `SavedStateHandle`，要么删掉这个参数。

#### H25 — `feature/quicknote/detail/QuickNoteDetailViewModel.kt:137-144` `getRef` 一次性 get，冲突状态变化不刷新 chip

- 修复:`refDao.observeForNote(id)` collect 到 StateFlow。

## MEDIUM(精选 10 项)

- `feature/settings/data/SettingsDataViewModel.kt:113,148` `catch (e: Exception)` 吞 `CancellationException` → 结构化并发违例
- `app/build.gradle.kts:50` release signing 在 `keyAlias` 缺失时静默 unsigned,CI 缺警告
- `core/ai/provider/CustomProviderStore.kt:75-83` save→encode 丢失未知字段，DataStore 并发 edit 安全但 round-trip 不稳定
- `core/prefs/SecureApiKeyStore.kt:85` 长寿命 `scope` 永不清，reveal 计时器 `delay` 泄漏
- `feature/quicknote/edit/WikilinkAutocomplete.kt:42-44` LIKE `%...%` 全表扫，无 FTS 索引
- `feature/quicknote/list/QuickNoteListViewModel.kt:96` `feishuRef` 主线程取 Room + 网络链
- `app/src/main/AndroidManifest.xml:84` OAuth `https://xiaozha.nananxue.cn/...` 回调无 `<intent-filter>`，只能靠 custom scheme 兜底
- `scripts/release-server/publish-release.sh:42-55` `scp`/`ssh` 未 quote 变量 + 无 `StrictHostKeyChecking=yes` + `CODE`/`NOTES`/`APK` 无正则校验 → shell 注入 + TOFU
- `feature/settings/data/SettingsDataScreen.kt:79` `settings_data_save_failed` 不是合法 format string 却 `String.format` — 即 lint 报的第三项 error
- `feature/onboarding/ApikeyPromptViewModel.kt:43-57` `onAck` 和 `onSkip` 实现完全相同，文档未说明意图

## LOW(精选 8 项)

- `core/prefs/SearchHistoryStore.kt:26` 用户搜索历史写入无长度 cap，大文本可膨胀 DataStore
- `feature/quicknote/list/NoteRow.kt:151` `DateFormat.getDateTimeInstance` 每行重 alloc，长列表浪费 GC
- `app/src/main/res/xml/widget_info_1x1.xml:4` min 40×40dp，低于 Pixel/Samsung launcher 阈值
- `gradle/libs.versions.toml:7` compose-bom 2024.10.01 落后 20 个月
- `feature/my/AboutScreen.kt:127` Snackbar 文案硬编码中文，其他屏幕走 strings.xml
- `feature/quicknote/detail/QuickNoteDetailScreen.kt:294-307` 导出 filename 用 `note.title`,SAF 时未 trim 长度 / 路径分隔符
- `core/note/wikilink/WikilinkParser.kt:5` wikilink 不处理 `|` alias 语法，`[[Note|Alias]]` 解析失败
- `feature/my/MeTabTarget.kt:15` Kdoc 说 "Settings hub"，实际只有 AI 开关 — 文档漂移

## KSP / 编译警告(非 blocker，但建议顺手修)

- `core/data/db/entity/NoteAttachmentEntity.kt:22` `noteId` 外键列无索引 — 全表扫风险
- `core/data/repo/NoteRepository.kt:66` FlowPreview API 未 `@OptIn`
- 3 处 `Modifier.menuAnchor()` deprecation → 新 API `MenuAnchorType` 重载
- `feature/aiwriting/action/ActionSheet.kt:101` `Icons.Filled.ShortText` 已 deprecate → `Icons.AutoMirrored.Filled.ShortText`

## 修复优先级建议

1. **0-day**(必须先修，否则 CI 红 + 安全洞):
   - C1 / C2 / C3 / C4 / C8
   - lint 3 error(H1 + SettingsDataScreen.kt:79)
2. **本 milestone 内**:
   - C5 / C6 / C7 + H5–H8 / H14–H18(Hilt scope + WebDav stub + LLM 注入手)
3. **下个 milestone**:
   - H19–H25(UX / Editor race / VM error event)+ MEDIUM 全部
4. **清理 backlog**:
   - LOW + KSP warning + 6 个编译 deprecation
   - `app/lint-baseline.xml` 129 whitelisted 需重新审计(尤其 `ConstantLocale` QuickNoteDetailViewModel.kt:240、`StringFormatCount` values-en/values 错配)

## 备注

- `di/` 目录只有 `.gitkeep`，实际 Hilt module 在 `core/<feature>/di/`，符合 CLAUDE.md §"包结构" — 不需动。
- ktlint 全绿，说明项目代码风格已收敛(对比 2026-06-20 r2 的 580 violation，大幅改善)。
- 单测全绿，但本次 reviewer 多次提到 `runBlocking { DataStore }` / `runCatching { swallow }` 这类 silent-failure 模式 — 单测覆盖这种 anti-pattern 时需要 assert side-effect。
- prompt 注入面(C6 + H9 + H12)三处独立出现，建议抽一个 `core/ai/prompt/SafePromptTemplate.kt` 集中处理。