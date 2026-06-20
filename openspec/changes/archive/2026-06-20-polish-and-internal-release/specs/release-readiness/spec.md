## ADDED Requirements

### Requirement: ktlint does not flag @Composable PascalCase functions

`app/build.gradle.kts` `ktlint {}` 块 MUST disable `standard:function-naming` 规则;ktlint 1.0.x 无法选择性排除 `@Composable` 注解函数,全局禁用是唯一方案。禁用后 `./gradlew :app:ktlintCheck` MUST return 0 violations。

非 Compose 普通函数 camelCase 检查 MUST 走 Kotlin `-Xreport-all-warnings` + IDE inspection `IdentifierNaming` 兜底,不因禁用 ktlint 规则而降级。

#### Scenario: ktlintCheck zero violations
- **WHEN** `./gradlew :app:ktlintCheck` 运行
- **THEN** 返回 0 violations(kotlin-standard:function-naming 规则不在 active rule set)

#### Scenario: Gradle ktlint filter excludes function-naming
- **WHEN** `app/build.gradle.kts` `ktlint {}` 块内容检查
- **THEN** `ktlint { }` 含 `ruleConfig { "ktlint_standard_function-naming" to "disabled" }`

### Requirement: Robolectric dependency integrated for Android Framework tests

`gradle/libs.versions.toml` MUST 新增 `robolectric = "4.13"` version + `robolectric-core = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }` library;`app/build.gradle.kts` MUST 加 `testImplementation(libs.robolectric.core)` + `testImplementation(libs.androidx.test.core)` + `testImplementation(libs.androidx.test.runner)`。

Robolectric test 文件 MUST 使用 `@RunWith(AndroidJUnit4::class)` + `@Config(sdk = [34])` 注解,MUST 放在 `app/src/test/java/com/yy/writingwithai/` 相应子包内,套用 JUnit5 + `runTest` 协程测试器(走 `kotlinx-coroutines-test`)。

#### Scenario: Robolectric test suite compiles and runs
- **WHEN** `./gradlew :app:testDebugUnitTest` 运行
- **THEN** 所有 Robolectric test class 正常编译 + 执行通过;Robolectric `@Config` 注解无缺

#### Scenario: Robolectric test coverage
- **WHEN** `find app/src/test -name "*RobolectricTest*" -o -name "*Robolectric*"` 执行
- **THEN** 至少返回 3 个文件(`SecureApiKeyStoreRobolectricTest.kt` + `MainActivityConsentGateTest.kt` + `WritingAppConsentGateTest.kt`)

### Requirement: OnboardingScreen has Compose UI test for scroll-to-bottom unlock

`app/src/test/java/com/yy/writingwithai/feature/onboarding/OnboardingScreenUiTest.kt` MUST 包含 Compose UI test,用 `createComposeRule()` + `setActivityContent { MaterialTheme { OnboardingRoute(vm) } }` 验证 `FakeConsentStore` 注入的 `OnboardingViewModel` + `LazyColumn` 滚动到底部后"同意"按钮 `enabled = true`。

UI test MUST 不依赖真实 NavHost(`OnboardingRoute` 只有 Composable 层级,由 test 直接提供所有参数);MUST 不依赖 Hilt(构造 VM 用 FakeConsentStore,`setActivityContent` 不启动 Activity)。

#### Scenario: 滚动到底部解锁按钮
- **WHEN** Compose UI test 启动 + `performScrollToIndex(lastIndex)` on privacy policy `LazyColumn`
- **THEN** "同意并继续"按钮 `onNodeWithTag("accept_button")` 的 `enabled` property 为 `true`

#### Scenario: 未滚动时按钮禁用
- **WHEN** Compose UI test 启动但未执行滚动
- **THEN** "同意并继续"按钮 `onNodeWithTag("accept_button")` 的 `enabled` property 为 `false`

#### Scenario: 中点滚动不满足 firstVisible > 0
- **WHEN** `LazyColumn` 只有 3 项总长不满一屏,`performScrollToIndex(2)` 执行
- **THEN** `firstVisible == 0` → 同意按钮仍 disabled(短文一键同意阻止)

### Requirement: ROM compatibility notes document covers 4 major OEMs

`docs/usage/rom-compatibility-notes.md` MUST 涵盖小米 MIUI / 华为 HarmonyOS / OPPO ColorOS / vivo OriginOS 的 widget 限制、predictive back 行为、降级方案;每 ROM 一段 3-5 行,用 markdown 表格 + 段落;末尾提供统一降级说明(用户在 widget 不可用时可走 app 内快捷入口)。

#### Scenario: ROM notes file exists and covers 4 OEMs
- **WHEN** `docs/usage/rom-compatibility-notes.md` 文件检查
- **THEN** 包含 `## 小米 MIUI` / `## 华为 HarmonyOS` / `## OPPO ColorOS` / `## vivo OriginOS` 四个 H2 段

#### Scenario: ROM notes references widget fallback
- **WHEN** `docs/usage/rom-compatibility-notes.md` 末尾段检查
- **THEN** 含"降级方案"段,说明 widget 不可用时用户可进 App 内快捷入口完成笔记操作

### Requirement: MainActivity consent check uses async IO dispatcher

`MainActivity.handleRawRoute` 内 `runBlocking { consentStore.isConsented(...) }` MUST 替换为 `lifecycleScope.launch(Dispatchers.IO) { val isConsented = consentStore.isConsented(...); withContext(Dispatchers.Main) { ... } }`;主线程不再阻塞 `runBlocking` ~50ms。

`widgetPendingRoute` 写入 State 逻辑 MUST 保持在 `withContext(Dispatchers.Main)` 块内;`navigate` 调用 MUST 在 Main 线程。

#### Scenario: handleRawRoute no runBlocking
- **WHEN** `MainActivity.kt` 源码 grep `runBlocking`
- **THEN** 0 匹配(handleRawRoute 内无 runBlocking)

#### Scenario: consent check runs on IO
- **WHEN** `MainActivity` 收到 widget Intent,`lifecycleScope.launch(Dispatchers.IO)` 被执行
- **THEN** `consentStore.isConsented` 调用在 IO 调度器而非主线程;`navigate` 回到 Main 线程;主线程无阻塞
