## Why

当前飞书同步结果反馈只有两种形态：`SyncMessage.Success(docUrl)` 和 `SyncMessage.Failure(reason)`，UI 端对两者都弹 `AlertDialog` 展示，**所有错误都折叠成一行文案**（"同步冲突:请选择保留本地或远端" / "同步目标文件夹已变更" / `mapToUserMessage(e)`），用户看不到具体原因，也得不到可执行的下一步操作建议。

成功路径更成问题：每次 push/pull 成功都强制弹一个 AlertDialog 让用户看 docUrl + 复制/关闭，**打断写作流程** —— 用户绝大多数时候不需要看 docUrl，需要时自己点右上角菜单"查看飞书文档"即可。

## What Changes

- **SyncMessage sealed 扩成多 case**：从 `Success(docUrl) / Failure(reason)` 扩成 `Success(noteTitle, docUrl) / Failure`（`Failure` 拆成 `Conflict` / `FolderMigration` / `RemoteDeleted` / `Empty` / `Network` / `Server` / `RateLimited` / `Unknown(cause)`），类型与 FeishuError 一一映射。
- **UI 渲染分流**：成功用 `Snackbar`（带"复制链接" action），失败用 `AlertDialog`，按 `Failure` 子类型给分类文案 + 操作建议。
- **strings.xml 加 8 个左右 key**：`feishu_sync_success_fmt` / `feishu_sync_error_conflict` / `feishu_sync_error_folder_migration` / `feishu_sync_error_remote_deleted` / `feishu_sync_error_empty` / `feishu_sync_error_network` / `feishu_sync_error_server` / `feishu_sync_error_rate_limited` / `feishu_sync_action_retry` / `feishu_sync_action_copy` 等。
- **QuickNoteDetailScreen 的 syncMessage 处理改为单 LaunchedEffect 分流**，不再每次都 `showSyncMessageDialog`。

## Capabilities

### New Capabilities

- `feishu-sync-feedback`: 飞书同步 push/pull 结果对用户的呈现契约 —— 成功走 Snackbar 不打断流程，失败走 Dialog 按异常类型给具体原因和操作建议。

### Modified Capabilities

- `feishu-bidir-sync`: `SyncMessage` sealed 形态从 2 case 扩成 9 case（Success + 8 Failure 子类型），影响 `QuickNoteDetailViewModel` 的 `syncMessage` StateFlow 形态以及 `QuickNoteDetailScreen` 的渲染分支。

## Impact

- **代码**：`feature/quicknote/detail/QuickNoteDetailViewModel.kt`（SyncMessage sealed + 7 个 catch 块改派发到对应 Failure 子类型）、`feature/quicknote/detail/QuickNoteDetailScreen.kt`（LaunchedEffect 分流 + Snackbar/Dialog 渲染）、`app/src/main/res/values/strings.xml`（8-10 个 i18n key）。
- **测试**：`QuickNoteDetailViewModel` 的同步测试需要新增"catch FolderTokenMismatch → Failure.FolderMigration"等场景。
- **API**：`SyncMessage` sealed interface 是 `QuickNoteDetailViewModel` 对外的契约，扩 case 不破坏现有调用方（都是 sealed，编译器会指认未处理 case）。
- **依赖**：无外部依赖变动。