## ADDED Requirements

### Requirement: AttachmentStore id validation and path containment

`core/media/AttachmentStore` MUST validate every `noteId`, `attachmentId`, and `extension` parameter against the allow-list `Regex("^[A-Za-z0-9_-]{1,64}$")` (extension may also contain `.` before the suffix). For `getAttachmentFile`, `save`, `delete`, and `deleteAllForNote`, the resolver MUST compute the canonical path of the final `File` and verify it is either equal to or contained under `attachmentsDir.canonicalPath`. If validation fails or path escapes the root, the function MUST throw `IllegalArgumentException` (caught at the I/O boundary and surfaced as `Result.failure`).

#### Scenario: Valid id resolves
- **WHEN** `getAttachmentFile(noteId = "n1", attachmentId = "a1", extension = "jpg")`
- **THEN** returns `File(attachmentsDir, "n1/a1.jpg")` without throwing

#### Scenario: noteId with slash rejected
- **WHEN** `getAttachmentFile(noteId = "../etc", attachmentId = "a1", extension = "jpg")`
- **THEN** throws `IllegalArgumentException("noteId must match SAFE_ID")` and no file is read

#### Scenario: Path traversal in attachmentId rejected
- **WHEN** `delete(noteId = "n1", attachmentId = "../../../sensitive", extension = "jpg")`
- **THEN** throws `IllegalArgumentException` after canonical-path check and no file outside `attachmentsDir` is deleted

#### Scenario: deleteAllForNote validated
- **WHEN** `deleteAllForNote(noteId = "..")` is called
- **THEN** throws `IllegalArgumentException` BEFORE `deleteRecursively()` runs

### Requirement: WebDavSyncEngine push/pull returns Unsupported for unimplemented backend

`core/sync/WebDavSyncEngine` push and pull MUST return `SyncResult.Unsupported(reason: String)` rather than `SyncResult.Failure(cause)` while the WebDAV backend is unimplemented (B5b). This separates "feature not yet enabled" from "feature failed (network / credentials / conflict)". UI surfaces (e.g. Settings > Cloud Sync status) MUST distinguish the two cases: `Unsupported` displays a localized "未启用" message; `Failure` displays the cause detail.

#### Scenario: Push on stub returns Unsupported
- **WHEN** `WebDavSyncEngine.push(notes)` is called before B5b is merged
- **THEN** returns `SyncResult.Unsupported(reason = "WebDAV sync not implemented yet (B5b)")`

#### Scenario: Pull on stub returns Unsupported
- **WHEN** `WebDavSyncEngine.pull(since)` is called before B5b is merged
- **THEN** returns `SyncResult.Unsupported(reason = "WebDAV sync not implemented yet (B5b)")`

#### Scenario: SyncResult sealed adds Unsupported case
- **WHEN** reading `core/sync/SyncTypes.kt`
- **THEN** `SyncResult` sealed interface includes a `data class Unsupported(val reason: String) : SyncResult` case