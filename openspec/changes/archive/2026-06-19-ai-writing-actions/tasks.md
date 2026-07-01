## 1. Feature entry 与 ViewModel 工厂

- [ ] 1.1 新建 `feature/aiwriting/AiwritingEntry.kt` — `object AiwritingEntry` 暴露 `@Composable fun rememberAiActionViewModel(noteId: String): AiActionViewModel` 封装 `hiltViewModel<AiActionViewModel>()` + 通过 `viewModelStoreOwner` 绑定到 noteId
- [ ] 1.2 暴露 `data class AiActionFabState(val selectionEmpty: Boolean)` 给详情屏(避免 detail 屏 import 内部 ViewModel)
- [ ] 1.3 `AiwritingEntry` 不 import `feature/aiwriting.streaming.AiActionUiState`(返回 ViewModel 类型可，但 detail 屏只通过 `state` 字段读)

## 2. AiError → 显示文案扩展

- [ ] 2.1 新建 `feature/aiwriting/error/AiErrorDisplay.kt` — `internal fun AiError.toDisplayMessage(Context): String`,6 个分支映射到 R.string.aiwriting_error_*(见 §11 i18n);`is Network / Auth / InsufficientBalance / Timeout / ProviderNotFound / Deserialization / Unknown`
- [ ] 2.2 加 JUnit5 测试 `feature/aiwriting/error/AiErrorDisplayTest.kt`:每个 AiError 子类映射到正确 R.string

## 3. AiActionViewModel 状态机

- [ ] 3.1 新建 `feature/aiwriting/streaming/AiActionUiState.kt` — `sealed interface AiActionUiState` 4 态:`Idle / Streaming(op, partialText, isCancelled) / Done(op, finalText, usage) / Failed(op, error)`(参照 design §4)
- [ ] 3.2 新建 `feature/aiwriting/streaming/AiActionViewModel.kt` — `@HiltViewModel class`，注入 `AiGateway` + `NoteRepository`:
  - `companion object { const val PROVIDER_ID_FAKE = "fake" }`
  - 私有 `MutableStateFlow<AiActionUiState>(Idle)` + 公开 `val state: StateFlow<AiActionUiState>`
  - 私有 `private var streamJob: Job?` + `private var lastOp: WritingOp?` + `private var lastSourceText: String?` + `private var lastNoteId: String?`(供 `regenerate()` 复用)
  - `fun start(op: WritingOp, sourceText: String, noteId: String)`:取消旧 job → 在 `viewModelScope.launch` 中 `aiGateway.streamWritingOp(op, sourceText, PROVIDER_ID_FAKE, null).collect { ... }`，每 event 映射到 `AiActionUiState`
  - `fun acceptReplace()`:state 必须是 `Done` → 读 `finalText` + `op` + `lastNoteId` → `withContext(NonCancellable) { val existing = repo.getNote(noteId)?:return@withContext; val updated = existing.note.copy(content=finalText, updatedAt=now); repo.upsert(updated, existing.tags); repo.updateAiMetadata(noteId, op.name.lowercase(), now) }` → state = Idle
  - `fun reject()`:state = Done → state = Idle(不替换)
  - `fun cancel()`:state = Streaming → `streamJob?.cancel()` → state = Idle
  - `fun dismiss()`:state = any → `streamJob?.cancel()` → state = Idle
  - `fun regenerate()`:state = Done → 复用 `lastOp` / `lastSourceText` / `lastNoteId` 调 `start(...)`
- [ ] 3.3 加 JUnit5 测试 `AiActionViewModelTest.kt`(用 Turbine + MockK):验状态机转移(Idle→Streaming→Done → acceptReplace 落库 → Idle;Streaming→cancel → Idle;Done→reject → Idle 不落库;Done→regenerate → Streaming;Failed→dismiss → Idle)

## 4. ActionSheet Composable

- [ ] 4.1 新建 `feature/aiwriting/action/ActionSheet.kt` — `@Composable fun ActionSheet(expanded: Boolean, onDismiss: () -> Unit, onExpand: () -> Unit, onPolish: () -> Unit, onOrganize: () -> Unit, onCopy: () -> Unit)`，内部用 `DropdownMenu`(`expanded` / `onDismissRequest`)+ 4 个 `DropdownMenuItem`，文案全部 `stringResource(R.string.aiwriting_action_*)`
- [ ] 4.2 新建 `feature/aiwriting/action/ActionSelectionViewModel.kt` — `@HiltViewModel class`，持有 `MutableStateFlow<TextRange>` + `fun onSelectionChange(range: TextRange)`，供 detail 屏 Composable 监听 BasicTextField 的 selection
- [ ] 4.3 新建 `AiActionFab.kt`(`@Composable`):根据 `selectionEmpty` 在 `Icons.Filled.Share` 和 `Icons.Filled.AutoAwesome` 之间切换;`onClick` 行为透传给 caller

## 5. StreamingPanel 与 ModalBottomSheet

