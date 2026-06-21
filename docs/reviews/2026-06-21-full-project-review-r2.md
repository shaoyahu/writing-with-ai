# 全项目 review r2 — 2026-06-21

> 接同日 r1(`2026-06-21-full-project-review-r1.md`)。本轮聚焦:
> 1. **uncommitted diff**(22 文件,+719/-141)
> 2. **3 个 active OpenSpec change**(`note-association` / `model-management-detail-dropdown` / `widget-rome-compat`)
> 3. **r1 fix 验证 + 跨方向 regression**
>
> 3 个并行 reviewer:correctness(agent #aa39e9d2a422d77ac)+ security(agent #a27cc3b107f0cd2b1)+ architecture(agent #aa484b964457ca6c4)。主线综合去重 + 排序。

## 决策

**REQUEST CHANGES** — 9 项 commit-block(置信度 ≥80%)。当前 uncommitted diff **不建议** 直接 commit;先按本 review 的 HIGH 段修完,再走常规 review 流程。

---

## HIGH(commit-block,9)

### H1 · `CoreAiGateway.streamWritingOp` 主线程 `runBlocking` [confidence 95]
**位置**:`app/src/main/java/com/yy/writingwithai/core/ai/CoreAiGateway.kt:114-117`

```kotlin
val apiFormatOverride = kotlinx.coroutines.runBlocking {
    runCatching { providerPrefsStore.getApiFormat(providerId) }.getOrNull()
}
```

`streamWritingOp` 是 streaming flow 入口;`runBlocking` 阻塞**调用方 dispatcher**——生产由 `viewModelScope` 收集,= 阻塞 `Dispatchers.Main.immediate`,DataStore 冷启可达 200ms+ → **ANR**。

r1 提到的 `resolveProviderFlow`(L167-178) `runBlocking` 是"兜底"角色;本轮 `model-management-detail-dropdown` 把它**提升为常规路径**,等于把 ANR 风险从兜底扩散成日常。

**修**:`streamWritingOp` 加 `suspend` + 在顶部 `providerPrefsStore.getApiFormat(providerId).await()`(flow builder 之外)。`ping` 已是 suspend,同样在 await 后构造 `AiRequest`。

### H2 · `LlmNoteLinkExtractor` SQL `LIKE` 不转义反斜杠 [confidence 90]
**位置**:`app/src/main/java/com/yy/writingwithai/core/note/impl/LlmNoteLinkExtractor.kt:50-52`

```kotlin
val query = sanitize(src.content).take(50)
val q = "%$query%"
val candidates = noteDao.search(q).first().filter { it.id != noteId }
```

DAO 用 `LIKE :q ESCAPE '\\'`(Repository 主路径 M1 r1 H4 修过),调用方拼 `q` 时**未转义** `%` / `_` / `\`。**M1 r1 H4 同类 regression 出现在 LLM 路径**。

例:用户笔记含 `100\off` → `q = "%100\off%"` → SQLite `\o` 视为转义 → 错误匹配 `100*off`。

**修**:
```kotlin
val query = sanitize(src.content).take(50)
    .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
val q = "%$query%"
```

### H3 · `CoreAiGateway.onCompletion` 持久化 `sourceText` + `error.detail` 不脱敏 [confidence 90]
**位置**:`app/src/main/java/com/yy/writingwithai/core/ai/CoreAiGateway.kt:135-160`

```kotlin
historyRepo.get().record(
    ...
    inputSnapshot = sourceText,              // ← 用户原文,可能含 apikey / PII
    outputSnapshot = outputBuilder.toString(),
    error = errorMsg                          // ← raw provider response body
)
```

`sourceText` 是用户原文(可能粘贴过 apikey);`error` 来自 `AnthropicCompatibleAdapter.kt:118` `response.body?.string()`(provider 5xx 经常回显 `Authorization` header)。

**违反 CLAUDE.md "AI 集成约定" 规则:apikey 不进 Room**。

**修**:
- `sourceText.replace(Regex("sk-[A-Za-z0-9_-]{16,}"), "sk-***")` 之类脱敏 pass
- `errorMsg.take(200)` + apikey pattern 脱敏
- `AiHistoryRepository.record` 入库前集中脱敏(单一 source of truth,避免 gateway 与 extractor 各自实现漂移)

### H4 · `QuickNoteDetailScreen` 直接 import 5 个 `feature/aiwriting` 内部类 [confidence 85]
**位置**:`app/src/main/java/com/yy/writingwithai/feature/quicknote/detail/QuickNoteDetailScreen.kt:65-70`

```kotlin
import com.yy.writingwithai.feature.aiwriting.action.ActionSheet
import com.yy.writingwithai.feature.aiwriting.action.copyToClipboard
import com.yy.writingwithai.feature.aiwriting.streaming.AiActionUiState
import com.yy.writingwithai.feature.aiwriting.streaming.AiActionViewModel
import com.yy.writingwithai.feature.aiwriting.streaming.StreamingPanel
```

CLAUDE.md "包结构" 硬规则:**跨 feature 引用走 `feature/<name>/<Feature>Entry.kt`**。当前 `AiwritingEntry` 只暴露 `rememberAiActionViewModel` + `requestConsent` + `AiActionFabState`,屏幕绕开 Entry 直接 import 5 个 internals。

**修**:扩 `AiwritingEntry` 暴露 `ActionSheetRoute(...)` / `StreamingPanelRoute(state, callbacks)` / re-export `AiActionUiState`。`feature/quicknote` 只知 `AiwritingEntry`。

### H5 · `CompositeNoteLinker` 反向依赖 `feature/settings.NoteAssociationSettings` [confidence 90]
**位置**:`app/src/main/java/com/yy/writingwithai/core/note/impl/CompositeNoteLinker.kt:9`

```kotlin
import com.yy.writingwithai.feature.settings.NoteAssociationSettings
```

CLAUDE.md "包结构" 硬规则:**`core/` 不依赖 `feature/*`**——删除/移动 feature 不应影响 core。当前 `core/note/` 依赖 `feature/settings/`,**删 `feature/settings` 直接编译失败**。

**修**:把 `NoteAssociationSettings` 移到 `core/prefs/NoteAssociationSettingsStore`(DataStore 已存在);`core/note/impl/CompositeNoteLinker` 依赖新 core store;`feature/settings/NoteAssociationSettingsScreen` 通过新 store 读写,UI 层零行为变化。

### H6 · `AiActionViewModel.acceptReplace` `String.replace` 静默 no-op [confidence 85]
**位置**:`app/src/main/java/com/yy/writingwithai/feature/aiwriting/streaming/AiActionViewModel.kt:188`

```kotlin
val updatedContent = originalContent.replace(sourceText, aiText)
```

`String.replace(oldValue, newValue)` 三种静默失败:
1. `originalContent` 不含 `sourceText`(AI 流期间用户编辑)→ 返回原 content,**状态仍走 Replaced**
2. `sourceText` 多次出现 → 只换第一个(可能不是用户想要的区段)
3. (Kotlin 不是 regex,这条不适用)

**AI replace 是核心写操作,无反馈 = 用户以为已替换但内容没动**。

**修**:
- `indexOf(sourceText)` 检查,缺失 → emit `Failed(..., "原文已被修改,请重新生成")`,不落库
- 多次出现 → emit `Failed(..., "原文有多处匹配,请手动选择")`
- 终极方案:走 selection 范围(用户实际选中的起止 offset),不走全文 replace

### H7 · `AiActionViewModel.acceptReplace` `delay(150)` hack + `tryEmit` 强刷 [confidence 90]
**位置**:`app/src/main/java/com/yy/writingwithai/feature/aiwriting/streaming/AiActionViewModel.kt:195-199`

```kotlin
// 等 Room invalidation 传播后 push 刷新事件
kotlinx.coroutines.delay(150)
val emitted = noteRepository.noteUpdateEvents.tryEmit(noteId)
```

M1 r1 H1 同款 anti-pattern(等 Room invalidation 用硬编码 delay)— 已修过"用 `withContext(NonCancellable)` 包 upsert + emit"的 idiom,**本轮复发**。

`150ms` 是 magic number;Room invalidation 不保证 150ms 内到,慢设备 / 主线程忙时 race;快设备 150ms 内 detail VM 主路径已收到 → emit 重复触发强刷 → UI 双重组。

**修**:删 `delay(150)`。`NonCancellable { upsert; widgetUpdater.updateAll; }` 退栈时 Room invalidation 已传播,主路径 Flow 自然收到——`noteUpdateEvents` + `tryEmit` 强刷逻辑**全删**,single source of truth 交给 Room Flow。

### H8 · `QuickNoteDetailViewModel` 双 `viewModelScope.launch` 写同一 `_uiState` race [confidence 90]
**位置**:`app/src/main/java/com/yy/writingwithai/feature/quicknote/detail/QuickNoteDetailViewModel.kt:46-84`

```kotlin
viewModelScope.launch { repository.observeNoteWithTags(noteId).collect { ... _uiState.value = ... } }
viewModelScope.launch { repository.noteUpdateEvents.collect { updatedId -> ... delay(100); _uiState.value = ... } }
```

两个并行 launch 写同一个 `MutableStateFlow`。race 缓解靠 `delay(100)` magic number + 偷旧 tags——顺序错就丢 tags 或覆盖未应用编辑。`MutableStateFlow.value` 不是原子的"读-计算-写",两个协程交错是真实 race。

**修**:
- 单一 source of truth:`observeNoteWithTags(noteId)` + `noteUpdateEvents` 走 `merge` / `combine` 进同一 collector
- 配合 H7:删除 `noteUpdateEvents` 路径,只让 Room Flow 做 single source of truth
- 若保留 push,改用 `_uiState.update { current -> if (current is Content) current.copy(...) else current }` CAS

### H9 · `CustomProviderEditViewModel.pingFromForm` 明文 apikey 在 VM state + 绕过 gateway history [confidence 85]
**位置**:`app/src/main/java/com/yy/writingwithai/feature/settings/model/CustomProviderEditViewModel.kt:179-186`

```kotlin
fun pingFromForm(state: ...) {
    val credentials = AiCredentials(s.apiKey)   // ← VM state 里的明文 apikey
    provider.stream(AiRequest(...), credentials).collect { ... }
}
```

两个问题:
1. **明文 apikey 驻留 VM state**(`pingFromForm` + 其他读取路径),直到 VM 销毁——SecureApiKeyStore 的 5s reveal timer 失效,apikey 永远在内存。
2. **绕过 `CoreAiGateway.streamWritingOp`** → 跳过 `onCompletion { historyRepo.get().record(...) }`,ping 不进 `ai_history`,**CLAUDE.md "Token / 成本可观测" 规则被破坏**——所有 ping 不可见。

**修**:
- `pingFromForm` 改为 read apikey 时**立刻**调用,不在 VM state 保留;VM 只持有 form 字段,apikey 在 SecureApiKeyStore 临时取
- 路径走 `CoreAiGateway.ping()`(已存在 `CoreAiGateway.kt:180`),保留 history 记录

---

## MEDIUM(commit-block 之外,16)

> 不阻断当前 diff commit。建议另开 `polish-2026-06-21-r2` change 集中清。

### M1 · 7+2 处 `android.util.Log.*` 无 `BuildConfig.DEBUG` gate [75]
**位置**:
- `feature/aiwriting/streaming/AiActionViewModel.kt:169, 171, 175, 198, 199, 204`
- `feature/quicknote/detail/QuickNoteDetailViewModel.kt:49, 70`

`Log.d("AiVM", "acceptReplace called")` / `Log.d("DetailVM", "Room emit content.first=...")` 在 release 包照样打,泄露 `noteId`(UUID 但可关联)和 `e.message`(可能含 provider URL / stacktrace)。r1 C6 同款**本轮复发**。

**修**:`if (BuildConfig.DEBUG) Log.d(...)` 全部 gate;或抽 `core/common/Logger.kt { fun d(tag: String, msg: String) { if (BuildConfig.DEBUG) android.util.Log.d(tag, msg) } }`。

### M2 · 4 处 `catch (e: Exception)` 吞 `CancellationException` [70]
**位置**:
- `feature/quicknote/detail/RelatedNotesSection.kt:116`(F-07)
- `core/note/impl/LlmNoteLinkExtractor.kt:108`(F-08)
- `feature/settings/model/ModelManagementViewModel.kt:183, 201`(F-09 / F-10)
- `feature/aiwriting/streaming/AiActionViewModel.kt:203`(已有 CancellationException 重抛,但 `undo` / `regenerate` 无 try/catch,F-06)

**修**:suspend 函数 + viewModelScope.launch 内,`catch (e: Exception)` 前必须先 `if (e is CancellationException) throw e`。

### M3 · `AnthropicCompatibleAdapter` error mapping 灌 raw provider body [60]
**位置**:`app/src/main/java/com/yy/writingwithai/core/ai/provider/AnthropicCompatibleAdapter.kt:115-133`

```kotlin
val detail = response.body?.string() ?: ""
...
emit(AiStreamEvent.Failed(AiError.Auth(code, detail), ...))
```

provider 502/504 返回 KB 级 HTML 错误页(可能含 `Authorization` header 回显),这里:
1. 拼进 `AiError.detail` → UI `toDisplayMessage` 原样显示
2. 走 `CoreAiGateway.onCompletion` 持久化到 `ai_history.error`(配合 H3)

**修**:`detail.take(200)` + apikey pattern 脱敏;HTML 页面检测(`<html>` / `<body>`)直接换成"上游服务错误"。

### M4 · SSE parser 无 per-event 长度上限 → OOM [55]
**位置**:`app/src/main/java/com/yy/writingwithai/core/ai/stream/SseParser.kt:18-19`

`while (!source.exhausted())` + `dataBuffer` 无上限。恶意 / 误配置 provider 可发 multi-GB 单事件 → OOM crash。

**修**:bound `dataBuffer` 到 1MB;超限 → emit `Failed(Network(-1, "SSE event too large"))`。

### M5 · `LlmNoteLinkExtractor` 手动拼 `evidence` JSON(只 escape 引号) [60]
**位置**:`app/src/main/java/com/yy/writingwithai/core/note/impl/LlmNoteLinkExtractor.kt:97-99`

```kotlin
val evidence = "{\"reason\":\"${l.reason.replace("\"", "\\\"")}\",\"lastLlmExtractAt\":$now}"
```

只 escape 引号。`reason` 含 `\n` / `\` / 控制字符 → JSON 破坏;未来 evidence 回灌 LLM prompt 还是 injection 入口。

**修**:`@Serializable data class Evidence(val reason: String, val lastLlmExtractAt: Long)` + `Json.encodeToString(Evidence(...))`。

### M6 · `LlmNoteLinkExtractor` 用 `SharedPreferences` 而非 `DataStore` [55]
**位置**:`core/note/impl/LlmNoteLinkExtractor.kt:37`

```kotlin
private val ratePrefs = context.getSharedPreferences(PREFS_RATE, Context.MODE_PRIVATE)
```

项目统一用 DataStore(`ProviderPrefsStore` / `SecureApiKeyStore` / `CustomProviderStore` / `ConsentStore` / `PromptTemplateStore` 全 DataStore);LLM 速率限频一个独苗 SharedPreferences,**违反统一约定**。

**修**:挪到 DataStore (`note_assoc_rate_prefs`)+ `setLastLlmExtractAt(noteId, epochMs)` + `getLastLlmExtractAt(noteId)`。

### M7 · `LlmNoteLinkExtractor` 与 `CoreAiGateway` 双重 history 记录 [55]
**位置**:`core/note/impl/LlmNoteLinkExtractor.kt:104-113` + `core/ai/CoreAiGateway.kt:144-160`

`LlmNoteLinkExtractor.extractAndPersist` 自己 record 一次 history;同一 `gateway.streamWritingOp` 调用在 `CoreAiGateway.onCompletion` 又 record 一次——**同一次 AI 调用两条 ai_history 记录**,统计 / 成本翻倍。

**修**:删 `LlmNoteLinkExtractor.recordHistory(...)`,只让 `CoreAiGateway.onCompletion` 统一记录(`op = "note-association-extract"` 通过 `AiRequest.op` 传)。

### M8 · `AiActionViewModel` 8 依赖 + 直接 mutate widget + persist note [60]
**位置**:`app/src/main/java/com/yy/writingwithai/feature/aiwriting/streaming/AiActionViewModel.kt:53-64`

VM 直接 inject `QuickNoteWidgetUpdater`(`core/widget/`) + `NoteRepository`(`core/data/repo/`)+ 直接 `widgetUpdater.updateAll(context)` + `noteRepository.upsert/...`。**`feature/aiwriting` 不再 self-contained**——删 `feature/aiwriting` 会留下 orphan widget 刷新和 note 持久化。

**修**:抽 `AiActionApplier`(放 `core/ai/api/` 或 `core/data/`)封装 "accept AI output → upsert note → refresh widget → emit noteUpdateEvents",VM 只调一个入口。

### M9 · `AiActionFabState` 跨 feature 共享 [55]
**位置**:`feature/quicknote/detail/QuickNoteDetailViewModel.kt:9` import `feature.aiwriting.AiActionFabState`

`AiActionFabState` 是 `Selection → FAB enum` 纯投影,无 aiwriting 业务逻辑。被 `feature/quicknote` 直接 import。

**修**:挪 `core/ui/AiActionFabState.kt`,两个 feature 都引 core。

### M10 · `ApiFormat` 应在 `core/ai/api/` 而非 `core/ai/provider/` [50]
**位置**:`core/ai/provider/ApiFormat.kt`(被 `feature/settings/model/ModelManagementViewModel.kt` / `ModelProviderDetailScreen.kt` import)

`ApiFormat` 是抽象层概念(provider 用哪条 SSE 路径),放 `provider/` 子包导致 feature 层引用 `core/ai/provider/` 的内部 enum,跟 `core/ai/api/` 的 `AiRequest.apiFormatOverride` 形成跨包耦合。

**修**:挪到 `core/ai/api/ApiFormat.kt`,`provider/` 引用 api。

### M11 · `ModelManagementViewModel` 硬编码 3 provider config [55]
**位置**:`app/src/main/java/com/yy/writingwithai/feature/settings/model/ModelManagementViewModel.kt:105-107, 262`

```kotlin
when (providerId) {
    "deepseek" -> DeepseekConfig.config
    "minimax" -> MinimaxConfig.config
    "mimo" -> MimoConfig.config
}
```

加第 4 个内置 provider 必须改 VM。`effectiveModel` fallback 也写死 `DeepseekConfig.config.defaultModel`。

**修**:`core/ai/provider/BuiltinProviderRegistry`(interface in `core/ai/api/`):`fun byId(id: String): ProviderConfig?` + `fun all(): List<ProviderConfig>`;Hilt multibindings 注入。

### M12 · `ProviderPrefsStore.getApiFormat` 旧 enum 残留静默清除 [55]
**位置**:`app/src/main/java/com/yy/writingwithai/core/ai/provider/ProviderPrefsStore.kt:87-100`

```kotlin
.map { it[apiFormatKey(providerId)]?.let { name -> runCatching { ApiFormat.valueOf(name) }.getOrNull() } }
```

旧版本存 `"openai_compatible"` 字符串 → 升级到新 enum 后 `valueOf` 抛 → `getOrNull()` 吞掉 → **用户偏好静默丢失**(回退到 ProviderConfig 默认)。

**修**:解析失败时打 `Log.w(TAG, "Unknown apiFormat name=$name, resetting")`,不静默;UI 提示用户"协议覆盖已重置"。

### M13 · `AiActionViewModel.resolveProviderId()` dead code [70]
**位置**:`feature/aiwriting/streaming/AiActionViewModel.kt:162-166`

`start()` 直接 inline `providerPrefsStore.getSelectedProviderId()` + `secureApiKeyStore.get(providerId)` + apikey gate(L109-114),`resolveProviderId()` 整个函数未被调用。r1 已建议删除,本轮未删。

**修**:删函数,或统一改用函数(从 inline 改函数调用,避免逻辑散落)。

### M14 · `AiActionViewModel.undo()` 无 try/catch [65]
**位置**:`feature/aiwriting/streaming/AiActionViewModel.kt:213-229`

`undo` 跟 `acceptReplace` 同样 `withContext(NonCancellable) { noteRepository.upsert ... }`,但无 `try/catch`——`upsert` 抛异常(磁盘满 / Room migration)→ coroutine 崩溃,UI 永远卡 `Replaced` 态无法撤回。

**修**:同样 catch + emit `Failed`(或单独 `UndoFailed` 子态)。

### M15 · `ModelManagementViewModel.onModelSelected` / `onApiFormatSelected` 静默 catch [60]
**位置**:`feature/settings/model/ModelManagementViewModel.kt:183-211`

写 prefs 的 suspend 函数包在 `try { ... } catch (_: Exception) {}`,用户切模型 / 协议 UI 无反馈。"用户切了但没生效" UX 困惑。

**修**:catch 后 emit SaveResult.Failed(message);UI 已有的 SnackbarHost 接住。

### M16 · `RelatedNotesSection` `rememberCoroutineScope` + 用户离开 → Loading 卡死 [65]
**位置**:`feature/quicknote/detail/RelatedNotesSection.kt:113-114`

`rememberCoroutineScope().launch { ... }` 在 Composable 内启动 suspend;用户离开屏幕 → scope 取消,但 `noteLinker.extract(...)` 仍在 IO 执行(因为不传 Dispatchers);返回时 UI 已 dispose → 拿到结果时 `MutableStateFlow` 设值无 observer → 等下次进入才"瞬间显示"。视觉表现:Loading 转圈永远不结束。

**修**:用 `viewModel.xxx()` 调,所有 suspend 走 viewModelScope;或在 DisposableEffect 内 cancel。

---

## LOW(12)

| # | 位置 | 问题 |
|---|---|---|
| L1 | `app/src/main/AndroidManifest.xml` | `usesCleartextTraffic` 未显式 false(targetSdk=35 默认 false 但显式更稳) |
| L2 | `app/src/main/AndroidManifest.xml` + `res/xml/{backup_rules,data_extraction_rules}.xml` | `allowBackup="false"` 已关 Auto Backup,但 `fullBackupContent` / `dataExtractionRules` 仍引用 xml 文件——dead config,易让后来者误以为 backup 已配置 |
| L3 | `core/prefs/SecureApiKeyStore.kt:78` | `Log.w(TAG, "EncryptedSharedPreferences init failed: ${e.javaClass.simpleName}: ${e.message}")` — Google KeyStore 异常 message 一般不含 alias,但脱敏 pass 仍推荐 |
| L4 | `core/ai/stream/SseParser.kt:32-40` | `line.startsWith("data:")` 大小写敏感,RFC 允许大小写;`startsWith("data:", ignoreCase = true)` |
| L5 | `core/ai/provider/AnthropicCompatibleAdapter.kt:225, 265` | `parseUsage` 失败静默,无 debug log;misbehaving provider 不可见 |
| L6 | `core/ai/provider/AnthropicCompatibleAdapter.kt:189-200` | `customHeaders` 后写后赢(OkHttp `header()` 行为),用户自定义 provider 加 `Authorization` 会覆盖默认 `Bearer` 头 |
| L7 | `core/ai/provider/ProviderPrefsStore.kt:107-114` | `selectedModelKey(providerId)` / `apiFormatKey(providerId)` companion factory 暴露,任意字符串可拼 key |
| L8 | `app/src/main/java/com/yy/writingwithai/app/AppNav.kt:101-120` | widget 回放走 string prefix `pending.startsWith("quicknote/edit")` 而非 type-safe route(`QuicknoteEdit` / `QuicknoteDetail`)——同文件底部已定义类型安全 route,绕开了 |
| L9 | `feature/aiwriting/streaming/AiActionViewModel.kt:198-199` | `tryEmit false` 时 log 误导:`success=$emitted subscribers=N` 当 subscribers=0 时正常(没人订阅就不需 emit),不需要 log |
| L10 | `feature/quicknote/detail/QuickNoteDetailViewModel.kt:68` | `kotlinx.coroutines.delay(100)` 硬编码——见 H8 race 缓解,删除 `noteUpdateEvents` listener 后此 delay 也删 |
| L11 | `feature/aiwriting/streaming/StreamingPanel.kt:136` | `state.error.toDisplayMessage(ctx)` 拿 `LocalContext.current` 进 UI;M3 把 `toDisplayMessage` 提到 `@Composable` 拿 `@StringRes` 更纯 |
| L12 | `AnthropicCompatibleAdapter.kt:48` | `encodeDefaults = true` 写显式 `max_tokens = 2048`;若 provider 不接受显式 max_tokens(如某些 OpenAI proxy),可能 400 |

---

## 跨文件 / 跨方向一致性

1. **`runBlocking` 从"兜底"扩散成"常规路径"**:本轮 `model-management-detail-dropdown` 落地把 `CoreAiGateway.streamWritingOp` 的 `runBlocking` 从 fallback 提升为常规(`apiFormatOverride` 解析);`resolveProviderFlow`(L167-178) 也是 fallback 角色但已成实际使用路径。progress.md M5 polish 第 110 行说"`grep runBlocking MainActivity.kt → 0 匹配`",但**全项目 grep 应该还有大量匹配**(`CoreAiGateway.kt` × 2 / `AiActionViewModel.kt` × 1)。建议补 grep 验证 progress.md 描述。
2. **`catch (e: Exception)` 吞 `CancellationException` 新增 4 处**(RelatedNotesSection / LlmNoteLinkExtractor / ModelManagementVM × 2);M4-3 r1 H1 修过同类,本轮新增未复用 idiom。
3. **SQL `LIKE` ESCAPE**:`NoteRepository` 主路径 M1 r1 H4 修过;`LlmNoteLinkExtractor` LLM 路径**漏修**——明确 regression。
4. **`Locale.getDefault()` 顶层 hoist 不跟随 Configuration 变化**:M1 r1 polish 已知 follow-up,本轮仍未修。
5. **`runBlocking` 落地判定与 progress.md 描述不一致**:`grep -r runBlocking app/src/main/` 应还有 ≥3 匹配,需修 progress.md 或清 runBlocking。

---

## active change 状态盘点

| change | 状态 | 落地 vs spec gap |
|---|---|---|
| `note-association` | 大半落地,uncommitted 部分文件 | 反向依赖 `feature/settings`(H5)+ SQL LIKE 转义 regression(H2)+ JSON 手拼(M5)+ SharedPreferences 独苗(M6)+ 双重 history(M7) |
| `model-management-detail-dropdown` | 落地基本完成 | `runBlocking` 扩散成常规路径(H1)+ ApiFormat 残留静默(M12)+ onModelSelected 静默 catch(M15) |
| `widget-rome-compat` | 主要代码已 commit(progress.md 第 27-35 行),本轮 uncommitted 不涉及 widget | — |

---

## 建议下一步

1. **不开新 change**,把 HIGH 9 项在**当前 uncommitted diff 上**修完,逐项 commit(或一条聚合 commit `fix(review-r2): ...`)。
2. MEDIUM 16 项**另开 `polish-2026-06-21-r2`** change(M5 polish 风格),分批修。
3. LOW 11 项随缘——M6 polish 或随手清。
4. r1 C6(日志无 DEBUG gate)本轮**复发**(M1),建议在 polish change 优先清。
5. 修完后**不开自动 commit**,等用户指令(CLAUDE.md "提交控制" 硬规则)。

---

## 附录:reviewer 来源

| 维度 | agent ID | 主要关注 |
|---|---|---|
| correctness + coroutine | aa39e9d2a422d77ac | state machine / race / cancel / silent fail / null safety |
| security + AI integration | a27cc3b107f0cd2b1 | apikey 流向 / 持久化脱敏 / SQL injection / SSE OOM / manifest |
| architecture + layering | aa484b964457ca6c4 | self-containment / feature boundary / Hilt scope / provider registry |
| 综合 + 排序 + 写文档 | 主线 | 去重 + 优先级 + 行号 + 修法 |
