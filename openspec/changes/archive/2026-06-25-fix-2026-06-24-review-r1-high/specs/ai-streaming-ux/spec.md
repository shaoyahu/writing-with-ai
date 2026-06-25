## ADDED Requirements

### Requirement: AiwritingEntry noteId reaches AiActionViewModel via SavedStateHandle

`feature/aiwriting/AiwritingEntry(noteId: String, ...)` Composable MUST pass `noteId` into `AiActionViewModel` via Hilt's `SavedStateHandle["noteId"]` (rather than only through the Composable parameter list, which `hiltViewModel()` silently drops). The ViewModel MUST read the parameter from `SavedStateHandle.get<String>("noteId")` in its constructor block.

#### Scenario: AiwritingEntry launched with noteId
- **WHEN** caller invokes `AiwritingEntry(noteId = "n1", ...)` from `QuickNoteDetailScreen`
- **THEN** the resolved `AiActionViewModel.lastNoteId` equals `"n1"` and `start(op, sourceText, noteId = "n1")` runs with the correct noteId for ai_metadata update