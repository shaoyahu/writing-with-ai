## Context

M2 已落地 `AiGateway`(业务入口)/ `AiProvider`(SPI)/ `AnthropicCompatibleAdapter`(唯一实现，由 ProviderConfig 驱动，三家 preset 各独立实例)/ `FakeProvider`(单测/UI 验收用)/ `AiHistoryEntity + AiHistoryRepository`(每次调用自动落库)/ `CoreAiGateway`(流式路由 + onCompletion 落库)。M1 已落地 `Note` + tag 完整 CRUD + 详情/编辑/列表 + 分享 + 字数/阅读时间 + R.string 双语。M2 / M1 都过了 r1/r2 review。

需求进入 M3:把 AI 抽象层转成"详情屏选中文本 → 弹操作 → 流式面板 → 接受/拒绝"完整闭环，写 `Note.lastAiOp`。roadmap §3.2 拍板:

- 三类操作:扩写(在原文基础上保留语气扩写)/ 润色(优化表达不改变原意)/ 整理(结构化 Markdown 输出)
- **流式 SSE / chunked** 返回，长任务配进度条 + 取消按钮
- **网络失败 / 余额不足 / apikey 无效**:必须有 fallback(不白屏)
- **用户拒绝/接受**:由 UI 决定，AI 不直接写库
- **每次调用记录 token 用量**(已 M2 自动落库)

## Goals / Non-Goals

**Goals:**
- 详情屏选中文本(可任意范围)→ 弹 ActionSheet(扩写/润色/整理 + 复制)→ 点击后 ModalBottomSheet 流式展示 AI 返回
- 接受/拒绝/取消三种用户操作完整覆盖;接受 → 替换原文并写 `Note.lastAiOp`
- 错误降级:无网络/超时/401/402 → UI 显示明确文案 + 关闭面板(不白屏)
- i18n 完整(中文 + 英文 TODO 占位)
- 写"@Composable `AiActionViewModel.collectAsStateWithLifecycle` 收 Flow"的标准模式

**Non-Goals:**
- 真 provider 切换(M5 onboarding-consent + apikey 加密);M3 写死 `providerId = "fake"`
- 自定义 system prompt(M5)
- 对话历史 / 多轮对话(M3 单次操作为主，history 在 AiHistory 表里)
- `n > 1` 多次采样 / 候选对比
- Markdown 渲染 AI 输出(M3 仅 plain text;Markwon M5)
- 选中文本 vs 全文歧义(M3 用 selection 优先;无选区时显示"请先选择文本")
- apikey 加密 / 设置页(全部 M4/M5)

## Decisions

### 1. ActionSheet 用 `DropdownMenu`(不是 PopUp)
**Why**:Material 3 `DropdownMenu` 锚定到 FAB / `Text` 选区，自动处理 dismiss + 焦点;比 `PopUp` + 手动算位置更简单，且不依赖选区位置精确。
**替代方案**:`PopUp` — 需手算锚点;`ModalBottomSheet`(太重，适合流式面板)。

### 2. 流式面板用 `ModalBottomSheet`
**Why**:Material 3 标准;`skipPartiallyExpanded` 让用户决定不展开就完全不展开;`onDismissRequest` 处理 back 关闭。
**替代方案**:全屏 Dialog — 太重，挡住上下文。

### 3. 选中文本用 `TextField` + `TextFieldValue.selection`
**Why**:Compose `Text` 是不可选的(只有 SelectionContainer 包装才能长按复制)。要把 selection 状态拿到 ViewModel，得用 `TextField`。M3 详情屏改用 `BasicTextField`(只读模式)+ 监听 selection。
**替代方案**:SelectionContainer + onTextContextMenuClick 钩子 — API 复杂;`TextField` 是标准做法。

### 4. AiActionViewModel 状态机:sealed `AiActionUiState`
```kotlin
sealed interface AiActionUiState {
    data object Idle : AiActionUiState
    data class Streaming(
        val op: WritingOp,
        val partialText: String,
        val isCancelled: Boolean = false,
    ) : AiActionUiState
    data class Done(
        val op: WritingOp,
        val finalText: String,
        val usage: AiStreamEvent.Usage?,
    ) : AiActionUiState
    data class Failed(val op: WritingOp, val error: AiError) : AiActionUiState
}
```

**Why**:5 个状态干净;`Streaming` 持 partialText 让 UI 实时渲染 delta;`isCancelled` 区分"用户取消 vs 中断"。

### 5. `acceptReplace(aiText, op)` 走事务
**Why**:替换正文 + 写 lastAiOp 必须**原子**(失败不能只改一半)。`withContext(NonCancellable)` 包整段(类似 M1 r1 review M6 修的删除场景)。
**实现:**
```kotlin
fun acceptReplace(aiText: String, op: WritingOp) {
    viewModelScope.launch {
        withContext(NonCancellable) {
            val now = System.currentTimeMillis()
            val existing = noteRepo.getNote(noteId) ?: return@withContext
            val updated = existing.note.copy(content = aiText, updatedAt = now)
            noteRepo.upsert(updated, existing.tags)
            noteRepo.updateAiMetadata(noteId, op.name.lowercase(), now)
        }
        onAccepted()
    }
}
```

