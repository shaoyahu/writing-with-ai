## ADDED Requirements

### Requirement: Check for app updates
The system SHALL allow the user to manually check for a newer version of the app by tapping a "检查更新" button on the About screen.

#### Scenario: User checks for update with newer version available
- **WHEN** user taps "检查更新" and remote `versionCode > local `BuildConfig.VERSION_CODE``
- **THEN** system fetches `https://xiaozha.nananxue.cn/app/version.json` via HTTPS
- **AND** parses JSON into `AppUpdateManifest`
- **AND** displays an `UpdateDialog` showing remote `versionName`, `releaseNotes`, and a download button

#### Scenario: User checks for update with no newer version
- **WHEN** user taps "检查更新" and remote `versionCode <= local `BuildConfig.VERSION_CODE``
- **THEN** system shows a non-blocking message "已是最新 v{versionName}"

#### Scenario: User checks for update and network fails
- **WHEN** user taps "检查更新" and the HTTPS request fails with `IOException`
- **THEN** system shows a non-blocking error "检查失败，稍后重试"
- **AND** logs the exception (no PII)

#### Scenario: User checks for update and server returns malformed JSON
- **WHEN** user taps "检查更新" and response body fails `kotlinx.serialization` decode
- **THEN** system shows the same network-failure error message
- **AND** logs the parse exception

### Requirement: Download and verify update APK
The system SHALL download the APK referenced by the manifest and verify its SHA-256 hash before triggering installation.

#### Scenario: User taps download in UpdateDialog
- **WHEN** user taps "下载" in `UpdateDialog`
- **THEN** system calls `DownloadManager.enqueue()` with `apkUrl`, title, description
- **AND** dismisses `UpdateDialog`

#### Scenario: Download completes with matching SHA-256
- **WHEN** `ACTION_DOWNLOAD_COMPLETE` fires for the download ID
- **AND** `MessageDigest("SHA-256")` of the downloaded file matches `manifest.apkSha256`
- **THEN** system builds an `ACTION_VIEW` intent with MIME `application/vnd.android.package-archive`
- **AND** sets `FLAG_GRANT_READ_URI_PERMISSION | FLAG_ACTIVITY_NEW_TASK`
- **AND** calls `startActivity()` to trigger system installer

#### Scenario: Download completes with mismatched SHA-256
- **WHEN** `ACTION_DOWNLOAD_COMPLETE` fires for the download ID
- **AND** SHA-256 hash of the downloaded file DOES NOT match `manifest.apkSha256`
- **THEN** system deletes the downloaded file
- **AND** shows a Toast "下载文件损坏，请重试"

### Requirement: HTTPS-only transport
The system SHALL fetch the version manifest over HTTPS and SHALL NOT fall back to HTTP.

#### Scenario: Manifest URL is HTTPS
- **WHEN** `AppUpdateChecker.fetch()` is called
- **THEN** request URL is `https://xiaozha.nananxue.cn/app/version.json` (no HTTP fallback)

#### Scenario: Cleartext HTTP blocked by manifest
- **WHEN** app builds with `android:usesCleartextTraffic="false"` (already set)
- **THEN** any HTTP request to the update endpoint fails fast

### Requirement: Optional update UX
The system SHALL treat all updates as optional; no update SHALL block app usage or force exit.

#### Scenario: User dismisses UpdateDialog
- **WHEN** user taps "稍后" or "取消" in `UpdateDialog`
- **THEN** dialog closes
- **AND** app continues normally with current installed version

#### Scenario: Manifest with mandatory=true is received
- **WHEN** manifest contains `"mandatory": true`
- **THEN** app shows the dialog with "稍后" button disabled (placeholder behavior; full mandatory UX is a future change)
- **NOTE** this change does not yet implement full mandatory flow; the field is reserved in the schema

### Requirement: Version manifest schema
The server SHALL serve `/app/version.json` with the following JSON schema.

#### Scenario: Valid manifest response
- **WHEN** client receives 200 OK with JSON body
- **THEN** body MUST contain fields: `versionCode` (int), `versionName` (string), `apkUrl` (string, https), `apkSize` (int, bytes), `apkSha256` (string, 64 hex chars), `releaseNotes` (string), `releasedAt` (string, ISO8601)
- **AND** SHOULD contain `minSupportedVersionCode` (int), `mandatory` (bool) — schema permits omission; client treats missing as defaults

### Requirement: Single source of truth for server manifest
The server SHALL derive `version.json` from the on-disk APK directory; no manual edits permitted.

#### Scenario: build-version-json.py scans directory
- **WHEN** operator runs `./build-version-json.py > version.json` on the server
- **THEN** script MUST scan `/var/www/xiaozha/app/download/writing-with-ai-*.apk`
- **AND** MUST pick the entry with the highest numeric `versionCode` as the `latest`
- **AND** MUST compute `apkSha256` via `hashlib.sha256` over the APK file
- **AND** MUST emit one JSON object to stdout, exits non-zero if no APK found

### Requirement: Publish script atomicity
The `publish-release.sh` script SHALL perform all steps in sequence and allow safe re-run on partial failure.

#### Scenario: Publish script steps
- **WHEN** operator runs `./publish-release.sh <versionCode> <versionName> <notes.md> <app-release.apk>`
- **THEN** script sequentially: `scp` APK, `scp` notes, `ssh` `ln -sfn` latest symlink, `ssh` re-run `build-version-json.py > version.json`
- **AND** script exits non-zero on any step failure (no half-published state)
- **AND** re-running with the same `versionCode` overwrites cleanly (idempotent)

### Requirement: HTTP cache for manifest
The server SHALL cache `version.json` for 5 minutes at the Nginx layer to reduce repeated fetches.

#### Scenario: Nginx serves version.json
- **WHEN** client requests `/app/version.json`
- **THEN** Nginx returns `Cache-Control: public, max-age=300`
- **AND** APK files in `/app/download/*.apk` are served with `Cache-Control: no-cache, no-store, must-revalidate`

### Requirement: Server release checklist
The system SHALL provide a release checklist document describing the manual publish flow.

#### Scenario: Operator follows release-checklist.md
- **WHEN** operator publishes a new APK
- **THEN** checklist covers: assembleRelease → write release-notes → publish-release.sh → curl verify 200 → install on test device → tap "检查更新" e2e