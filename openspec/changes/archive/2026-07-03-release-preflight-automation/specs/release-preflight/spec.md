## ADDED Requirements

### Requirement: checkReleaseReadiness Gradle Task runs 4 preflight checks before release build

The system SHALL provide a custom Gradle Task `checkReleaseReadiness` that executes 4 preflight checks via `ExecOperations`. The Task MUST be registered in `app/build.gradle.kts` and MUST be invoked automatically before any `release` buildType configuration via `dependsOn`.

The 4 preflight checks SHALL be:
1. **No TODO placeholders in English strings** — `grep -rn '__TODO__' app/src/main/res/values-en/strings.xml` MUST return no matches
2. **No plaintext apikey literals in production code** — `grep -rnE '\bapikey\s*=\s*"[a-zA-Z0-9_-]{16,}"' app/src/main/java/com/yy/writingwithai/` MUST return no matches
3. **Backup rules files exist** — both `app/src/main/res/xml/backup_rules.xml` AND `app/src/main/res/xml/data_extraction_rules.xml` MUST exist on disk
4. **ktlint passes** — `./gradlew :app:ktlintCheck` MUST return 0 violations (declared as separate `dependsOn` in `release.buildTypes`, not nested inside `checkReleaseReadiness`)

When any check fails, the Task MUST abort the build with an error message in format `Preflight FAILED [check-N]: file:line — pattern` where `check-N` corresponds to the 1~4 numbered check above.

#### Scenario: All 4 preflight checks pass
- **WHEN** `./gradlew :app:assembleRelease` runs on a project with no `__TODO__` placeholders, no plaintext apikey literals, both backup_rules files present, and 0 ktlint violations
- **THEN** `checkReleaseReadiness` completes successfully and `assembleRelease` proceeds to APK generation

#### Scenario: TODO placeholder in English strings blocks release
- **WHEN** `app/src/main/res/values-en/strings.xml` contains `<string name="example">__TODO__</string>`
- **THEN** `checkReleaseReadiness` fails with `Preflight FAILED [check-1]: app/src/main/res/values-en/strings.xml:LINE — __TODO__` and `assembleRelease` aborts before APK generation

#### Scenario: Plaintext apikey literal blocks release
- **WHEN** `app/src/main/java/com/yy/writingwithai/` contains a Kotlin file with `val apikey = "sk-abcdef1234567890abcdef12"`
- **THEN** `checkReleaseReadiness` fails with `Preflight FAILED [check-2]: FILE_PATH:LINE — \bapikey\s*=\s*"..."` and `assembleRelease` aborts

#### Scenario: Missing backup_rules.xml blocks release
- **WHEN** `app/src/main/res/xml/backup_rules.xml` does not exist on disk
- **THEN** `checkReleaseReadiness` fails with `Preflight FAILED [check-3]: backup_rules.xml missing` and `assembleRelease` aborts

#### Scenario: ktlint violation blocks release via separate dependsOn
- **WHEN** `./gradlew :app:ktlintCheck` reports 1+ violations
- **THEN** the `dependsOn("ktlintCheck")` chain on `release.buildTypes` fails with ktlint's own error format and `assembleRelease` aborts before `checkReleaseReadiness` runs (Gradle dependency order)

#### Scenario: Debug builds skip preflight
- **WHEN** `./gradlew :app:assembleDebug` runs
- **THEN** `checkReleaseReadiness` is NOT invoked (only `release.buildTypes` declares `dependsOn`, not `debug.buildTypes`); debug builds proceed normally even if preflight checks would fail

### Requirement: Parsing logic for grep output is unit-testable

The internal logic that parses `grep -rn` output into a list of failure records MUST be implemented as a top-level `internal` function in `app/build.gradle.kts` script body (or a sibling `.kts` file under `app/`) and MUST be independently callable from JVM unit tests in `app/src/test/java/com/yy/writingwithai/buildlogic/`.

#### Scenario: Grep output parsing handles empty input
- **WHEN** `parseGrepOutput(emptyString)` is called
- **THEN** it returns an empty list (no failures)

#### Scenario: Grep output parsing handles multiple lines
- **WHEN** `parseGrepOutput("file1.kt:10: match1\nfile2.kt:20: match2")` is called
- **THEN** it returns 2 records each with `file`, `line`, `match` fields populated