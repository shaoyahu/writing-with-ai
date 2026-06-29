# Full Project Code Review R6

**日期**: 2026-06-27
**范围**: R5 fix 落地后 working tree 全项目(R5 1 CRITICAL + 2 HIGH + 2 MEDIUM 已修)
**触发**: 用户"v1 内测 change 是什么内容?" → "行开启这个"(启动真 provider 联调 runbook 落地 + 全项目 R6 复审)
**方法**: 3 个并行 review agent(correctness + regression / CLAUDE.md + i18n + 架构 / silent-fail + lifecycle + 线程),评分 ≥80 过滤后 7 项真问题
**基线**: R5 fix 已 apply,`:app:check` 全绿,真 provider 联调 runbook 已落档(`docs/usage/real-provider-integration.md`)

## R5 fix 验证(5 项)

| # | Fix | 验证 | 备注 |
|---|---|---|---|
| R5-1 | `AppNav.kt:121-131` Effect A `consentFlow.filter { it != EMPTY }.first()` | ⚠️ **局部回退** | Effect C (L293-316) 漏加 filter,R6-2 修 |
| R5-2 | `AppNav.kt:137-156` else if 分支到非 onboarding 路由 | ✅ correct | 已同意用户冷启到 AppShell 时 widgetPendingRoute 也能回放 |
| R5-3 | `OnboardingScreen.kt:61-66` 三态 produceState + UI 分支 | ✅ correct | 冷启动 ~50-300ms 不再闪"加载失败" |
| R5-4 | `Theme.kt:82,89` + `SettingsScreen.kt` Log.w gate on `LocalInspectionMode` | ⚠️ **局部回退** | `OnboardingScreen.kt:188-205` `loadPrivacyPolicy` 内 Log.w/Log.e 漏 gate,R6-1 修 |
| R5-5 | skip(doc-only) | ✅ correct | 不动 |

## R6 ≥80 findings

### R6-1 · CRITICAL · OnboardingScreen Log.w/Log.e 在 Release 跑污染 logcat(R5-4 局部回退)

- **文件**:`app/src/main/java/com/yy/writingwithai/feature/onboarding/OnboardingScreen.kt:188-205`
- **问题**:
  1. R5-3 fix 加的内层 fallback `Log.w` / `Log.e` 在 `loadPrivacyPolicy` 私有函数内
  2. `loadPrivacyPolicy` 是普通 Composable 内私有 helper,**不是 Composable**,无法直接读 `LocalInspectionMode.current`
  3. APK 漏打包隐私政策资产时,每次冷启动 release 跑都打 `Log.w/Log.e`,污染 logcat 几百万次
  4. 跟 R5-4 修 Theme.kt / SettingsScreen.kt 的 Log gate 哲学不一致
- **修法**:
  - 删 `Log.w` / `Log.e` 调用 + `import android.util.Log`
  - 保留 `runCatching` 三层 fallback(zh → en → ""),但 UI 层 `policyLoadFailed` 三态会渲染错误提示
  - 加注释说明 caller 已在 Composable 域但日志在 caller 打不优雅,APK 漏打包靠 UI 兜底暴露
- **优先级**:P0

### R6-2 · CRITICAL · AppNav.kt Effect C `initialRoute` 处理 `consentFlow.first()` 同步返 EMPTY → 已同意用户首次 widget click 启动不跳转(R5-1 局部回退)

- **文件**:`app/src/main/java/com/yy/writingwithai/app/AppNav.kt:291-317`
- **问题**:
  1. R5-1 修的是 Effect A(L121-131,启动时强制 gate),加 `.filter { it != EMPTY }.first()`
  2. Effect C(L291-317,widget 启动路由解析)同样调 `.first()` 但**漏加 filter**
  3. 场景:已同意用户从 widget 点 "新建笔记" → `initialRoute = "quicknote/edit"`
  4. `consentFlow.first()` 同步返 EMPTY → `state.accepted = false` → 整个 `if (state.accepted && version >= CURRENT)` 分支 false
  5. **widget click 启动不跳转,停留在 AppShell**,用户点 FAB 都没反应
  6. 与 R5-1 同根因,但 R5-1 review 时漏看 Effect C
