## ADDED Requirements

### Requirement: Sync success feedback uses Snackbar, not Dialog

The system MUST surface a successful `FeishuSyncService.push` / `.pull` outcome as a non-blocking Snackbar, not a modal AlertDialog. The Snackbar message MUST be a localized template (e.g. `feishu_sync_success_fmt`) accepting the note title; the action label MUST be "复制链接"; tapping the action copies the resulting Feishu doc URL to the system clipboard. The Snackbar MUST auto-dismiss on the default timeout (≈4 s) and MUST NOT block UI navigation or further edits.

#### Scenario: push success shows Snackbar
- **WHEN** `QuickNoteDetailViewModel.pushToFeishu()` completes with `SyncResult.Ok(docUrl)`
- **THEN** `syncMessage` StateFlow emits `SyncMessage.Success(noteTitle, docUrl)`
- **AND** `QuickNoteDetailScreen` renders a `Snackbar` with `feishu_sync_success_fmt` text
- **AND** the Snackbar action label is `feishu_sync_action_copy`
- **AND** no `AlertDialog` is shown

#### Scenario: Snackbar copy action writes to clipboard
- **WHEN** user taps the "复制链接" action on the success Snackbar
- **THEN** `ClipboardManager.setPrimaryClip(ClipData.newPlainText("Feishu doc URL", docUrl))` is called
- **AND** a follow-up Snackbar (or visual confirmation) shows "已复制链接"

#### Scenario: pull success shows Snackbar with note title
- **WHEN** `pullFromFeishu(docUrl)` completes successfully
- **THEN** `syncMessage` emits `SyncMessage.Success(noteTitle, docUrl)`
- **AND** Snackbar text uses the pulled note's title from `feishu_sync_success_fmt`

### Requirement: Sync failure feedback uses Dialog with typed classification

The system MUST surface any failure from `FeishuSyncService.push` / `.pull` as a modal `AlertDialog`. The Dialog's body text and action buttons MUST be driven by the `SyncMessage.Failure` subtype, NOT a single collapsed `Failure(reason: String)` string. The following subtype → presentation mapping MUST hold:

| SyncMessage.Failure subtype | Dialog title | Body text resource | Primary action | Dismiss action |
| --- | --- | --- | --- | --- |
| `Conflict` | `feishu_sync_dialog_conflict_title` | `feishu_sync_dialog_conflict_body` | "保留本地" / "保留远端" (via existing `FeishuConflictDialog`) | "取消" |
| `FolderMigration` | `feishu_sync_dialog_folder_migration_title` | `feishu_sync_dialog_folder_migration_body` | existing `FolderMigrationDialog` buttons | "取消" |
| `RemoteDeleted` | `feishu_sync_dialog_remote_deleted_title` | `feishu_sync_dialog_remote_deleted_body` | "重新同步为新文档" (calls `viewModel.recreateFeishuDoc()`) | "关闭" |
| `Empty` | `feishu_sync_dialog_empty_title` | `feishu_sync_dialog_empty_body` | n/a | "关闭" |
| `Network` | `feishu_sync_dialog_network_title` | `feishu_sync_dialog_network_body` | "重试" (calls `pushToFeishu()` / `pullFromFeishu(docUrl)` again) | "关闭" |
| `Server` | `feishu_sync_dialog_server_title` | `feishu_sync_dialog_server_body` | "重试" | "关闭" |
| `RateLimited` | `feishu_sync_dialog_rate_limited_title` | `feishu_sync_dialog_rate_limited_body` (with `retryAfterSeconds` placeholder) | "我知道了" | n/a |
| `Unknown` | `feishu_sync_dialog_unknown_title` | `feishu_sync_dialog_unknown_body` (with `cause` placeholder) | "复制错误" (writes `cause` to clipboard) | "关闭" |

#### Scenario: FolderTokenMismatch error becomes FolderMigration Dialog
- **WHEN** `pushToFeishu()` catches `FeishuError.FolderTokenMismatch`
- **THEN** `syncMessage` emits `SyncMessage.Failure.FolderMigration(noteId, docId, docUrl, currentFolderToken, refFolderToken)`
- **AND** existing `FolderMigrationDialog` is rendered (not the generic sync-error Dialog)

