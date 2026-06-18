## MODIFIED Requirements

### Requirement: Note entity schema

系统 MUST 在 Room `notes` 表中持久化 `Note` 实体,M1 已有 `lastAiOp` / `lastAiAt` 字段但始终为 null。M2 修改行为:**当 AiGateway 完成一次 stream 后,系统 MUST 更新该 Note 的 `lastAiOp` 为操作类型字符串(`"expand"` / `"polish"` / `"organize"`) 和 `lastAiAt` 为当前 epoch millis**。

#### Scenario: AI operation completes, metadata written
- **WHEN** `AiGateway.streamWritingOp(op=EXPAND, sourceText="...", ...)` 完成(Done event)
- **THEN** `notes` 表中对应行的 `lastAiOp="expand"` 且 `lastAiAt=<completionTime>`

#### Scenario: AI operation fails, metadata not written
- **WHEN** `AiGateway.streamWritingOp(...)` 只收到 Failed(未收到 Done)
- **THEN** `notes` 表的 `lastAiOp` / `lastAiAt` 保持原值(不被 Failed 覆盖)

### Requirement: Note CRUD via Repository

系统 MUST 在 `NoteRepository` 中新增 `updateAiMetadata(noteId: String, op: String, at: Long)`。此方法与 `streamWritingOp(...)` 的 Done 事件配对调用:Gateway 在完成时调 repo 写字段。其余 CRUD 行为不变。

#### Scenario: updateAiMetadata sets fields
- **WHEN** 调用 `NoteRepository.updateAiMetadata(noteId="abc", op="polish", at=1700000000000L)`
- **THEN** `notes` 表中 `id="abc"` 的行 `lastAiOp="polish"`,`lastAiAt=1700000000000L`
