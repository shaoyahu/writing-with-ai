# localization

## Purpose

TBD — synced from OpenSpec change `init-android-project`(2026-06-18)。原 change 在 `openspec/changes/archive/2026-06-18-init-android-project/`。

`values/strings.xml`(中文 default)+ `values-en/strings.xml`,跟随系统语言;占位 `app_name` 等最少字符串;Lint 规则禁止 Compose 内硬编码中文字符串。

> **已知限制**(2026-06-18 review r2):Requirement "Hardcoded Chinese strings in Composable code are blocked by lint" 当前无法 100% 自动验证 —— Android Lint `HardcodedText` 规则只扫描 XML 资源,**不扫 Kotlin / Compose 源码**。`app/lint.xml` 已配置升级 HardcodedText 为 error,作为未来 XML layout(M4 widget)的拦截兜底;Compose 业务代码拦截依赖 code review + 自审,等 `polish-and-internal-release` change(M5)补 detekt Compose 规则或改 spec 为"lint (XML) + code review (Compose)"双轨。

## Requirements

### Requirement: Strings resolve per system locale

User-visible strings MUST be loaded from `res/values/strings.xml` (default locale, Simplified Chinese) or `res/values-en/strings.xml` (English) according to the device system language.

#### Scenario: Simplified Chinese system shows Chinese strings
- **WHEN** the device locale is `zh-CN` AND the app reads `R.string.app_name`
- **THEN** the returned value is the Chinese `app_name` declared in `values/strings.xml`

#### Scenario: English system shows English strings
- **WHEN** the device locale is `en-US` (or any non-`zh` locale) AND the app reads `R.string.app_name`
- **THEN** the returned value is the English `app_name` declared in `values-en/strings.xml`

### Requirement: strings.xml exists for default and English locales

Both `values/strings.xml` and `values-en/strings.xml` MUST exist and MUST contain at least `app_name` and a placeholder greeting string.

#### Scenario: Both resource files present
- **WHEN** the `app/src/main/res/` directory is listed
- **THEN** `values/strings.xml` AND `values-en/strings.xml` both exist

#### Scenario: Same key set in both locales
- **WHEN** the keys in `values/strings.xml` are compared to those in `values-en/strings.xml`
- **THEN** every key present in the default file is also present in the English file (no missing translations)

### Requirement: Hardcoded Chinese strings in Composable code are blocked by lint

A lint rule MUST flag any Composable code that passes a literal Chinese string to a Composable parameter expecting a `String` resource (e.g., `Text("中文")`).

> **已知限制**:Android Lint `HardcodedText` 仅扫描 XML,**不扫 Compose**;`app/lint.xml` 已配置但实际只对未来 XML layout 生效。当前依赖 code review + 自审拦截 Compose 硬编码中文。

#### Scenario: Lint warns on hardcoded Chinese
- **WHEN** a Composable contains `Text("你好")` and lint is run via `./gradlew :app:lintDebug`
- **THEN** the lint report contains a warning pointing to the literal Chinese string AND the warning category references `HardcodedText` (Android Lint built-in) or a custom equivalent

#### Scenario: Lint does not warn on stringResource references
- **WHEN** a Composable uses `Text(stringResource(R.string.app_name))` AND lint is run
- **THEN** no `HardcodedText` warning is reported on that line