## Context

笔记详情页（`QuickNoteDetailScreen`）当前使用 `BasicTextField(readOnly = true)` 展示笔记正文，支持文本选区追踪（用于 AI 操作）。底部有 `RelatedNotesSection` 展示关联笔记卡片。

现有实体系统已完整：
- `LlmEntityExtractor.extractAndPersist(noteId)` — LLM 抽取实体，保存 `NoteEntityRow`（含 `spanStart/spanEnd`）
- `EntityBacklinker.compute(srcNoteId)` — 通过共享实体计算 `ENTITY_HIT` 链接边
- `CompositeNoteLinker.recomputeForNote(noteId)` — 统一重算所有信号
- `NoteLinker.getBacklinks(noteId)` — 查反向链接
- `EntityAliasDao` — 别名展开

`spanStart/spanEnd` 基于 `title + "\n" + content` 拼接文本的字符偏移，已持久化但**从未用于 UI 渲染**。

## Goals / Non-Goals

**Goals:**
- 详情页下拉菜单新增"拆解"入口（需 AI 模型已配置）
- 触发后 AI 抽取实体 → 正文实体文本标注下划线
- 点击下划线实体 → ModalBottomSheet 展示关联笔记
- 实体结果持久化，再次打开笔记直接展示下划线
- 已拆解笔记菜单项文案变为"重新拆解"

**Non-Goals:**
- 标题中的实体不标注下划线（标题用 `Text`，改造成本高）
- 不新增数据库表或字段（复用现有 `note_entities` / `note_links`）
- 不修改 `LlmEntityExtractor` / `EntityBacklinker` / `CompositeNoteLinker` 的实现
- 不做实体类型筛选 UI（展示所有类型实体）
- 不做实体颜色区分（所有实体统一 primary 色下划线）

## Decisions

### D1: 正文渲染方案 — ClickableText 替换 BasicTextField

**选择**: 将详情页正文从 `BasicTextField(readOnly = true)` 改为 `ClickableText`，外层可选包裹 `SelectionContainer`。

**理由**:
- `BasicTextField(readOnly)` 不支持 `AnnotatedString` 的点击注解回调
- `ClickableText` 天然支持 `getStringAnnotations(tag, start, end)` 反查点击位置对应的实体
- 详情页正文是只读展示，不需要 `BasicTextField` 的编辑能力

**替代方案**:
- `BasicTextField` + `visualTransformation` + 自定义 `pointerInput`：可行但复杂，需手动处理点击位置→字符偏移映射
- 纯 `Text` + `pointerInput`：失去 `getStringAnnotations` 的便利性

### D2: 实体下划线渲染 — AnnotatedString + SpanStyle

**选择**: 用 `buildAnnotatedString` 在实体 span 范围添加 `SpanStyle(textDecoration = Underline, color = primary)` + `StringAnnotation(tag="entity", annotation=entityKey)`。

**理由**:
- Compose 原生支持，无需自定义 layout
- `StringAnnotation` 可在点击时反查实体 key
- 下划线 + 颜色变化双重视觉提示

**span 偏移映射**: `spanStart/spanEnd` 基于 `title + "\n" + content`，正文偏移 = `spanStart - title.length - 1`（减去标题长度和换行符）。只映射 `spanStart >= title.length + 1` 的实体到正文。

### D3: 实体重叠处理 — 最长匹配优先

**选择**: 当多个实体 span 重叠时，保留 span 范围最长的实体。

**理由**: "小明家"和"小明"重叠时，"小明家"作为更完整的实体信息更有价值。实现方式：按 `spanEnd - spanStart` 降序排列，逐个应用 SpanStyle，后应用的短 span 不会覆盖已应用的长 span 的注解。

### D4: BottomSheet 内容 — 复用 NoteLinker + 过滤 ENTITY_HIT

**选择**: 点击实体时，调用 `NoteLinker.getBacklinks(noteId)` 获取反向链接，过滤 `signals.contains(ENTITY_HIT)` 且 evidence 中 `sharedEntities` 包含当前实体 key 的笔记。

**理由**: 复用现有 `NoteLinker` SPI，不新增查询方法。`getBacklinks` 已返回 `RelatedNote`（含 title/preview/signals/evidence），UI 可直接渲染。

### D5: 拆解状态管理 — ViewModel StateFlow

**选择**: 在 `QuickNoteDetailViewModel` 中新增：
- `decomposeState: StateFlow<DecomposeState>` — Idle / Loading / Decomposed(entityCount) / Error(message)
- `entityRows: StateFlow<List<NoteEntityRow>>` — 当前笔记的实体列表
- `decompose()` 方法 — 触发抽取 + 重算关联 + 刷新实体列表

**理由**: 与现有 ViewModel 模式一致（`syncMessage`/`syncLoading`/`aiMetaDisplay` 等），状态驱动 UI。

### D6: 菜单项可见性 — 运行时检查 AI 配置

**选择**: 复用现有 `SecureApiKeyStore.observeConfiguredProviders()` 检查，与 AI 操作按钮行为一致。

**理由**: 不引入新的配置项，与项目现有 AI 功能入口的可见性逻辑统一。

## Risks / Trade-offs

- **[span 偏移失效]** 笔记内容编辑后，已保存的 `spanStart/spanEnd` 可能指向错误位置 → 重新拆解时 `extractAndPersist` 先 `deleteByNoteId` 再 `upsertAll`，覆盖旧结果
- **[ClickableText 与 SelectionContainer 冲突]** `SelectionContainer` 可能拦截 `ClickableText` 的点击事件 → 实测验证，如有冲突则改用 `Text` + `pointerInput` 方案
- **[indexOf 匹配不准]** `LlmEntityExtractor` 用 `content.indexOf(surface)` 只匹配首次出现，多次出现的同一 surfaceForm 只标注第一个 → 可接受，后续可优化为多位置匹配
- **[性能]** 大量实体时 AnnotatedString 构建开销 → 实体数量上限 66（`EntityBacklinker.MAX_HITS`），实际笔记实体通常 < 20，无性能问题
