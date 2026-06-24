## ADDED Requirements

### Requirement: UpdateDownloadReceiver safe filename derivation

`core/update/UpdateDownloadReceiver` MUST NOT derive the install-Intent filename from the server-side `DownloadManager.COLUMN_URI` (which is an HTTP URL whose last path segment is attacker-controlled). Instead, the filename MUST come from a manifest field (`manifest.apkName`) and MUST pass `PathSafety.SAFE_NAME` (`Regex("^[A-Za-z0-9._-]{1,128}$")`). If the manifest does not provide `apkName` or the value fails the regex, the receiver MUST fall back to the literal string `"update.apk"` and log a warning.

#### Scenario: Manifest filename passes
- **WHEN** `AppUpdateManifest.apkName = "writing-with-ai-1.2.3.apk"` and the download completes
- **THEN** the install Intent's `EXTRA_STREAM` `File` resolves to `<external-files>/app-update/writing-with-ai-1.2.3.apk`

#### Scenario: Malicious manifest filename rejected
- **WHEN** `AppUpdateManifest.apkName = "../../../etc/passwd"` (or any value not matching `SAFE_NAME`)
- **THEN** the receiver falls back to `"update.apk"` and logs `Log.w(TAG, "manifest.apkName unsafe, fallback to update.apk")`

#### Scenario: No URL substringAfterLast
- **WHEN** reading `UpdateDownloadReceiver.kt`
- **THEN** grep for `substringAfterLast` against `COLUMN_URI` MUST return 0 matches

### Requirement: UpdateDownloadReceiver cursor getColumnIndex null / range check

`UpdateDownloadReceiver` MUST guard every `cursor.getColumnIndex(...)` call against the documented `-1` return value (when the column is not present in the cursor). Acceptable patterns: `cursor.getColumnIndexOrThrow(COLUMN_URI)` (throws a typed exception caught at the boundary) OR an explicit `if (idx < 0) return / continue / bail` after retrieving the index. Bare `cursor.getString(cursor.getColumnIndex(...))` MUST NOT appear.

#### Scenario: Missing column handled
- **WHEN** the cursor does not contain `COLUMN_URI` for the given download id
- **THEN** the receiver logs `Log.w(TAG, "COLUMN_URI missing")` and exits early without throwing `IndexOutOfBoundsException`

#### Scenario: Valid column proceeds
- **WHEN** the cursor contains `COLUMN_URI` and `COLUMN_LOCAL_URI`
- **THEN** the receiver extracts both values and proceeds to SHA verification

#### Scenario: Lint Range error fixed
- **WHEN** `./gradlew :app:lintDebug` runs
- **THEN** the `Range` error on `UpdateDownloadReceiver.kt:59-60` MUST NOT appear in the lint report