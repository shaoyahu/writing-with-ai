# Code Review: writing-with-ai — 整项目 R1

**Reviewed**: 2026-06-29
**Reviewer**: Claude Code (MiniMax-M3) — 6 fan-out agent synthesis
**Scope**: app/src 全量(main + test),238 .kt / 32998 LOC
**Base**: `main` @ `eadd994`
**Decision**: **REQUEST CHANGES** (1 CRITICAL + 7 HIGH 必修，9 HIGH 强烈建议)

## 1. Summary

6 路并行 review agent 全量扫，6 维分工(security / AI streaming / feishu sync / UI+compose / app shell+architecture / code smell)。共产生 **120 条原始 finding**(去重后 ≈ 80 条实质问题)，分布: **1 CRITICAL / 23 HIGH / 58 MEDIUM / 38 LOW**。

代码总体质量良好:UI 层一致用 Material 3 token,DI 走 Hilt 严格分层，无明显硬编码 apikey / `Log.e` 敏感信息泄露。**核心阻塞点 1 处 CRITICAL 在 SSE 解析;7 处 HIGH 横跨安全/AI/Widget/Nav 关键路径** —— 修完方可发 v1。

## 2. Decision

| 状态 | 数量 | 含义 |
|---|---|---|
| CRITICAL | 1 | 必修，SSE 截断会向用户呈现"已完成"的脏数据 |
| HIGH | 7 | 必修，跨域关键路径有正确性 / 资源 / 安全缺陷 |
| HIGH (建议) | 9 | 强烈建议，真机或回归场景下会暴露 |
| MEDIUM | ~30 | 可分批修，优先 i18n / 死代码 / state saveable |
| LOW | ~30 | 风格/优化，非阻塞 |

**下一步建议**:
- 必修组(1C+7H)开 **OpenSpec change `hardening-sse-and-widget-init`** 单次归档
- 建议组(9H)按子系统分批:`hardening-arch-scope-leak` / `hardening-ai-state` / `hardening-feishu-sync`
- MEDIUM/LOW 跟后续 M6 打磨 / 偶发 polish 一起做

## 3. CRITICAL (1)

### C-1 · `SseParser` 截断流仍 emit `Done` —— UI 把脏数据当成功展示
**文件**: `app/src/main/java/com/yy/writingwithai/core/ai/stream/SseParser.kt:74-77`
**确认**: 已读源码，确认问题。

```
while (!source.exhausted()) {
    val rawLine = source.readUtf8Line() ?: break   // ← EOF / socket 截断走这里
    ...
}
if (dataBuffer.isNotEmpty()) {
    emit(SseEvent.Data(dataBuffer.toString().trimEnd()))
}
emit(SseEvent.Done)                                // ← 无论是否截断都 Done
```

**问题**:`readUtf8Line()` 在 socket 中途断连时返回 `null`，循环 break，残留的 `dataBuffer` 仍 emit 为 `Data`，紧接着 `Done` 无条件 emit。`AnthropicCompatibleAdapter` 把 `Done` 翻译成 `UiState.Done` → UI 进入"可接受"状态，展示截断的 AI 输出 → 用户点了 Accept → `acceptReplace` 写脏数据回 Note，无任何告警。

**修法**:
1. 维护 `cleanTermination: Boolean = true` 状态。
2. `[DONE]` 命中 / 完整 `data:` 行后跟空行 → `cleanTermination = true`。
3. `readUtf8Line() ?: break` 时设 `cleanTermination = false`(EOF 未必是干净的)。
4. 出循环后:若 `!cleanTermination && dataBuffer.isNotEmpty()` → emit `SseEvent.Error(EOFException("SSE stream truncated"))` 而非 `Data`。
5. 仅 `cleanTermination == true` 时 emit `Done`。

**测试**:补 3 用例 —— 截断无 `\n\n` 终止符 / 截断前有部分 `data:` 行 / 正常 `[DONE]` 路径不变。

## 4. HIGH (必修 7)

### H-1 · `QuickNoteWidgetWorker` 全 catch 吞异常 + 不 retry
**文件**: `app/src/main/java/com/yy/writingwithai/core/widget/QuickNoteWidgetWorker.kt:20-28`
**确认**: 已读源码，确认问题。

