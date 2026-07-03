# Code Review · note-decompose-highlight · r1

**日期**: 2026-07-03
**审查范围**: 全量未提交变更（24 文件，+967 / -249 行）
**审查方式**: 4 维度并行审查（正确性、Compose UI、架构 DI、安全性能）+ 对抗性验证
**结果**: 30 confirmed / 2 refuted

---

## HIGH — 3 findings

### H1. 错误信息直接暴露到 UI

**文件**: `QuickNoteDetailViewModel.kt:442`
**分类**: Security

`DecomposeState.Error(e.message ?: "未知错误")` 将原始异常消息传播到 UI，Snackbar 直接展示。AI API 调用失败的异常可能包含内部端点 URL、HTTP 状态码、响应体片段。

虽然 AI provider 层 (`AnthropicCompatibleAdapter`) 已做 `sanitizeErrorDetail()`（截断 200 字符 + 脱敏 API key），但 Room `SQLiteException`、`NoteLinker` 内部异常的 message 可能泄漏 SQL 语句或类名。同文件 `SyncMessage.Failure` 也有 10+ 处相同的 `e.message` 模式。

**修复建议**: 引入 `mapToUserMessage(e: Exception): String` 工具函数（放 `core/common/`），将已知异常类型映射到 string resource key，未知异常 fallback 通用文案，同时 `Log.w` 全量 message 供调试。对 `DecomposeState.Error` 和 `SyncMessage.Failure` 统一适用。

---

### H2. BottomSheet 关闭动画被截断

**文件**: `QuickNoteDetailScreen.kt:826-829`
**分类**: UI lifecycle

点击关联笔记卡片时，代码同步执行 `selectedEntity = null`，再 `launch { entitySheetState.hide() }`。但 `selectedEntity = null` 使 `if (entity != null && entitySheetState.isVisible)` 条件立刻为 false，ModalBottomSheet 从 Compose 树移除，关闭动画被跳过，视觉上瞬间消失。

**修复建议**: Card onClick 中只调用 `entitySheetState.hide()`，不设 `selectedEntity = null`。让 `onDismissRequest` 回调在动画完成后清理 `selectedEntity`：
```kotlin
onClick = {
    snackbarScope.launch { entitySheetState.hide() }
    onNavigateToNote(related.noteId)
}
```
注意：`onDismissRequest` 在 `hide()` 完成后也会触发，需确保不重复导航。

---

### H3. ClickableText onClick 中状态设置时序问题

**文件**: `QuickNoteDetailScreen.kt:657-665`
**分类**: UI state consistency

`selectedEntity = entity` 在协程外同步设置，`relatedForEntity` 和 `entitySheetState.show()` 在协程内异步执行。虽然当前代码中 `entitySheetState.isVisible` 在 `show()` 完成前为 false（sheet 不会提前渲染），但 `selectedEntity` 已非 null 会在重组时产生不一致的中间状态。若未来 visibility 判断逻辑变更，可能出现 sheet 短暂展示空数据。

**修复建议**: 将 `selectedEntity = entity` 移到协程内、`relatedForEntity` 赋值之后、`entitySheetState.show()` 之前：
```kotlin
snackbarScope.launch {
    val related = noteLinker.getBacklinks(currentNoteId)
    relatedForEntity = related.filter { ... }
    selectedEntity = entity
    entitySheetState.show()
}
```

---

## MEDIUM — 8 findings

### M1. Snackbar 在 Loading → Decomposed 时排队闪烁

**文件**: `QuickNoteDetailScreen.kt:210-226`
**分类**: UX

`LaunchedEffect(decomposeState)` 为每个状态都调 `showSnackbar`。`decompose()` 快速完成时 Loading Snackbar 先入队，Decomposed Snackbar 紧随其后，用户看到两条 Snackbar 依次闪过。Loading Snackbar 在此场景下是冗余的——结果马上就到。

