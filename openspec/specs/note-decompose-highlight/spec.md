# note-decompose-highlight Specification

## Purpose

笔记详情页「拆解」AI 能力:从笔记正文抽取实体,在正文中以 **primary 色字体 + 右上角十字星(✦)** 标记实体文本,点击实体弹出 BottomSheet 展示关联笔记。

Synced from OpenSpec changes:
- `note-decompose-highlight`(2026-07-03)
- `entity-source-tagging`(2026-07-07)
- `entity-management-and-ai-decompose`(2026-07-08)

## Requirements

### Requirement: Decompose menu entry

系统 SHALL 在笔记详情页下拉菜单中始终提供"拆解" / "重新拆解"菜单项。菜单项 MUST 视觉上正常显示(不灰),始终可点击。当用户未配置任何 AI 模型时,点击 MUST 弹出错误对话框 + 跳转入口。

#### Scenario: AI model configured shows decompose menu
- **WHEN** 用户已配置至少一个 AI 模型 apikey,且用户打开笔记详情页下拉菜单
- **THEN** 菜单中显示"拆解" / "重新拆解"菜单项,视觉正常,可点击

#### Scenario: No AI model shows decompose menu with error on click
- **WHEN** 用户未配置任何 AI 模型 apikey
- **THEN** 菜单仍显示"拆解"(视觉正常不灰),点击 MUST 弹出错误对话框"请先配置 AI 模型",带"去设置"按钮可跳 AI 设置页

#### Scenario: Already decomposed note shows re-decompose
- **WHEN** 当前笔记已有实体抽取记录(`note_entities` 表中存在该 noteId 的行)
- **THEN** 菜单项文案显示为"重新拆解"而非"拆解"

### Requirement: Decompose triggers entity extraction

系统 SHALL 在用户点击"拆解"或确认"重新拆解"时执行完整拆解流程:1) 全屏 loading;2) AI 抽取新实体;3) 匹配已有实体;4) 持久化结果;5) 关闭 loading + 刷新高亮。

#### Scenario: Successful decompose
- **WHEN** 用户点击"拆解"
- **THEN** 系统:1) 显示全屏 loading + "正在拆解...";2) 调 AI 抽新实体;3) 匹配已有实体;4) 持久化;5) 关闭 loading + 刷新高亮

#### Scenario: Re-decompose with confirmation
- **WHEN** 用户点击"重新拆解"
- **THEN** 系统 MUST 先弹确认对话框"重新拆解将覆盖现有实体,确定吗?",用户确认后才执行完整拆解

#### Scenario: Decompose finds no entities
- **WHEN** AI 返回空实体列表
- **THEN** loading 关闭 + Snackbar 提示"未发现实体"

#### Scenario: Decompose fails
- **WHEN** AI 调用失败
- **THEN** loading 关闭 + Snackbar 提示错误信息

#### Scenario: Decompose loading state
- **WHEN** 拆解 AI 调用进行中
- **THEN** 详情页顶部显示加载指示器(`LinearProgressIndicator`),菜单项不可重复触发

### Requirement: Auto-refresh existing entities on open

系统 SHALL 在用户打开笔记详情页时自动用已有实体匹配笔记内容(不调 AI)。

#### Scenario: Open note with existing entity mentions
- **WHEN** 用户打开一篇正文含"张三"的笔记,且数据库中存在匹配实体
- **THEN** 系统 MUST 自动创建 `note_entities` 关联 + 渲染 primary 色字体 + 十字星高亮

#### Scenario: Open note with no entity mentions
- **WHEN** 用户打开一篇无任何匹配实体的笔记
- **THEN** 系统 MUST 不创建任何关联 + 不显示高亮

### Requirement: Entity highlight rendering

系统 SHALL 在笔记详情页正文中,对已抽取的实体文本渲染 **`color = colorScheme.primary` 字体色 + 右上角十字星(✦)` 标记。标题范围内的实体 MUST NOT 渲染高亮。

#### Scenario: Entities shown with blue font and cross-star
- **WHEN** 当前笔记有实体抽取记录
- **THEN** 每个实体文本范围 MUST 用 `color = colorScheme.primary` 字体色 + 右上角小十字星(✦ 或自定义 drawable)

#### Scenario: Cross-star occupies fixed width
- **WHEN** 实体文本被渲染
- **THEN** 十字星 MUST 占约 8~12dp 宽度,不可覆盖相邻文字

#### Scenario: Title entities not highlighted
- **WHEN** 实体的 spanStart 位于标题范围内(spanStart < title.length + 1)
- **THEN** 该实体 MUST NOT 在正文中渲染高亮

#### Scenario: Overlapping entities use longest match
- **WHEN** 两个实体 span 重叠(如"小明"和"小明家")
- **THEN** 保留 span 范围最长的实体的高亮和点击注解

#### Scenario: Cached entities shown on re-entry
- **WHEN** 用户打开一篇之前已拆解过的笔记
- **THEN** 系统直接从 `note_entities` 表加载实体,正文立即展示高亮,无需重新触发 AI

### Requirement: Entity click opens bottom sheet

系统 SHALL 在用户点击正文中的高亮实体文本时,弹出底部抽屉展示关联笔记。

#### Scenario: Click entity shows related notes
- **WHEN** 用户点击正文中高亮实体文本
- **THEN** 系统弹出 ModalBottomSheet,标题根据实体来源显示:
  - 用户自定义实体(`source = USER_ADDED`):`surfaceForm · 自定义`(中文)/ `surfaceForm · Custom`(英文)
  - AI 提取实体(`source = AI_EXTRACTED`):`surfaceForm · 本地化类型名`(如 `强化学习 · 概念`)
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
- **THEN** BottomSheet 关闭,回到笔记详情页