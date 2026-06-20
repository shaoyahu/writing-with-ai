## MODIFIED Requirements

### Requirement: feature/onboarding/ package is self-contained

`feature/onboarding/` MUST 自包含:跨 feature 引用(若有)走 `feature/onboarding/OnboardingEntry.kt` object 暴露,不允许 `feature/aiwriting/**` / `feature/quicknote/**` / `app/**` 直接 import `OnboardingViewModel` / `OnboardingRoute` / `OnboardingScreen` / `SimpleMarkdown` 等内部文件(只允许 import `OnboardingEntry`)。

#### Scenario: 其他 feature 不直接 import onboarding 内部
- **WHEN** `grep -rE "feature.onboarding.(OnboardingViewModel|OnboardingRoute|OnboardingScreen|SimpleMarkdown)" app/src/main/java/com/yy/writingwithai/feature/(aiwriting|quicknote|settings)/`
- **THEN** 0 匹配(只允许 `feature.onboarding.OnboardingEntry` 之类入口 object)

#### Scenario: app-shell 也通过 OnboardingEntry 引用
- **WHEN** `grep -rE "(OnboardingViewModel|OnboardingRoute|OnboardingScreen|SimpleMarkdown)" app/src/main/java/com/yy/writingwithai/app/`
- **THEN** 0 匹配(`app/` 层只引 `OnboardingEntry.ROUTE_CONSENT`,不直接 import 内部 Composable)

#### Scenario: Compose UI test covers scroll-to-bottom unlock
- **WHEN** `app/src/test/java/com/yy/writingwithai/feature/onboarding/OnboardingScreenUiTest.kt` 文件存在
- **THEN** 用 `createComposeRule()` + `setActivityContent { OnboardingRoute(vm, onConsented = {}) }`,`FakeConsentStore` 注入 VM;含 3 个 test(滚动到底部解锁 `/` 未滚动禁用 `/` 短文一键同意阻止)
