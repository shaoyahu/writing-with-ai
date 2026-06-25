## ADDED Requirements

### Requirement: UpdateDownloadReceiver SHA-256 off main thread

`core/update/UpdateDownloadReceiver.onReceive(context, intent)` MUST NOT run SHA-256 on the system broadcast thread (causes ANR for > 50 MB APK). The receiver MUST call `goAsync()` to obtain a `PendingResult`, then `withContext(Dispatchers.IO)` for the SHA computation, then `pendingResult.finish()` once done.

#### Scenario: SHA computation off main
- **WHEN** APK download completes and receiver is invoked
- **THEN** grep `UpdateDownloadReceiver.kt` shows `goAsync()` + `Dispatchers.IO`; main thread returns within milliseconds

#### Scenario: goAsync timeout handled
- **WHEN** SHA computation exceeds 10s (Android `goAsync` limit)
- **THEN** receiver logs `WARN: SHA timeout` and finishes the pending result without installing