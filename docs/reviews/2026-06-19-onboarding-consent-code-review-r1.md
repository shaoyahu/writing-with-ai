# code-review · onboarding-consent · r1

**Date:** 2026-06-19
**Subject:** `onboarding-consent`(M4-4 首次启动同意门 + AI apikey 加密存储)— r1 review
**Review type:** code-review(r1, initial)
**Basis:** `openspec/changes/onboarding-consent/`(4 spec + design + tasks)+ 21 个产物文件 + 5 个新单测

## 总结

**实现路径与 spec 高度一致:** DataStore `ConsentStore` + `EncryptedSharedPreferences` 双 store 隔离;`BuildConfig.CONSENT_VERSION/GATE_ENABLED` 拍板;UI 双层防御(详情屏 FAB + `AiActionViewModel.start()`)就位;`AiError.UserConsentRequired` 闭环映射到 `R.string.onboarding_required`。**但 widget 启动→同意后回放这条核心 user flow 整个断掉**(`pendingRoute` 写入后没人读，`onNewIntent` 也没实现);`AiActionViewModel.start()` 同步读 `consentFlow.value` 在 race 下能漏判撤回。9 个问题中 3 HIGH / 4 MEDIUM / 2 LOW。

| 严重度 | 数量 |
| --- | --- |
| HIGH | 3 |
| MEDIUM | 4 |
| LOW | 2 |

| 验收项 | 结果 |
| --- | --- |
| `assembleDebug` | ✅ BUILD SUCCESSFUL(apply 自报) |
| `testDebugUnitTest` | ✅ 65 tests pass(apply 自报) |
| `lintDebug` | ✅ BUILD SUCCESSFUL(apply 自报) |
| `ktlintCheck` | 假设通过(M0 已知 `function-naming` 与本 change 无关) |

---

## HIGH — 必须修

### H1 · widget 启动未同意时，同意后 route 回放整条链路断掉

**spec:** `app-shell/spec.md` §"AppNav ConsentGate" Scenario "widget 入口未同意时改走 onboarding" + `app-shell/spec.md` L23 `"OnboardingViewModel.accept()` 后 `AppNav` 监听到 consent + 检查 `MainActivity.pendingRoute` 存在 → navigate 该 route + 清栈"。

**根因:** `MainActivity.onCreate` L45 写 `pendingRoute = rawRoute`，但**`pendingRoute` 没有任何 reader** —— 全项目 grep `pendingRoute` 0 个 consumer。

```
$ grep -rEn "pendingRoute" /Users/bytedance/code/writing-with-ai/app/src/main/
app/src/main/java/com/yy/writingwithai/app/MainActivity.kt:25  (注释)
app/src/main/java/com/yy/writingwithai/app/MainActivity.kt:26  (注释)
app/src/main/java/com/yy/writingwithai/app/MainActivity.kt:37  (var 声明)
app/src/main/java/com/yy/writingwithai/app/MainActivity.kt:45  (写入)
```

后果:用户从 widget "+" 启动 App → 未同意 → 走 onboarding → 同意 → 跳到主路由**而不是** widget 要的 `quicknote/edit?prefillFocus=true`。spec "widget 入口未同意时改走 onboarding" Scenario 直接 fail。spec 措辞假设了"`AppNav` 检查 `MainActivity.pendingRoute` 存在 → navigate 该 route"——这条路径在实现里被完全省略。

**修法(三选一，任一即可):**

1. `OnboardingViewModel.accept()` 写完 `ConsentStore` 后，`AppNav` 的 `LaunchedEffect(consentState)` 监听到 accepted=true 时检查 `MainActivity.pendingRoute` —— 但 `MainActivity` 需要暴露给 `AppNav`(当前是闭包内部类，只能在 `setContent` 闭包里读)。**简单做法**:`MainActivity` 把 `pendingRoute` 提到 `onCreate` 时调 `AppNav(initialRoute=if (consented) rawRoute else null, pendingRoute = rawRoute)`,`AppNav` 在 `LaunchedEffect(consentState.accepted, consentState.version)` 触发后 navigate 前先 check `pendingRoute`。
2. 把 `pendingRoute` 同样落到 `ConsentStore`(extras key，过期读删),AppNav 同意跳转时优先取 extras。
3. `OnboardingViewModel.accept()` 持一个 `pendingRoute` 字段(从 `MainActivity` 注入，接受后自己 navigate)，不走 AppNav。

