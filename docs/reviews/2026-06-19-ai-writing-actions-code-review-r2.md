# code-review · ai-writing-actions · r2

**Date:** 2026-06-19
**Subject:** `ai-writing-actions`(M3 AI 写作操作) — r2 review:验 r1 全部修复
**Review type:** code-review(r2,focused on fixes only)
**Basis:** `docs/reviews/2026-06-19-ai-writing-actions-code-review-r1.md`

---

## 总结

**r1 全部 13 项修复通过,无新引入 bug。** 0 个非 PascalCase 违规(ktlintCheck 仅 17 个已知 Compose PascalCase,同 M0 follow-up)。

| 评判 | 数量 |
| --- | --- |
| PASS | 13 |
| FAIL | 0 |

| 验收 | 结果 |
| --- | --- |
| `assembleDebug` | ✅ BUILD SUCCESSFUL |
| `testDebugUnitTest` | ✅ 27 tests pass(0 failure) |
| `lintDebug` | ✅ BUILD SUCCESSFUL |
| `ktlintCheck` | ✅ 0 非 PascalCase 新增(17 个 `function-naming` = 已知 M0 follow-up) |

---

## 逐项验证

### H1 — `aiState` snapshot read 不触发重组 ✅ PASS

`QuickNoteDetailScreen.kt:89-91` 改为 `by` 委托订阅:

```kotlin
val aiStateFlow: StateFlow<AiActionUiState> =
    aiVm?.state ?: remember { MutableStateFlow(AiActionUiState.Idle) }
val aiState: AiActionUiState by aiStateFlow.collectAsStateWithLifecycle()
```

`aiVm` 真存在时订阅其 StateFlow;`null` 时 remember 一个 Idle 流。Compose 在 State 变化时会触发重组,Sheet 正常显示。

### H2 — `noteId == null` 时残留 FAB ✅ PASS

`QuickNoteDetailScreen.kt` `floatingActionButton` block 改为三段 gate:

```kotlin
if (current != null && noteId != null && aiVm != null) {
    AiActionFab(...)
    ActionSheet(...)
}
```

`noteId` 从 saved state 缺失 / NotFound 状态下,FAB 与 Sheet 都不会渲染。

### H3 — `remember(current)` 重置选区 ✅ PASS

`QuickNoteDetailScreen.kt:96-100` 改 `remember(noteId)`:

```kotlin
var textFieldValue by remember(noteId) {
    mutableStateOf(TextFieldValue(text = current?.note?.note?.content.orEmpty()))
}
```

selection 不再从 ViewModel 反向同步;`BasicTextField.onValueChange` 单向推到 `ViewModel.onSelectionChange`。noteId 不变(典型用例)→ 选区稳定保留;noteId 变 → 重新初始化(预期)。

### M1 — `opNameToZh` 硬编码中文 ✅ PASS

`QuickNoteDetailViewModel.kt`:
- 删 `opNameToZh` 私有函数
- `AiMetaDisplay.opName: String` → `AiMetaDisplay.opKey: String`

`QuickNoteDetailScreen.kt:225` 改用:

```kotlin
stringResource(R.string.quicknote_meta_ai_fmt, stringResource(opKeyToRes(meta.opKey)), meta.opAt)
```

`opKeyToRes("expand")` → `R.string.aiwriting_action_expand` → `stringResource` 拿中文"扩写"。CLAUDE.md "禁止硬编码中文"硬规则满足。

### M2 — `acceptReplace` tags 读取 race ✅ PASS

`AiActionViewModel.kt:114-123` 改为单次 `observeNoteWithTags(noteId).first()`:

```kotlin
val existingFlow = noteRepository.observeNoteWithTags(noteId)
val existing = existingFlow.first() ?: return@withContext
val now = System.currentTimeMillis()
val updated = existing.note.copy(content = aiText, updatedAt = now)
noteRepository.upsert(updated, existing.tags)
```

注:`val existingFlow = ...` 中间变量是为了绕开 ktlint `multiline-expression-wrapping`(同一个文件 line 117 改成 `existingFlow.first()`,逻辑不变)。

测试相应更新(`getNote` mock 删,`coVerify { observeNoteWithTags("n1") }` 加)。

### M3 — `SimpleDateFormat` 每次重建 ✅ PASS

`QuickNoteDetailViewModel.kt:117-119` hoist 到顶层:

```kotlin
private val DATE_TIME_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
```

`formatLocalDateTime(epochMs)` 改成 `DATE_TIME_FORMAT.format(Date(epochMs))`。单例化,无每次 `map` 重建。

注:`SimpleDateFormat` 非线程安全 — 当前 `map { ... }` 单线程安全(`viewModelScope` 主 dispatcher);若未来需要并发,应换 `DateTimeFormatter` 或 `ThreadLocal<SimpleDateFormat>`。

### M4 — `noteId` 边缘 case ✅ PASS

H2 修了 gate 后,`currentNoteId = noteId` 复用(原 `current.note.note.id`),消除重复 `noteId` 来源。

### M5 — `StreamingPanel` Failed 顶部文案 ✅ PASS

`strings.xml` 加 `aiwriting_panel_failed_title = "出错"`(zh)+ `TODO(en):` 占位(en)。
`StreamingPanel.kt:114` 改用新 key:

```kotlin
HeaderRow(title = stringResource(R.string.aiwriting_panel_failed_title), usage = null)
```

### L1 — 全限定名 import 丑 ✅ PASS

`AiwritingEntry.kt` 删 1 行 `androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner.current`,改成函数体直接调 `hiltViewModel<AiActionViewModel>()`(同时满足 L4)。

### L2 — ActionSheet 整理 icon ✅ PASS

`ActionSheet.kt`:
- import `Icons.AutoMirrored.Filled.List`
- use site `Icons.AutoMirrored.Filled.Sort` → `Icons.AutoMirrored.Filled.List`
- 删 `Icons.AutoMirrored.Filled.Sort` import

### L3 — StreamingPanel 重复副标题 ✅ PASS

`StreamingPanel.kt:78` `partialText.ifBlank { stringResource(...) }` 改成 `partialText.ifBlank { "…" }`(占位三个点,不再重复"进行中…")。

### L4 — `hiltViewModel` 显式 storeOwner 多余 ✅ PASS

`AiwritingEntry.kt` 删 `viewModelStoreOwner = storeOwner` 参数。

### L5 — `lastUsage` 重置 ✅ PASS(N/A)

r1 注明实测 line 66 `lastUsage = null` 已在 `start` 重置,**无需修**。r2 验确认行为正确。

---

## 额外清理

| 项 | 说明 |
| --- | --- |
| 测试 | `accept_replace_upserts_and_writes_ai_metadata` 跟随 M2 改动,删 `getNote` mock + verify,加 `observeNoteWithTags` verify |
| ktlint | 跑 `ktlintFormat` 自动修 import-ordering(`kotlinx.coroutines.flow.*` 移到底部) |

---

## OpenSpec 收尾

r2 全过 → 可以 `openspec archive ai-writing-actions -y` → 更新 `docs/progress.md` + `docs/plans/writing-with-ai-mobile-roadmap.md` §13 / §15.2 标 done。