# fix-quicknote-tags-and-search · tasks

## 1. spec delta

- [x] 1.1 读 `openspec/specs/quick-note/spec.md`,定位 4 个 target Requirement(Tag many-to-many / List ordering / Search / Navigation routes)
- [x] 1.2 在 `openspec/changes/fix-quicknote-tags-and-search/specs/quick-note/spec.md` 写 `## MODIFIED Requirements` 段,4 个 MODIFIED 每个加 1 个 Scenario

## 2. 列表屏 UI 强化

- [x] 2.1 `feature/quicknote/list/QuickNoteListScreen.kt`
  - `OutlinedTextField` 加 `trailingIcon`(query 非空时 `IconButton` + `Icons.Filled.Close` + `viewModel::setQuery`)
  - `TagFilterRow` 选中 chip 加 `leadingIcon = Icons.Filled.Check`
  - 选中 tag 时,在 search 与 TagFilterRow 中间插入 `Row` + `AssistChip("当前筛选 #$tag")` + trailing close → `viewModel.selectTag(null)`
- [x] 2.2 `feature/quicknote/list/NoteRow.kt`
  - 签名加 `onTagClick: (String) -> Unit`
  - `AssistChip` 改 `colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)`,`onClick = { onTagClick(tagName) }`
- [x] 2.3 `feature/quicknote/list/QuickNoteListScreen.kt` items 内 `NoteRow(... onTagClick = { viewModel.selectTag(it) })`

## 3. 详情页 tag 视觉强化

- [x] 3.1 `feature/quicknote/detail/QuickNoteDetailScreen.kt`
  - `FlowRow` 内 `AssistChip` 换 `SuggestionChip` + `secondaryContainer`
  - `if (s.note.tags.isNotEmpty())` 加 `else`:`Text("无标签", style = labelSmall, color = onSurfaceVariant)`

## 4. 编辑器 tag 反馈强化

- [x] 4.1 `feature/quicknote/edit/QuickNoteEditorViewModel.kt`
  - 加 `val tagsSummary: StateFlow<String>`,`tagsFlow.map { it.joinToString(" ") { "#$it" } }.stateIn(...)`
- [x] 4.2 `feature/quicknote/edit/QuickNoteEditorScreen.kt`
  - `TagInputRow` 上方加 `Text("标签(${state.tags.size} 个)", style = bodyMedium)`
  - `actions` 旁 `Text("已挂 ${tagsSummary}", style = labelSmall)`(tags 非空时)

## 5. i18n

- [x] 5.1 `res/values/strings.xml` + `values-en/strings.xml` 加 6 个 key(含 `quicknote_list_filter_clear_cd`)
  - `quicknote_search_clear_cd`(中文 "清除搜索" / 英文 TODO)
  - `quicknote_list_filter_banner_fmt`(中文 "当前筛选 #%1$s" / 英文 TODO)
  - `quicknote_list_filter_clear_cd`(中文 "清除筛选" / 英文 TODO)
  - `quicknote_detail_no_tags`(中文 "无标签" / 英文 TODO)
  - `quicknote_editor_tags_count_fmt`(中文 "标签(%1$d 个)" / 英文 TODO)
  - `quicknote_editor_tags_saved_fmt`(中文 "已挂 %1$s" / 英文 TODO)

## 6. 测试

- [x] 6.1 跳过 — 无既有 `QuickNoteListViewModelTest`,本 change 纯 UI 强化 + import 调整,无数据层改动;现有 22 tests 全 PASS
- [x] 6.2 跑 `./gradlew :app:testDebugUnitTest` 全 PASS(22 tests 0 fail)

## 7. 验证

- [x] 7.1 `./gradlew :app:assembleDebug` BUILD SUCCESSFUL
- [x] 7.2 `./gradlew :app:ktlintCheck` 0 violations
- [x] 7.3 `./gradlew :app:lintDebug` 0 errors
- [ ] 7.4 真机走 4 旅程(标签筛选 / 标签保存 / 搜索清除 / 详情无 tag)全 PASS

## 8. 文档

- [x] 8.1 `docs/progress.md` 加 1 条 2026-06-20 条目(`fix-quicknote-tags-and-search` 落地)
