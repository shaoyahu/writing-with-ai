# code-review · ai-writing-actions · r1

**Date:** 2026-06-19
**Subject:** `ai-writing-actions`(M3 AI 写作操作) — r1 review:全文件 AI 自审
**Review type:** code-review(r1,initial)
**Basis:** `openspec/changes/ai-writing-actions/`(4 artifacts)+ 11 个产物文件

---

## 总结

**M3 主功能(状态机 + StreamingPanel + ActionSheet + acceptReplace NonCancellable)实现路径正确,与 spec 对齐。但 detail 屏整合层有 3 个 HIGH 阻断 UI 闭环跑通,4 个 MEDIUM(其中 1 个硬编码中文违反 CLAUDE.md)。**

| 严重度 | 数量 |
| --- | --- |
| HIGH | 3 |
| MEDIUM | 5 |
| LOW | 5 |

| 验收项 | 结果 |
| --- | --- |
| `assembleDebug` | ✅ BUILD SUCCESSFUL |
| `testDebugUnitTest` | ✅ 27 tests pass |
| `lintDebug` | ✅ BUILD SUCCESSFUL |
| `ktlintCheck` | ⚠️ 17 个 `function-naming` = 已知 Compose PascalCase(M0 follow-up),0 非 PascalCase 新增 |

---

## HIGH — 必须修

### H1 · `aiState` snapshot read 不触发重组,ModalBottomSheet 永远不显示 ⚠️ 阻断 UI

**文件:** `app/src/main/java/com/yy/writingwithai/feature/quicknote/detail/QuickNoteDetailScreen.kt:85-86`

```kotlin
val aiState: AiActionUiState =
    aiVm?.state?.collectAsStateWithLifecycle()?.value ?: AiActionUiState.Idle
```

**问题:** `.value` 是 `State<T>` 的普通 snapshot 读 — **不会触发 Composable 重组**。Compose 只在 `collectAsStateWithLifecycle()` 返回的 `State<T>` 对象被 `by` 委托订阅时,才订阅其变化并重组。当前写法:
- 每次重组 `collectAsStateWithLifecycle()` 都重跑(轻量但 OK)
- 但 `.value` 只取快照 — 即使 `aiVm.state` 从 `Idle → Streaming → Done` 流转,**整个 Composable 不会重组**

**后果:** `StreamingPanel` 永远拿到 `Idle`,第 55 行 `if (state is AiActionUiState.Idle) return` 直接退出。**整个 AI 流式 UI 不会出现在屏幕上**。spec §"StreamingPanel renders state-aware UI" 与 quick-note spec "Navigation routes unchanged" 描述的 ModalBottomSheet 全失效。

**修法:** 用 `by` 委托订阅,不是 `.value` 快照:
```kotlin
val aiState: AiActionUiState by aiVm?.state?.collectAsStateWithLifecycle()
    ?: remember { mutableStateOf(AiActionUiState.Idle) }
```
注意 `aiVm == null` 时不能再 `by`,需要 fallback。

**Priority:** 🔴 必修,不修 M3 用户验收跑不通

---

### H2 · `noteId == null` 时 detail 屏仍渲染 FAB 与 ActionSheet

**文件:** `app/src/main/java/com/yy/writingwithai/feature/quicknote/detail/QuickNoteDetailScreen.kt:168-192`

**问题:** M1 r1 H3 修 `noteId: String?`(saved state 缺失时 `null`,走 NotFound)。但 M3 `floatingActionButton` block 只判 `if (current != null)`,**没判 `noteId != null` 与 `aiVm != null` 的情况**:
- `current != null` 但 `noteId` 是空字符串(边缘 case,UI 加载时短暂)→ `aiVm = null` → `AiActionFab` 仍渲染但点击 FAB 无 ActionSheet
- `current != null` 但 `noteId` 真存在时 OK,但 `noteId` 从 saved state 来仍可能是边缘值

更严重:**H1 修了之后**,即使 `noteId` 正常,但 `aiVm = null` → `aiState` 走 fallback `Idle` → sheet 永不显示。如果 H1 修法用 `?: remember { mutableStateOf(...) }`,fallback 必须落到 `Idle`(同 H1 修法)。

**修法:** 在 `current != null && noteId != null` 才渲染 `floatingActionButton` block;`aiVm` 必须存在;`ActionSheet` 块同理 gate。

**Priority:** 🟠 高(H1 修了之后才能跑通完整流程)

---

