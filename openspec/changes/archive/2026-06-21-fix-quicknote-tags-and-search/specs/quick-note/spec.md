# quick-note Specification (delta)

## MODIFIED Requirements (fix-quicknote-tags-and-search)

### Requirement: Tag many-to-many (delta)

继承 M1 原 Requirement 不变;新增 Scenario:

#### Scenario: NoteRow chip 点击触发 tag 筛选

- **WHEN** 列表行内 `NoteRow` 渲染的 `AssistChip("#$tag")` 被点击
- **THEN** `QuickNoteListViewModel.selectTag(tag)` 被调用;`selectedTag = tag`;`observeNotesWithTags(query, tag)` emit 仅含该 tag 的笔记;顶部"当前筛选 #tag" banner 渲染

### Requirement: List ordering pinned-first and newest-first (delta)

继承 M1 原 Requirement 不变;新增 Scenario:

#### Scenario: 选中 tag 后顶部 banner 显示 + 一键清除

- **WHEN** 用户在 `TagFilterRow` 点选某个 tag(`selectedTag != null`)
- **THEN** `QuickNoteListScreen` 在 search 与 TagFilterRow 中间渲染 "当前筛选 #tag" `AssistChip` + trailing close;点 close → `viewModel.selectTag(null)` → banner 消失 + 列表恢复全部

### Requirement: Search by title or content with LIKE (delta)

继承 M1 原 Requirement 不变;新增 Scenario:

#### Scenario: 搜索框 trailingIcon 一键清除

- **WHEN** 搜索框 `query` 非空
- **THEN** `OutlinedTextField` `trailingIcon` 渲染 `IconButton(Close)`;点 close → `viewModel.setQuery("")` → 输入框清空 + trailingIcon 消失 + 列表恢复全部
- **WHEN** `query` 为空
- **THEN** `trailingIcon` 不渲染(节省屏宽)

### Requirement: Navigation routes for quick-note feature (delta)

继承 M1 原 Requirement 不变;新增 Scenario:

#### Scenario: 详情页无 tag 显示空状态文案

- **WHEN** `note.tags.isEmpty()`
- **THEN** `QuickNoteDetailScreen` 在 tag 区域渲染 `Text("无标签", style = labelSmall, color = onSurfaceVariant)`,替代原 `if (tags.isNotEmpty())` 的空白