**建议路线 1**(`AppNav(initialRoute, pendingRoute)`,AppNav 同意 LaunchedEffect 里 fallback navigate pendingRoute)，改动最小、回归风险最低。需补:`MainActivityEntryPoint` 已就绪，只需 `AppNav` 收 `pendingRoute: String?` 形参。

### H2 · `AiActionViewModel.start()` 同步读 `consentFlow.value` 在撤回 race 下漏判

**文件:** `app/src/main/java/com/yy/writingwithai/feature/aiwriting/streaming/AiActionViewModel.kt:86`

```kotlin
val consented = consentStore.consentFlow.value.accepted
if (!consented) {
    _state.value = AiActionUiState.Failed(op = op, error = AiError.UserConsentRequired)
    return
}
```

**问题:** 同步读 `consentFlow.value` 取的是 `consentFlow` 最近的 emit;但 `setAccepted` 由 `OnboardingViewModel.accept()` 在 `viewModelScope.launch { ... }` 里写，DataStore 写盘 + `stateIn` 重新 emit 之间**至少有 1 frame 延迟**(DataStore `edit { }` 走 IO dispatcher)。考虑:

- 极端 case A:用户**撤回**同意(`ConsentStore.setAccepted(version=0, at=0L)`)→ `consentFlow` 已经 emit `accepted=false` → 但用户同时在详情屏点 FAB → `start()` 同步读 `consentFlow.value` 此时已经反映撤回 → 正常 fail。OK。
- 极端 case B(更严重):用户**已同意**,`start()` 同步读 `.value.accepted = true`,**之后才发起流**;`acceptReplace()` 之前用户撤回同意 → 整个流跑完 → `acceptReplace()` 写库。**这个 case 在 spec 里被明确允许**(ai-actions spec Scenario "consent 状态变化时 AiActionUiState 联动": "已有 stream Flow 不强制取消(M3 行为保留)")，所以 B 不是 bug。
- 真问题:`ConsentStoreImpl` 的 `consentFlow` 用 `stateIn(scope = CoroutineScope(SupervisorJob() + Dispatchers.Default), SharingStarted.Eagerly, initialValue = ConsentState.EMPTY)` —— Eagerly 启动 + `scope` 是**自定义** `CoroutineScope(SupervisorJob() + Dispatchers.Default)`,**不是 `@Singleton` 类的字段(也意味着 `ConsentStoreImpl` 实例 GC 时 scope 还在跑——但 `@Singleton` 不会 GC,OK)**;但 `isConsented(currentVersion)` 用 `consentFlow.first()` 是 suspend，等价于"先等 flow emit 一次"。`start()` 同步读 `.value` 跳过这个等待——冷启首屏 `AiActionViewModel` 注入时，`stateIn` 还没把 `combine` 跑起来，`.value` 是 `ConsentState.EMPTY`,**这是正确的初始 fail 行为，OK**。

**但实际 bug:** 启动时 `ConsentStoreImpl` 创建 + `stateIn(Eagerly)` 启动后，`combine(store.data.map {...} x 3)` 三个 flow 是**冷的**，第一次 collect 时才会去 DataStore 读;`Eagerly` 启动只是说"立刻开始 collect"，但**首次 emit 不保证在 viewModel 注入前就发生**。所以 `AiActionViewModel.start()` 同步读 `.value` 在以下场景会**漏掉已同意用户**:

- 用户已同意，App 冷启 → `MainActivity.onCreate` 走 `runBlocking { isConsented(CURRENT) }` 读 → true → `initialRoute = rawRoute` → 渲染主路由
- 但 `MainActivity.runBlocking` 走的是 `consentFlow.first()`,**只 await 一次 emit**;这个 first 触发的 collect 跟 `stateIn` 的 collect 是**两个独立 collect**,first() 返回后 `stateIn` 内部 collect 还在进行 → `stateIn` 的 value 还没更新
- 详情屏 mount 时 `AiActionViewModel` 构造注入 `ConsentStore` → `start()` 同步读 `consentFlow.value` → **可能**还是 `ConsentState.EMPTY`(如果 `stateIn` 的 collect 比 viewModel 创建还慢)
- → 已同意用户被错误 fail `UserConsentRequired`,**必须重新走 onboarding 才能解锁 AI**

