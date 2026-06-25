## ADDED Requirements

### Requirement: Wikilink autocomplete prefix recomputed on content change

`feature/quicknote/edit/QuickNoteEditorScreen` wikilink autocomplete MUST recompute `lastOpen` (the index of `[[` in the current content) when `content` state changes via `remember(content) { content.lastIndexOf("[[") }`. The `onSelect` callback MUST derive the prefix from the *current* `content` snapshot at click time, not from any captured `lastOpen` value at recompute time. This prevents stale-prefix corruption when user types between recompute and selection.

#### Scenario: Content typed between recompute and select
- **WHEN** `lastOpen` is computed for content "abc [[" and then user types "def" before clicking an autocomplete suggestion
- **THEN** `onSelect` derives prefix from current content "abc [[def" (not from stale "abc [[")

### Requirement: QuickNoteDetailViewModel exposes feishuRef as StateFlow

`feature/quicknote/detail/QuickNoteDetailViewModel` MUST expose `_feishuRef: StateFlow<FeishuRefEntity?>` derived from `refDao.observeForNote(noteId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)` rather than a one-shot `getRef(id)` call. The chip in the detail UI MUST update when sync / pull / push operations modify the ref row.

#### Scenario: Push changes ref state visible
- **WHEN** user taps "同步到飞书" and `FeishuSyncService.push(noteId)` writes a new `feishu_ref` row
- **THEN** the detail screen's feishu chip transitions from "未同步" to "已同步" without manual refresh