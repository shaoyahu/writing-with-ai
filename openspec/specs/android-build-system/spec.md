# android-build-system

## Purpose

TBD — synced from OpenSpec change `init-android-project`(2026-06-18)。原 change 在 `openspec/changes/archive/2026-06-18-init-android-project/`。

Gradle 8 + Version Catalog + AGP + Kotlin 2.x + KSP + Compose Compiler + Hilt + Room/DataStore + OkHttp + Glance + ktlint 的依赖和插件配置;`assembleDebug` 与 `test` task 可跑通。

## Requirements

### Requirement: Gradle build tasks pass for the debug variant

The Gradle project MUST compile, assemble a debug APK, and run unit tests without errors using JDK 17. When the user has configured `~/.gradle/gradle.properties` with release signing credentials (`RELEASE_STORE_FILE` / `RELEASE_STORE_PASSWORD` / `RELEASE_KEY_ALIAS` / `RELEASE_KEY_PASSWORD`), `./gradlew :app:assembleRelease` MUST also succeed; full release-variant semantics live in the `release-readiness` spec.

#### Scenario: Debug APK assembly succeeds
- **WHEN** the user runs `./gradlew :app:assembleDebug` from the repo root on JDK 17
- **THEN** the task exits with status 0 AND an APK file exists at `app/build/outputs/apk/debug/app-debug.apk`

#### Scenario: Unit tests execute
- **WHEN** the user runs `./gradlew :app:testDebugUnitTest` from the repo root
- **THEN** the task exits with status 0 AND at least one test class from `app/src/test/` is executed

#### Scenario: Release APK assembly succeeds when signing config is present
- **WHEN** the user has configured `~/.gradle/gradle.properties` with `RELEASE_STORE_FILE` / `RELEASE_STORE_PASSWORD` / `RELEASE_KEY_ALIAS` / `RELEASE_KEY_PASSWORD` AND runs `./gradlew :app:assembleRelease`
- **THEN** the task exits with status 0 AND an APK file exists at `app/build/outputs/apk/release/app-release.apk` AND `app/build/outputs/mapping/release/mapping.txt` exists

### Requirement: Dependencies are managed via Version Catalog

All library and plugin versions MUST be declared in `gradle/libs.versions.toml`; `app/build.gradle.kts` MUST NOT contain hardcoded version strings.

#### Scenario: Catalog is the single source of versions
- **WHEN** a developer adds a new dependency to the app module
- **THEN** the version is declared in `[versions]` of `gradle/libs.versions.toml` AND referenced via `libs.<alias>` from `app/build.gradle.kts`

#### Scenario: No version strings in app module
- **WHEN** the `app/build.gradle.kts` file is inspected
- **THEN** no `group: "..." , name: "..." , version: "..."` form is used AND all coordinates come from `libs.versions.toml` entries

### Requirement: ktlint static check passes

The project MUST run ktlint in CI-equivalent mode and produce no errors; the rule set lives in `config/ktlint/.editorconfig`(per-rule 写法)+ `app/build.gradle.kts` 的 `ktlint {}` 块(集中 disabled rules)。

> **2026-06-20 全量扫描**(main 477 + test 109 = ~580 违规，top 5:`indent` 183, `trailing-comma-on-call-site` 142, `argument-list-wrapping` 34, `function-signature` 30, `trailing-comma-on-declaration-site` 23)。修复路径:
> 1. 跑 `./gradlew :app:ktlintFormat` 自动修大部分
> 2. 手工修 `app/src/main/java/com/yy/writingwithai/app/ui/theme/Type.kt` 整文件 indent(12 → 8)
> 3. 手工拆 `app/src/test/java/com/yy/writingwithai/feature/aiwriting/streaming/AiActionViewModelTest.kt:47,73,94,112,128,148,165` 多参数单行(8 参构造调 `AiActionViewModel(...)` 必须每参一行)
> 4. 验 `./gradlew :app:ktlintCheck` 0 violation

#### Scenario: ktlintCheck exits clean
- **WHEN** the user runs `./gradlew :app:ktlintCheck`
- **THEN** the task exits with status 0 AND no `error` lines are reported AND no `obsolete property 'ktlint_disabled_rules'` warning

#### Scenario: ktlint rules centralized
- **WHEN** a developer inspects ktlint configuration
- **THEN** ktlint rule overrides live in:
- (a) `config/ktlint/.editorconfig` — 用 ktlint 1.0 per-rule 写法(单 disabled rule 用 property)
- (b) `app/build.gradle.kts` 的 `ktlint { disabledRules.set(setOf(...)) }` 块 — 集中 disabled rules
- 不存在带 obsolete `ktlint_disabled_rules` 属性的 `.editorconfig`

#### Scenario: Type.kt indent 正确
- **WHEN** 读 `app/src/main/java/com/yy/writingwithai/app/ui/theme/Type.kt`
- **THEN** 所有 property 缩进为 4 的倍数(2 / 4 / 8 / 12 等)，无 `Unexpected indentation (12) (should be 8)` 类违规

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

## ADDED Requirements

### Requirement: WritingApp.onCreate does not block on DataStore IO

`app/WritingApp.onCreate()` MUST NOT use `runBlocking` for `ConsentStore.setAccepted(...)` or any other DataStore read/write. Instead, the application MUST own a long-lived `CoroutineScope(SupervisorJob() + Dispatchers.IO)` and `launch` the consent-state write. App cold start MUST NOT be blocked by DataStore disk I/O.

#### Scenario: Application init is non-blocking
- **WHEN** `./gradlew :app:assembleDebug` build APK cold-starts
- **THEN** grep `WritingApp.kt` for `runBlocking` MUST return 0 matches; onCreate returns within milliseconds

#### Scenario: Consent write completes async
- **WHEN** `onCreate` fires `consentStore.setAccepted(version)`
- **THEN** write happens on `Dispatchers.IO`; main thread already returned to caller