### H3 · BasicTextField 选区状态会被 `remember(current)` 重置

**文件:** `app/src/main/java/com/yy/writingwithai/feature/quicknote/detail/QuickNoteDetailScreen.kt:88-95`

```kotlin
var textFieldValue by remember(current) {
    mutableStateOf(
        TextFieldValue(
            text = current?.note?.note?.content.orEmpty(),
            selection = selection,
        ),
    )
}
```

**问题:** `remember(current)` 在 `current` 变化时重建 — 但 `current` 是整个 `NoteDetailUiState.Content` 包装,任何字段变(title / wordCount / readMinutes / tags / content)都会触发重建。重建时:
- 重新把 `text` 设到 `content`(OK)
- 重新把 `selection` 设到 ViewModel 的 `selection`(可能是 **旧选区** — 用户在流式期间选了 5 字符,Accept 后 content 变,ViewModel 里 selection 还在那 5 字符的 range,但 **新 content 可能已经更短** → 选区越界 → BasicTextField 自动 clamp 到 [0, len) → **选区被偷偷改掉**)

**后果:** 用户体验:接受 AI 输出后,BasicTextField 内选区状态丢失,需要重新长按选择才能再次触发 ActionSheet。

**修法:** 只在 `current?.note?.note?.id` 变化时重建,不要绑整个 content。同时 `selection` 应该是用户主动长按产生,**不应该从 ViewModel 反向同步回 BasicTextField**(单向 `BasicTextField.onValueChange → ViewModel.onSelectionChange` 就够)。

```kotlin
var textFieldValue by remember(current?.note?.note?.id) {
    mutableStateOf(TextFieldValue(text = current?.note?.note?.content.orEmpty()))
}
```

**Priority:** 🟠 高(影响 AI 闭环的"再生成 / 多次操作"体验)

---

## MEDIUM — 应该修

### M1 · `opNameToZh` 硬编码中文,违反 CLAUDE.md"禁止硬编码中文"

**文件:** `app/src/main/java/com/yy/writingwithai/feature/quicknote/detail/QuickNoteDetailViewModel.kt:117-123`

```kotlin
private fun opNameToZh(op: String): String =
    when (op) {
        "expand" -> "扩写"
        "polish" -> "润色"
        "organize" -> "整理"
        else -> op
    }
```

**问题:** CLAUDE.md "约定"明确:Composable/VM **禁止**硬编码中文,所有用户可见字符串走 `R.string.*`。`AiMetaDisplay` 的 `opName` 直接喂给 `stringResource(R.string.quicknote_meta_ai_fmt, opName, opAt)` — `opName` 应该是"expand" / "polish" / "organize" 之类的 key(或 enum),在 detail screen 才用 `stringResource` 翻成中文。

**修法:** `AiMetaDisplay` 持 `opKey: String`(或 enum),UI 层 `when (opKey) { "expand" -> stringResource(R.string.aiwriting_action_expand); ... }`。

**Priority:** 🟡 中(CLAUDE.md 硬规则)

---

### M2 · `acceptReplace` 中 `tags` 读取有 race

**文件:** `app/src/main/java/com/yy/writingwithai/feature/aiwriting/streaming/AiActionViewModel.kt:114-123`

```kotlin
withContext(NonCancellable) {
    val existing = noteRepository.getNote(noteId) ?: return@withContext
    val now = System.currentTimeMillis()
    val updated = existing.copy(content = aiText, updatedAt = now)
    val existingTags =
        noteRepository.observeNoteWithTags(noteId).first()?.tags ?: emptyList()
    noteRepository.upsert(updated, existingTags)
    noteRepository.updateAiMetadata(noteId, op.name.lowercase(), now)
}
```

**问题:** `getNote()` 取基础字段,然后 `observeNoteWithTags().first()` 再取 tags — 两次 IO,且**两次之间如果用户改了 tag,upsert 写的 tags 会覆盖最新 tag**。M1 r1 修的"删除走事务"是为了避免类似 race — **acceptReplace 也应走事务**。

**修法:** 直接用 `noteRepository.observeNoteWithTags(noteId).first()` 一次拿 Note + tags,删 `getNote()` 调用。

**Priority:** 🟡 中(edge case:用户操作速度比 AI 流式快,但 accept 触发时是 modal sheet,用户大概率点不到别的操作;竞态概率低)

---

### M3 · `formatLocalDateTime` 每次调用都 new `SimpleDateFormat`

