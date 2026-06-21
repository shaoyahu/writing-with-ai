# fix-quicknote-tags-and-search

## Why

真机 walkthrough(2026-06-20 三处 fix 之后)暴露 3 个 quicknote feature 的 UX bug,均已在 v1 上线前列为"必修":

- **bug 1**:标签"没有作用"。现状:`QuickNoteListScreen` 顶部已有 `TagFilterRow`(`FilterChip` + `selectTag` 触发 `NoteRepository.observeNotesWithTags(query, tag)`),`NoteRow` 列表行已渲染 `AssistChip("#$tag")`,详情页 `QuickNoteDetailScreen` 已渲染 tags 区域(`FlowRow` + `AssistChip`)。从代码看,标签的"纯组织"作用(列表按 tag 筛选 + 行内可见 + 详情页可见)已实现。**但用户感知不到**:
  1. `NoteRow` 内的 `AssistChip.onClick = { onClick(note.id) }` 跳详情,用户点 chip 期望"按此 tag 筛选"→ 无反应
  2. `TagFilterRow` 的"全部 / 单 tag"切换视觉差异弱(只是 chip 颜色),用户感知不到"已筛选"状态
  3. 没有"当前筛选 #tag"提示 banner,清空筛选只能再点一次同一个 chip
- **bug 2**:编辑笔记时标签"保存不上,也看不到新建笔记时添加的标签"。现状:`QuickNoteEditorViewModel.addTag/removeTag` 改 `tagsFlow`,`save()` 调 `repository.upsert(note, tagsFlow.value)` 走事务写 `note_tags` 表(`removeAllForNote` + 逐个 add)。从代码看保存路径完整,`TagInputRow` 已显示已挂 tag(`InputChip` + trailing close)。**但用户感知不到**:
  1. 编辑器 tag 输入区视觉位置在内容框下方,字号 / 间距小,新建时加完 tag 不显著
  2. 详情页 tag 区域条件渲染 `if (s.note.tags.isNotEmpty())`,但字体颜色用 `MaterialTheme.colorScheme.onSurface`(对比度低),tag 数字小 → 用户没注意
  3. 保存后跳详情,如果 tag chip 不显眼,用户没意识到保存成功
- **bug 3**:搜索框无"一键清除"按钮。现状:`QuickNoteListScreen.OutlinedTextField` 只有 `leadingIcon = Search`,无 trailingIcon。用户清除只能长按全选删除,体验差。

三者均属于 quicknote feature 内部 UX 问题,跨方向调整 0 边界 → 同一 OpenSpec change。

## What changes

- `feature/quicknote/list/QuickNoteListScreen.kt`
  - `OutlinedTextField` 加 `trailingIcon`:query 非空时渲染 `IconButton(Close)` 清空 query
  - `TagFilterRow` 选中 chip 视觉强化:加 `leadingIcon = Check`(已筛选态清晰)
  - 顶部加"当前筛选 #tag" banner(`AssistChip` + trailing close → `viewModel.selectTag(null)`),仅在 `selectedTag != null` 时显示
- `feature/quicknote/list/NoteRow.kt`
  - `AssistChip.onClick` 改 `onTagClick(tag)`(由 `QuickNoteListScreen` 传入),不再跳详情 → 点 chip 触发列表筛选(直观)
  - chip 颜色:有 tag 的行 chip 用 `MaterialTheme.colorScheme.secondaryContainer`(突出 vs 默认 surface)
- `feature/quicknote/list/QuickNoteListScreen.kt` 接收 `onTagClick`,内部 `viewModel.selectTag(tag)`
- `feature/quicknote/detail/QuickNoteDetailScreen.kt`
  - tag 区域改用 `SuggestionChip` + `secondaryContainer` + 字号 `bodyMedium` → 显眼
  - 无 tag 时显示空状态提示:`"无标签"`(灰色小字,避免空白迷惑)
- `feature/quicknote/edit/QuickNoteEditorScreen.kt`
  - TagInputRow 上方加 "标签(X 个)" label
  - 保存按钮旁加 "已挂 #a #b" 副文案(给保存成功反馈)
- `feature/quicknote/edit/QuickNoteEditorViewModel.kt` 加 `tagsSummary: StateFlow<String>`
- i18n:加 5 个 key(中英):`quicknote_search_clear_cd` / `quicknote_list_filter_banner_fmt` / `quicknote_detail_no_tags` / `quicknote_editor_tags_count_fmt` / `quicknote_editor_tags_saved_fmt`

不改:`NoteRepository` / `NoteDao` / `NoteTagDao` / 数据库 schema / `QuickNoteEditorViewModel.save()` / `QuickNoteListViewModel.selectTag()`(数据层逻辑已完整,纯 UI 强化)。

## Impact

- 影响的 spec:`openspec/specs/quick-note/spec.md`
  - MODIFIED `Requirement: Tag many-to-many` → 加 Scenario "NoteRow chip 点击触发 tag 筛选"
  - MODIFIED `Requirement: List ordering pinned-first and newest-first` → 加 Scenario "选中 tag 后顶部 banner 显示 + 一键清除"
  - MODIFIED `Requirement: Search by title or content with LIKE` → 加 Scenario "搜索框 trailingIcon 一键清除"
  - MODIFIED `Requirement: Navigation routes for quick-note feature` → 加 Scenario "详情页无 tag 显示空状态文案"
- 不影响其他 spec
- 不引入新依赖;不破坏现有测试(M1 list/search/tag 已 12 tests + r1 fix 全 PASS)
- spec 自包含:`feature/quicknote/` 内文件改,跨 feature 0 引用