**修法:** 改用 `suspend isConsented(BuildConfig.CONSENT_VERSION)` 同步方法 `ConsentStore` 已经暴露(spec L48 `suspend fun isConsented(currentVersion: Int): Boolean`)——但 `start()` 本身非 suspend。**两条路**:

1. 改 `start()` 为 `suspend fun start(...)` 并在 caller(Composable `onExpand` 等)放 `viewModelScope.launch { vm.start(...) }`。
2. 在 `AiActionViewModel` 构造里加 `viewModelScope.launch { consentStore.consentFlow.collect { _consentedInternal.value = it.accepted && it.version >= CURRENT } }`，然后 `start()` 读 `_consentedInternal.value` —— 内部 `_consentedInternal` 是 hot StateFlow，等 `stateIn` 跑完 first emit 后是准的。
3. 折中:`start()` 内 `val consented = runBlocking { consentStore.isConsented(BuildConfig.CONSENT_VERSION) }`(参考 M3 `MainActivity` 走 `runBlocking` 的先例，spec 也接受 "冷启可接受" 的同步决策)。**这条最简单**。

**建议路线 3**(`runBlocking { isConsented(CURRENT) }` + 加注释说明冷启可接受)，与 `MainActivity.onCreate` L44 现有模式一致。

### H3 · `OnboardingViewModel.ProceedWithoutConsent` 分支在 `CONSENT_GATE_ENABLED=false` 时把 App 永久锁在 onboarding

**文件:** `app/src/main/java/com/yy/writingwithai/feature/onboarding/OnboardingViewModel.kt:46-48,59-61` + `OnboardingRoute.kt:44-48`

**问题:** `accept()` / `reject()` 都在 `if (!BuildConfig.CONSENT_GATE_ENABLED)` 时 emit `Action.ProceedWithoutConsent`，但 `OnboardingRoute.LaunchedEffect(action)` 对 `ProceedWithoutConsent` 的处理是:

```kotlin
OnboardingViewModel.Action.ProceedWithoutConsent -> {
    viewModel.consumeAction()
    // CONSENT_GATE_ENABLED=false → 不写 DataStore,AppNav 的 gate 仍要看到
    // accepted=true 才让进主路由;此处不 hack，直接走回滚路径由用户在 settings 启用。
}
```

**注释自承"不 hack，直接走回滚路径由用户在 settings 启用"** —— 但 `feature/settings/` 当前**没有任何 Settings UI 落地**(`docs/progress.md` 也只说 M4 后续 polish);`settings_data_*` 资源是 M4-3 数据迁移，不是 consent 开关。

**后果:** `BuildConfig.CONSENT_GATE_ENABLED = false` 时(任何 release 想跑回滚逃生口的同学都得这么做),`OnboardingScreen.accept()` 调 `accept()` → emit `ProceedWithoutConsent` → `OnboardingRoute` 把它 consume 掉但**没 navigate 任何东西** → 用户**永远卡在 OnboardingScreen**。"回滚逃生口"变成"App 死锁"。

**修法:** 选一:

1. 删除 `ProceedWithoutConsent` 分支，`CONSENT_GATE_ENABLED=false` 时 `accept()` 直接调 `consentStore.setAccepted(version = 0, at = 0L, accepted = true)`(给 ConsentStore 加个"force accept"方法，或者直接写一个 0 键 `consent_force_accept`).这条最干净 —— 不需要真暴露"撤回同意" UI 也能让回滚路径走通。
2. 在 `ProceedWithoutConsent` 分支里直接 `navController.navigate(QuicknoteList) { popUpTo(0) }`,**绕开 ConsentStore**，因为 gate 已关。这需要 `OnboardingRoute` 拿 `navController`，目前 `OnboardingRoute` 形参只有 `consentStore` + `onExitApp`,**得加 `onConsentGateDisabled: () -> Unit` 形参由 AppNav 注入**。
3. 走 `OpenSpec change` 把"撤回同意"UI(M5 polish 之前)提前到本 change，让"回滚路径由用户在 settings 启用"承诺成立。但**本 change 范围外**，会爆 scope。