**修复建议**: 方案一（简单）：不给 Loading 状态显示 Snackbar，改用 `LinearProgressIndicator` 或仅依赖 T3 菜单项 disabled 状态表示加载中。方案二：用 `Job?` 引用取消前一个 Snackbar：
```kotlin
var snackbarJob by remember { mutableStateOf<Job?>(null) }
LaunchedEffect(decomposeState) {
    snackbarJob?.cancel()
    snackbarJob = snackbarScope.launch { snackbarHostState.showSnackbar(...) }
}
```

---

### M2. `String.format` 替代 `context.getString` 传入格式参数

**文件**: `QuickNoteDetailScreen.kt:217`
**分类**: Android best practice

`decomposeFoundFmt.format(ds.entityCount)` 使用 Kotlin `String.format()`，绕过了 Android 编译期格式参数类型检查。`context.getString(R.string.note_decompose_found_fmt, ds.entityCount)` 更安全，且与项目其他格式化用法一致。

**修复建议**: 替换为 `context.getString(R.string.note_decompose_found_fmt, ds.entityCount)`。

---

### M3. 重叠实体的 annotation 解析歧义

**文件**: `QuickNoteDetailScreen.kt:1043-1072`（`buildEntityAnnotatedString`）
**分类**: Logic

重叠实体按 span 长度降序添加 `addStringAnnotation`，两个重叠实体的 annotation 在相同 offset 上都存在。`getStringAnnotations(start, end)` 返回所有匹配，但代码用 `firstOrNull()` 取第一个——结果取决于 `AnnotatedString` 内部存储顺序，不一定是最长 span。

实际风险较低（AI 实体提取很少产生真正重叠的 span），但逻辑不确定。

**修复建议**: 如需确定性，可在添加 annotation 时跳过已被更长 span 覆盖的 offset 区间，或在 onClick 中用 `maxByOrNull` 取最长的 annotation。

---

### M4. UI 层直接引用 Room Entity 和 DB 枚举

**文件**: `QuickNoteDetailScreen.kt:87-88, 229, 661`
**分类**: Architecture

Screen 直接 import `NoteEntityRow`（Room Entity）和 `LinkType`（DB 枚举），用于 UI 状态（`selectedEntity: NoteEntityRow?`）和内联过滤逻辑（`signals.contains(LinkType.ENTITY_HIT)`）。违反"UI 消费 domain model"原则。

注意 `RelatedNote.signals: Set<LinkType>` 已让 `LinkType` 成为 UI 的传递依赖，所以完全隔离需一并处理。

**修复建议**: 创建轻量 domain 投影 `EntityHighlight(surfaceForm, entityType, entityKey, contentStart, contentEnd)`，在 ViewModel 中映射。将 `signals.contains(LinkType.ENTITY_HIT)` 过滤逻辑移入 ViewModel 或 `NoteLinker.getRelatedByEntity(noteId, entityKey)` 方法。

---

### M5. `loadCachedEntities` 中 StateFlow 赋值非原子

**文件**: `QuickNoteDetailViewModel.kt` — `loadCachedEntities()`
**分类**: Thread safety

`_entityRows.value = ...` 和 `_decomposeState.value = ...` 两次赋值间可能被其他协程插入（如 `decompose()` 同时触发），导致 `_decomposeState` 引用的 entityCount 与 `_entityRows.value.size` 不一致。

**修复建议**: 合并为单次 StateFlow 更新，或使用 `update {}` 确保原子性。实际上由于 ViewModel 方法均跑在 `viewModelScope`（main dispatcher），且 `loadCachedEntities` 和 `decompose` 都有 Loading guard，竞态窗口极小，但代码防御性不足。

---

### M6. `NoteRepository.observeNotesWithTags()` 双订阅 `notesFlow`

**文件**: `NoteRepository.kt:121-133`
**分类**: Performance

`notesFlow` 被 `combine()` 第一个参数和 `flatMapLatest` 内部分别订阅，每次 emission 触发两次 DAO 查询和 `toModel()` 映射。

**修复建议**: 用 `shareIn` 共享上游，或重构为单一 `flatMapLatest` 在一次订阅中同时携带 notes + firstImages。

---

### M7. NoteRow 中 `produceState` 解码 Bitmap 无缓存

**文件**: `NoteRow.kt:148-165`
**分类**: Performance

