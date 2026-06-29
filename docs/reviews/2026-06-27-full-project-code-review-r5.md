# Full Project Code Review R5

**日期**: 2026-06-27
**范围**: R4 修复落地后 working tree(R4 review 6 HIGH + 8 MEDIUM 全部 fix)
**触发**: 用户指令"循环 review 至少 2 遍"(R4 完成,本轮为 R5 复审)
**方法**: 3 个并行 review agent(correctness + regression / CLAUDE.md + i18n + 架构 / silent-fail + lifecycle + 线程)
**基线**: R4 fix 已 apply,`:app:check` 全绿

## R4 fix 验证(13 项)

| # | Fix | 验证 | 备注 |
|---|---|---|---|
| H1 | SimpleMarkdown sectionIcon/sectionSummaryRes 加 "Withdraw"/"Revoke" | ✅ correct | 关键词三语映射完整 |
| H2 | SimpleMarkdown line 168 default 从 `_data_storage` 改 `_data_storage_summary` | ✅ correct | 修复 NPE on release |
| H3 | AnimationStylePreviewScreen line 161 Settings.Secure → Settings.Global | ✅ correct | TRANSITION_ANIMATION_SCALE 在 Global namespace |
| H4 | Theme.kt collectAsState → collectAsStateWithLifecycle(initialValue=) | ✅ correct | lifecycle-runtime-compose 2.8+ 参数名 |
| H5 | Theme.kt + SettingsScreen.kt runCatching.onFailure { Log.w(...) } | ✅ correct | 但会污染 Release logcat(R5-4 修) |
| H6 | OnboardingScreen canAccept 滚动解锁逻辑 | ✅ correct | 短内容 + 滚到底两种路径都覆盖 |
| M1 | AnimationStylePreviewScreen effectiveStyle reduce-motion 强制 NONE | ✅ correct | RadioButton enabled 也正确 |
| M2 | OnboardingScreen produceState + withContext(IO) | ✅ correct | 但初始 "" 闪烁(R5-3 修) |
| M3 | OnboardingScreen policyLoadFailed + fallback UI | ⚠️ 落地正确但有副作用 | 闪烁(R5-3 修) |
| M4 | UserPrefsStore animationStyleFlow distinctUntilChanged | ✅ correct | 防腐,无害 |
| M5 | 移除 `me_anim_style_title` 重复 string | ✅ correct | 双语 strings.xml 都清 |
| M6 | ConsentSectionCard KeyboardArrowUp/Down + contentDescription | ✅ correct | Material Icons 正确 import |
| M7 | AppNav consentFlow.collectAsStateWithLifecycle() 不传 initialValue | ⚠️ 设计正确但有盲点 | EMPTY flash(R5-1 修) + widget drop(R5-2 修) |
| M8 | UserPrefsStore ackApikeyPromptFlow distinctUntilChanged | ✅ correct | 同 M4 |

## R5 ≥80 findings

### R5-1 · CRITICAL · consentFlow.first() 同步返 EMPTY → 已同意用户冷启闪 onboarding

- **文件**:`app/src/main/java/com/yy/writingwithai/app/AppNav.kt:121-129`
- **问题**:
  1. `ConsentStoreImpl` line 71-75 `consentFlow` 是 `stateIn(Eagerly, initialValue = ConsentState.EMPTY)`
  2. 第一个 `LaunchedEffect(Unit) { ... .first() ... }` 同步拿到 EMPTY
  3. `needsOnboarding = true` → `navigate(OnboardingEntry.ROUTE_CONSENT)` → **已同意用户冷启动也会闪现 onboarding 页**
  4. 第二个 LaunchedEffect 用 real value 救场,但已经晚了一帧到几帧
- **历史**:R2 fix 加了 line 161 `if (consentState != ConsentState.EMPTY)` guard,但**只 guard 了第二个 LaunchedEffect**,第一个遗漏
- **修法**:`consentFlow.filter { it != ConsentState.EMPTY }.first()`
- **优先级**:P0

### R5-2 · HIGH · widgetPendingRoute 仅在 onboarding route 回放,post-consent 写入会被丢

- **文件**:`app/src/main/java/com/yy/writingwithai/app/AppNav.kt:137-156`
- **场景**:
  1. 已同意用户,MainActivity 写入 `widgetPendingRoute`(来自 widget click intent)
  2. `currentRoute` 是 `AppShell`(不在 onboarding)
  3. 原 if 条件 `currentRoute?.contains("onboarding") == true` 不命中
  4. **pending route 被静默吞掉**,用户点击 widget 期望跳转 detail/edit,实际停留在 AppShell
