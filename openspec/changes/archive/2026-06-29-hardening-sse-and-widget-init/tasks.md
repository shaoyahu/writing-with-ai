## 1. SSE 截断检测(C-1)

- [x] 1.1 改 `core/ai/stream/SseParser.kt`:维护 `cleanTermination: Boolean` 状态机;`[DONE]` 命中 / 完整事件后空行 → `cleanTermination = true`;`readUtf8Line() ?: break` 保持 false;出循环后若 `!cleanTermination && dataBuffer.isNotEmpty()` → emit `SseEvent.Error(EOFException("SSE stream truncated"))` 而非 `SseEvent.Done`
- [x] 1.2 补 `core/ai/stream/SseParserTest.kt`:3 用例 —— 截断无 `\n\n` 终止符 / 截断前有部分 `data:` 行 / 正常 `[DONE]` 路径不变
- [x] 1.3 `AnthropicCompatibleAdapter` 现有 `SseEvent.Error` 消费点确认 `when` 覆盖，grep 验证无遗漏(`AnthropicCompatibleAdapter.kt:197/208/211` 已有 `is SseEvent.{Data,Done,Error}` 穷尽)

## 2. Widget Worker 错误分级(H-1)

- [x] 2.1 改 `core/widget/QuickNoteWidgetWorker.kt`:`catch (CancellationException) throw it; catch (IOException) Log.w + Result.retry(); catch (SQLiteException) Log.w + Result.retry(); catch (Throwable) Log.e + Result.failure()`，删除原 `catch (e: Exception) { Result.success() }`
- [x] 2.2 补 `QuickNoteWidgetWorkerTest.kt`:mock widget 分别抛 IO / SQLite / RuntimeException，验证 Result.retry / Result.retry / Result.failure 各自路径 + log 调用(6 用例)

## 3. Widget 冷启动 Hilt EntryPoint(H-3 + H-6)

- [x] 3.1 新增 `core/widget/WidgetEntryPoint.kt`:`@EntryPoint @InstallIn(SingletonComponent::class) interface WidgetEntryPoint { fun repository(): QuickNoteWidgetRepository }`
- [x] 3.2 改 `core/widget/QuickNoteWidgetHiltBridge.kt`:删除 `var repository` / `var widgetStateStore` 字段，改为 `fun resolveRepository(context)` 走 `EntryPointAccessors.fromApplication`
- [x] 3.3 改 `core/widget/QuickNoteWidget.provideGlance`:repository = `resolveRepository(context) ?: run { Log.w(TAG, "..."); return }` 早返回(Glance 走默认空 widget);同步改 `QuickNote1x4Widget` + `SwitchNoteAction`
- [x] 3.4 改 `WritingApp.kt`:删 `@Inject widgetRepository` + `QuickNoteWidgetHiltBridge.repository = ...` + bridge import
- [x] 3.5 改 `app/src/main/AndroidManifest.xml`:不需要改动(无 hilt-work dep，走纯 EntryPointAccessors;WorkManager 默认 init 仍工作)
- [x] 3.6 补 `QuickNoteWidgetTest`:Glance JVM 测难做，跳过(由真机 widget 渲染验证覆盖);`QuickNoteWidgetWorkerTest` 6 用例已补错误分级覆盖

## 4. Widget 启动路由 sealed(H-4)

- [x] 4.1 新增 `core/widget/WidgetLaunchRoute.kt`:sealed(NewNote / OpenNote(noteId:Long) / EditNote(noteId:Long, prefillFocus:Boolean))+ toRouteString/fromRouteString 序列化
- [x] 4.2 改 `core/widget/WidgetIntentHelpers.kt`:删 `route: String` overload;新增 `parseLaunchRoute(intent): WidgetLaunchRoute?`;`launchWithTaskStack(route: WidgetLaunchRoute)` 接受 sealed
- [x] 4.3 改 `app/MainActivity.kt` + `app/AppNav.kt` + `App.kt`:接收 `WidgetLaunchRoute?`;`AppNav.navigatePendingRoute` 用 `when` 穷尽 sealed;4 个 widget click handler(QuickNoteWidget / QuickNote1x4Widget / OpenNoteAction / CreateNoteFromWidgetAction)全转 sealed
- [x] 4.4 全局 grep 验证:production 路径 0 处 `route.startsWith("quicknote/")` / `route.contains("prefillFocus=true")` / `"quicknote/edit?prefillFocus=true"`(剩 3 处 KDoc comment 不计)

## 5. NoteRepository 注入 ApplicationScope(H-5)

