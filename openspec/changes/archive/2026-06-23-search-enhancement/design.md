## Context

当前 NoteDao.search() 走 `content LIKE '%q%'`，数据量小时可用但无法排序、无词干、无前缀匹配。

## Goals / Non-Goals

**Goals:**
- FTS4 全文搜索(MATCH 查询 + rank 排序)
- 标签 + 实体联合过滤
- 搜索历史(最近 20 条)
- 搜索排序切换(时间/相关性)

**Non-Goals:**
- 不做搜索结果高亮(预留 v2)
- 不做拼写纠错
- 不做搜索建议(只做历史)

## Decisions

### D1: Room FTS4 content-sync 模式

用 `@Fts4(contentEntity = NoteEntity::class)` 让 Room 自动同步主表内容到 FTS 表，不需要手动维护索引。

### D2: 搜索历史用 DataStore

简单场景，只存最近 20 条搜索词，用 `DataStore<List<String>>` 即可。

### D3: 实体过滤走 note_entity_rows JOIN

已有 `note_entity_rows` 表，通过 noteId JOIN 过滤。
