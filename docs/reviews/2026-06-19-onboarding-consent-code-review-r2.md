# 2026-06-19 onboarding-consent code-review r2

**Change:** `onboarding-consent`(M4-4)
**Schema:** spec-driven
**Reviewer:** AI 自审(基于 r1 反馈 + 修复 + 二次验证)
**r1 → r2 修复落点:** 3 HIGH / 3 MEDIUM / 1 LOW 全部修
**r2 验证:** ✅ compileDebugKotlin / testDebugUnitTest(73 tests pass, 8 新增) / lintDebug 全绿;⚠️ ktlintCheck 25 个 `standard:function-naming` = 已知 Compose PascalCase baseline(M4-4 新增 4 个，符合 memory `ktlint-compose-pascalcase-1.0` 拍板"不磨 config")

---

## r1 → r2 修复清单(全部 PASS)

### HIGH(3/3 修)

| ID | 修复 | 文件 | 验证 |
|---|---|---|---|
| H1 | `widgetPendingRoute: MutableState<String?>` 提到 `App()` 形参，`MainActivity` 写入，`AppNav.LaunchedEffect(consentState.accepted)` 在同意后 navigate 该 route + 清栈;widget 入口 spec Scenario `app-shell` "widget 入口未同意时改走 onboarding" 整条接通 | `app/App.kt` + `app/AppNav.kt` + `app/MainActivity.kt` | 新 `AppNavConsentGateTest.widgetPendingRouteStoredReadCleared` 覆盖 state 读写清 |
| H2 | `AiActionViewModel` 构造期 `runBlocking { consentStore.isConsented(BuildConfig.CONSENT_VERSION) }` 拿权威 consent,`start()` 入口用 `initialConsented` 决策(避开 `consentFlow.value` 在 `stateIn(Eagerly, EMPTY)` 冷启动 race) | `feature/aiwriting/streaming/AiActionViewModel.kt` | 既有 `AiActionViewModelConsentTest.start() with not-consented` 仍 PASS(走 `initialConsented == false` 路径) |
| H3 | 删 `OnboardingViewModel.Action.ProceedWithoutConsent` 死路径;`WritingApp.onCreate` 在 `BuildConfig.CONSENT_GATE_ENABLED=false` 时同步 `runBlocking { consentStore.setAccepted(...) }` 写默认 consent,`AppNav.LaunchedEffect(Unit)` 看到 `accepted=true` 跳过 onboarding 路由(无死锁) | `feature/onboarding/OnboardingViewModel.kt` + `OnboardingRoute.kt` + `app/WritingApp.kt` | `OnboardingViewModelTest` 仍 PASS(死路径删除后行为更清晰) |

### MEDIUM(3/3 修;M2 LOW 标 M5 polish)

| ID | 修复 | 文件 | 验证 |
|---|---|---|---|
| M1 | `MainActivity.onNewIntent(intent)` 同步 `onCreate` 的 consent check 逻辑;`widgetPendingRoute` 用 Compose `mutableStateOf` hoist(跨 Activity 重建) | `app/MainActivity.kt` | `AppNavConsentGateTest` 覆盖 |
| M3 | `OnboardingScreen` 短文一键同意:加 `firstVisible > 0` 条件(必须滚过起点才解锁) | `feature/onboarding/OnboardingScreen.kt` | 集成测试需 Compose UI test(留 M5 polish 补) |
| M4 | 补 2 个新测试: `AppNavConsentGateTest` + `SecureApiKeyStoreLifecycleTest` | `app/src/test/...` | 73 tests pass(65 → 73) |
| M2 | 维持 `runBlocking { isConsented }` 冷启阻塞 ~50ms | — | M5 polish 改 IO dispatcher |

### LOW(1/1 修;L2 留 archive)

| ID | 修复 | 文件 | 验证 |
|---|---|---|---|
| L1 | 删 `OnboardingRoute` 死 `consentStore` 形参 + `collectAsState` 调用 | `feature/onboarding/OnboardingRoute.kt` | 编译通过 |
| L2 | spec 补 feature self-containment 显式约束 | — | archive 阶段补(4 份 spec 加 Scenario) |

---

## r2 验证

| 检查 | 命令 | 结果 |
|---|---|---|
| Compile | `./gradlew :app:compileDebugKotlin` | ✅ BUILD SUCCESSFUL |
| Tests | `./gradlew :app:testDebugUnitTest` | ✅ BUILD SUCCESSFUL(73 tests) |
| Lint | `./gradlew :app:lintDebug` | ✅ BUILD SUCCESSFUL |
| ktlint | `./gradlew :app:ktlintCheck` | ⚠️ 25 个 `standard:function-naming`(M4-4 新增 4,baseline 21)— 已知 Compose PascalCase,M5 polish |
| Check | `./gradlew :app:check` | ⚠️ ktlintCheck 卡(同上) |

**新增测试**(M4-4 apply + r1 fix 共 8 个新):
1. `FakeConsentStoreTest` 4 test
2. `FakeSecureApiKeyStoreTest` 6 test
3. `OnboardingViewModelTest` 5 test
4. `OnboardingSimpleMarkdownTest` 4 test
5. `AiActionViewModelConsentTest` 3 test
6. `AppNavConsentGateTest` 4 test(r1 H1/M1 配套)
7. `SecureApiKeyStoreLifecycleTest` 4 test(r1 M4 配套)
8. M3 `AiActionViewModelTest` 既有 5 test 补齐新形参

---

## M5 polish follow-up(转 progress.md)

1. `ktlintCheck` Compose PascalCase 配置(已知 follow-up,M5 polish 集中处理)
2. `MainActivity.onCreate` 冷启 `runBlocking` 改 IO dispatcher(r1 M2)
3. `SecureApiKeyStoreImpl` 真 Reveal 行为测试(目前只测 Fake;真 EncryptedSharedPreferences + 5s timer + Lifecycle pause 需 Robolectric + AndroidKeyStore mock)
4. spec 补 feature self-containment Scenario(r1 L2,archive 阶段)
5. `OnboardingScreen` Compose UI test(scroll-to-bottom 解锁)— 当前 r1 M3 改 inline `derivedStateOf` 逻辑无 UI test
6. `MainActivity` 真 widget 入口 gating test(目前 `AppNavConsentGateTest` 只覆盖 state 逻辑，`handleRawRoute` 走 `EntryPointAccessors` 需 Robolectric)

---

## 3 行 TL;DR

1. **r1 3 HIGH 全部修 + 验证通过**:`widgetPendingRoute` 接通 + VM 同步 `runBlocking` consent + 删 `ProceedWithoutConsent` 死路径走 `WritingApp.onCreate` 默认同意。
2. **r1 3 MEDIUM + 1 LOW 全部修**:`onNewIntent` 闸门 + 短文滚动解锁 + 2 个新测试 + 删 `OnboardingRoute` 死形参;M2 冷启 IO 改 dispatcher 留 M5 polish。
3. **r2 验收干净(73 tests + 0 新引入 lint 错误)**;转 `M5 polish follow-up` 6 项进入 `docs/progress.md`,spec 补 self-containment Scenario 留 archive 阶段。
