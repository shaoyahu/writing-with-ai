## ADDED Requirements

### Requirement: Entity detail display

The system SHALL display entity details including `surfaceForm`, type, source, and associated notes list.

#### Scenario: Detail shows entity info
- **WHEN** EntityDetailScreen loads for entity "张三"
- **THEN** screen shows `surfaceForm = "张三"`, type = "人物", source = "AI提取" or "自定义"

#### Scenario: Detail shows associated notes
- **WHEN** entity has 3 associated notes
- **THEN** list shows all 3 notes with title and context snippet containing the entity

#### Scenario: Context snippet with ellipsis
- **WHEN** note content is "...今天遇到了张三，他..."
- **THEN** snippet shows "...今天遇到了张三，他..." with entity text highlighted

### Requirement: Navigate to note from entity detail

The system SHALL navigate to note detail when clicking an associated note.

#### Scenario: Click associated note
- **WHEN** user clicks a note in the associated notes list
- **THEN** system navigates to QuickNoteDetailScreen for that note

### Requirement: Entity detail delete

The system SHALL allow deleting entity from detail page with confirmation.

#### Scenario: Delete with confirmation
- **WHEN** user clicks delete button
- **THEN** confirmation dialog shows "确定要删除实体"张三"吗？删除后所有关联笔记中的高亮将消失。"

#### Scenario: Confirm delete
- **WHEN** user confirms deletion
- **THEN** entity and all associations are deleted, system navigates back to entity list

### Requirement: Entity source display

The system SHALL display entity source (AI extracted or user added) in detail page.

#### Scenario: AI extracted entity
- **WHEN** entity source is "AI_EXTRACTED"
- **THEN** detail shows "来源：AI 自动提取"

#### Scenario: User added entity
- **WHEN** entity source is "USER_ADDED"
- **THEN** detail shows "来源：用户自定义"
