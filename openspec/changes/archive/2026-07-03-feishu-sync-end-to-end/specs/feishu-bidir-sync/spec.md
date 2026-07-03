## MODIFIED Requirements

### Requirement: Sync engine is reachable from QuickNote detail UI

QuickNote 详情 / 列表 UI MUST 把 `core/feishu/sync/FeishuSyncService` 的 push / pull / ref 查询能力端到端暴露给用户:详情 TopAppBar overflow menu 提供 "推送到飞书" / "从飞书拉取 URL" 入口，列表项右侧按 `FeishuRefEntity.status` 渲染 `AssistChip`，冲突场景弹 `FeishuConflictDialog`(3 选项强制选择)。

#### Scenario: Detail page TopAppBar menu exposes push entry
- **WHEN** user on `QuickNoteDetailScreen` viewing saved note
- **THEN** TopAppBar overflow menu MUST contain "推送到飞书" item calling `FeishuShareViewModel.push(noteId)`;success → chip 状态转 SYNCED
- **AND** menu MUST contain "从飞书拉取 URL" item opening `FeishuShareDialog` for URL input

#### Scenario: List item chip displays feishu ref state
- **WHEN** `QuickNoteListScreen` renders note with `FeishuRefEntity`
- **THEN** row MUST display `AssistChip` colored per status:SYNCED → tertiaryContainer / CONFLICT → errorContainer / REMOTE_DELETED → surfaceVariant / PENDING → secondaryContainer
- **AND** notes without `FeishuRefEntity` MUST NOT display chip

#### Scenario: Conflict resolution dialog requires explicit user choice
- **WHEN** `FeishuShareViewModel.pull` returns `SyncResult.Conflict`
- **THEN** `FeishuConflictDialog` MUST show with 3 options:"保留本地" / "保留远端" / "取消";no option preselected;user MUST tap one to dismiss
- **AND** "保留本地" overwrites remote with local;"保留远端" overwrites local with remote;"取消" leaves both sides unchanged