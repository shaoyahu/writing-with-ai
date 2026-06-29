## Context

`docs/reviews/2026-06-29-writing-with-ai-full-project-code-review-r1.md` R1 review 暴露 1C+7H 必修。本 change 集中在 4 个独立技术主题:

1. **SSE 截断检测** — `core/ai/stream/SseParser.kt:74-77` 把 socket 中途断连的 EOF 当成 clean termination,emit `SseEvent.Done` 而非 `Error`,导致用户拿到截断的 AI 输出且 UI 提示"已完成"。
2. **桌面小组件冷启动** — `core/widget/QuickNoteWidgetHiltBridge.kt` 用全局 mutable 字段跨进程桥接 repository,Glance 在 `Application.onCreate` 完成前触发 render 时 `repository == null`,`provideGlance` 静默 fallback 到 `emptyList()`。
3. **Worker 错误吞掉** — `core/widget/QuickNoteWidgetWorker.kt:20-28` `catch (e: Exception) { Result.success() }` 吞掉 IO/DB 瞬时错误,widget 永远 stale。
4. **跨 feature import + 路由解析 + Repository 协程 scope** — 散落在多个文件,互相独立但都属于"系统韧性"主题。

约束:
- 不能动 `AnthropicCompatibleAdapter.stream` 的事件契约(下游已对接 `SseEvent` 枚举),只能改 `SseParser`。
- 不能动 widget receiver 的 Manifest 声明(系统侧)。
- 不能引入新第三方依赖。
- 必修组必须 v1 发版前完成(参考 `docs/progress.md` 中 M 阶段定义)。

## Goals / Non-Goals

**Goals**:
- SSE 流截断 → UI 进 Failed 态,带 `error = "stream_truncated"` 等可观察原因。
- 桌面小组件冷启动拿不到 repo 时,Glance 走 `Result.failure()` 而非静默空 widget;下次周期 15min 内可恢复。
- Worker 区分 transient / fatal,前者 `Result.retry()` + log,后者 `Result.failure()` + log,无静默吞异常。
- `feature/aiwriting` 不再直接 import `feature/onboarding`。
- widget 启动路由用 sealed `WidgetLaunchRoute` 解析,消除 string prefix 拼装。
- `NoteRepository` 不再持有自管 SupervisorJob,统一走 Hilt `ApplicationScope`。
- OAuth re-delivery 流程在 exchange launch 前 re-persist pending,二次 crash 可恢复。

**Non-Goals**:
- 不优化 SSE 性能(本次只动 termination 语义)。
- 不重构 `AnthropicCompatibleAdapter` 整体结构(只动事件出口 contract 与 SSE 解析配合点)。
- 不动 widget UI 渲染(只动数据来源与失败语义)。
- 不修 review §6 MEDIUM / §7 LOW 条目(留后续 polish change)。
- 不做 v1 性能调优 / 内存分析。

## Decisions

### D-1 · SSE 截断检测用 `cleanTermination` 状态机
**决策**:`SseParser` 内部维护 `var cleanTermination: Boolean = false`,三种事件改写:
- `[DONE]` 命中 → `cleanTermination = true` → emit `Done` + return。
- 空行(事件结束) → `cleanTermination = true`(下一个事件开始时由 `dataBuffer.isNotEmpty()` 分支处理)。
- `readUtf8Line() ?: break`(EOF) → `cleanTermination` 保持 false(没看到结束标志)。
- 出循环后:若 `!cleanTermination && dataBuffer.isNotEmpty()` → emit `SseEvent.Error(EOFException("SSE stream truncated"))`;若 `cleanTermination` → emit `Done`;若 `dataBuffer` 仍非空且 `cleanTermination` → emit `Data` + `Done`。

**理由**:最小入侵,保留 `[DONE]` sentinel 协议(Anthropic / OpenAI / DeepSeek 都用),只增加"EOF 不等于 clean"的语义。

**考虑过**:在 adapter 层用 response body length 校验(❌ 依赖 Content-Length,且 chunked 无此 header);在 client 层用 OkHttp 拦截器(❌ 改动大,跨多 provider 不通用)。