```kotlin
override suspend fun doWork(): Result = try {
    QuickNoteWidget().updateAll(applicationContext)
    Result.success()
} catch (e: Exception) {
    Result.success()  // ← 吞掉所有异常，无 log 无 retry
}
```

**问题**:Room 锁、DataStore 损坏、磁盘满等瞬时错误被静默吞掉，widget 永远 stale，且没有 logcat 痕迹，事后无法追查。注释说"Glance 内部已自带子任务，所以不 retry"——这是错的，Glance 内部管的是"把 UI 推到桌面"，不是"网络/DB 出错后重试"。

**修法**:
```kotlin
} catch (e: CancellationException) {
    throw e
} catch (e: IOException) {
    Log.w(TAG, "widget update transient IO failure", e)
    Result.retry()
} catch (e: android.database.sqlite.SQLiteException) {
    Log.w(TAG, "widget update DB failure", e)
    Result.retry()
} catch (e: Exception) {
    Log.e(TAG, "widget update unknown failure", e)
    Result.failure()
}
```

### H-2 · `feature/aiwriting` → `feature/onboarding` 跨 feature import
**文件**: `app/src/main/java/com/yy/writingwithai/feature/aiwriting/AiwritingEntry.kt:12, 43-45`
**确认**: 已读源码，确认问题。

```kotlin
import com.yy.writingwithai.feature.onboarding.OnboardingEntry  // ← 跨 feature
...
fun requestConsent(navController: NavController) {
    OnboardingEntry.requestConsent(navController)
}
```

**问题**:直接违反 `CLAUDE.md` "feature 必须自包含 —— 跨 feature 引用走 `feature/<name>/<Feature>Entry.kt`"。M9 留下的"包装 `OnboardingEntry.requestConsent` 保持 aiwriting 自包含"注释是反的——包装不等于"不依赖"。`onboarding` 改名/移包 → `aiwriting` 编译挂。

**修法**:
- 在 `app/AppNav.kt` / `AiwritingEntry` 宿主把 `OnboardingEntry.requestConsent` 作为 lambda 参数传入(由 `app/` 编排)。
- 或新增 `core/nav/ConsentGate.kt` composable 持有"未同意跳同意"逻辑，`aiwriting` 依赖 `core/` 不依赖 `feature/onboarding`。
- 删除 `import com.yy.writingwithai.feature.onboarding.OnboardingEntry`。

### H-3 · `QuickNoteWidgetHiltBridge` 全局 mutable 状态 + Glance 冷启动竞态
**文件**: `app/src/main/java/com/yy/writingwithai/core/widget/QuickNoteWidgetHiltBridge.kt:12-18` + `QuickNoteWidget.kt:45-47`
**确认**: 已读源码，确认问题。

**问题**:`var repository: QuickNoteWidgetRepository?` 由 `WritingApp.onCreate` 赋值。Glance widget 跑在 launcher / system UI 进程，常在 `Application.onCreate` 完成前就触发 render → `repository == null` → `provideGlance` 静默 fallback 到 `emptyList()` → widget 显示空。30s TTL 缓存更糟，把这个错误状态锁 30s，期间无任何重试。

**修法**:
1. 用 `HiltWorkerFactory` 注入 repository(标准模式，见 `androidx.hilt:hilt-work`)。
2. `provideGlance` 拿不到 repo 时，emit `Result.failure()` 让 Glance 走默认空 widget，下次周期 15min 内会再试。
3. 或:repository 注入改为 lazy provider，不依赖 `Application.onCreate` 完成时机。

### H-4 · `MainActivity` widget 启动路由 string prefix 解析，可被构造攻击向量
**文件**: `app/src/main/java/com/yy/writingwithai/app/MainActivity.kt:83` + `WidgetIntentHelpers.kt` + `AppNav.kt:134-164`
**确认**: 上下文已读，确认问题。

