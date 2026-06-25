## ADDED Requirements

### Requirement: Push local note to Feishu

The system MUST provide a `FeishuSyncRepository.push(noteId)` action that creates a new Feishu document if none exists, or updates the existing one referenced in `feishu_ref`. The action MUST be triggered manually only; no auto-push.

#### Scenario: First push creates new Feishu doc
- **WHEN** user taps "同步到飞书" on a note with no existing `feishu_ref`
- **THEN** the system MUST call `FeishuApiClient.createDocument(note.title)` → write `feishu_ref(docId, docUrl, status=SYNCED)` → append Markdown blocks via `appendChildren`

#### Scenario: Subsequent push updates existing doc
- **WHEN** user taps "同步到飞书" on a note with existing `feishu_ref`
- **THEN** the system MUST delete old blocks via `batch_delete` and append new blocks from current Markdown

#### Scenario: Push does not consume AI token
- **WHEN** push executes
- **THEN** NO AI provider call is made; only `FeishuApiClient` calls are issued

### Requirement: Pull Feishu doc into local note

The system MUST provide `FeishuSyncRepository.pull(docUrl)` that fetches the document blocks and creates or updates a local note.

#### Scenario: Pull creates new local note
- **WHEN** user provides a `docUrl` not yet referenced by any `feishu_ref`
- **THEN** the system MUST `GET /docx/v1/documents/{docId}/blocks` → convert to Markdown → create `NoteEntity(title, content)` → write `feishu_ref(syncDirection=PULL)`

#### Scenario: Pull updates existing local note
- **WHEN** user pulls a docId already referenced and the local note is unmodified
- **THEN** the system MUST overwrite the local note content with the pulled Markdown

#### Scenario: Pull does not consume AI token
- **WHEN** pull executes
- **THEN** NO AI provider call is made

### Requirement: Conflict detection and resolution

When `localRevision > storedRemoteRevision` AND `newRemoteRevision > storedRemoteRevision` simultaneously, the system MUST mark the `feishu_ref.status = CONFLICT` and present a 3-option dialog: keep local, keep Feishu, cancel.

#### Scenario: Local-only change with remote stale
- **WHEN** only the local note changed since last sync
- **THEN** the push MUST proceed without conflict; remote is overwritten with local

#### Scenario: Remote-only change with local stale
- **WHEN** only the remote document changed since last sync
- **THEN** the pull MUST proceed without conflict; local is overwritten

#### Scenario: Both sides changed
- **WHEN** both local and remote changed since last sync
- **THEN** the system MUST mark `CONFLICT` and show resolution dialog with default selection "保留飞书"

#### Scenario: User picks keep local
- **WHEN** user picks "保留本地" in conflict dialog
- **THEN** `feishu_ref.status = DIRTY`; next push will overwrite remote

#### Scenario: User picks keep remote
- **WHEN** user picks "保留飞书" in conflict dialog
- **THEN** local content is replaced with remote; `feishu_ref.status = SYNCED`

### Requirement: Empty remote protection

If the Feishu document has no content blocks, push MUST be refused with a user-visible warning that the remote is empty (avoid accidental overwrite of the local note with empty content).

#### Scenario: Push to empty remote refused
- **WHEN** user pushes a note to a Feishu doc whose block list is empty
- **THEN** the system MUST show "飞书端为空，不覆盖本地" and MUST NOT clear the local content

### Requirement: Remote deletion handling

If `FeishuApiClient` returns 404 for a docId during push or pull, the system MUST mark `feishu_ref.status = REMOTE_DELETED` and surface a "远程已删除" indicator in the list page.

#### Scenario: Push to deleted remote
- **WHEN** push receives 404 from FeishuApiClient
- **THEN** `feishu_ref.status = REMOTE_DELETED`; the note remains local-only

#### Scenario: User re-syncs as new document
- **WHEN** user taps "重新同步为新文档" on a REMOTE_DELETED note
- **THEN** the old `feishu_ref` row is deleted and a fresh push creates a new Feishu doc

### Requirement: List page status indicators

The note list MUST display a Feishu status indicator per note that has a `feishu_ref` row: SYNCED (gray icon), DIRTY (yellow "待同步"), CONFLICT (red "冲突"), REMOTE_DELETED (gray strikethrough "远程已删").

#### Scenario: Note with CONFLICT shows red chip
- **WHEN** a note has `feishu_ref.status = CONFLICT`
- **THEN** the list row MUST show a red "冲突" chip

### Requirement: Sync event log

The system MUST persist each sync operation as a `FeishuSyncEventEntity` row. The settings page MUST display the last 20 events sorted desc by createdAt. Events MUST be capped at 100 total.

#### Scenario: Sync event persisted
- **WHEN** a push or pull operation completes
- **THEN** a new `FeishuSyncEventEntity` row MUST be inserted with the outcome

#### Scenario: Log capped at 100 events
- **WHEN** more than 100 sync events exist
- **THEN** the oldest events MUST be deleted so the total never exceeds 100

### Requirement: Manual-only trigger

No automatic push or pull MUST occur. Every sync operation MUST be initiated by an explicit user tap on "同步到飞书" / "从飞书链接拉取" / "重新同步为新文档".

#### Scenario: Note save does not auto-sync
- **WHEN** a note is saved or modified locally
- **THEN** NO sync call is made; `feishu_ref.status` may transition to DIRTY but no network request fires

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

## ADDED Requirements

### Requirement: FeishuSyncService.pull writes note + ref in single transaction

`core/feishu/sync/FeishuSyncService.pull(docId)` MUST write the local `NoteEntity` (with tags) AND the corresponding `FeishuRefEntity` row in a single Room `@Transaction`-annotated DAO method. If the process crashes between the two writes, the next pull must observe either both or neither (no orphan note without ref, no orphan ref without note).

#### Scenario: Pull succeeds atomically
- **WHEN** `pull("doc123")` completes
- **THEN** both `notes` row `(id = "doc123")` and `feishu_refs` row `(noteId = "doc123")` are persisted; query either table returns the matching row

#### Scenario: Crash mid-pull leaves no orphan
- **WHEN** `pull("doc456")` is interrupted (process killed) between `noteDao.upsert` and `refDao.upsert` (legacy non-transactional code)
- **THEN** with the fix in place, Room rolls back the note write; neither table has `doc456`