### D-2 · Widget repository 注入用 `HiltWorkerFactory` + EntryPointAccessors
**决策**:
1. `QuickNoteWidgetHiltBridge` 改为持有 `EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java).repository()`,函数式取值,无 mutable 字段。
2. `QuickNoteWidgetWorker` 用 `@HiltWorker` + `HiltWorkerFactory` 注入 `WidgetEntryPoint`,work 调用时 `QuickNoteWidget().updateAll(applicationContext)`。
3. `provideGlance` 拿不到 repo 时,emit `Result.failure()` 并 `Log.w`,Glance 走默认空 widget。
4. `Application.onCreate` 不再负责赋 `repository` 字段。

**理由**:`HiltWorkerFactory` 是 `androidx.hilt:hilt-work` 标准模式,Glance 进程触发 render 时若 `Application` 未就绪,EntryPointAccessors 抛 `EntryPointNotFoundException` → 走 `Result.failure()` 路径。

**考虑过**:用 `App Startup` 库预热(❌ 加新依赖);用 `ContentProvider.onCreate`(❌ Glance 进程不保证先跑 App Startup)。

### D-3 · Worker 错误分级用 sealed `WidgetUpdateError`
**决策**:
```kotlin
sealed class WidgetUpdateError(open val cause: Throwable?) {
    data class Transient(override val cause: Throwable) : WidgetUpdateError(cause)  // IO / DB lock
    data class Fatal(override val cause: Throwable) : WidgetUpdateError(cause)     // 其他
}
```
`QuickNoteWidgetWorker.doWork` 内部:
```kotlin
try { ... } catch (e: CancellationException) { throw e }
catch (e: IOException) { Log.w(TAG, "transient IO", e); Result.retry() }
catch (e: SQLiteException) { Log.w(TAG, "transient DB", e); Result.retry() }
catch (e: Throwable) { Log.e(TAG, "fatal", e); Result.failure() }
```

**理由**:`Result.retry()` 让 WorkManager 在 30s/5min/15min backoff 后再试,符合 transient 语义;`Result.failure()` 写 failure log 到 WorkManager DB,可追溯。

### D-4 · Cross-feature 改造用 lambda 注入
**决策**:`AiwritingEntry.requestConsent(navController, requestConsent: (NavController) -> Unit)`,调用方在 `app/AppNav.kt` 处:
```kotlin
AiwritingEntry.requestConsent(navController) { nav ->
    OnboardingEntry.requestConsent(nav)
}
```

**理由**:`AiwritingEntry` 不再 import `OnboardingEntry`,`app/` 编排时注入具体实现,符合 CLAUDE.md "feature 自包含"。

**考虑过**:`core/nav/ConsentGate.kt` composable(❌ 改动面大,Composable 编排 consent 流程涉及 AppNav 路由图重排;v1 时间窗不值)。

### D-5 · 路由解析用 sealed `WidgetLaunchRoute`
**决策**:
```kotlin
sealed class WidgetLaunchRoute {
    object NewNote : WidgetLaunchRoute()
    data class OpenNote(val noteId: Long) : WidgetLaunchRoute()
    data class EditNote(val noteId: Long, val prefillFocus: Boolean = false) : WidgetLaunchRoute()
}
```
- `WidgetIntentHelpers.launchWithTaskStack(context, route: WidgetLaunchRoute)` 接受 sealed。
- `AppNav` 解析 `intent.extras` 时按 sealed `when` 穷尽,无 `startsWith`。

**理由**:`when` 穷尽 + 编译期类型检查,新增路由必须扩展 sealed + 加分支,无法漏写。

### D-6 · `NoteRepository` 注入 `ApplicationScope`
**决策**:
1. 新增 `di/ApplicationScope.kt`:
   ```kotlin
   @Qualifier annotation class ApplicationScope
   @Provides @Singleton @ApplicationScope
   fun provideApplicationScope(): CoroutineScope =
       CoroutineScope(SupervisorJob() + Dispatchers.Default)
   ```