#### Scenario: Conflict error opens conflict resolution Dialog
- **WHEN** push or pull catches `FeishuError.Conflict`
- **THEN** `syncMessage` emits `SyncMessage.Failure.Conflict(noteId, docId, docUrl)`
- **AND** existing `FeishuConflictDialog` is rendered

#### Scenario: NotFound (remote deleted) offers re-sync button
- **WHEN** push catches `FeishuError.NotFound` from `updateDoc`
- **THEN** `syncMessage` emits `SyncMessage.Failure.RemoteDeleted(noteId, docId, docUrl)`
- **AND** Dialog body explains "远端文档已被删除"
- **AND** Dialog primary action "重新同步为新文档" calls `viewModel.recreateFeishuDoc()`

#### Scenario: Network error offers retry
- **WHEN** push or pull catches `FeishuError.NetworkError`
- **THEN** `syncMessage` emits `SyncMessage.Failure.Network(detail)`
- **AND** Dialog body explains "网络异常，请检查连接"
- **AND** Dialog primary action "重试" re-invokes the same push or pull

#### Scenario: Server error offers retry
- **WHEN** push or pull catches `FeishuError.ServerError`
- **THEN** `syncMessage` emits `SyncMessage.Failure.Server(code)`
- **AND** Dialog body explains "飞书服务暂时不可用"
- **AND** Dialog primary action "重试" re-invokes the same push or pull

#### Scenario: Empty remote warning is informational
- **WHEN** pull catches `FeishuError.BadRequest("飞书端为空")`
- **THEN** `syncMessage` emits `SyncMessage.Failure.Empty`
- **AND** Dialog body explains "飞书端为空，不会覆盖本地"
- **AND** only "关闭" button is shown

#### Scenario: Rate-limited shows retry-after seconds
- **WHEN** API call catches `FeishuError.RateLimited(retryAfterSeconds)`
- **THEN** `syncMessage` emits `SyncMessage.Failure.RateLimited(retryAfterSeconds)`
- **AND** Dialog body shows "操作过于频繁，请在 {retryAfterSeconds} 秒后重试"

#### Scenario: Unknown error offers copy-cause
- **WHEN** catch block hits an unclassified `Throwable` (not a `FeishuError` subtype)
- **THEN** `syncMessage` emits `SyncMessage.Failure.Unknown(cause.toString())`
- **AND** Dialog primary action "复制错误" writes `cause` to clipboard

### Requirement: SyncMessage sealed interface expands to 9 cases

`SyncMessage` in `QuickNoteDetailViewModel.kt` MUST be a sealed interface with the following cases:
- `Success(noteTitle: String, docUrl: String)`
- `Failure.Conflict(noteId, docId, docUrl)`
- `Failure.FolderMigration(noteId, docId, docUrl, currentFolderToken, refFolderToken)`
- `Failure.RemoteDeleted(noteId, docId, docUrl)`
- `Failure.Empty`
- `Failure.Network(detail: String)`
- `Failure.Server(code: Int)`
- `Failure.RateLimited(retryAfterSeconds: Int)`
- `Failure.Unknown(cause: String)`

`SyncMessage.Failure` MUST be a sealed parent type so each subtype is exhaustive under `when`. The previous `Failure(reason: String)` flat form is **REMOVED** — callers MUST switch on the typed subtype.

#### Scenario: exhaustive when compiles
- **WHEN** `QuickNoteDetailScreen` SwitchMaterial on `SyncMessage` covers all 9 cases
- **THEN** `kotlinc` exits 0 with no "non-exhaustive when" warning

#### Scenario: legacy Failure(reason: String) no longer exists
- **WHEN** grep for `SyncMessage.Failure(`
- **THEN** 0 hits with a `String` parameter; only the typed subtypes remain

### Requirement: i18n strings for sync feedback live in strings.xml

All `feishu_sync_*` UI strings MUST be present in `values/strings.xml` (Chinese, authoritative) and `values-en/strings.xml` (English). The following keys MUST exist:

| key | Chinese value | purpose |
| --- | --- | --- |
| `feishu_sync_success_fmt` | "已同步到飞书: %1$s" | Snackbar text, %1$s = note title |
| `feishu_sync_action_copy` | "复制链接" | Snackbar action |
| `feishu_sync_copied` | "已复制链接" | Confirmation Snackbar after copy |
| `feishu_sync_dialog_conflict_title` | "同步冲突" | Conflict Dialog title |
| `feishu_sync_dialog_conflict_body` | "本地与飞书端都有修改，请选择保留哪一方" | Conflict Dialog body |
| `feishu_sync_dialog_folder_migration_title` | "同步目标文件夹已变更" | FolderMigration Dialog title |
| `feishu_sync_dialog_folder_migration_body` | "该笔记之前已同步到其他位置，当前文件夹设置已变更" | FolderMigration Dialog body |
| `feishu_sync_dialog_remote_deleted_title` | "飞书文档已删除" | RemoteDeleted Dialog title |
| `feishu_sync_dialog_remote_deleted_body` | "远端文档已被删除，是否重新同步为新文档？" | RemoteDeleted Dialog body |
| `feishu_sync_dialog_remote_deleted_action_recreate` | "重新同步为新文档" | RemoteDeleted action |
| `feishu_sync_dialog_empty_title` | "飞书端为空" | Empty Dialog title |
| `feishu_sync_dialog_empty_body` | "飞书端内容为空，不会覆盖本地笔记" | Empty Dialog body |
| `feishu_sync_dialog_network_title` | "网络异常" | Network Dialog title |
| `feishu_sync_dialog_network_body` | "请检查网络连接后重试" | Network Dialog body |
| `feishu_sync_dialog_server_title` | "飞书服务暂不可用" | Server Dialog title |
| `feishu_sync_dialog_server_body` | "飞书服务暂时不可用，请稍后重试" | Server Dialog body |
| `feishu_sync_dialog_rate_limited_title` | "操作过于频繁" | RateLimited Dialog title |
| `feishu_sync_dialog_rate_limited_body` | "请在 %1$d 秒后重试" | RateLimited Dialog body, %1$d = seconds |
| `feishu_sync_dialog_unknown_title` | "同步失败" | Unknown Dialog title |
| `feishu_sync_dialog_unknown_body` | "%1$s" | Unknown Dialog body, %1$s = cause string |
| `feishu_sync_action_retry` | "重试" | Retry action (Network/Server) |
| `feishu_sync_action_close` | "关闭" | Dismiss action |
| `feishu_sync_action_copy_error` | "复制错误" | Unknown action |
| `feishu_sync_action_ack` | "我知道了" | RateLimited action |

#### Scenario: zh locale shows Chinese text
- **WHEN** system language is Chinese
- **THEN** all `feishu_sync_*` strings render in Chinese as listed above

#### Scenario: en locale uses values-en strings
- **WHEN** system language is English
- **THEN** all `feishu_sync_*` strings render the localized English values (no hard-coded Chinese fallback)

## REMOVED Requirements

### Requirement: Generic sync-result AlertDialog on success
**Reason**: Push/pull success now uses Snackbar, not AlertDialog. The old `showSyncMessageDialog` boolean state and the Success AlertDialog that showed docUrl + 关闭/复制按钮 are obsolete.
**Migration**: `QuickNoteDetailScreen` removes `var showSyncMessageDialog by remember { mutableStateOf(false) }`. The launch-effect that opens it on `SyncMessage.Success` becomes a `snackbarHostState.showSnackbar(...)` call. Doc URL is still copyable via Snackbar action.

### Requirement: Collapsed Failure(reason: String) on error
**Reason**: All Failure cases must be typed for the UI to pick the correct Dialog copy and actions. A flat `String` reason gives no signal for which Dialog subtype to render.
**Migration**: Replace `Failure(reason: String)` with the 8 typed subtypes listed above. `QuickNoteDetailViewModel` catch blocks MUST map `FeishuError` subtypes to the matching `SyncMessage.Failure` subtype (1:1 mapping documented in the table above).