- **修法**:对齐 R5-1 修法,加 `.filter { it != ConsentState.EMPTY }.first()`
- **优先级**:P0

### R6-3 · HIGH · `core/prefs/UserPrefsStore` 反向依赖 `app/ui/theme/AnimationStyle`,枚举又背 R.string

- **文件**:`app/src/main/java/com/yy/writingwithai/core/prefs/UserPrefsStore.kt:10,39,42` + `app/ui/theme/AnimationStyle.kt:12-16` + `app/src/main/java/com/yy/writingwithai/core/prefs/FakeUserPrefsStore.kt` + `app/src/test/java/com/yy/writingwithai/core/prefs/FakeUserPrefsStore.kt` + `app/src/main/java/com/yy/writingwithai/feature/settings/animation/AnimationStylePreviewViewModel.kt` + `app/src/main/java/com/yy/writingwithai/feature/settings/animation/AnimationStylePreviewScreen.kt` + `app/src/main/java/com/yy/writingwithai/app/ui/theme/Theme.kt:92-97`
- **问题**(双层反向依赖):
  1. `core/prefs/UserPrefsStore` (CLAUDE.md "core/ 是基础设施")反向 `import com.yy.writingwithai.app.ui.theme.AnimationStyle`
  2. `AnimationStyle` 枚举构造里 `@StringRes val displayNameRes/descriptionRes` 绑死 `R.string.anim_style_*`
  3. R.string 是 `app` 模块资源,枚举背 R 后,任何要 reuse 枚举的下游(core/)也被迫背 R / 整个 app 模块资源
  4. CLAUDE.md 架构原则:core 不进 feature;UI 平台入口放最外层。AnimationStyle 本身可以放 core(纯数据 / 枚举),但 R.string 绑定把它钉死在 app 模块
- **修法**:
  - `AnimationStyle` 枚举构造去掉 `@StringRes` 参数,保留 4 个常量
  - 文案映射改 `companion object` 静态函数 `displayNameRes(style)` / `descriptionRes(style)`(函数体仍在 `app/ui/theme`,允许依赖 R)
  - `UserPrefsStore.animationStyleFlow` 改 `Flow<String>`,`setAnimationStyle(style)` 改 `setAnimationStyleName(name: String)`
  - UI 边界(VM + Theme.kt)做 `String` → `AnimationStyle` 映射:`AnimationStyle.fromName(name)`
- **优先级**:P1(架构级 reverse dep,影响未来模块边界)

### R6-4 · MEDIUM · `me_section_about` / `me_section_ai_config` / `me_section_data` en locale 缺翻译

- **文件**:`app/src/main/res/values/strings.xml:388-390`(zh OK)+ `app/src/main/res/values-en/strings.xml`(3 项完全缺失)
- **问题**:
  1. `MyScreen` 三段 section header 在 zh 正常显示"AI 配置 / 数据管理 / 关于"
  2. 切到 en locale(系统语言英文)→ `Resources.NotFoundException` 或 fallback 到 zh
  3. 跟 R5 verify 已翻的 `me_data_title / me_model_title / me_settings_title` 等具体 item title 不一致(那些是 item,section header 是分组标题)
- **修法**:`values-en/strings.xml` 加 3 行:
  ```xml
  <string name="me_section_ai_config">AI Configuration</string>
  <string name="me_section_data">Data Management</string>
  <string name="me_section_about">About</string>
  ```
- **优先级**:P2

### R6-5 · MEDIUM · `core/ui/AnimatedSwitch.kt` 是 YAGNI 壳,纯 Switch 转发无 token 消费