**建议路线 1**(`ConsentStore.setAccepted(0L, 0)` 时还是 `accepted=true` —— 等等，这跟 spec "撤回同意"scenario 冲突)。实际路线 1 应该是:在 `ConsentStore` 加 `suspend fun forceAccept()` —— 写一个**新** key `consent_force_accept: Boolean = true`,`isConsented()` 判定时 OR 这个 key;`AppNav` 同意后 LaunchedEffect 读到这个 key 也走 navigate 主路由;`OnboardingViewModel.accept()` 在 `!GATE_ENABLED` 时调 `forceAccept()`。**完全独立，不影响撤回同意的 spec 语义**。

---

## MEDIUM — 应修

### M1 · `MainActivity` 没实现 `onNewIntent`，且 `pendingRoute` 字段在 `setContent` 后 Composable 拿不到

**文件:** `app/src/main/java/com/yy/writingwithai/app/MainActivity.kt`

**问题:** H1 的同源问题。`MainActivity` 没重写 `onNewIntent` —— 实际 widget Intent flag 是 `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK`(`WidgetIntentHelpers.kt:29`),CLEAR_TASK 会强制销毁原 Activity 重建，走 `onCreate`,**所以 onNewIntent 不会触发**;这条不算 bug。**但**:

- 单 app 内 `Intent.ACTION_VIEW` / 通知点击等场景如果走 singleTop / 复用 Activity，会触发 `onNewIntent`,Intent 里的 `EXTRA_ROUTE` 不会被处理。
- `MainActivity.pendingRoute` 是 `var`，声明在 `setContent` 之前，但**没人读** —— Composable 没法读 Activity 字段(可以传 lambda，但当前 `App(initialRoute=initialRoute)` 没把 `pendingRoute` 透出)。

**修法:** `MainActivity` 重写 `onNewIntent(intent)`(即使当前没 caller，防 future regression);`App` 形参加 `pendingRoute: String?`,`AppNav` 内部 H1 修法里消费 `pendingRoute` 走回放。`pendingRoute` 在 navigate 后置 null 避免重复。

### M2 · 同步 `runBlocking { consentStore.isConsented(...) }` 在冷启阻塞主线程(DataStore first() 等待 IO)

**文件:** `app/src/main/java/com/yy/writingwithai/app/MainActivity.kt:44`

**问题:** `MainActivity.onCreate` 同步 `runBlocking { consentStore.isConsented(currentVersion) }` —— `isConsented` 内部 `consentFlow.first()`,`consentFlow` 是 `combine` 三个 `store.data.map { ... }`,DataStore 第一次读要走 IO + 反序列化 + 三个 flow 的 combine。**冷启 ANR 风险** —— Android Vitals 报告主线程 5s 阻塞即 ANR，实际 DataStore 1~50ms 范围，**多数情况 OK**，但低端机 / 加密文件系统上可能逼近红线。

spec 措辞 L7: "走 runBlocking + DataStore first()，冷启可接受" —— 接受了风险。但 `DataStore` 内部其实有 `runBlocking` 友好的同步 API (`runBlocking { dataStore.data.first() }` 等价)，或者可以用 `DataStore<Preferences>.data` + `runBlocking` 走 IO 线程(`Dispatchers.IO`)。当前没切线程，直接在主线程跑 `runBlocking` + DataStore IO,**实测 OK 但不够鲁棒**。

**修法(M5 polish 即可，r1 标 MEDIUM):** `runBlocking(Dispatchers.IO) { consentStore.isConsented(currentVersion) }`，让 DataStore IO 至少在 IO 池里跑;或者改用 `SharedPreferences.getBoolean("consent_accepted", false) + version`,`ConsentStore` 启动时把 version 缓存到 `SharedPreferences`，冷启同步读 SharedPreferences 比 DataStore 快 10x。

