## 1. SyncMessage sealed 扩展

- [x] 1.1 扩 `SyncMessage` sealed：把 `Failure(reason: String)` 换成 `Failure` 嵌套 sealed interface，下含 8 个 case（Conflict / FolderMigration / RemoteDeleted / Empty / Network / Server / RateLimited / Unknown）
- [x] 1.2 把 `Success(docUrl)` 换成 `Success(noteTitle: String, docUrl: String)`
- [x] 1.3 在 `QuickNoteDetailViewModel` 加 `_lastSyncAction: SyncAction` 私有 mutableStateOf（`SyncAction` 是 PUSH / PULL(docUrl) sealed）
- [x] 1.4 加 `retryLastSync()` 方法：根据 `_lastSyncAction` 调 `pushToFeishu()` 或 `pullFromFeishu(docUrl)`
- [x] 1.5 `pushToFeishu()` 开头写 `_lastSyncAction = SyncAction.PUSH`
- [x] 1.6 `pullFromFeishu(docUrl)` 开头写 `_lastSyncAction = SyncAction.PULL(docUrl)`
- [x] 1.7 改 `pushToFeishu()` / `pullFromFeishu()` / `resolveConflictKeepLocal()` / `resolveConflictKeepRemote()` / `resolveFolderMigration()` 的 catch 块：每个 `FeishuError` 子类型映射到对应 `SyncMessage.Failure` 子类型（按 D1 表）
- [x] 1.8 catch 兜底 `Throwable` 改为 emit `Failure.Unknown(cause.toString())`

## 2. i18n 字符串

- [x] 2.1 `values/strings.xml` 加 23 个 `feishu_sync_*` key（中文权威值）
- [x] 2.2 `values-en/strings.xml` 加同样 23 个 key，英文用占位 `TODO(en): feishu_sync_*`
- [x] 2.3 跑 `./gradlew :app:ktlintCheck` 确认 ExtraTranslation Lint 不报错

## 3. 成功 Snackbar 渲染

- [x] 3.1 `QuickNoteDetailScreen` 的 `LaunchedEffect(syncMessage)`：检测 `is SyncMessage.Success` 时调 `snackbarHostState.showSnackbar(message = context.getString(R.string.feishu_sync_success_fmt, msg.noteTitle), actionLabel = context.getString(R.string.feishu_sync_action_copy), duration = SnackbarDuration.Short)`
- [x] 3.2 `SnackbarResult.ActionPerformed` 分支调 `ClipboardManager.setPrimaryClip(ClipData.newPlainText("Feishu doc URL", msg.docUrl))` + 再发"已复制链接" Snackbar
- [x] 3.3 用 `snackbarScope.launch` cancel 前一个 job，避免连续弹 Snackbar 排队（同 decomposeSnackbarJob 模式）
- [x] 3.4 Snackbar 显示后调 `viewModel.clearSyncMessage()`，避免 syncMessage 重新消费

## 4. 失败 Dialog 渲染

- [x] 4.1 新建 `feature/quicknote/detail/SyncFailureDialog.kt`，定义 8 个 Composable：`ConflictFailureDialog` / `FolderMigrationFailureDialog` / `RemoteDeletedFailureDialog` / `EmptyFailureDialog` / `NetworkFailureDialog` / `ServerFailureDialog` / `RateLimitedFailureDialog` / `UnknownFailureDialog`
- [x] 4.2 每个 Composable 接 typed Failure case 参数 + onDismiss + onRetry/onCopy/onRecreate 回调
- [x] 4.3 `QuickNoteDetailScreen` 主体的 syncMessage 处理 `when` 分两段：顶层 `is Success` → 走 Snackbar 路径；`is Failure` → nested when 调对应 Dialog Composable
- [x] 4.4 `ConflictFailureDialog` 复用 `FeishuConflictDialog` 内部渲染（直接 delegate），不重写
- [x] 4.5 `FolderMigrationFailureDialog` 复用 `FolderMigrationDialog` 内部渲染
- [x] 4.6 `RemoteDeletedFailureDialog` 主按钮调 `viewModel.recreateFeishuDoc()`
- [x] 4.7 `NetworkFailureDialog` / `ServerFailureDialog` 主按钮"重试"调 `viewModel.retryLastSync()`
- [x] 4.8 `UnknownFailureDialog` 主按钮"复制错误"调 `ClipboardManager.setPrimaryClip`
- [x] 4.9 任一 Dialog 关闭（确认 / dismiss / onCancel）调 `viewModel.clearSyncMessage()` 清空 syncMessage

## 5. 清理旧代码

- [x] 5.1 `QuickNoteDetailScreen` 删 `var showSyncMessageDialog by remember { mutableStateOf(false) }`
- [x] 5.2 删旧的 success/failure AlertDialog Composable
- [x] 5.3 删 `_syncMessage.value = SyncMessage.Failure("...")` 的所有 string-typed Failure 调用点
- [x] 5.4 grep `SyncMessage.Failure(reason` / `SyncMessage.Success(docUrl)` 确认 0 hit

## 6. 编译 + 单测验证

- [x] 6.1 `./gradlew :app:assembleDebug` 通过（kotlinc 检查 when exhaustive）
- [x] 6.2 `./gradlew :app:testDebugUnitTest` 通过
- [x] 6.3 更新 `QuickNoteDetailViewModelTest` 中所有 SyncMessage 断言为新形态
- [x] 6.4 新增测试：`pushToFeishu catches FolderTokenMismatch → emits Failure.FolderMigration` / `pullFromFeishu catches BadRequest 空 → emits Failure.Empty` / `retryLastSync PULL re-invokes pullFromFeishu with saved docUrl` 三个 case
- [x] 6.5 `./gradlew :app:ktlintCheck` 通过
- [x] 6.6 `./gradlew :app:installDebug` 装到模拟器，手动验证：
  - 成功 push → 弹底部 Snackbar，点"复制链接"后弹"已复制链接"短 Snackbar
  - 故意关网再 push → 弹"网络异常" Dialog，点"重试"重新 push
  - 故意触发 folder migration（删 ref.folderToken 或改设置）→ 弹 FolderMigration Dialog
  - 故意触发 NotFound（远端 docId 改为不存在）→ 弹"飞书文档已删除" Dialog，点"重新同步为新文档"