- **文件**:`app/src/main/java/com/yy/writingwithai/core/ui/AnimatedSwitch.kt:20-37`(全文件 37 行)+ `feature/settings/SettingsScreen.kt:96` + `feature/settings/association/NoteAssociationSettingsScreen.kt:179`
- **问题**:
  1. YAGNI 模式:纯 forward Switch 全部参数,无 `LocalAnimationTokens.current.switchSpec` 消费
  2. kdoc 自承 "未来实现自定义 thumb 动画时再引入 LocalAnimationTokens"
  3. 文件位置错:在 `core/ui/`(基础设施层),但功能完全空,等于让未来读代码的人误以为有个抽象
  4. 删优于改:既然目前 Switch 行为已 OK,直接用 Material3 `Switch`,后面真要做动画再加回来
- **修法**:
  - `rm core/ui/AnimatedSwitch.kt`
  - `SettingsScreen.kt:96` `AnimatedSwitch` → `Switch`,删 import,加 `import androidx.compose.material3.Switch`
  - `NoteAssociationSettingsScreen.kt:179` 同改
- **优先级**:P2

### R6-6 · HIGH · `CustomProviderEditScreen` LifecycleResumeEffect + onPauseOrDispose { job.cancel() } 丢 onPause 期间 VM events

- **文件**:`app/src/main/java/com/yy/writingwithai/feature/settings/model/CustomProviderEditScreen.kt:113-143`
- **问题**:
  1. `LifecycleResumeEffect(Unit)` + `onPauseOrDispose { job.cancel() }` 在 `onPause` 期间(用户按 Home / 切后台 / 锁屏)取消 events collect
  2. 场景:用户填好自定义 provider 表单 → 点保存 → 立即按 Home → VM `save()` 协程在 IO 跑 → 完成后 emit `Saved` event
  3. 由于 onPause 已触发 `job.cancel()`,`Saved` 事件被吞
  4. 用户回前台 → Toast 没弹,没回退到 list 页,看起来"保存没成功",可能重复保存
  5. M7 fix 误用 `LifecycleResumeEffect` 想替换 deprecated `LocalLifecycleOwner + repeatOnLifecycle`,但语义不对 — `repeatOnLifecycle(STARTED)` 才对应"屏幕可见",`LifecycleResumeEffect` 是 "RESUMED" 粒度
- **修法**:
  - 删 `LifecycleResumeEffect` + `rememberCoroutineScope` + `kotlinx.coroutines.launch` imports
  - 改 `LaunchedEffect(viewModel) { viewModel.events.collect { ... } }`
  - LaunchedEffect 绑定 Composable 生命周期,离开 Composition 才取消(不是 onPause 才取消)
  - 加注释说明 RESUMED 粒度 vs STARTED 粒度的区别
- **优先级**:P2

### R6-7 · HIGH · `AppNav.kt ApikeyPromptRoute.onFinished` 内手动 navigate 与 Effect B 双 navigate race

- **文件**:`app/src/main/java/com/yy/writingwithai/app/AppNav.kt:276-287`
- **问题**:
  1. `onFinished` 内手动调 `navigateWidgetPendingRoute(navController, pending)` 导航到主路由
  2. 同时上方 `LaunchedEffect(consentState.accepted, consentState.version, ackApikeyPrompt)`(L143-181,Effect B)在 collect 到 `ackApikeyPrompt=true` 时也会调 `navigate(AppShell) { popUpTo(0) }`
  3. 两处 navigate 并发竞态:
     - 顺序 A:onFinished 先 navigate → Effect B 再 navigate 到 AppShell → popUpTo 把 apikey-prompt route 弹出栈 ✓ OK
     - 顺序 B:Effect B 先 navigate → onFinished 再 navigate 到 AppShell / pending → popUpTo 把 AppShell 弹出栈 ✗ 黑屏 / 卡死
  4. CLAUDE.md "Composable 单点导航" hard rule 明确反模式
  5. 注释自承 "上面 LaunchedEffect ... 也会再次 navigate" 但用 popUpTo 兜底,实际只是碰巧没崩
- **修法**:
  - `onFinished = {}`(空 lambda)
  - 单点导航交给 Effect B(ackApikeyPrompt collect)
  - 删掉"防止双跳"那段误导注释