### M3 · `SimpleMarkdown` 短内容时 "scroll-to-bottom 立刻解锁"，违反 spec "用户需阅读整篇"意图

**文件:** `app/src/main/java/com/yy/writingwithai/feature/onboarding/OnboardingScreen.kt:55-64`

**问题:** `derivedStateOf` 判定 `lastVisible >= total - 1` —— 当 `total == 1`(单段落)或 `total == 0`(空文件 fallback)时，首次 `layoutInfo` 拿到 `total=1` + `lastVisible=0`,**直接 `lastVisible(0) >= total(1) - 1 = 0` 为 true**，按钮立刻 enabled。**短隐私条款(全文 5 行)用户能"一键同意"**,spec Scenario "滚动到底部解锁"语义是"用户必须阅读整篇",**当前实现等价于"按钮永远 enabled"**。

`assets/privacy_policy_zh.md` 当前 45 行，转 MarkdownBlock 约 30 个 block，屏幕一屏 ~10 个 block，实际用户至少要滑 2 次，**没真触发短内容一键同意 bug**;**但 spec 没强制文档长度，这个隐患留了**。

**修法:**

1. 加一个 `MIN_READING_MS: Long = 1500L`(或更人性化的 3000ms)门槛:页面 mount 时记录 `pageEnterAt`,`canAccept` 同时要求 `System.currentTimeMillis() - pageEnterAt >= MIN_READING_MS`。
2. 或者要求"滚动了至少 N 像素"(累计 scroll distance ≥ 1.5x 屏幕高)。
3. 最简单(也最 spec 友好):`derivedStateOf` 内加 `info.totalItemsCount >= 3`(短文 fallback 时强制 disabled，等 M5 polish 换真 Markdown 渲染器时一并处理)。

**建议路线 3**(`total >= 3` 判定)，与 spec "Privacy policy rendered as Markdown with scroll-to-bottom unlock" 兼容(M5 polish 一起改更彻底)。

### M4 · 测试未覆盖 `OnboardingScreen` Composable + `MainActivity` widget 入口 gating + 撤回同意触发 navigate

**文件:** `app/src/test/java/com/yy/writingwithai/feature/onboarding/OnboardingViewModelTest.kt` + 缺测

**问题:** 5 个新单测覆盖:
- `OnboardingViewModelTest` (5 个):accept / reject / scrolledToBottom / seed —— **缺测** `ProceedWithoutConsent` 分支(`CONSENT_GATE_ENABLED=false` 时 accept emit `ProceedWithoutConsent`)、`BuildConfig.CONSENT_VERSION` 真的写对(`assertEquals(BuildConfig.CONSENT_VERSION, state.version)` 太松散，应该 hard-code `1`)。
- `OnboardingSimpleMarkdownTest` (4 个):覆盖 H1/H2/list/paragraph/bold —— OK 但缺测"短文一键同意"(M3 的 fix 应该被 test 锁住)。
- `FakeConsentStoreTest` (4 个) + `FakeSecureApiKeyStoreTest` (5 个) + `AiActionViewModelConsentTest` (3 个):fake 测 OK，但**`ConsentStoreImpl` 真 DataStore 行为没测**(只测 fake),`SecureApiKeyStoreImpl` 也没测(没 Robolectric)。

**缺测最严重的 3 个 Scenario:**

1. `app-shell/spec.md` Scenario "widget 入口未同意时改走 onboarding"——**没有测试**。H1 的 bug 在 r1 之前就漏了。
2. `app-shell/spec.md` Scenario "撤回同意后回 onboarding"——`OnboardingRoute.LaunchedEffect(consentState)` 监听到 accepted=false 跳回 onboarding，这条 path 无测试;`AiActionViewModel.start()` 同步读 `.value` 撤回时是否真返回 false(H2 race)，也无测试。
3. `secure-prefs/spec.md` Scenario "切后台 5s 后回来仍隐藏" + "切后台不到 5s 回来仍可见"——`SecureApiKeyStoreImpl` 无 Robolectric 测试覆盖，只在 fake 里 assert `RevealState.Hidden` 默认值;真 lifecycle pause 行为没验。