**问题**:`WidgetIntentHelpers.launchWithTaskStack` 用 `route.startsWith("quicknote/...")` 决定 forward 到 `AppNav`。一旦 `AppNav` 增加新路由(尤其带 query 参数),`startsWith` 误匹配把畸形 route 推给 navigator，导航到意外 destination 或 crash。CLAUDE.md "字符串前缀路由解析" 是脆弱反模式，已在多处出现。

**修法**:
- 路由解析改类型安全 sealed:`sealed class WidgetLaunchRoute { object NewNote : WidgetLaunchRoute(); data class EditNote(val id: Long) : WidgetLaunchRoute(); data class DetailNote(val id: Long) : WidgetLaunchRoute() }`，用 `when` 穷尽。
- 删除 `route.startsWith` / `route.contains("prefillFocus=true")` 等字符串拼装。

### H-5 · `NoteRepository` 自管 `SupervisorJob` 永不取消 — Hilt singleton scope leak
**文件**: `app/src/main/java/com/yy/writingwithai/core/data/repo/NoteRepository.kt:57-58`

**问题**:`@Singleton` repository 持有 `CoroutineScope(SupervisorJob() + Dispatchers.IO)`,Application exit 时不取消，`recomputeFlow.debounce(...).collect` 长期持有 Room observers。Application `onTerminate` 几乎不会被调用 → leak 实际是 process 终结才清。

**修法**:
- 删除 repo 自管 scope，改为注入 Hilt `ApplicationScope`(单一来源，进程内统一生命周期)。
- 或:在 repo 上加 `CancellationHandler`，在 `Application.onTerminate` 主动 cancel(虽然 onTerminate 不可靠，但写明意图)。

### H-6 · `QuickNoteWidget` `provideGlance` null repo 静默空 widget
**文件**: `app/src/main/java/com/yy/writingwithai/core/widget/QuickNoteWidget.kt:45-47`

**问题**:`repository ?: emptyList()` 直接返回空列表，无 log，无 retry。用户看到空 widget，不知道是"真的没 note"还是"app 没起来"。和 H-3 一起修。

### H-7 · `OAuthCodeReceiver` re-delivery 分支 consume 后未 re-persist —— 二次 crash 永久丢失状态
**文件**: `app/src/main/java/com/yy/writingwithai/core/feishu/auth/OAuthCodeReceiver.kt:81-112`

**问题**:
```kotlin
if (authStore.hasPendingExchange()) {
    val pending = authStore.consumePendingExchange()  // ← 清掉 pending keys
    if (pending != null) {
        ...
        appScope.launch { performExchange(...) }       // ← 不重新 persist
        ...
    }
}
```

第二次启动时 `consumePendingExchange` 已返回，pending keys 已 remove;如果 `performExchange` 在执行期间进程被系统杀掉，`persistPendingExchange` 不在 re-delivery 路径调用，第三次启动无 pending 可 consume，用户必须重新走 OAuth。

**修法**:re-delivery 路径在 `consumePendingExchange` 拿到 pending 后、`performExchange` 之前，**先重新 `persistPendingExchange`** 一次(同 code/appId/secret/requestId)，再 launch exchange。

## 5. HIGH (强烈建议，9)

| ID | 文件 | 一句话 |
|---|---|---|
| H-8 | `AppNav.kt:358` `QuicknoteEdit(id: String? = "NEW")` | 改 sealed `EditMode.New/Edit(id)` |
| H-9 | `FeishuApiClientImpl.kt:195-198` | 429 不自动 `Result.retry()`，补 backoff wrapper |
| H-10 | `FeishuSyncService.kt:62-74` | docId alias 冲突可能改错 note，先按 docUrl 查 |
| H-11 | `FeishuDocService.kt:40-58` | `createDoc` 非幂等，retry 会创建两份 doc |
| H-12 | `FeishuSyncService.kt:44-56` + `FeishuDocService.kt:81-94` | push lost-update，缺 `expectedRevision` |
| H-13 | `AuthInterceptor.kt:47-51` | `runBlocking` 在 OkHttp dispatcher 上有 deadlock 风险 |
| H-14 | `OAuthCodeReceiver.kt` re-delivery | OAuth 流程 `requestId` 命名空间无界 |
| H-15 | `CoreAiGateway.kt:151-187` | token accounting:Usage 后 Delta 漏算 |
| H-16 | `CoreAiGateway.kt:159-187` `onCompletion` | Cancellation 也写 history，污染列表 |

