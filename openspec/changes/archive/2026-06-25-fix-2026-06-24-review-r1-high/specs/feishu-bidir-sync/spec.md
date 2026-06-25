## ADDED Requirements

### Requirement: FeishuSyncService.pull writes note + ref in single transaction

`core/feishu/sync/FeishuSyncService.pull(docId)` MUST write the local `NoteEntity` (with tags) AND the corresponding `FeishuRefEntity` row in a single Room `@Transaction`-annotated DAO method. If the process crashes between the two writes, the next pull must observe either both or neither (no orphan note without ref, no orphan ref without note).

#### Scenario: Pull succeeds atomically
- **WHEN** `pull("doc123")` completes
- **THEN** both `notes` row `(id = "doc123")` and `feishu_refs` row `(noteId = "doc123")` are persisted; query either table returns the matching row

#### Scenario: Crash mid-pull leaves no orphan
- **WHEN** `pull("doc456")` is interrupted (process killed) between `noteDao.upsert` and `refDao.upsert` (legacy non-transactional code)
- **THEN** with the fix in place, Room rolls back the note write; neither table has `doc456`