### 6. `providerId` 写死 `"fake"`(M3 阶段)
**Why**:M3 验证 UI 闭环，不接真 provider;M4/M5 设设置页时换用户选中的 provider。FakeProvider 返回可配文本 + 延迟 + 错误注入，UI 验收够用。

### 7. 错误降级:每个 AiError → 文案映射
```kotlin
fun AiError.toDisplayMessage(ctx: Context): String = when (this) {
    is Network -> ctx.getString(R.string.aiwriting_error_network)
    is Auth -> ctx.getString(R.string.aiwriting_error_auth)
    is InsufficientBalance -> ctx.getString(R.string.aiwriting_error_balance)
    is Timeout -> ctx.getString(R.string.aiwriting_error_timeout)
    else -> ctx.getString(R.string.aiwriting_error_unknown)
}
```
UI 显示"X,close 面板"按钮，不弹 dialog(简化 UX)。

### 8. 流式 panel 文本渲染:`Text` + 实时 append
**Why**:简单;M3 输出不会超过 10k 字符(超过 5k 警告);`Text` 自带滚动。**不**用 `LazyColumn` —— 单条流式输出不需要 virtualize，简单直白。
**替代方案**:`LazyColumn` + 累加 entries — 过度设计。

### 9. 取消 vs 拒绝 区分
- **取消**(cancel):Streaming 中途 → 不替换正文，关闭 panel
- **拒绝**(reject):Done 后 → 不替换正文，关闭 panel
- **接受**(accept):Done 后 → 替换正文，关闭 panel

**Why**:两个 UI 按钮但语义不同(roadmap §3.2 拍板);UI 显示时:`Streaming` 态只有"取消",`Done` 态显示"接受/拒绝"。

### 10. 详情屏 FAB 触发表单
**Why**:M1 r1 review 修过 "404" 硬编码 + 详情屏 UX 升级需要 affordance。FAB 用 `Icons.Filled.AutoAwesome`(sparkle,AI 标志性图标)，只在 selection 非空时显示;空时显示`Icons.Filled.Share`(M1 已有，继续用)。

### 11. Compose recomposition 防御
**Why**:StreamingPanel 接收 `partialText: String` 参数;每次 Delta 都触发重组。`Text` 内部 stable，无性能问题。如果将来 > 5k 字符，改 `rememberSaveable` + 手动 append。

### 12. i18n 文案 14 个 key
集中在 `feature/aiwriting` 命名空间，放 `R.string.aiwriting_*`;Compose 内只引 `stringResource`,**禁止**硬编码中文。英文 `TODO(en):` 占位(同 M1/M2 模式)。

## Risks / Trade-offs

- **[Risk] ActionSheet 选区捕获与 TextField 冲突** → 用 `BasicTextField` + `TextFieldValue`(selection 是 TextFieldValue 一部分);M1 的 `SelectionContainer` 删除;UI 行为"长按出现" 变成 "点 FAB 出现"
- **[Risk] 详情屏 background 状态下回到前台，流式 UI 状态丢失** → `rememberSaveable` 不适用(`AiActionUiState` 含 Flow，不可序列化);用 ViewModel scope(已配 HiltViewModel + viewModelScope)持状态
- **[Risk] 选中文本包含 Markdown 格式** → M3 plain text 输出，Markwon 推迟 M5;AI 输出"## 标题"显示成纯文本，用户接受后自己修
- **[Risk] 接受后不刷新详情屏** → `NoteRepository.upsert()` 触发 Room update，详情屏 `observeNoteWithTags` Flow 推送新数据;无需手动 refresh
- **[Risk] 并发:同时打开两个 ActionSheet** → 同一 noteId 的 AiActionViewModel 由 Hilt scoped 持;单实例;用户实际操作不会并发

## Migration Plan

无(纯新增 + M1/M2 既有 schema 增字段写入)。

回滚:git revert + 数据库 MIGRATION 不变(只是 lastAiOp 字段被填充)。

## Open Questions

- **ActionSheet 选区消失后还能不能继续?** 倾向:能 — selection 抓到 selectedText 后传给 ViewModel,ViewModel 持 selectedText 直到 panel 关闭;FAB 关闭后 selection 才清空
- **面板内文本超出 10k 字符怎么办?** 倾向:M3 不加压缩;M3 警告 toast "Output too long, may be truncated" 若 `finalText.length > 10000`
- **再生成按钮(同 op 二次)是否需要?** 倾向:需要 — 简单 `start(op, sourceText, noteId)` 二次调用，M3 加一个"再生成"按钮在 Done 态(接受/拒绝/再生成)
- **M3 是否需要把"复制"作为 AI 操作?** 倾向:不算 AI，是 clipboard 工具;放 ActionSheet 但调用 `ClipboardManager`，不进 AiGateway