## 6. MEDIUM (去重后 ~30 条)

按子域分组(节选最高频):

**AI / Streaming**:
- `StreamingPanel.kt:104` `accumulated` regenerate 时不清空
- `AiActionViewModel.kt:284-290` empty sourceText 误判"多匹配"
- `AiActionViewModel.kt:118-133` `lastOriginalContent` race,undo 静默 no-op
- `AiActionViewModel.kt:155-168` `ProviderNotConfigured` 错误不区分"未选 vs 缺 key"
- `FakeAiProvider.kt:48-77` token count 语义与真实 provider 不一致
- `AiActionUiState.kt:36` `isCancelled` 字段 dead code

**Feishu / OAuth**:
- `FeishuAuthStore.kt:172-178, 241-251` `secretCache` 永不过期
- `FeishuAuthStore.kt:132-138` verbose logging 暴露 authorize URL `client_id`/`scope`
- `OAuthCodeReceiver.kt:119-123, 129-133` Toast 暴露 OAuth state
- `FeishuApiClientImpl.kt:140-145` 1MB EOF fallback 多余
- `FeishuAuthStore.kt:269-280` `currentTimeMillis()` 可被用户改，改 `elapsedRealtime()`
- `AppManifest.xml:132-154` `<queries>` 暴露 http/https 意图

**Security**:
- `SecureApiKeyStore.kt:228` Revealed 5s 太长 + 流入 UI state
- `AnthropicCompatibleAdapter.kt:260-264` custom header 缺 allowlist(`Proxy-*` / `Cookie`)
- `AnthropicCompatibleAdapter.kt:74-82` `baseUrl` 无 SSRF 防护(可打内网)
- `UserTokenProvider.kt:186-188, OAuthCodeReceiver.kt:230-244` raw error 含 secret
- `AnthropicCompatibleAdapter.kt:248-258` blank apikey 仍发 `Authorization: Bearer `

**UI / Compose**:
- 5 处 IconButton/TextButton 18~28dp 触达区 < 48dp
- `QuickNoteDetailScreen.kt:165,176` 等 `remember` 应改 `rememberSaveable` 防 process death 丢状态
- `QuickNoteEditorScreen.kt:82-89` `existingAttachments` 协程未取消
- 9 处 i18n 拼接 + `ClickableText` 弃用
- `OnboardingScreen.kt:204-208` `LaunchedEffect` 写状态可能成环
- `MyScreen.kt:90-104` `consume()` 重复触发 Snackbar
- `ConsentBottomBar.kt:93-103` disabled Button 颜色覆盖致灰化失效

**Code smell**:
- `QuickNoteDetailViewModel.kt:42-330` 371 LOC,8 职责，拆 `FeishuSyncCoordinator` / `NoteAttachmentsCoordinator`
- `QuickNoteEditorViewModel.kt:44-54` 6 个 MutableStateFlow 应合并为单 `UiState` data class
- `ModelManagementViewModel.kt` + `CustomProviderEditViewModel.kt` try-catch 12+ 重复
- `CustomProviderEditScreen.kt` 480 LOC
- VM import `androidx.compose.ui.text.TextRange` UI 类型(JVM 测试受污染)
- `QuickNoteDetailViewModel.kt:291-329` vs `QuickNoteEditorViewModel.kt:219-250` 附件 IO 30 LOC 复制
- 多处硬编码中文(VM 拼 fallback)
- `SearchHistoryStore.kt` `object` + Context 参数，DI bypass
- `core/prefs/SecureApiKeyStore.kt:197,210,222` 三处空 `if (flow.value is RevealState.Hidden)` dead branch
- `MutableSharedFlow(extraBufferCapacity = 8, DROP_OLDEST, replay=0)` 屏外事件丢失

