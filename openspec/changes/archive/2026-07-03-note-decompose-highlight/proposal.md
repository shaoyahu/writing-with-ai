# note-decompose-highlight

## 概述

笔记详情页新增"拆解"功能：通过 AI 提取当前笔记中的实体文本，在正文中以**下划线**标注，点击带下划线的实体弹出**底部抽屉**展示所有通过该实体关联的其他笔记，实现不同笔记之间的互联互通。

## 动机

现有实体抽取系统（`LlmEntityExtractor` + `EntityBacklinker`）已能提取实体并计算笔记间的 `ENTITY_HIT` 关联，但用户无法在阅读笔记时直观感知这些关联 — 实体信息只存在于数据库和底部的"关联"Section 中，缺乏沉浸式的交互体验。

"拆解"功能将隐藏的实体关联可视化：实体文本直接在正文中标注下划线，点击即可浏览关联笔记，让笔记之间的关联从"被动查看"变为"主动发现"。

## 用户故事

1. 作为用户，我在笔记详情页点击右上角菜单的"拆解"按钮，AI 分析当前笔记后，正文中的实体文本（人名、书名、地点等）自动标注下划线
2. 作为用户，我点击任意带下划线的实体文本，底部弹出抽屉展示所有包含相同/相似实体的其他笔记
3. 作为用户，我在抽屉中点击某条关联笔记，跳转到该笔记详情页
4. 作为用户，我下次打开同一篇笔记时，之前拆解的实体下划线仍然可见（结果已缓存）

## 核心设计

### 1. 菜单入口

- 位置：笔记详情页右上角 `AppActionDropdown` 菜单
- 条件：`SecureApiKeyStore.observeConfiguredProviders().first().isNotEmpty()` 时才显示
- 图标：`Icons.Outlined.Hub`
- 点击行为：
  1. 调用 `LlmEntityExtractor.extractAndPersist(noteId)` 抽取实体
  2. 调用 `CompositeNoteLinker.recomputeForNote(noteId)` 重算关联
  3. 更新 UI 状态进入"已拆解"模式，显示实体下划线

### 2. 实体下划线标注

- 使用 `BasicTextField(readOnly = true)` 的 `visualTransformation` 参数，将 `NoteEntityRow` 的 `spanStart/spanEnd` 映射为 `SpanStyle(textDecoration = TextDecoration.Underline, color = MaterialTheme.colorScheme.primary)`
- 注意：`spanStart/spanEnd` 基于 `title + "\n" + content` 拼接文本的偏移，需减去标题部分长度后映射到纯正文偏移
- 详情页标题中的实体暂不标注下划线（标题用 `Text` 而非 `BasicTextField`，改造成本高且交互价值低）
- 实体重叠时（如"小明"和"小明家"），取最长匹配

### 3. 点击实体 → 底部抽屉

- 使用 `ModalBottomSheet`（项目中已有 `StreamingPanel` 的使用范例）
- 点击下划线文本时，根据点击位置反查对应的 `NoteEntityRow`
- 抽屉内容：
  - 标题：实体 surfaceForm + 实体类型（如"小明 · 人物"）
  - 列表：所有通过该实体关联的其他笔记（复用 `NoteLinker.getBacklinks` 的 `ENTITY_HIT` 过滤结果）
  - 每条笔记显示：标题、内容预览、共享实体 chip
  - 点击跳转到对应笔记详情页

### 4. 缓存策略

- 实体抽取结果已持久化在 `note_entities` 表（`LlmEntityExtractor.extractAndPersist` 的 upsert 行为）
- 关联计算结果已持久化在 `note_links` 表
- 进入笔记详情页时，若 `note_entities` 中已有该笔记的实体记录，直接展示下划线，无需重新触发 AI
- 下拉菜单中的"拆解"按钮文案区分：
  - 未拆解过："拆解"
  - 已拆解过："重新拆解"（触发重新抽取覆盖旧结果）

### 5. 点击位置 → 实体反查

`BasicTextField(readOnly = true)` 本身不提供点击位置回调。需要采用以下方案：