- [ ] 5.1 新建 `feature/aiwriting/streaming/StreamingPanel.kt` — `@Composable fun StreamingPanel(state: AiActionUiState, onAccept: () -> Unit, onReject: () -> Unit, onCancel: () -> Unit, onRegenerate: () -> Unit, onClose: () -> Unit, onDismiss: () -> Unit)`，内部用 `ModalBottomSheet(onDismissRequest = onDismiss, skipPartiallyExpanded = true)`，根据 state 分支渲染:
  - `Idle` → 不渲染任何内容(Composed out)
  - `Streaming` → 顶部 `Text(stringResource(aiwriting_panel_title_*) + stringResource(aiwriting_panel_streaming))`;中部 `Text(partialText)`(可滚动 Column);底部 `Button(onClick=onCancel) { Text(stringResource(aiwriting_panel_cancel)) }`
  - `Done` → 顶部 `<op> 完成`;中部 `Text(finalText)`;底部 Row 含 `Button(onClick=onAccept, enabled=finalText.isNotBlank()) { Text("接受") }` / `TextButton(onClick=onReject) { Text("拒绝") }` / `TextButton(onClick=onRegenerate) { Text("再生成") }`;顶部右侧 `usage` chip(若有):`Text(stringResource(aiwriting_panel_usage_fmt, usage.inputTokens, usage.outputTokens))`
  - `Failed` → 顶部 `Text("出错")`;中部 `Text(state.error.toDisplayMessage(LocalContext.current))`;底部 `Button(onClick=onClose) { Text("关闭") }`
- [ ] 5.2 `StreamingPanel` 不直接 import `feature/aiwriting.action.*`(单向依赖 action → streaming，反过来不行)
- [ ] 5.3 加 Compose UI 测试 `StreamingPanelTest.kt`(可选，M3 可推 M5):验 4 个 state 渲染对应按钮(用 `composeTestRule`)

## 6. Clipboard 工具(非 AI)

- [ ] 6.1 新建 `feature/aiwriting/action/CopyText.kt` — `internal fun Context.copyToClipboard(text: String)`，内部 `ClipboardManager.setPrimaryClip(ClipData.newPlainText("writing-with-ai", text))`;不需要 apikey，不走 AiGateway
- [ ] 6.2 加 Compose UI 集成路径:ActionSheet 的"复制"项 `onClick = { ctx.copyToClipboard(sourceText); onCopy() }`

## 7. 详情屏 BasicTextField 改造

- [ ] 7.1 改 `feature/quicknote/detail/QuickNoteDetailScreen.kt`:
  - 把 `SelectionContainer { Text(content) }` 替换为 `BasicTextField(value = textFieldValue, onValueChange = { newValue -> vm.onSelectionChange(newValue.selection); textFieldValue = newValue }, readOnly = true, textStyle = MaterialTheme.typography.bodyLarge)`
  - 在 `Scaffold.floatingActionButton` 块加 `AiwritingEntry.rememberAiActionViewModel(noteId)` 拿到 ViewModel
  - 用 `viewModel.state.collectAsStateWithLifecycle()` 订阅 AiActionUiState
  - FAB 渲染:用 `AiwritingEntry.rememberAiActionFabState(...)` 拿到 `selectionEmpty`;若空 → `Share` FAB + 触发 M1 既有 share;若非空 → `AutoAwesome` FAB + `DropdownMenu(expanded = sheetOpen, onDismissRequest = { sheetOpen = false }) { ActionSheet(...) }`
  - ModalBottomSheet 渲染:`if (state !is Idle) StreamingPanel(state=state, onAccept=vm::acceptReplace, onReject=vm::reject, onCancel=vm::cancel, onRegenerate=vm::regenerate, onClose=vm::dismiss, onDismiss=vm::dismiss)`
- [ ] 7.2 改 `feature/quicknote/detail/QuickNoteDetailViewModel.kt`:
  - 加 `val selectionState: StateFlow<TextRange>` + `fun onSelectionChange(range: TextRange)`
  - 加 `val aiMetaDisplay: StateFlow<String?>`(`lastAiOp` / `lastAiAt` 投影，给 UI 显示"上次 AI 操作"行)
  - `selectionState` 由 ViewModel 持有，跨重组 / 跨 config change 不丢
- [ ] 7.3 改 `feature/quicknote/detail/QuickNoteDetailScreen.kt` 顶部 metadata 行:`if (aiMetaDisplay != null) Text(stringResource(R.string.aiwriting_meta_fmt, aiMetaDisplay))`
- [ ] 7.4 选区消失时(用户点空白),`BasicTextField.onValueChange` 收到 `selection = TextRange.Zero` → `vm.onSelectionChange(TextRange.Zero)` → FAB 自动切回 Share

## 8. 编辑器屏零改动验证

- [ ] 8.1 确认 `feature/quicknote/edit/QuickNoteEditorScreen.kt` / `QuickNoteEditorViewModel.kt` 无 import `core.ai.*` / `feature.aiwriting.*`
- [ ] 8.2 编辑器保存路径走 M1 既有 `repo.upsert(...)`,**不**主动修改 `lastAiOp` / `lastAiAt`(M1 既有 `Note` 字段保留)

