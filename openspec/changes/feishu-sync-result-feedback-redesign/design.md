## Context

当前 `SyncMessage` 是 `Success(docUrl) / Failure(reason: String)` 两态。`QuickNoteDetailScreen` 在 `LaunchedEffect(syncMessage)` 里检测到非空值就 `showSyncMessageDialog = true` 弹 AlertDialog：

```kotlin
LaunchedEffect(syncMessage) {
    val msg = syncMessage ?: return@LaunchedEffect
    if (msg is SyncMessage.Success || msg is SyncMessage.Failure) {
        showSyncMessageDialog = true
    }
}
```

Dialog body 对成功显示完整 docUrl + 关闭/复制按钮，对失败显示一行 `msg.reason` 字符串 + 关闭/复制错误按钮。问题：

1. **成功打断写作**：每次 push/pull 都强制 modal，用户不需要看 docUrl 也要点关闭才能继续。
2. **失败信息折叠**：所有异常类型压成一行，folder migration / conflict / remote-deleted / network 这些完全不同的恢复路径用同一行文案。
3. **失败无可执行操作**：除了"复制错误"，用户没别的出路。

约束：

- 现有 `FeishuConflictDialog` 和 `FolderMigrationDialog` 已经处理了 Conflict 和 FolderMigration 两个具体场景的 UI（3 选项强制选择），不能丢。新 Dialog 层是补充，不是替代。
- `SyncMessage` 是 `QuickNoteDetailViewModel` 对外暴露的 StateFlow 契约，扩 sealed 不破坏调用方。
- i18n 走 `stringResource(R.string.*)` + `values/strings.xml`（中文权威）+ `values-en/strings.xml`（英文）。

## Goals / Non-Goals

**Goals:**

- 成功用 Snackbar：3 秒自动消失、不阻塞、底部弹、保留"复制链接"能力。
- 失败用 Dialog：按 `SyncMessage.Failure` 子类型给分类文案 + 操作建议（重试/重新同步/关闭/复制错误）。
- `SyncMessage` 扩成 9 个 case（Success + 8 Failure 子类型），exhaustive when 不漏分支。
- 失败文案从英文 fallback / 通用文案升级到"远端文档已删除"/"网络异常，请检查连接"等具体原因。
- 每次失败给一个明确的下一步操作（重试 / 重新同步 / 复制错误）。
- 现有 `FeishuConflictDialog` / `FolderMigrationDialog` 继续工作，作为 `Failure.Conflict` / `Failure.FolderMigration` 的渲染层。

**Non-Goals:**

- 不重构 FeishuConflictDialog / FolderMigrationDialog 的内部逻辑（仅复用）。
- 不改 `FeishuError` 形态（catch 现有 sealed，1:1 映射到新 Failure 子类型）。
- 不加 toast / banner / haptic feedback 等额外反馈通道（Snackbar + Dialog 够用）。
- 不做离线队列 / 自动重试（保留单次手动同步语义）。
- 不动 `FeishuSyncRepository.pull` 的 docUrl 输入对话框（pull 的入口 UX 不在本 change 范围）。

## Decisions

### D1 · SyncMessage 用嵌套 sealed：Success + Failure(子 sealed)

**选择**：把 `Failure` 做成嵌套 `sealed interface Failure : SyncMessage`，其下 8 个 data class/data object 子类型。

**理由**：

- 顶层 `Success` 是单独的"好结果"形态，跟 Failure 不在同维度（Success 不需要分类）。
- 嵌套 sealed 让 UI 的 `when` 分两段：`is Success ->` 处理 Snackbar；`is Failure ->` 再 nested when 分 8 个子类型。两段清晰，exhaustive 检查好做。
- 比扁平 9 个 case 的写法（`Success` + `Conflict` + `FolderMigration` + ...）更可读：失败态一眼能看出是一类东西。

**备选**：

- 扁平 9 case → 拒绝：UI 的 when 顶层一长串，失败态之间没有共同类型抽象，扩展时（比如加 `Failure.AuthExpired`）容易漏分支。
- 单一 `Failure(reason: String)` → 拒绝：现状，已经验证不够用。

### D2 · 成功 Snackbar 用现有 snackbarHostState，失败 Dialog 用 Composable 局部 AlertDialog

**选择**：

- 成功：在 `QuickNoteDetailScreen` 已有的 `snackbarHostState` 里 `showSnackbar(message = ..., actionLabel = ..., withDismissAction = false)`。点击 action 调 `ClipboardManager.setPrimaryClip` 复制 docUrl，然后 `snackbarHostState.showSnackbar("已复制链接")` 给个二次确认。
- 失败：在 Composable 里 `when (val f = syncMessage) { is Failure.X -> { ... AlertDialog(...) } }` 局部 AlertDialog。每个 subtype 一个 Dialog，避免堆叠多个 Dialog。

**理由**：

- Snackbar 通道已存在（apikey 缺失、decompose 反馈都用它），不新加通道、不重复建设。
- 失败 Dialog 局部声明，跟 syncMessage StateFlow 同生命周期（StateFlow 置 null → Dialog 自动消失），不引入额外 boolean state。
- 删 `showSyncMessageDialog` 这块 boolean state —— 之前是为了"等用户点关闭再清空 syncMessage"，现在 Snackbar/Dialog 都靠 `clearSyncMessage()` 自动消费。

