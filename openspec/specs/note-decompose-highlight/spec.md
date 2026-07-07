# note-decompose-highlight Specification

## Purpose

笔记详情页「拆解」AI 能力:从笔记正文抽取实体，并在正文中以带下划线 + primary 色高亮、点击实体弹出 BottomSheet 展示关联笔记。

Synced from OpenSpec change `note-decompose-highlight`(2026-07-03)。

## Requirements

### Requirement: Decompose menu entry
系统 SHALL 在笔记详情页右上角下拉菜单中始终提供"拆解"菜单项。当用户未配置任何 AI 模型时，菜单项显示为淡灰色且不可点击；当用户已配置至少一个 AI 模型时，菜单项可正常点击。

#### Scenario: AI model configured shows decompose menu
- **WHEN** 用户已配置至少一个 AI 模型 apikey，且用户打开笔记详情页下拉菜单
- **THEN** 菜单中显示"拆解"菜单项，图标为 `Icons.Outlined.Hub`，菜单项可点击

#### Scenario: No AI model shows disabled decompose menu
- **WHEN** 用户未配置任何 AI 模型 apikey，且用户打开笔记详情页下拉菜单
- **THEN** 菜单中显示"拆解"菜单项，但文字和图标呈淡灰色（`onSurface.copy(alpha = 0.38f)`），菜单项不可点击

#### Scenario: Already decomposed note shows re-decompose
- **WHEN** 当前笔记已有实体抽取记录（`note_entities` 表中存在该 noteId 的行），且用户打开下拉菜单
- **THEN** 菜单项文案显示为"重新拆解"而非"拆解"

### Requirement: Decompose triggers entity extraction
系统 SHALL 在用户点击"拆解"菜单项时，调用 AI 抽取当前笔记的实体并重算笔记关联。

#### Scenario: Successful decompose
- **WHEN** 用户点击"拆解"菜单项
- **THEN** 系统依次执行：1) `LlmEntityExtractor.extractAndPersist(noteId)` 抽取实体；2) `CompositeNoteLinker.recomputeForNote(noteId)` 重算关联；3) 刷新 UI 展示实体下划线

#### Scenario: Decompose finds no entities
- **WHEN** 用户点击"拆解"但 AI 未发现任何实体
- **THEN** 系统展示 Snackbar 提示"未发现实体"

#### Scenario: Decompose fails
- **WHEN** 用户点击"拆解"但 AI 调用失败
- **THEN** 系统展示 Snackbar 提示错误信息

#### Scenario: Decompose loading state
- **WHEN** 拆解 AI 调用进行中
- **THEN** 详情页顶部显示加载指示器，菜单项不可重复触发

### Requirement: Entity underline rendering
系统 SHALL 在笔记详情页正文中，对已抽取的实体文本渲染下划线样式。

#### Scenario: Entities shown with underline
- **WHEN** 当前笔记有实体抽取记录且拆解完成
- **THEN** 正文中每个实体文本范围显示 `TextDecoration.Underline` + `colorScheme.primary` 颜色的下划线样式

#### Scenario: Title entities not underlined
- **WHEN** 实体的 spanStart 位于标题范围内（spanStart < title.length + 1）
- **THEN** 该实体不在正文中渲染下划线

#### Scenario: Overlapping entities use longest match
- **WHEN** 两个实体 span 重叠（如"小明"和"小明家"）
- **THEN** 保留 span 范围最长的实体的下划线和点击注解

#### Scenario: Cached entities shown on re-entry
- **WHEN** 用户打开一篇之前已拆解过的笔记
- **THEN** 系统直接从 `note_entities` 表加载实体，正文立即展示下划线，无需重新触发 AI

### Requirement: Entity click opens bottom sheet
系统 SHALL 在用户点击正文中的下划线实体文本时，弹出底部抽屉展示关联笔记。

#### Scenario: Click entity shows related notes
- **WHEN** 用户点击正文中带下划线的实体文本
- **THEN** 系统弹出 ModalBottomSheet，标题根据实体来源显示：
  - 用户自定义实体（`source = USER_ADDED`）：`surfaceForm · 自定义`（中文）/ `surfaceForm · Custom`（英文）
  - AI 提取实体（`source = AI_EXTRACTED`）：`surfaceForm · 本地化类型名`（如 `强化学习 · 概念`）
  列表展示所有通过该实体关联的其他笔记

#### Scenario: Related notes are ENTITY_HIT filtered
- **WHEN** BottomSheet 展示关联笔记
- **THEN** 列表中的笔记均为 `NoteLinker.getBacklinks(noteId)` 结果中 `signals` 含 `ENTITY_HIT` 且 evidence 的 `sharedEntities` 包含当前实体 key 的笔记

#### Scenario: Navigate to related note
- **WHEN** 用户在 BottomSheet 中点击某条关联笔记
- **THEN** 系统关闭 BottomSheet 并导航到该笔记详情页

#### Scenario: No related notes for entity
- **WHEN** 用户点击实体但该实体无关联笔记
- **THEN** BottomSheet 展示空态提示"暂无关联笔记"

#### Scenario: Dismiss bottom sheet
- **WHEN** 用户下拉或点击 BottomSheet 外部区域
- **THEN** BottomSheet 关闭，回到笔记详情页
