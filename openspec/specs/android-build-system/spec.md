# android-build-system

## Purpose

TBD — synced from OpenSpec change `init-android-project`(2026-06-18)。原 change 在 `openspec/changes/archive/2026-06-18-init-android-project/`。

Gradle 8 + Version Catalog + AGP + Kotlin 2.x + KSP + Compose Compiler + Hilt + Room/DataStore + OkHttp + Glance + ktlint 的依赖和插件配置;`assembleDebug` 与 `test` task 可跑通。

## Requirements

### Requirement: Gradle build tasks pass for the debug variant

The Gradle project MUST compile, assemble a debug APK, and run unit tests without errors using JDK 17.

#### Scenario: Debug APK assembly succeeds
- **WHEN** the user runs `./gradlew :app:assembleDebug` from the repo root on JDK 17
- **THEN** the task exits with status 0 AND an APK file exists at `app/build/outputs/apk/debug/app-debug.apk`

#### Scenario: Unit tests execute
- **WHEN** the user runs `./gradlew :app:testDebugUnitTest` from the repo root
- **THEN** the task exits with status 0 AND at least one test class from `app/src/test/` is executed

### Requirement: Dependencies are managed via Version Catalog

All library and plugin versions MUST be declared in `gradle/libs.versions.toml`; `app/build.gradle.kts` MUST NOT contain hardcoded version strings.

#### Scenario: Catalog is the single source of versions
- **WHEN** a developer adds a new dependency to the app module
- **THEN** the version is declared in `[versions]` of `gradle/libs.versions.toml` AND referenced via `libs.<alias>` from `app/build.gradle.kts`

#### Scenario: No version strings in app module
- **WHEN** the `app/build.gradle.kts` file is inspected
- **THEN** no `group: "..." , name: "..." , version: "..."` form is used AND all coordinates come from `libs.versions.toml` entries

### Requirement: ktlint static check passes

The project MUST run ktlint in CI-equivalent mode and produce no errors; the rule set lives in `config/ktlint/.editorconfig`.

> **已知限制**(2026-06-18 review r2):ktlint rule-engine 1.0.x(plugin 12.x 拉的)对 Compose Composable PascalCase 命名硬冲突;`disabledRules` / `@file:Suppress` / `@Suppress` / `// ktlint-disable` 已知 4 种 disable 方式均不生效;`./gradlew :app:ktlintCheck` 当前会报 5 个 standard:function-naming 违规。推迟到 `polish-and-internal-release` change 升 rule-engine ≥ 1.1 走 `experimental:annotation` 机制。

#### Scenario: ktlintCheck exits clean
- **WHEN** the user runs `./gradlew :app:ktlintCheck`
- **THEN** the task exits with status 0 AND no `error` lines are reported

#### Scenario: ktlint rules centralized
- **WHEN** a developer inspects ktlint configuration
- **THEN** all ktlint rule overrides live in `config/ktlint/.editorconfig` (or the `ktlint {}` block of `app/build.gradle.kts`); no second `.editorconfig` with ktlint rules exists elsewhere

### Requirement: Aggregate check task combines lint and tests

The `./gradlew :app:check` task MUST aggregate lint (`ktlintCheck`, `lint`) and unit tests so CI runs a single command.

#### Scenario: check covers all quality gates
- **WHEN** the user runs `./gradlew :app:check`
- **THEN** `ktlintCheck`, `lint`, and `testDebugUnitTest` are all executed AND the task exits with status 0 only if all of them pass

### Requirement: Glance and OkHttp are configured but unused in M0

The Gradle build MUST include Glance and OkHttp dependencies so subsequent changes (`quick-note-widget`, `ai-abstraction-layer`) do not need to add them; no Glance widget code or OkHttp call exists in M0.

#### Scenario: Dependencies present, no usage yet
- **WHEN** the source tree under `app/src/main/` is scanned
- **THEN** no file imports `androidx.glance.*` AND no file calls OkHttp APIs, but `libs.versions.toml` declares both entries