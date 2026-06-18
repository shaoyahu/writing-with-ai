## ADDED Requirements

### Requirement: Test framework dependencies are wired

The `app/build.gradle.kts` MUST declare JUnit5 (Jupiter API + engine), MockK, Turbine, and `androidx.compose.ui:ui-test-junit4` as test dependencies; `./gradlew :app:testDebugUnitTest` MUST find and run Jupiter tests.

#### Scenario: Jupiter engine on test classpath
- **WHEN** `./gradlew :app:testDebugUnitTest` is executed
- **THEN** the build log shows JUnit Jupiter engine being used AND a `*.class` under `app/src/test/` annotated `@Test` from `org.junit.jupiter.api` is executed

#### Scenario: MockK available
- **WHEN** a test file imports `io.mockk.mockk` and uses it in a test method
- **THEN** the test compiles AND runs without `ClassNotFoundException`

#### Scenario: Turbine available
- **WHEN** a test file imports `app.cash.turbine.test`
- **THEN** the test compiles AND runs without `ClassNotFoundException`

#### Scenario: Compose Test available
- **WHEN** a test file imports `androidx.compose.ui.test.junit4.createComposeRule`
- **THEN** the test file compiles inside `app/src/androidTest/`

### Requirement: Placeholder test passes

A trivial test MUST exist under `app/src/test/` and pass under `./gradlew :app:testDebugUnitTest`, proving the test task works end-to-end.

#### Scenario: Sample test passes
- **WHEN** `./gradlew :app:testDebugUnitTest` runs
- **THEN** at least one `@Test` method executes AND the test result is `SUCCESSFUL` AND the task exits with status 0

#### Scenario: Sample test uses JUnit Jupiter
- **WHEN** the placeholder test file is inspected
- **THEN** it imports `org.junit.jupiter.api.Test` AND `org.junit.jupiter.api.Assertions.assertEquals` (proving Jupiter, not JUnit4, is the active framework)