**备选**：

- 全用 Snackbar（含失败）→ 拒绝：失败需要用户决策（重试？保留本地？），Snackbar 自动消失会让用户错过。
- 全用 Dialog（含成功）→ 拒绝：现状，已被用户否定。
- 新建独立 SyncFeedbackViewModel → 拒绝：over-engineering，单屏单 feedback 通道，没必要拆 VM。

### D3 · Snackbar action "复制链接" 后再发一个 1.5 秒短 Snackbar 确认

**选择**：点击"复制链接"后，`snackbarHostState.showSnackbar("已复制链接", duration = Short)` 立即弹出确认，不阻塞。

**理由**：Material 3 Snackbar 的 action 按钮被点击后默认 Snackbar 立即消失（不像旧版有二次确认），需要给用户视觉反馈"复制成功"。

**备选**：

- 只调 clipboard 不给反馈 → 用户不知道是否真的复制了，不够。
- 用 Toast → 引入新通道，over-engineering。

### D4 · RemoteDeleted 的"重新同步"按钮复用现有 recreateFeishuDoc()

**选择**：`Failure.RemoteDeleted` Dialog 主按钮调 `viewModel.recreateFeishuDoc()`（已有逻辑：删旧 ref + push 创建新文档）。

**理由**：`recreateFeishuDoc()` 已经在 `QuickNoteDetailViewModel` 里实现（来自 feishu-bidir-sync spec 的 "Remote deletion handling"），无需新写。

**备选**：

- 直接 inline 写新逻辑 → 拒绝：复制粘贴，违背 DRY。

### D5 · Network/Server 错误的"重试"按钮直接调 pushToFeishu() / pullFromFeishu() 重跑

**选择**：`Failure.Network` / `Failure.Server` Dialog 主按钮"重试"调 `viewModel.pushToFeishu()`（push 场景）或 `viewModel.pullFromFeishu(原 docUrl)`（pull 场景）。VM 需要记 last action type。

**实现细节**：

- `QuickNoteDetailViewModel` 加 `_lastSyncAction: SyncAction { PUSH, PULL(docUrl) }` private mutableStateOf。
- `pushToFeishu()` 开头 `_lastSyncAction = PUSH`。
- `pullFromFeishu(docUrl)` 开头 `_lastSyncAction = PULL(docUrl)`。
- 新增 `retryLastSync()` 方法，根据 `_lastSyncAction` 调对应函数。

**理由**：retry 必须知道上次做了什么，类型不同时不能盲目调 push（pull 失败后调 push 会创建新文档，破坏用户预期）。

**备选**：

- 让 retry 总是调 push → 拒绝：pull 失败后用户希望重 pull，不是创建新 push。

### D6 · Unknown 的"复制错误"调 ClipboardManager.setPrimaryClip

**选择**：`Failure.Unknown(cause)` Dialog 主按钮"复制错误"调 `ClipboardManager.setPrimaryClip(ClipData.newPlainText("同步错误", cause))`。

**理由**：`cause` 是 `Throwable.toString()`，包含 stacktrace 关键信息，方便用户贴给开发者排查。

**备选**：

- 把 cause 完整写到 Dialog body → body 太长，Dialog 高度会撑爆。
- 只复制 message 不复制 stacktrace → 信息量不够，开发者不好定位。

## Risks / Trade-offs

[R1] SyncMessage 扩 9 case 后，旧的 UI 测试需要更新断言 → Mitigation：在 tasks.md 阶段写明更新所有 `SyncMessage.Success(docUrl)` / `SyncMessage.Failure(reason)` 的测试用例为新形态。

[R2] 重试按钮需要 VM 记 last action type，引入新状态 → Mitigation：状态简单（一个 enum 或 sealed），跟 syncMessage 一起消费/重置；不引入新的 StateFlow 对外暴露。

[R3] Snackbar action 后立刻再发一个 1.5 秒短 Snackbar 确认，连续两个 Snackbar 排队显示，间隔可能让用户误以为是同一事件 → Mitigation：用 `snackbarScope.launch` 协程 cancel 前一个 job（跟现有 decomposeSnackbarJob 同模式），避免排队。

[R4] Failure.Dialog 数量从 1 个变 8 个，代码行数膨胀 → Mitigation：每个 Dialog 单独 Composable 抽到 `feature/quicknote/detail/SyncFailureDialog.kt`，主屏只写 `when` 分发。可读性比内联好。

[R5] values-en/strings.xml 翻译工作量 → Mitigation：本 change 落地时英文用 TODO 占位（`TODO(en): feishu_sync_*`），M5 polish 再翻。Lint `ExtraTranslation` 检查会过。

[R6] Snackbar 在 detail 屏的 SnackbarHostState 是全局共享的（apikey 缺失 / decompose / 同步成功都共用），重试失败后可能跟其他 Snackbar 排队 → Mitigation：用 SnackbarJob cancel 模式（同 R3），并把 `syncMessage` 消费放在 `clearSyncMessage()` 调用，确保 syncMessage 不会重复弹。