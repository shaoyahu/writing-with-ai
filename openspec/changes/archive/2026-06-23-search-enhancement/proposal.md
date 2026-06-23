## Why

当前搜索走 LIKE '%q%'，无 FTS、无实体过滤、无搜索历史，笔记多了搜索慢且不精确。需要 FTS4 全文搜索 + 标签/实体联合过滤 + 搜索历史。

## What Changes

- 引入 Room FTS4 虚拟表 `notes_fts`，自动同步主表
- NoteDao 加 searchFts() 走 MATCH 查询
- 列表页加实体 filter chip 行 + 排序切换(时间/相关性)
- 搜索历史 DataStore + 搜索框下方显示

## Capabilities

### New Capabilities
- `search-enhancement`: FTS 全文搜索 + 联合过滤 + 搜索历史规范

### Modified Capabilities
- `quick-note`: 列表页搜索/过滤 UI 增强

## Impact

- AppDatabase v6 Migration + FtsNoteEntity + NoteDao + NoteRepository + QuickNoteListScreen/ViewModel + SearchHistoryStore