**修法(可拆 r1/r2 两次):** r1 必修 #1 + #2(各加 1~2 个测试，主要验 H1 修法和 H2 race)。#3(r1 标 MEDIUM,M5 polish 之前补 Robolectric 测 lifecycle，或提取 `reveal()` 内部 `updateRevealState` 为可注入 `now: () -> Long` 函数以纯 JVM 测时间)。

---

## LOW — M5 polish follow-up

### L1 · `OnboardingRoute` 收 `consentStore` 形参但实际无消费

**文件:** `app/src/main/java/com/yy/writingwithai/feature/onboarding/OnboardingRoute.kt:11,26,33`

**问题:** 形参 `consentStore: ConsentStore` 形参 L26 收 + L33 `consentState by consentStore.consentFlow.collectAsState(initial = ConsentState.EMPTY)` 读 + L33 `@Suppress("UnusedPrivateMember")` 自承 unused。spec L22 注 "由 AppNav 统一负责 navigate"，这条形参预留了但本 change 不消费。

**修法(M5 polish 即可):** 删 `consentStore` 形参(配合 H1 修法:把 `pendingRoute` 透出);或者保留形参 + 加 `// M5 polish 撤回同意 UI 用` 注释。当前 `@Suppress` 屏蔽了 ktlint 警告但隐藏了 dead code，长期会腐化。

### L2 · `app-shell/spec.md` "package layout follows feature self-containment" 没在 spec 显式约束 onboarding feature 自身 import 隔离

**文件:** `openspec/changes/onboarding-consent/specs/app-shell/spec.md`(整文件)

**问题:** 实测 `feature/onboarding/` 当前只被 `app/AppNav.kt` + `feature/aiwriting/AiwritingEntry.kt` + `feature/onboarding/OnboardingRoute.kt` 自身引用，跨 feature 入口收敛在 `OnboardingEntry`(单 object),**实现合格**。但 spec 文件没显式 Requirement "feature/onboarding/ 跨 feature 仅暴露 OnboardingEntry"——CLAUDE.md §"包结构硬规则"有总约束，但没在 onboarding spec 里 mirror 一次。**如果未来有人从 `feature/quicknote/` import `OnboardingScreen` 直接用，无 spec 拦截**。

**修法(M5 polish 即可):** 给 `app-shell/spec.md` 加一个 Requirement: "feature/onboarding/ 跨 feature 入口收敛:外部仅允许 import `OnboardingEntry`，不允许 import `OnboardingRoute` / `OnboardingScreen` / `OnboardingViewModel` 内部。" 加一个 Scenario "grep 验证"。

---

## r1 总结

| 严重度 | 数量 | 主要议题 |
| --- | --- | --- |
| HIGH | 3 | widget 同意后 route 回放断链(H1)/ AiActionViewModel 同步读 race 漏判已同意用户(H2)/ `ProceedWithoutConsent` 把 App 永久锁 onboarding(H3) |
| MEDIUM | 4 | `onNewIntent` 未实现 + `pendingRoute` Composable 不可见(M1)/ 冷启 `runBlocking` 阻塞主线程(M2)/ 短文一键同意(M3)/ widget + 撤回 + lifecycle 5s 缺测(M4) |
| LOW | 2 | `OnboardingRoute` 形参 dead code(L1)/ spec 缺 feature self-containment 显式约束(L2) |

**3 行 TL;DR:**

1. **H1 widget route 回放整条断掉**(`pendingRoute` 写后无人读),`app-shell/spec.md` Scenario "widget 入口未同意时改走 onboarding" 直接 fail，必修。
2. **H2/H3 各自是同步 `runBlocking` + `consentFlow.value` race 风险** 与 **"回滚逃生口"在 settings UI 缺位下变成死锁**，逻辑可补;r1 必修。
3. **测试覆盖漏 widget 入口 gating + 撤回 consent navigate + 真 SecureApiKeyStore lifecycle**,H1 修法应配套 1~2 个新测试，r2 验 0 新引入 bug。
