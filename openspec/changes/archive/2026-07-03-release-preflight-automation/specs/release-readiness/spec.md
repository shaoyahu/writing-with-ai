## ADDED Requirements

### Requirement: Release build runs checkReleaseReadiness preflight Task

The release `buildType` configuration in `app/build.gradle.kts` MUST declare `dependsOn("checkReleaseReadiness", "ktlintCheck")` so that release APK assembly aborts if any of the 4 preflight checks (TODO placeholders / plaintext apikey literals / backup_rules files / ktlint violations) fail.

The `debug` `buildType` MUST NOT declare `dependsOn("checkReleaseReadiness")` to allow temporary TODO placeholders and experimental code paths during development.

#### Scenario: assembleRelease triggers preflight Task
- **WHEN** `./gradlew :app:assembleRelease` runs
- **THEN** Gradle executes `checkReleaseReadiness` and `ktlintCheck` BEFORE invoking any APK packaging tasks; failures in either Task abort the build before any APK file is written

#### Scenario: assembleDebug does not trigger preflight Task
- **WHEN** `./gradlew :app:assembleDebug` runs
- **THEN** `checkReleaseReadiness` is NOT in the executed Task graph; debug APK packaging proceeds even if preflight conditions would fail

#### Scenario: checkReleaseReadiness Task is registered
- **WHEN** `./gradlew :app:tasks --all` runs
- **THEN** `checkReleaseReadiness` appears in the output Task list under `Verification tasks` group

#### Scenario: Preflight failure messages point to file:line
- **WHEN** preflight check-2 detects a plaintext apikey literal in `app/src/main/java/com/yy/writingwithai/core/ai/Provider.kt` line 42
- **THEN** Gradle output contains `Preflight FAILED [check-2]: app/src/main/java/com/yy/writingwithai/core/ai/Provider.kt:42 — \bapikey\s*=\s*"..."` enabling the developer to locate the violation by file path and line number