- **优先级**:P2

## R6 <80(已记录但不动)

- `MeScreen.kt`/`MyScreen.kt` 多处 `TODO(en)` 翻译占位 — 待 v1 内测期间逐步补,不阻塞
- `values-en/strings.xml` 多处 QuickNote / AI writing / M4-1 widget / M5 onboarding key TODO 占位 — 同上
- `AppShell.kt:137` `MeTabTarget.SettingsAnimationStyle` navigate 跟 Settings 路由入口可考虑折叠 — UX nit
- `core/ai/api/AiError` 错误分类粒度可加 `RateLimited`(429 separate) — 已有 QuotaExceeded 涵盖,不动
- `NoteEntityDaoTest` / `EntityAliasDaoTest` / `NoteLinkDaoTest` 测试覆盖可继续扩 — 不阻塞 R6

## R6 fix 落地

| Fix | 文件 | 改动 | 验证 |
|---|---|---|---|
| R6-1 | `OnboardingScreen.kt:188-205` | 删 Log.w/Log.e 调用 + `import android.util.Log`;保留三层 fallback runCatching;加注释 caller 打日志不优雅 | ✅ |
| R6-2 | `AppNav.kt:296-298` | `consentFlow.first()` 前加 `.filter { it != ConsentState.EMPTY }`(对齐 R5-1 Effect A 修法) | ✅ |
| R6-3 | `AnimationStyle.kt:12-30` | 枚举构造去 `@StringRes`;`displayNameRes()/descriptionRes()` 改 companion 静态函数(还在 app/ui/theme);`UserPrefsStore.kt` 改 `Flow<String>` + `setAnimationStyleName(name: String)`;`FakeUserPrefsStore.kt`(main + test)同步;`AnimationStylePreviewViewModel.kt` 加 `.map { AnimationStyle.fromName(it) }` + `setAnimationStyleName(style.name)`;`AnimationStylePreviewScreen.kt` 改 `stringResource(AnimationStyle.displayNameRes(style))`;`Theme.kt:92-97` `collectAsStateWithLifecycle(initialValue = AnimationStyle.MINIMAL.name)` + `AnimationStyle.fromName(name).tokens()` | ✅ |
| R6-4 | `values-en/strings.xml` | 加 `me_section_ai_config / me_section_data / me_section_about` 3 行 | ✅ |
| R6-5 | `core/ui/AnimatedSwitch.kt` | `rm` 文件;`SettingsScreen.kt:96` `AnimatedSwitch → Switch` + import 调整;`NoteAssociationSettingsScreen.kt:179` 同改 + import 调整 | ✅ |
| R6-6 | `CustomProviderEditScreen.kt:113-143` | `LifecycleResumeEffect(Unit) { val job = ... ; onPauseOrDispose { job.cancel() } }` → `LaunchedEffect(viewModel) { viewModel.events.collect { ... } }`;删 `LifecycleResumeEffect` + `rememberCoroutineScope` + `kotlinx.coroutines.launch` imports | ✅ |
| R6-7 | `AppNav.kt:276-287` | `onFinished = {}`;删手动 navigate + "防止双跳"误导注释 | ✅ |

**build 验证**:`./gradlew :app:ktlintCheck :app:assembleDebug :app:testDebugUnitTest` ✅ 全绿 12s

## R6 收口

- 7 项真问题(2 CRITICAL + 3 HIGH + 2 MEDIUM)全修
- **R5 局部回退暴露**:R5-1 (AppNav Effect C 漏 filter) + R5-4 (OnboardingScreen Log 漏 gate) 都靠 R6 review 重新发现
- **架构级 reverse dep 修复**:core/prefs 不再 import app/ui/theme,enum 也不再背 R.string — R6-3 修后模块边界对齐 CLAUDE.md 原则
- **Composable 导航单点原则强化**:R6-7 删双 navigate,符合 CLAUDE.md hard rule
- **建议**:进入 v1 内测 change 起草 / 真 provider 联调跑通 / R7 暂停(除非用户再次指令)