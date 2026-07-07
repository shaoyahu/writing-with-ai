## Why

当前笔记详情页的实体弹窗中，实体名称右侧显示的是英文类型名（如 `concept`），这既不本地化也不直观。用户反馈无法理解 `concept` 的含义，且没有区分用户手动添加的实体和 AI 自动提取的实体。需要为实体添加来源标识，并本地化显示类型标签。

用户原话："这种用户自己添加的实体，需要明确标识为用户自定义，需要在实体词右边添加一个 tag，显示为自定义。"

## What Changes

1. **数据库 schema 变更**：在 `note_entities` 表添加 `source` 字段，区分 `USER_ADDED`（用户手动添加）和 `AI_EXTRACTED`（AI 自动提取）
2. **实体弹窗 UI 调整**：
   - 用户自定义实体：显示 `实体名 · 自定义`
   - AI 提取实体：显示 `实体名 · 本地化类型名`（如 `强化学习 · 概念`）
3. **本地化支持**：实体类型名称支持多语言（中文/英文）
4. **数据迁移**：现有实体默认标记为 `AI_EXTRACTED`

## Capabilities

### New Capabilities
- `entity-source-tagging`: 实体来源标识与本地化显示

### Modified Capabilities
- `note-decompose-highlight`: 实体详情弹窗的标题格式从 `实体名 · 英文类型` 改为根据来源显示不同标签

## Impact

- **数据库**：`note_entities` 表新增 `source` 列，需 Room Migration
- **UI**：`QuickNoteDetailScreen` 实体弹窗标题渲染逻辑
- **本地化**：`strings.xml` 新增实体类型名称和"自定义"标签
- **数据层**：`NoteEntityRow` 和 `NoteEntityDao` 需适配新字段
- **调用方**：
  - `QuickNoteDetailViewModel.addEntityFromSelection()` — 用户添加实体时设置 `source = USER_ADDED`
  - `LlmEntityExtractor` — AI 提取实体时设置 `source = AI_EXTRACTED`
