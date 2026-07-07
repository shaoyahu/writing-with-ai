# entity-source-tagging Specification

## Purpose

为笔记实体添加来源标识（用户手动添加 vs AI 自动提取），并在实体详情弹窗中根据来源显示不同的标签：用户自定义实体显示"自定义"标签，AI 提取实体显示本地化的类型名称。

Synced from OpenSpec change `entity-source-tagging`(2026-07-07)。

## Requirements

### Requirement: Entity source field
系统 SHALL 在 `note_entities` 表中添加 `source` 字段，用于标识实体的来源。`source` 为枚举类型，取值 `USER_ADDED`（用户手动添加）或 `AI_EXTRACTED`（AI 自动提取）。

#### Scenario: User adds entity manually
- **WHEN** 用户在笔记详情页选中文本并点击"添加实体"
- **THEN** 系统创建 `NoteEntityRow` 时设置 `source = USER_ADDED`

#### Scenario: AI extracts entities
- **WHEN** 用户点击"拆解"菜单项，AI 抽取实体
- **THEN** 系统创建 `NoteEntityRow` 时设置 `source = AI_EXTRACTED`

#### Scenario: Migration sets default source
- **WHEN** 数据库从旧版本迁移到新版本（添加 `source` 列）
- **THEN** 现有实体的 `source` 字段默认值为 `AI_EXTRACTED`

### Requirement: Entity type localization
系统 SHALL 为 `EntityType` 枚举的 12 个类型提供本地化显示名称，支持中文和英文。

#### Scenario: Chinese locale shows Chinese type names
- **WHEN** 系统语言为中文
- **THEN** `EntityType.CONCEPT` 显示为"概念"，`EntityType.PERSON` 显示为"人物"，以此类推

#### Scenario: English locale shows English type names
- **WHEN** 系统语言为英文
- **THEN** `EntityType.CONCEPT` 显示为"Concept"，`EntityType.PERSON` 显示为"Person"，以此类推

### Requirement: Entity sheet title format
系统 SHALL 在实体详情弹窗（ModalBottomSheet）标题中，根据实体来源显示不同的标签格式。

#### Scenario: User-added entity shows custom tag
- **WHEN** 用户点击一个 `source = USER_ADDED` 的实体
- **THEN** 弹窗标题显示为 `surfaceForm · 自定义`（中文）或 `surfaceForm · Custom`（英文）

#### Scenario: AI-extracted entity shows localized type
- **WHEN** 用户点击一个 `source = AI_EXTRACTED` 的实体
- **THEN** 弹窗标题显示为 `surfaceForm · 本地化类型名`，如 `强化学习 · 概念`