**文件:** `app/src/main/java/com/yy/writingwithai/feature/quicknote/detail/QuickNoteDetailViewModel.kt:125-127`

**问题:** `SimpleDateFormat` 创建开销大,`map { formatLocalDateTime(...) }` 每次上游 emit 都会重建。AI History 多 / 多次 acceptReplace 后 Detail 屏重建触发次数多。

**修法:** hoist 到 `companion object` 单例(`SimpleDateFormat` 本身非线程安全,这里 `map` 单线程 OK;真并发场景应换 `DateTimeFormatter` or `ThreadLocal`)。

**Priority:** 🟢 低(性能优化,功能正确)

---

### M4 · `noteId: String?` 在 `current?.note?.note?.id` 仍可能空

**文件:** `app/src/main/java/com/yy/writingwithai/feature/quicknote/detail/QuickNoteDetailScreen.kt:181`

```kotlin
val sourceText =
    if (selection.collapsed || selection.length == 0) {
        current.note.note.content
    } else {
        current.note.note.content.substring(selection.min, selection.max)
    }
val currentNoteId = current.note.note.id  // 当前假设 current.note.note.id 非空
```

**问题:** `current.note.note.id` 来自 `Note` 模型,M1 创笔记时 `Note.id` 是 UUID 字符串(非空)。OK,但 **与 H2 联动**:如果 H2 修了 `noteId != null` gate,这行可以删。

**Priority:** 🟡 中(H2 修了联动清理)

---

### M5 · `StreamingPanel` Failed 态顶部用 `aiwriting_error_unknown` 而非 op 名

**文件:** `app/src/main/java/com/yy/writingwithai/feature/aiwriting/streaming/StreamingPanel.kt:114`

```kotlin
HeaderRow(title = stringResource(R.string.aiwriting_error_unknown), usage = null)
```

**问题:** spec §"StreamingPanel renders state-aware UI" 表格说 Failed 顶部写"出错",但这里用的是 `aiwriting_error_unknown` — 文案是"出错了,请稍后重试",跟"出错"略有出入。**功能性 OK,文案不一致是文案问题**(可走 §11 加 key)。

**修法:** 加 R.string.aiwriting_panel_failed_title = "出错",改用之。

**Priority:** 🟢 低(文案一致性)

---

## LOW — 可选

### L1 · `AiwritingEntry.kt` 全限定名 import 丑

```kotlin
val storeOwner: ViewModelStoreOwner =
    androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner.current
```

**修法:** 顶部 `import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner`。

---

### L2 · `ActionSheet.kt` "整理" icon 用 `Sort` 不直观

`Sort` 是排序图标,语义对不上"整理"。建议 `Icons.AutoMirrored.Filled.List` 或 `Icons.Filled.FormatListBulleted`。

---

### L3 · `StreamingPanel.kt:78` `partialText.ifBlank { ... }` 重复显示副标题

```kotlin
ScrollableBody(text = state.partialText.ifBlank { stringResource(R.string.aiwriting_panel_streaming) })
```

Streaming 态 HeaderRow 已经写了"扩写 · 进行中…",body 区再写"进行中…" 重复。应保持 body 区空白(`partialText` 自然就是空)。

---

### L4 · `AiwritingEntry.kt:28` `hiltViewModel` 显式 storeOwner 多余

```kotlin
hiltViewModel<AiActionViewModel>(viewModelStoreOwner = storeOwner)
```

`hiltViewModel` 默认就是 `LocalViewModelStoreOwner.current`(就是上面的 storeOwner),可简化。

---

### L5 · `AiActionViewModel.kt:55` `lastUsage` 重置 OK 但未在 Done 后清掉

实测 line 66 `lastUsage = null` 在 `start` 已重置,Done 后续调 `regenerate` 会再次清,逻辑正确。**只是说明,无需修**。

---

## 推荐修复优先级

| 顺序 | 项 | 阻塞 |
| --- | --- | --- |
| 1 | **H1** | 🔴 UI 闭环不跑通 |
| 2 | **H2** | 🟠 跟 H1 联动 |
| 3 | **H3** | 🟠 选区闪烁 |
| 4 | **M1** | 🟡 CLAUDE.md 硬规则 |
| 5 | **M2-M5** | 🟢 顺手修 |
| 6 | **L1-L5** | 🟢 review r2 一起过 |

---

## OpenSpec 收尾

H1-H3 + M1-M5 修完后跑 §13 4 项验收,再写 r2 review 验,最后 `openspec archive ai-writing-actions -y`。