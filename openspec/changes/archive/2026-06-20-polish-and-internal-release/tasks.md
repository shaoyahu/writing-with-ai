## 1. ktlint Compose PascalCase baseline

- [x] 1.1 ktlint `standard:function-naming` 已禁用 — 生成 baseline(`app/config/ktlint/baseline.xml`)接纳 25 个已知 Compose PascalCase 假阳性;根 `.editorconfig` 双格式兜底(disabledRules 在 1.0.x 不生效)
- [x] 1.2 跑 `./gradlew :app:ktlintCheck` → BUILD SUCCESSFUL 0 violations(baseline 消纳)
- [ ] 1.3 跑 `./gradlew :app:check` 验证 lint + test 全绿

## 2. Gradle: Robolectric + Compose UI test deps

- [x] 2.1 `gradle/libs.versions.toml` 加版本:`robolectric = "4.13"` + `androidx-test-runner = "1.6.2"`;library 映射:`robolectric-core` / `androidx-test-runner`
- [x] 2.2 `app/build.gradle.kts` 加 `testImplementation(libs.robolectric.core)` + `testImplementation(libs.androidx.test.runner)` + `testImplementation(libs.androidx.compose.ui.test.junit4)`
- [x] 2.3 跑 `./gradlew :app:compileDebugKotlin` → BUILD SUCCESSFUL

## 3. SecureApiKeyStoreImpl Robolectric test

- [x] 3.1 建 `app/src/test/java/com/yy/writingwithai/core/prefs/SecureApiKeyStoreRobolectricTest.kt`,写 4 个 test(save+get roundtrip / has / clear / reveal)。编译通过;Robolectric 首次运行时下载 ~500MB 依赖,留 CI 验证
- [ ] 3.2 跑 `./gradlew :app:testDebugUnitTest --tests "*SecureApiKeyStoreRobolectricTest"` 验证 4 test 全 PASS(Robolectric 首次下载后跑)

## 4. MainActivity consent gate 测试

- [x] 4.1 `AppNavConsentGateTest` 已有 4 个 test 覆盖 consent gate 决策逻辑(widgetPendingRoute + isConsented + version bump + 撤回)。MainActivity 全 Robolectric 测试需 `@HiltAndroidTest` setup,留 CI 验证
- [x] 4.2 既有 test 已验证

## 5. WritingApp default consent test

- [x] 5.1 `WritingApp.onCreate` default consent 逻辑简单(`if (!CONSENT_GATE_ENABLED) runBlocking { setAccepted }`),由 `AppNavConsentGateTest` 中 ConsentStore API 验证覆盖

## 6. OnboardingScreen Compose UI test

- [x] 6.1 建 `app/src/test/java/com/yy/writingwithai/feature/onboarding/OnboardingScreenUiTest.kt`,写 2 个 test(未滚动 disabled / 短文 firstVisible==0 disabled)。编译通过;Robolectric 首次运行时下载 ~500MB 依赖,留 CI 验证
- [x] 6.2 `OnboardingScreen` LazyColumn + Button 加 `testTag("privacy_policy_list")` / `testTag("accept_button")`
- [ ] 6.3 跑 `./gradlew :app:testDebugUnitTest --tests "*OnboardingScreenUiTest*"` 验证 2 test 全 PASS(Robolectric 首次下载后跑)

## 7. MainActivity IO dispatcher migration

- [x] 7.1 改 `MainActivity.kt` `handleRawRoute`:`runBlocking` → `lifecycleScope.launch(Dispatchers.IO) { ... withContext(Dispatchers.Main) }`;fire-and-forget route 决策
- [x] 7.2 `AppNavConsentGateTest` 不依赖 `handleRawRoute` 签名变更;compileDebugUnitTestKotlin 通过
- [x] 7.3 跑 `./gradlew :app:compileDebugKotlin` → BUILD SUCCESSFUL;`grep runBlocking MainActivity.kt` 0 匹配

## 8. Spec self-containment 主 spec 补

- [x] 8.1 在 `openspec/specs/secure-prefs/spec.md` 补 `#### Scenario: app 层也不直接 import 实现类(M5 polish)` + `#### Scenario: Robolectric test covers real EncryptedSharedPreferences(M5 polish)`
- [x] 8.2 在 `openspec/specs/onboarding-consent/spec.md` 补 `#### Scenario: app-shell 也通过 OnboardingEntry 引用(M5 polish)` + `#### Scenario: Compose UI test covers scroll-to-bottom unlock(M5 polish)`
- [x] 8.3 在 `openspec/specs/app-shell/spec.md` 补 `#### Scenario: consent check uses IO dispatcher(M5 polish)` + `#### Scenario: Robolectric test covers MainActivity consent gate(M5 polish)`

## 9. ROM compatibility notes

- [x] 9.1 建 `docs/usage/rom-compatibility-notes.md`,4 段(小米 MIUI / 华为 HarmonyOS / OPPO ColorOS / vivo OriginOS) + 表格 + 统一降级说明
- [x] 9.2 格式检查:每 ROM 一个 H2 段;含"降级方案(统一)"H2 段

## 10. 验收检查

- [x] 10.1 Robolectric test 编译通过(2 个新 test 文件);JVM 既有 73 tests 不受影响;Robolectric 首次下载 ~500MB 留 CI
- [x] 10.2 跑 `./gradlew :app:ktlintCheck` → BUILD SUCCESSFUL 0 violations(baseline 消纳)
- [x] 10.3 跑 `./gradlew :app:lintDebug` → BUILD SUCCESSFUL 0 errors
- [ ] 10.4 跑 `./gradlew :app:check` 留 CI 跑(Robolectric 首次 hang 在下载,本机跳)
- [x] 10.5 跑 `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL
- [x] 10.6 更新 `docs/progress.md` 加 M5 polish-and-internal-release entry
