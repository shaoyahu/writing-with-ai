## Why

v1 内测前需要集中收口 M4-4 r2 遗留的 6 项 M5 polish follow-up(详见 `docs/reviews/2026-06-19-onboarding-consent-code-review-r2.md`):ktlint Compose PascalCase baseline、MainActivity 冷启 `runBlocking` 改 IO dispatcher、Robolectric 落地真测试、OnboardingScreen Compose UI test、spec self-containment Scenario、国产 ROM 适配笔记。零散处理会跨 4 个 change 走 4 次 review 流程，集中到 1 个 polish change 走 1 次 review 更省成本，且 v1 内测是一道"打包点"。

## What Changes

- **新增** Robolectric 依赖入 Version Catalog + `app/build.gradle.kts`，把 `testDebugUnitTest` 升级为可跑 Android Framework 桩的测试套件
- **新增** `app/src/test/java/.../core/prefs/SecureApiKeyStoreRobolectricTest.kt`:`SecureApiKeyStoreImpl` 真 `EncryptedSharedPreferences` + 5s reveal timer + `ActivityLifecycleCallbacks` pause 时间戳
- **新增** `app/src/test/java/.../feature/onboarding/OnboardingScreenUiTest.kt`:`OnboardingScreen` 滚动到底部解锁按钮的 Compose UI test
- **新增** `app/src/test/java/.../app/MainActivityConsentGateTest.kt`:`MainActivity.onCreate` / `onNewIntent` 同意门 + `widgetPendingRoute` 接入的 Robolectric 测试
- **修改** `app/src/main/java/.../app/MainActivity.kt`:`handleRawRoute` 内 `runBlocking { isConsented }` 移入 `Dispatchers.IO` 协程上下文，冷启阻塞不再占主线程
- **修改** `app/build.gradle.kts` `ktlint { }` 块:为 `*.kt` 内的 `@Composable fun` PascalCase 关掉 `standard:function-naming`，改用 `compose:compositionlocal-naming` 规则配套(走 config 而非 suppress)
- **新增** `docs/usage/rom-compatibility-notes.md`:国产 ROM(小米 MIUI / 华为 HarmonyOS / OPPO ColorOS / vivo OriginOS)的 widget 限制、predictive back 行为、降级方案
- **修改** `openspec/specs/onboarding-consent/spec.md` + `app-shell/spec.md` + `secure-prefs/spec.md`:补 feature self-containment Scenario(grep 校验)+ 真 test 契约(Robolectric / Compose UI test 用例)
- **修改** `docs/progress.md` 加 M5 entry

## Capabilities

### New Capabilities

- `release-readiness`: v1 内测前的 polish umbrella — ktlint Compose PascalCase baseline / Robolectric 集成 / Compose UI test 落地 / ROM 适配笔记 / 冷启 IO dispatcher 迁移

### Modified Capabilities

- `app-shell`: 冷启同意门读 `ConsentStore` 改 `Dispatchers.IO`(主线程不再 `runBlocking` ~50ms);`MainActivity` 同意门 + widget route gating 增加 Robolectric test 契约
- `secure-prefs`: `SecureApiKeyStoreImpl` 真 `EncryptedSharedPreferences` + 5s reveal timer + `Lifecycle` pause 时间戳增加 Robolectric test 契约
- `onboarding-consent`: `OnboardingScreen` 滚动到底部解锁按钮增加 Compose UI test 契约;feature self-containment 显式 grep Scenario

## Impact

**新文件**:
- `gradle/libs.versions.toml` — `robolectric = "4.13"` 入 [versions] + `androidx-test-ext-junit` 升级
- `app/build.gradle.kts` — `testImplementation(libs.robolectric)` + `androidTestImplementation(libs.androidx.test.ext.junit)` + Compose UI test deps
- `app/src/test/java/com/yy/writingwithai/core/prefs/SecureApiKeyStoreRobolectricTest.kt`(新增 4~6 个 test)
- `app/src/test/java/com/yy/writingwithai/feature/onboarding/OnboardingScreenUiTest.kt`(新增 3~4 个 test)
- `app/src/test/java/com/yy/writingwithai/app/MainActivityConsentGateTest.kt`(新增 3~4 个 test)
- `docs/usage/rom-compatibility-notes.md`

**修改文件**:
- `app/src/main/java/com/yy/writingwithai/app/MainActivity.kt` — `handleRawRoute` `runBlocking` → `Dispatchers.IO`
- `app/build.gradle.kts` — `ktlint { }` 块关 `standard:function-naming` for `@Composable fun`
- `openspec/specs/{app-shell,secure-prefs,onboarding-consent}/spec.md` — 补 Scenario
- `docs/progress.md` — 加 M5 entry

**依赖**:
- 新增 `org.robolectric:robolectric:4.13`(M0 已有依赖声明未实际使用，本次启用)
- `androidx.compose.ui:ui-test-junit4`(M0 未启用，本次启用)
- `androidx.test.ext:junit-ktx`(M0 已有)

**回归风险**:
- Robolectric 启动慢(单 test ~1~3s),CI 总时长 +30~60s;接受(内测前必跑)
- Compose UI test 需 `createComposeRule()`,Activity 还没启就跑;需确保 VM 状态走 Fake 注入
- ktlint 关 `standard:function-naming` 仅限 `@Composable fun`(其他函数仍要 camelCase)，规则集边界要写清楚避免误伤
- MainActivity `Dispatchers.IO` 迁移涉及 `EntryPointAccessors` 调用上下文，不能直接把 Activity scope 拿走，需用 `lifecycleScope.launch(IO)`