2. `NoteRepository` 构造函数加 `@Inject constructor(@ApplicationScope private val scope: CoroutineScope, ...)`,删除自管 `private val scope` 字段。
3. Hilt 模块 `DataModule` 暂不需改(DI module 已存在,加 `@Provides` 即可)。

**理由**:`@ApplicationScope` 限定符让所有"进程级常驻"任务共用一个 scope,Application 退出时不依赖 `onTerminate`(实际不可靠)。

**考虑过**:在 `Application.onTerminate` 主动 cancel(❌ `onTerminate` 在真机不调用,debug-only)。

### D-7 · OAuth re-delivery re-persist
**决策**:`OAuthCodeReceiver.onCreate` re-delivery 分支:
```kotlin
if (authStore.hasPendingExchange()) {
    val pending = authStore.consumePendingExchange() ?: run {
        // 已过期或被其他流程消费
        Log.w(TAG, "...")
        // 走原 state 校验路径
    }
    if (pending != null) {
        // H-7 fix:re-persist 在 exchange launch 之前,
        // 保证二次 crash 仍可 resume。
        lifecycleScope.launch {
            authStore.persistPendingExchange(pending.code, pending.appId, pending.secret, pending.requestId)
        }.join()  // 同步落盘后再 launch exchange
        ...
    }
}
```

**理由**:`persistPendingExchange` 是 IO 操作,需 100-200ms(EncryptedSharedPreferences);`join()` 阻塞主线程是必要的——不能在 launch 后立即 finish 也不等落盘就 launch exchange。

**考虑过**:用 `runBlocking`(❌ H2 修里把 runBlocking 改成 lifecycleScope 原因已记;此处 join() 是 cooperative 的,等价于顺序 await);`Mutex` 跨 Activity(❌ OAuthAppScope 已有 SupervisorJob,加 mutex 反而复杂)。

## Risks / Trade-offs

- **R-1** `SseParser` 改动对 `AnthropicCompatibleAdapter` / `CoreAiGateway` 的 `Done` 事件消费者是 breaking change(原本 "Data 之后 Done" 表示成功,现在可能是 "Data 之后 Error")。 → **Mitigation**: 改动前先 grep 所有 `SseEvent.Done` 消费点,确认消费者用 `when` 处理 `Done` / `Error` / `Failed` 三态。`UiState.Done` / `UiState.Failed` 已是分别的 sealed case,不会漏。
- **R-2** `HiltWorkerFactory` 需要在 `WritingApp` 配置 `Configuration.Provider` + `WorkManager.initialize`,新加 manifest receiver。 → **Mitigation**: 复用现有 `androidx.work` 依赖,只需 `AndroidManifest.xml` 删掉默认 `WorkManagerInitializer`,在 `Application.onCreate` 里 `WorkManager.initialize(this, Configuration.Builder().setWorkerFactory(factory).build())`。
- **R-3** `ApplicationScope` 引入统一进程级 scope,若 `NoteRepository` 之外的代码(未来)误用,会引入共享可变状态。 → **Mitigation**: 在 `ApplicationScope` 限定符 + KDoc 注明"只用于进程级 fire-and-forget 任务,长生命周期 IO 跟踪"。限定符本身在编译期防误用其他 scope。
- **R-4** sealed `WidgetLaunchRoute` 改动后,`intent.extras` → `WidgetLaunchRoute` 转换需要解析 `noteId` Long + `prefillFocus` Boolean,与 `AppNav` 当前 `id: String?` 数据流不兼容。 → **Mitigation**: `AppNav.QuicknoteDetail(id: Long)` 改 `Long` 类型(删除现有 `String?` sentinel),string → Long 转换在 `WidgetIntentHelpers` 内 try/catch,转换失败 emit `Result.failure()` 不导航。
- **R-5** OAuth re-delivery 同步 `lifecycleScope.launch { ... }.join()` 阻塞主线程 ~200ms。 → **Mitigation**: 阻塞只发生在 re-delivery 罕见路径(只在前次 exchange crash 后),不阻塞常规启动。已有 fix-H2 修过类似模式(EncryptedSharedPreferences 冷启动延迟),项目接受这个延迟。
- **R-6** `MainActivity.kt:83` 之外的可能调用方(若 future change 引入)继续用 string prefix 解析。 → **Mitigation**: 删除 `WidgetIntentHelpers` 中 `route: String` overload,只保留 `route: WidgetLaunchRoute`,编译期强制。