**Architecture**:
- `WritingApp.kt:50-79` `onCreate` 同步入 WorkManager DB，加 30-80ms TTFD
- `MainActivity.kt:117-131` vs `AppNav.kt:121-129` 双重 `LaunchedEffect` 写 `widgetPendingRoute` 顺序不确定
- `QuickNoteWidgetCache` 30s TTL 实际是 bug 而非修
- `QuickNoteWidgetUpdater.kt:25-28` 每次 new `GlanceAppWidget` 可能与 receiver 实例不一致
- `NoteAssociationSettingsStore` `callbackFlow` 模板 3 份，抽 `observeKey` helper
- `AiActionFabState.kt:13-22` 静态 `TextRange` 依赖把 Compose 拽到非 UI 包

## 7. LOW (节选)

- `AnimationStyleTokens.kt:30-105` token 类字段在 class-init，改需重启
- `RomDetector.kt:22-40` 每次 widget refresh 重读 `Build.MANUFACTURER`
- `AppNav.kt:100-118` `AppPreview` 缺 `LocalActivity` 直接跑会 throw
- `ConsentSectionCard.kt:46-100` Card clickable 缺 `role = Role.Button`
- `Feature/onboarding/OnboardingScreen.kt:169` `expandedSet` 应 `rememberSaveable`
- 等等 ~30 条

## 8. 验证结果

| Check | Result |
|---|---|
| Type check | Skipped(本次未跑 — 改前不动编译) |
| Lint (ktlint) | Pass(`eadd994` 已绿) |
| Unit tests | Pass(`eadd994` 已绿) |
| Build | Pass(`eadd994` 已绿) |

## 9. Files Reviewed (6 agents)

**Security**:`SecureApiKeyStore` / `PathSafety` / `core/feishu/auth/*` / `ApkDownloader` / `WebDavSyncEngine` / `CoreAiGateway` / `AnthropicCompatibleAdapter` / `AppManifest.xml` / `SyncWorker`

**AI Streaming**:`core/ai/*` 全 + `feature/aiwriting/streaming/*` + `feature/aiwriting/action/*`

**Feishu Sync**:`core/feishu/api/*` + `core/feishu/auth/*` + `core/feishu/sync/*`

**UI/Compose**:`feature/quicknote/*` 全部 832-255 LOC + `feature/onboarding/*` + `feature/my/MyScreen` + `feature/aiwriting/streaming/StreamingPanel` + `feature/aiwriting/action/*` + `feature/aiwriting/error/*` + `feature/settings/feishu/FeishuAuthScreen` + `feature/settings/animation/AnimationStylePreviewScreen`

**App Shell**:`app/*` + `core/widget/*` + `core/editor/*` + `core/note/*` + `core/ui/*` + `core/data/*` + `di/*`

**Code Smell**:`feature/quicknote/detail/ViewModel` + `feature/quicknote/edit/ViewModel` + `feature/settings/model/*` + `feature/onboarding/ViewModel` + `feature/my/CheckUpdateViewModel` + `core/prefs/*`

## 10. 后续步骤

1. **必修组(1C+7H)**:开 `openspec new change hardening-sse-and-widget-init`,design 列 8 项修复 + 各自测试，`/opsx:apply` 实施，`/opsx:archive` 归档。
2. **建议组(9H)**:按子系统分 3 个 change:`hardening-arch-scope-leak` (H8/Widget scope/Repository scope) / `hardening-ai-state` (H15/H16) / `hardening-feishu-sync` (H9~H14)。
3. **MEDIUM/LOW**:跟 M6 后续打磨 / 偶发 polish 一起做(CLAUDE.md 允许纯本地环境问题直接动手)。
4. **本 review** 归档:文件已在 `docs/reviews/2026-06-29-writing-with-ai-full-project-code-review-r1.md`，用户授权后 commit + push(CLAUDE.md 默认不自动提交，等指令)。

## 11. 关联材料

- `eadd994` 最终 commit(本次 review 的 base)
- 之前 4 轮 review:R3-R6 修复归档于 commits `5a0500e` / `3833ee7` / `396d524` / `b01e447` / `eadd994`
- OpenSpec 已归档 change 6 个(`init-android-project` ~ `animation-system-and-consent-redesign`)
- `docs/progress.md` 记录 R7 scope leak 决策 + 修正
