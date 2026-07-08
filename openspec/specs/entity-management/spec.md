# entity-management Specification

## Purpose

`EntityManagementScreen` 实体管理页:列表展示 + 搜索 + 排序 + 筛选 + 多选删除。

Synced from OpenSpec change `entity-management-and-ai-decompose`(2026-07-08)。

## Requirements

### Requirement: Entity management entry

The system SHALL provide an "实体管理" entry in the "我的" tab under the "数据管理" section.

#### Scenario: Entry visible
- **WHEN** user navigates to "我的" tab
- **THEN** "实体管理" entry is visible under "数据管理" section

#### Scenario: Entry navigates to list
- **WHEN** user clicks "实体管理"
- **THEN** system navigates to EntityManagementScreen

### Requirement: Entity list display

The system SHALL display all entities in a list with `surfaceForm`, type name, and associated note count.

#### Scenario: List shows entity info
- **WHEN** EntityManagementScreen loads
- **THEN** list shows each entity's `surfaceForm`, localized type name (e.g., "人物"), and note count

#### Scenario: Empty state
- **WHEN** no entities exist
- **THEN** screen shows empty state illustration with "暂无实体" text

### Requirement: Entity list sorting

The system SHALL support sorting entities by name, note count, or last extracted time.

#### Scenario: Sort by name
- **WHEN** user selects "按名称排序"
- **THEN** entities are sorted alphabetically by `surfaceForm`

#### Scenario: Sort by note count
- **WHEN** user selects "按关联笔记数"
- **THEN** entities are sorted by associated note count descending

#### Scenario: Sort by last extracted
- **WHEN** user selects "按最近出现"
- **THEN** entities are sorted by `lastExtractedAt` descending

### Requirement: Entity list filtering

The system SHALL support filtering entities by type.

#### Scenario: Filter by type
- **WHEN** user selects "人物" filter
- **THEN** only entities with `entityType = PERSON` are shown

#### Scenario: Show all types
- **WHEN** user selects "全部"
- **THEN** all entities are shown regardless of type

### Requirement: Entity search

The system SHALL support searching entities by `surfaceForm`.

#### Scenario: Search matches
- **WHEN** user types "张三" in search box
- **THEN** only entities with `surfaceForm` containing "张三" are shown

#### Scenario: Search empty
- **WHEN** user clears search box
- **THEN** all entities are shown

### Requirement: Entity selection and batch delete

The system SHALL support multi-select and batch delete.

#### Scenario: Enter selection mode
- **WHEN** user long-presses an entity
- **THEN** selection mode activates with checkbox shown

#### Scenario: Batch delete
- **WHEN** user selects multiple entities and clicks delete
- **THEN** confirmation dialog shows "确定要删除选中的 X 个实体吗？", on confirm deletes entities and their associations

#### Scenario: Single delete from detail
- **WHEN** user clicks delete on entity detail page
- **THEN** confirmation dialog shows, on confirm deletes entity and cascades to associations

### Requirement: Navigate to entity detail

The system SHALL navigate to entity detail when clicking an entity.

#### Scenario: Click entity
- **WHEN** user clicks an entity in the list
- **THEN** system navigates to EntityDetailScreen with entity info