## Migration Plan

1. **Step 1**(C-1 + tests):改 `SseParser`,补 3 用例(截断无 `\n\n` / 截断前有部分 `data:` / 正常 `[DONE]`)。验证 `AnthropicCompatibleAdapter` / `CoreAiGateway` 流消费无回归。
2. **Step 2**(H-1):改 `QuickNoteWidgetWorker` 错误分级,补单元测试(mock Glance widget 抛 IO / SQLite / RuntimeException 各自路径)。
3. **Step 3**(H-3 + H-6):改 `QuickNoteWidgetHiltBridge` + `QuickNoteWidget` 用 EntryPointAccessors;`WritingApp` 接 `HiltWorkerFactory`。
4. **Step 4**(H-4):改 `WidgetIntentHelpers` / `AppNav` 用 sealed `WidgetLaunchRoute`,删除 string prefix 拼装。
5. **Step 5**(H-5):新增 `di/ApplicationScope.kt`,改 `NoteRepository` 注入;清理自管 scope 字段。
6. **Step 6**(H-2):改 `AiwritingEntry` lambda 化,`AppNav` 注入。
7. **Step 7**(H-7):改 `OAuthCodeReceiver` re-delivery re-persist 顺序。
8. **Step 8**(收口):全量 `assembleDebug` + `ktlintCheck` + `testDebugUnitTest`,review R2 回归本 change 修复点。

**Rollback**:
- `SseParser` 改动回退一行(改回 unconditional Done),widget worker 改回 catch-all success(回退 H-1)。
- `NoteRepository` scope 字段加回自管 `CoroutineScope`,构造器签名加回 `scope: CoroutineScope` 参数(需新 default parameter 或 DI override)。
- `WidgetLaunchRoute` sealed 改回 `String`(回退 H-4)。
- `AiwritingEntry.requestConsent` 签名改回 `(navController)` 单参数(回退 H-2)。
- `OAuthCodeReceiver` re-delivery 删 re-persist 段(回退 H-7)。

每步可独立回退(无相互依赖 commit)。

## Open Questions

- Q-1: `SseEvent.Error(cause: Throwable)` 是否要带可序列化原因 type(给 i18n mapper 用)?目前 `AiError` 已有 `NetworkError` / `StreamTruncated` 等 enum,需要扩展 `SseEvent.Error` 携带 `ErrorType` 字段。**等** C-1 设计落地时确定。
- Q-2: `HiltWorkerFactory` 改造是否需要同步迁移 `BackfillScheduler` 用的 Worker?(`entity-extraction` / `note-association` 等) → **决定**:本次不动,只 `QuickNoteWidgetWorker` 接 Hilt,其他 worker 保留旧 `WorkerFactory` 走 `WorkManagerInitializer`(manifest 默认)。
- Q-3: `ApplicationScope` 是否要支持"非 IO 任务用 Dispatchers.Default,IO 任务用 Dispatchers.IO"分流?目前 `NoteRepository.recomputeFlow` 实际是 Room observer,不需要 IO;**简化**:统一 `Dispatchers.Default`,需要时再加 `IoScope` 限定符。
- Q-4: `sealed WidgetLaunchRoute` 与 `app/AppNav.kt` 现有 `QuicknoteEdit(id: String? = "NEW")` 是否需要一并修?目前 ux-2026-06-29 H-8 标了 "建议修",**决定**:本次只动 widget → AppNav 路径(`route startsWith("quicknote/...")` 那一段),`QuicknoteEdit` 自身仍保留 `String?` 类型以最小化改动面;后续 polish change 一起改。