- **修法**:扩展 `else if (pending != null)` 分支,在非 onboarding route 也 replay
- **优先级**:P1

### R5-3 · HIGH · `policyLoadFailed = policy.isEmpty()` 在 produceState 初始 "" 阶段误判 → 冷启闪烁"加载失败"

- **文件**:`app/src/main/java/com/yy/writingwithai/feature/onboarding/OnboardingScreen.kt:63,176-187`
- **场景**:
  1. M2 改用 `produceState("", context) { withContext(IO) { loadPrivacyPolicy(...) } }`
  2. M3 加 `policyLoadFailed = policy.isEmpty()` 作为 fallback 触发
  3. **初始 `""` 与"加载失败"返回的 `""` 同语义,无法区分**
  4. 冷启动 ~50-300ms 内 UI 显示"条款加载失败"提示,然后被真实内容覆盖
  5. 内层 `runCatching { ... }.getOrElse { _ -> "" }` 完全静默 — APK 漏打包资产时无法排查
- **修法**:
  - `produceState<String?>(null, ...)` 三态(null/""/内容)
  - `policyLoading`、`policyLoadFailed`、`else` 三态 UI 分支
  - 内层 fallback catch 加 `Log.e` 记录
- **优先级**:P1

### R5-4 · MEDIUM · Log.w 在 Release 跑污染 logcat

- **文件**:`app/src/main/java/com/yy/writingwithai/app/ui/theme/Theme.kt:86` + `app/src/main/java/com/yy/writingwithai/feature/settings/SettingsScreen.kt:72`
- **场景**:
  - R4-H5 加 `Log.w("WritingAppTheme", "Hilt EntryPoint unavailable (Preview?)", it)` 在 runCatching.onFailure
  - 该 onFailure 在 Preview 才会触发(运行时 Hilt 正常)
  - 但 Compose `Log.w` 不会自动 gate `BuildConfig.DEBUG`,每次 Preview render 都打 stack trace
  - Release APK 用户跑 Logcat 看到几百条 "Hilt EntryPoint unavailable" 是因为某个真实异常触发
- **修法**:`val isPreview = LocalInspectionMode.current` + `if (isPreview) Log.w(...)`
- **优先级**:P2

### R5-5 · MEDIUM · ackApikeyPrompt 用 initialValue=false 跟 consentFlow 哲学不一致

- **文件**:`app/src/main/java/com/yy/writingwithai/app/AppNav.kt:135-136`
- **备注**:不动 — `ackApikeyPrompt=false` 初始值确保了"未 ack 必走 apikey-prompt",与 M7 skip 设计哲学相反但语义正确(consent 是状态可推,ack 是行为可推)。Doc-only fix,加注释说明不对称原因
- **优先级**:P3

## R5 <80

- `UserPrefsStore.kt:56,68` `distinctUntilChanged()` 对 DataStore 是冗余的(DataStore 已经 dedup by key),无害(M4/M8 fix 留下的副作用)
- `OnboardingScreen.kt:73-79` initial measurement 时 `visibleItemsInfo.size=0` 走 `total<=1` → return false,正常 recomposition 后恢复,无 bug
- `loadPrivacyPolicy` 缺 `@WorkerThread` 注解 — 唯一 caller 是 IO dispatcher,style nit

## R5 fix 落地

| Fix | 文件 | 验证 |
|---|---|---|
| R5-1 | AppNav.kt:121-131 加 `filter { it != EMPTY }` + import filter | ✅ |
| R5-2 | AppNav.kt:137-156 加 `else if (pending != null)` 非 onboarding 也 replay | ✅ |
| R5-3 | OnboardingScreen.kt produceState 三态 + 三态 UI 分支 + Log.e fallback | ✅ |
| R5-4 | Theme.kt + SettingsScreen.kt Log.w gate on `LocalInspectionMode.current` | ✅ |
| R5-5 | skip(已在代码 comment 说明不对称原因) | — |

**build 验证**:`./gradlew :app:ktlintCheck :app:assembleDebug :app:testDebugUnitTest` ✅ 全绿

## R5 收口

- 4 项真问题(1 CRITICAL + 3 HIGH/MEDIUM)已修
- 用户原始诉求"循环 review 至少 2 遍"完成(R4 + R5)
- R5 fix 暴露 R4 fix 的设计盲点 — `produceState<String>` 状态机不严谨、`collectAsStateWithLifecycle` 不传 initialValue 的语义要 paired with `.first()` guard
- **建议**:进入 M5 polish 阶段(真 provider 联调 / AI 历史持久化等),暂停 review 循环