**方案 A（推荐）：AnnotatedString + ClickableText**

1. 将正文构建为 `buildAnnotatedString`，在实体 span 范围添加 `withAnnotation(tag = "entity", annotation = entityKey)` + 下划线 `SpanStyle`
2. 用 `ClickableText`（或 `BasicTextField` + `visualTransformation` + 自定义点击处理）处理点击
3. 点击时通过 `getTextLayoutResult().getOffsetForPosition()` + `getStringAnnotations(tag, start, end)` 获取实体

方案 A 更符合 Compose 惯用模式，且 `ClickableText` 天然支持点击位置 → 注解反查。

**方案 B：点击手势 + TextField 布局偏移**

1. 在 `BasicTextField` 外层叠加透明 `Box` + `pointerInput` 拦截点击
2. 点击时获取相对文本区域的偏移量
3. 用 `TextLayoutResult` 的 `getOffsetForPosition()` 将像素偏移转为字符偏移
4. 查找该字符偏移落在哪个 `NoteEntityRow` 的 `[spanStart, spanEnd)` 区间

需要切换到 `TextField`（非 `BasicTextField`）以获取 `TextLayoutResult`，或者使用 `onTextLayout` 回调缓存布局信息。

### 6. 拆解状态 UI

- 拆解进行中：顶部显示 `LinearProgressIndicator` + "正在拆解…"
- 拆解完成：Snackbar 提示"已发现 N 个实体"
- 拆解失败：Snackbar 提示错误信息
- 已拆解状态：实体下划线常驻，菜单项变为"重新拆解"

## 技术要点

### 修改文件清单（预估）

| 文件 | 改动 |
|---|---|
| `QuickNoteDetailScreen.kt` | 添加菜单项 + 实体下划线渲染 + BottomSheet + 拆解状态 |
| `QuickNoteDetailViewModel.kt` | 添加拆解相关状态和方法 |
| `NoteEntityDao.kt` | 无新增，复用 `getByNoteId` |
| `LlmEntityExtractor.kt` | 无改动，复用 `extractAndPersist` |
| `CompositeNoteLinker.kt` | 无改动，复用 `recomputeForNote` |
| `strings.xml` | 添加"拆解"/"重新拆解"/"正在拆解…"/"已发现 N 个实体"等文案 |

### 数据流

```
用户点击"拆解"菜单
    |
    v
ViewModel.decompose()
    |-- LlmEntityExtractor.extractAndPersist(noteId) → List<NoteEntityRow>
    |-- CompositeNoteLinker.recomputeForNote(noteId)  → 更新 note_links
    |-- entityDao.getByNoteId(noteId)                  → 刷新 UI 状态
    |
    v
UI: BasicTextField + visualTransformation 渲染实体下划线
    |
    v (用户点击实体下划线)
NoteLinker.getBacklinks(noteId).filter(ENTITY_HIT) → BottomSheet 展示
```

### 边界情况

- **笔记内容变更后**：span 位置可能失效 → 重新拆解时先 `deleteByNoteId` 再 `upsertAll`
- **无 AI 模型**：菜单项不显示（与现有 AI 操作按钮行为一致）
- **空笔记**：无实体可抽取，Snackbar 提示"未发现实体"
- **实体跨标题/正文边界**：只标注正文中的实体（spanStart > title.length 的部分）
- `indexOf(surface)` 可能匹配到错误位置（多次出现）→ 使用 `indexOf(surface, searchFrom)` 逐步推进

## 决策记录

- **D1: 交互形式** → 底部抽屉 (ModalBottomSheet)，用户选择
- **D2: 缓存策略** → 一次性触发+持久化，用户选择
- **D3: 点击方案** → 待实现时在方案 A/B 间选择（倾向 A：AnnotatedString）
- **D4: 标题实体** → 暂不标注下划线（标题用 `Text`，改造成本高）
- **D5: 菜单项可见性** → 需要 AI 模型已配置才显示（与现有 AI 功能一致）
