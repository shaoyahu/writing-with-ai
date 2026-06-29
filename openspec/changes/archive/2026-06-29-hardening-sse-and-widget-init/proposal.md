## Why

`docs/reviews/2026-06-29-writing-with-ai-full-project-code-review-r1.md` 整项目 R1 review 暴露 1 处 CRITICAL(SSE 截断被吞成 "Done")+ 7 处 HIGH 必修,全部横跨**数据正确性 / 资源生命周期 / 跨模块边界** 三个层面。必修组在 v1 发版前必须解决,否则会出现:

- AI 输出流被截断后用户拿到脏数据且无告警
- 桌面小组件冷启动空 widget 静默 30s
- OAuth 授权流程在二次 crash 后用户必须从头来
- 主仓库 `recomputeFlow` 协程永不取消,长期 leak
- `feature/aiwriting` 直接依赖 `feature/onboarding`,违反 CLAUDE.md "feature 自包含"

## What Changes

- **C-1 [CRITICAL]** `SseParser` 区分"clean termination"和"EOF truncation": 截断状态下 emit `SseEvent.Error` 而非 `SseEvent.Done`,UI 进入 Failed 态而非 Done 态。
- **H-1** `QuickNoteWidgetWorker` 区分瞬时错误(IO/DB)与未知错误: IO/SQLite → `Result.retry()` + log;其他 → `Result.failure()` + log。取消吞异常。
- **H-2** 删除 `feature/aiwriting/AiwritingEntry.kt:12` 的 `feature.onboarding.OnboardingEntry` 导入;`requestConsent` 改为 lambda 参数,由 `app/AppNav.kt` 编排时注入。
- **H-3** `QuickNoteWidgetHiltBridge` 改用 `HiltWorkerFactory` + EntryPointAccessors 注入 repository,`provideGlance` 在 repo 不可用时 emit `Result.failure()` 而非 fallback 空列表。
- **H-4** widget 启动路由解析改类型安全 sealed `WidgetLaunchRoute`,删除 `route.startsWith(...)` / `route.contains("prefillFocus=true")` 等字符串拼装。
- **H-5** `NoteRepository` 删除自管 `CoroutineScope(SupervisorJob() + Dispatchers.IO)`,改为注入 Hilt `ApplicationScope`,`recomputeFlow` collect 改用 caller-scope。
- **H-6** 与 H-3 合并修。
- **H-7** `OAuthCodeReceiver` re-delivery 分支在 `consumePendingExchange` 拿到 pending 后、`performExchange` launch 前,先重新 `persistPendingExchange` 一次,保证二次 crash 可 resume。

## Capabilities

### New Capabilities

- `sse-stream-robustness`: SSE 流解析的截断检测 + Done/Error 区分,涵盖 `SseParser` / `AnthropicCompatibleAdapter` 流事件契约。
- `widget-init-race`: 桌面小组件冷启动竞态(Hilt EntryPoint bridge / Glance `provideGlance` 失败语义 / worker 错误分级)。
- `app-route-parsing`: 应用内 widget 启动路由的 sealed-type 解析,涵盖 `WidgetIntentHelpers` / `AppNav` route 注入路径。
- `repository-scope-leak`: Hilt 单例 repository 的协程 scope 治理,涵盖 `NoteRepository` / `ApplicationScope` 注入。

### Modified Capabilities

- `feishu-auth`: OAuth re-delivery 流程增加 re-persist 步骤,确保二次 crash 仍可 resume,行为契约变更(原 spec 只描述单次 lifecycle)。
- `home-screen-widget`: 桌面小组件 worker 错误分级 + repository 注入契约,行为契约变更(原 spec 只描述周期性 update)。

## Impact

**代码影响(8 个文件,直接修改)**:
- `app/src/main/java/com/yy/writingwithai/core/ai/stream/SseParser.kt` — C-1
- `app/src/main/java/com/yy/writingwithai/core/ai/stream/SseParserTest.kt`(新增) — C-1
- `app/src/main/java/com/yy/writingwithai/core/widget/QuickNoteWidgetWorker.kt` — H-1
- `app/src/main/java/com/yy/writingwithai/core/widget/QuickNoteWidget.kt` — H-3+H-6
- `app/src/main/java/com/yy/writingwithai/core/widget/QuickNoteWidgetHiltBridge.kt` — H-3
- `app/src/main/java/com/yy/writingwithai/feature/aiwriting/AiwritingEntry.kt` — H-2
- `app/src/main/java/com/yy/writingwithai/app/AppNav.kt` — H-2 + H-4
- `app/src/main/java/com/yy/writingwithai/core/widget/WidgetIntentHelpers.kt` — H-4
- `app/src/main/java/com/yy/writingwithai/core/data/repo/NoteRepository.kt` — H-5
- `app/src/main/java/com/yy/writingwithai/di/ApplicationScope.kt`(新增) — H-5
- `app/src/main/java/com/yy/writingwithai/core/feishu/auth/OAuthCodeReceiver.kt` — H-7
- `app/src/main/java/com/yy/writingwithai/core/feishu/auth/FeishuAuthStore.kt` — H-7(若需要新增 re-persist API)

**API 影响**:
- `NoteRepository` 构造函数:删除 `scope` 参数,改 `@Inject` + 注入 Hilt `ApplicationScope`。
- `AiwritingEntry.requestConsent(navController)`:签名变 `(navController, requestConsent: (NavController) -> Unit)`,调用方在 `app/AppNav.kt` 处注入 lambda。
- `WidgetIntentHelpers.launchWithTaskStack`:参数类型 `route: String` → `route: WidgetLaunchRoute`。

**依赖**:无新增第三方依赖。

**测试影响**:补 SseParser 3 用例,Widget 路径需要 WorkManager + Glance 集成测试(单元测试 + 必要时 instrumentation)。
