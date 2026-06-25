## ADDED Requirements

### Requirement: WritingApp.onCreate does not block on DataStore IO

`app/WritingApp.onCreate()` MUST NOT use `runBlocking` for `ConsentStore.setAccepted(...)` or any other DataStore read/write. Instead, the application MUST own a long-lived `CoroutineScope(SupervisorJob() + Dispatchers.IO)` and `launch` the consent-state write. App cold start MUST NOT be blocked by DataStore disk I/O.

#### Scenario: Application init is non-blocking
- **WHEN** `./gradlew :app:assembleDebug` build APK cold-starts
- **THEN** grep `WritingApp.kt` for `runBlocking` MUST return 0 matches; onCreate returns within milliseconds

#### Scenario: Consent write completes async
- **WHEN** `onCreate` fires `consentStore.setAccepted(version)`
- **THEN** write happens on `Dispatchers.IO`; main thread already returned to caller