## 9. AppNav 零改动验证

- [ ] 9.1 确认 `app/AppNav.kt` 无新 route(StreamingPanel 走 ModalBottomSheet，不走 NavController)
- [ ] 9.2 详情屏 nav route `quicknote/detail/{id}` 保持不变

## 10. 测试

- [ ] 10.1 加 `feature/aiwriting/streaming/AiActionViewModelTest.kt`(JUnit5 + Turbine + MockK):
  - 用 `MockK` mock `AiGateway`，返回可控 Flow(emits `Started` → `Delta("你")` → `Delta("好")` → `Usage(2,3,5)` → `Done`)
  - 验 `start(EXPAND, "x", "n1")` → state 转移到 `Streaming` → `Done`
  - 验 `acceptReplace()` → MockK 验证 `noteRepo.upsert(...)` + `noteRepo.updateAiMetadata(...)` 被调，`withContext(NonCancellable)` 用 `TestCoroutineDispatcher` 验不被取消
  - 验 `regenerate()` 复用 last op / sourceText / noteId
  - 验 `Failed(AiError.Network)` → state 转 `Failed`,`dismiss()` → `Idle`
- [ ] 10.2 加 `feature/aiwriting/error/AiErrorDisplayTest.kt`(JUnit5 + Robolectric 或 `RuntimeEnvironment`):每个 AiError 子类映射到正确 string
- [ ] 10.3 跑 `./gradlew :app:testDebugUnitTest` 全部新增测试通过(M1/M2 既有测试不退化)

## 11. i18n 文案

- [ ] 11.1 改 `app/src/main/res/values/strings.xml` 加 19 个 `aiwriting_*` key(权威中文，见 ai-actions spec Requirement "i18n" 表格)
- [ ] 11.2 改 `app/src/main/res/values-en/strings.xml` 加 19 个对应 key，值为 `TODO(en): aiwriting_xxx`(同 M1/M2 模式，英文 M5 polish 时补)
- [ ] 11.3 改 `app/src/main/res/values/strings.xml` 加 `quicknote_meta_ai_fmt` 中文 `上次 AI 操作 · %1$s · %2$s`(detail 屏顶部 metadata 行)
- [ ] 11.4 改 `app/src/main/res/values-en/strings.xml` 加 `quicknote_meta_ai_fmt` 英文 `TODO(en): quicknote_meta_ai_fmt`

## 12. ktlint + Compose PascalCase

- [ ] 12.1 跑 `./gradlew :app:ktlintCheck` → 已知 11 个 `function-naming`(M2 既有)以外，0 新增;新增 Composable 函数全部 PascalCase 开头(`ActionSheet` / `StreamingPanel` / `AiActionFab` / `rememberAiActionViewModel` — 后者驼峰例外，因是 `remember*` 工厂)
- [ ] 12.2 跑 `./gradlew :app:ktlintFormat` 修可自动修项

## 13. 整体验收

- [ ] 13.1 `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL
- [ ] 13.2 `./gradlew :app:testDebugUnitTest` → M1/M2/M3 全部测试通过
- [ ] 13.3 `./gradlew :app:lintDebug` → BUILD SUCCESSFUL
- [ ] 13.4 `./gradlew :app:ktlintCheck` → 0 新增违规
- [ ] 13.5 手工冒烟(在模拟器 / 真机):详情屏长按选中 5 字符 → AutoAwesome FAB 出现 → 点 FAB → 选"扩写" → ModalBottomSheet 弹出 → FakeProvider 返回文本 → 看到 streaming + done → 点"接受" → 正文替换为 AI 输出 + lastAiOp = "expand" + 顶部 metadata 行出现
- [ ] 13.6 手工冒烟(错误):用 `FakeConfigHolder.set(errorAfterTokens=1)` → 同样流程 → 看到 "网络连接失败" + "关闭" 按钮 → 点关闭 → sheet 消失 → 不白屏
- [ ] 13.7 手工冒烟(取消):Streaming 中途点"取消" → sheet 消失 → 正文不变 → AiHistory 落库(成功或失败视 FakeProvider 行为)
- [ ] 13.8 手工冒烟(再生成):Done 态点"再生成" → 重新进入 Streaming → 原 finalText 丢弃

## 14. OpenSpec 收尾(apply 通过 review 后做)

- [ ] 14.1 review 通过后，跑 `openspec archive ai-writing-actions -y`
- [ ] 14.2 更新 `docs/progress.md`:M3 完成
- [ ] 14.3 在 `docs/plans/writing-with-ai-mobile-roadmap.md` §13 标注 M3 完成;§15.2 标 `ai-writing-actions` done
- [ ] 14.4 `openspec/changes/quick-note/specs/quick-note/spec.md` 是 MODIFIED 段(本 change 在 archive 时合并到 `openspec/specs/quick-note/spec.md`)，需要 review 后执行 `openspec sync` 把 MODIFIED 段 sync 上去