LazyColumn 滚出再滚入时 `produceState` 重新 `BitmapFactory.decodeFile`，无应用级 `LruCache`。快速滑动时重复 IO 解码造成卡顿。

**修复建议**: 引入 `core/media/LruBitmapCache`（1/8 可用内存），先查缓存再解码。长期考虑引入 Coil。

---

### M8. `observeFirstImageForNotes` IN 子句无上限保护

**文件**: `NoteAttachmentDao.kt:208-221`
**分类**: Edge case

`WHERE noteId IN (:noteIds)` 无 list size 限制。旧版 SQLite `SQLITE_MAX_VARIABLE_NUMBER=999`，千条笔记时可能崩溃。Repository 层有 `isEmpty()` guard 但无上限 guard。

**修复建议**: 在 `NoteRepository` 调用前加防御性 cap：`if (notes.size > 500) take(500)`，或 DAO 注释中说明上限约束。

---

## LOW — 5 findings

### L1. `CenterCreateCard` 用 `Color.White` 替代 `onTertiary`
**文件**: `AppShell.kt:290`
暗色模式下白字在浅 tertiary 背景上可能对比度不足。应改用 `MaterialTheme.colorScheme.onTertiary`。

### L2. `CenterCreateCard` / `TabCard` 的 `indication = null` 无触摸反馈
**文件**: `AppShell.kt:286-305`
移除了 ripple，对无障碍用户不友好。`CenterCreateCard` 无选中态更需视觉反馈。

### L3. `AnimationStyle` 枚举重排序
**文件**: `AnimationStyle.kt:11-23`
IMMERSIVE 从 ordinal 3 移到 0。DataStore 按 name 序列化没问题，但需确认无任何代码按 ordinal 持久化。默认值从 MINIMAL 改为 IMMERSIVE 是有意变更。

### L4. `observeFirstImageForNotes` 空列表风险
**文件**: `NoteAttachmentDao.kt:51-64`
DAO 无空列表 guard，Room 会生成无效 SQL `IN ()`。Repository 有 `isEmpty()` 保护，但 DAO 缺 KDoc 警告。

### L5. ClickableText 无 accessibility semantics
**文件**: `QuickNoteDetailScreen.kt:639-667`
实体标注区域无 contentDescription，屏幕阅读器用户无法发现可交互实体。项目早期阶段优先级低。

---

## Refuted — 2 findings

1. **"decompose() 触发两次 recomputeForNote()"** — 实际不成立。ViewModel 的 `decompose()` 不调用 `repository.upsert()` 或 `addTagToNote()`，因此 Repository 的 debounced recomputeFlow 不会被触发。双重 `NoteLinker` 注入是代码组织味道，非数据竞争。
2. **"titleLen 偏移量计算在空标题时出错"** — 不成立。`LlmEntityExtractor` 拼接 `title + "\n" + content`，screen 端 `titleLen = title.length + 1` 完全匹配（空标题时 titleLen=1，extractor 产出 `"\n" + content`，偏移一致）。风险仅是隐式契约漂移——应抽取共享常量。

---

## 建议修复优先级

| 优先级 | Finding | 工作量 |
|--------|---------|--------|
| 🔴 立修 | H1 错误信息暴露 | S (1h) |
| 🔴 立修 | H2 BottomSheet 动画截断 | S (30min) |
| 🔴 立修 | H3 onClick 状态时序 | S (15min) |
| 🟡 本轮 | M1 Snackbar 排队 | S (30min) |
| 🟡 本轮 | M2 String.format → getString | XS (5min) |
| 🟡 本轮 | M4 UI 层引用 DB 类型 | M (2h) |
| 🟢 推迟 | M3 重叠 annotation | S (1h) |
| 🟢 推迟 | M5 StateFlow 非原子 | S (30min) |
| 🟢 推迟 | M6 notesFlow 双订阅 | M (2h) |
| 🟢 推迟 | M7 Bitmap 缓存 | M (2h) |
| 🟢 推迟 | M8 IN 子句上限 | S (30min) |
| ⚪ 不修 | L1-L5 | 记录即可 |
