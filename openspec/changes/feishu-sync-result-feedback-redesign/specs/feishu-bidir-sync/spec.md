## MODIFIED Requirements

### Requirement: SyncMessage sealed interface expanded to typed Failure subtypes

The previous `SyncMessage` shape was:
```kotlin
sealed interface SyncMessage {
    data class Success(val docUrl: String) : SyncMessage
    data class Failure(val reason: String) : SyncMessage
}
```

After this change, the shape MUST be:
```kotlin
sealed interface SyncMessage {
    data class Success(val noteTitle: String, val docUrl: String) : SyncMessage
    sealed interface Failure : SyncMessage {
        data class Conflict(val noteId: String, val docId: String, val docUrl: String) : Failure
        data class FolderMigration(
            val noteId: String,
            val docId: String,
            val docUrl: String,
            val currentFolderToken: String?,
            val refFolderToken: String?
        ) : Failure
        data class RemoteDeleted(val noteId: String, val docId: String, val docUrl: String) : Failure
        data object Empty : Failure
        data class Network(val detail: String) : Failure
        data class Server(val code: Int) : Failure
        data class RateLimited(val retryAfterSeconds: Int) : Failure
        data class Unknown(val cause: String) : Failure
    }
}
```

`QuickNoteDetailViewModel` catch blocks MUST map `FeishuError` subtypes 1:1 to `SyncMessage.Failure` subtypes:

| FeishuError catch | SyncMessage.Failure |
| --- | --- |
| `FeishuError.Conflict` | `Failure.Conflict` |
| `FeishuError.FolderTokenMismatch` | `Failure.FolderMigration` |
| `FeishuError.NotFound` (from `updateDoc`) | `Failure.RemoteDeleted` |
| `FeishuError.BadRequest` with msg "飞书端为空" | `Failure.Empty` |
| `FeishuError.NetworkError` | `Failure.Network(detail)` |
| `FeishuError.ServerError` | `Failure.Server(code)` |
| `FeishuError.RateLimited` | `Failure.RateLimited(retryAfterSeconds)` |
| any other `Throwable` (not CancellationException) | `Failure.Unknown(cause.toString())` |

The previous `Failure(reason: String)` flat form is removed. Callers MUST switch on the typed subtype.

#### Scenario: all FeishuError subtypes map 1:1
- **WHEN** the new `QuickNoteDetailViewModel` is compiled
- **THEN** each catch block above assigns to exactly the listed `Failure` subtype with no String-typed `Failure(reason)` call sites remaining

#### Scenario: typed when is exhaustive
- **WHEN** `QuickNoteDetailScreen` writes `when (val msg = syncMessage) { ... }`
- **THEN** all 9 `SyncMessage` cases (1 Success + 8 Failure) are covered with no `else ->` fallback

## REMOVED Requirements

### Requirement: Push success launches showSyncMessageDialog AlertDialog
**Reason**: Replaced by Snackbar in feishu-sync-feedback spec ("Sync success feedback uses Snackbar, not Dialog"). The `showSyncMessageDialog` boolean state in `QuickNoteDetailScreen` and the AlertDialog that renders docUrl + 关闭/复制 are obsolete.
**Migration**: Remove `var showSyncMessageDialog by remember { mutableStateOf(false) }`. Replace the LaunchedEffect that sets `showSyncMessageDialog = true` with `snackbarHostState.showSnackbar(...)` calls.

### Requirement: Pull success launches showSyncMessageDialog AlertDialog
**Reason**: Same as push success above — Snackbar replaces Dialog for all success feedback.
**Migration**: Same as above.

### Requirement: Sync failure uses generic AlertDialog with single reason text
**Reason**: Replaced by typed `SyncMessage.Failure` subtype-driven Dialog rendering in feishu-sync-feedback spec ("Sync failure feedback uses Dialog with typed classification"). Each `FeishuError` subtype gets its own Dialog copy + actions.
**Migration**: Remove the failure AlertDialog block in `QuickNoteDetailScreen`. Add a `when` block that dispatches to typed failure Dialogs (Conflict / FolderMigration / RemoteDeleted / Empty / Network / Server / RateLimited / Unknown). Existing `FeishuConflictDialog` and `FolderMigrationDialog` continue to render as-is; new typed Dialogs render for the other 5 Failure subtypes.