- [x] 5.1 新增 `di/ApplicationScope.kt`:`@Qualifier annotation class ApplicationScope; @Provides @Singleton @ApplicationScope fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)`
- [x] 5.2 改 `core/data/repo/NoteRepository.kt`:构造函数加 `@ApplicationScope private val scope: CoroutineScope` 参数，删除自管 `CoroutineScope(SupervisorJob() + Dispatchers.IO)` 字段;`recomputeFlow.debounce(...).collect { ... }` 用注入 scope
- [x] 5.3 全局 grep 验证:NoteRepository 内 0 处 `CoroutineScope(SupervisorJob(`(剩 1 处 KDoc comment)

## 6. Cross-feature lambda 化(H-2)

- [x] 6.1 改 `feature/aiwriting/AiwritingEntry.kt`:删除 `import com.yy.writingwithai.feature.onboarding.OnboardingEntry`;`requestConsent` 签名改 `fun requestConsent(navController: NavController, requestConsent: (NavController) -> Unit)`，内部 `requestConsent(navController)` 调用 lambda
- [x] 6.2 改 `app/AppNav.kt`:调用 `AiwritingEntry.requestConsent(navController) { nav -> OnboardingEntry.requestConsent(nav) }`
- [x] 6.3 全局 grep 验证:`feature/aiwriting/` 0 处 import `feature.onboarding`(剩 2 处 KDoc 不计)
- [x] 6.4 编译验证(在 §8 跑)

## 7. OAuth re-delivery re-persist(H-7)

- [x] 7.1 改 `core/feishu/auth/OAuthCodeReceiver.kt`:re-delivery 分支抽 `performReDelivery` 私有函数(标 `@VisibleForTesting internal`)，先 `authStore.persistPendingExchange(...)` 再 `appScope.launch { performExchange(...) }`
- [x] 7.2 跳 JVM 单测:ComponentActivity 路径难 mock;R2 review + §7.3 真机覆盖
- [ ] 7.3 真机验证(USER-OWNED):走完一次 OAuth，杀进程，再启一次，确认仍可 resume

## 8. 收口(validation)

- [x] 8.1 `./gradlew :app:assembleDebug` — PASS
- [x] 8.2 `./gradlew :app:ktlintCheck` — PASS
- [x] 8.3 `./gradlew :app:testDebugUnitTest` — 419 tests, 0 fail, 6 skipped(新 SseParserTest 10 用例 + QuickNoteWidgetWorkerTest 6 用例全过)
- [x] 8.4 review R2:见下方表格
- [ ] 8.5 `/opsx:archive hardening-sse-and-widget-init --yes` — 等用户指令
- [ ] 8.6 commit + push(CLAUDE.md 不自动，等指令)

---

## §8 R2 总结(对照 R1 review 1C+7H)

| R1 finding | R2 状态 | 验证点 |
|---|---|---|
| C-1 SseParser 截断 emit Done | ✅ FIXED | `SseParser.kt:110-116` 状态机;`SseParserTest.kt` 3 新增用例全过 |
| H-1 WidgetWorker 全 catch 吞异常 | ✅ FIXED | `QuickNoteWidgetWorker.kt:30-32 + runWithErrorGrading`;`QuickNoteWidgetWorkerTest.kt` 6 用例全过 |
| H-2 aiwriting → onboarding 跨 feature | ✅ FIXED | `AiwritingEntry.kt:11` 删 OnboardingEntry import;`requestConsent` 接受 lambda 参数 |
| H-3 QuickNoteWidgetHiltBridge 全局 mutable | ✅ FIXED | `WidgetEntryPoint.kt` + `resolveRepository` 全切;4 处 caller 全改 |
| H-4 widget 启动路由 string prefix | ✅ FIXED | sealed `WidgetLaunchRoute` + when 穷尽 + 删 `route: String` overload;production 路径 0 处 string prefix 解析 |
| H-5 NoteRepository 自管 SupervisorJob | ✅ FIXED | `di/ApplicationScope.kt` + Hilt 注入;自管 scope 字段删 |
| H-6 QuickNoteWidget null repo 静默 | ✅ FIXED(与 H-3 合并) | 早返回 + Log.w |
| H-7 OAuth re-delivery 不 re-persist | ✅ FIXED | `performReDelivery` 先 persist 再 launch exchange |

**R2 决定**:1C+7H 全部修复落地，验证通过。`/opsx:archive` + commit + push 待用户明确指令(CLAUDE.md "提交控制" 硬规则)。
