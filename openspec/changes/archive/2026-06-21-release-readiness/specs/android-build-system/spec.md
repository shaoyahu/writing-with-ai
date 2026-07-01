# android-build-system Delta Spec (release-readiness)

## MODIFIED Requirements

### Requirement: Gradle build tasks pass for the debug variant

The Gradle project MUST compile, assemble a debug APK, and run unit tests without errors using JDK 17.

**release-readiness 增量**:release variant 在用户本地配置 `~/.gradle/gradle.properties` 4 个 release 凭据后，`./gradlew :app:assembleRelease` 同样能跑通(debug 与 release 是独立 buildType，本 requirement 主轴不变)。release 配置细节见 [release-readiness spec](../../release-readiness/spec.md)。

#### Scenario: Debug APK assembly succeeds
- **WHEN** the user runs `./gradlew :app:assembleDebug` from the repo root on JDK 17
- **THEN** the task exits with status 0 AND an APK file exists at `app/build/outputs/apk/debug/app-debug.apk`

#### Scenario: Unit tests execute
- **WHEN** the user runs `./gradlew :app:testDebugUnitTest` from the repo root
- **THEN** the task exits with status 0 AND at least one test class from `app/src/test/` is executed

#### Scenario: Release APK assembly succeeds when signing config is present
- **WHEN** the user has configured `~/.gradle/gradle.properties` with `RELEASE_STORE_FILE` / `RELEASE_STORE_PASSWORD` / `RELEASE_KEY_ALIAS` / `RELEASE_KEY_PASSWORD` AND runs `./gradlew :app:assembleRelease`
- **THEN** the task exits with status 0 AND an APK file exists at `app/build/outputs/apk/release/app-release.apk` AND `app/build/outputs/mapping/release/mapping.txt` exists
