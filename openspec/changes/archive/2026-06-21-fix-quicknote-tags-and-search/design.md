# fix-quicknote-tags-and-search · design

## 真因再确认(代码对照)

bug 1 "标签没作用"代码层已实现(`FilterChip` + `selectTag` + `observeNotesWithTags` + `NoteRow` chip + `DetailScreen` chip),但用户感知不到。**真因 = 交互意图不明确**:

- `NoteRow.AssistChip.onClick = { onClick(note.id) }` 跳详情 → 用户点 chip 期望"按此 tag 筛选列表",实际跳详情,**意图不对**
- `TagFilterRow` 选中态仅 chip 颜色变化,无图标 / 无文字加强 → 用户看不出"已筛选"
- 无"当前筛选"提示 banner,清空筛选只能再点 chip → 路径长

bug 2 "标签保存不上 / 看不到"代码层 `Editor VM.addTag → tagsFlow → save → upsert(note, tags) → withTransaction removeAll + add all → 写 note_tags` 已完整。**真因 = 视觉反馈缺失**:

- `TagInputRow` 在内容框下方,字号 `bodyMedium`,加完 tag 不显著
- 详情页 `if (s.note.tags.isNotEmpty()) FlowRow(AssistChip)` 字号 `bodyMedium` 默认色 → tag 数字小
- 保存后跳详情,用户没意识保存成功(只看到 TopAppBar 的 ✓,无文案确认)

bug 3 "搜索框无清除" = `OutlinedTextField` 缺 `trailingIcon`。`setQuery("")` 路径已存在(`QuickNoteListViewModel.setQuery`),只是 UI 缺按钮。

## 设计决策

### 决策 1 · NoteRow chip 行为:跳详情 vs 触发筛选

候选:
- (a) 保持跳详情 → 配 "在详情页点 tag 跳列表筛选"(两跳)
- (b) **改 chip onClick 触发列表筛选**(`onTagClick(tag)` → `viewModel.selectTag(tag)`)→ 一跳到位

选 (b)。理由:用户列表行内点 chip 期望"按此组织维度筛选",跳详情是次要场景(整行点击已能跳)。一行 chip 行为统一更直觉。

### 决策 2 · 已筛选 tag 的视觉反馈强度

候选:
- (a) 仅 chip 颜色变化(Material 3 默认 `selectedContainerColor`)
- (b) 颜色 + 选中 icon(`FilterChipDefaults.leadingIcon = Check`) + 顶部 banner

选 (b)。理由:用户反馈"没作用"= 完全没感知到状态变化,需要冗余视觉信号(色 + 形 + 文字 banner 三重)。

### 决策 3 · 详情页无 tag 状态

候选:
- (a) 完全不渲染(现状)
- (b) 渲染 "无标签" 灰色小字

选 (b)。理由:空状态显式比空白好,符合 Material 3 空状态设计原则;文案简短不打扰。

### 决策 4 · 编辑器保存反馈

候选:
- (a) 仅 TopAppBar ✓ icon(现状)
- (b) **✓ icon + 副文案 "已挂 #a #b"** 在保存按钮旁
- (c) Snackbar "已保存"

选 (b)。理由:Snackbar 会覆盖其他提示;副文案直接挂在保存按钮旁,不抢占主流程;简短。

### 决策 5 · 标签"纯组织"作用范围

确认(用户拍板):**仅组织**(列表筛选 + 行内可见 + 详情可见 + 编辑保存),不联动 AI prompt 模板,不联动 widget 分类。v1 不扩展 tag 元数据自动化(留 v2+ 演进)。

## 实现路径

按 quicknote 现有结构:

1. `feature/quicknote/list/QuickNoteListScreen.kt`
   - `OutlinedTextField` 加 `trailingIcon`:query 非空 → `IconButton` + `Icons.Filled.Close`,`onClick = viewModel::setQuery`
   - `TagFilterRow` 选中 chip 加 `leadingIcon = Check`
   - selectedTag 非空时,在 `OutlinedTextField` + `TagFilterRow` 中间插入 "当前筛选 #tag" `AssistChip` + trailing close → `viewModel.selectTag(null)`
   - 签名增加 `onTagClick: (String) -> Unit = {}`
2. `feature/quicknote/list/NoteRow.kt`
   - 签名增加 `onTagClick: (String) -> Unit`
   - `AssistChip(onClick = { onTagClick(tagName) }, label = ..., colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.secondaryContainer))`
   - 整行 `Card(onClick = ...)` 跳详情不变
3. `feature/quicknote/list/QuickNoteListScreen.kt`
   - `items(...) { NoteRow(item = it, onClick = onNoteClick, onTagClick = { viewModel.selectTag(it) }) }`
4. `feature/quicknote/detail/QuickNoteDetailScreen.kt`
   - `FlowRow` 内 `AssistChip` 改 `SuggestionChip(onClick = {}, label = ..., colors = SuggestionChipDefaults.suggestionChipColors(containerColor = MaterialTheme.colorScheme.secondaryContainer))`
   - `if (s.note.tags.isNotEmpty())` 后加 `else` 分支:`Text("无标签", style = labelSmall, color = onSurfaceVariant)`
5. `feature/quicknote/edit/QuickNoteEditorScreen.kt`
   - `TagInputRow` 上方加 `Text("标签(${state.tags.size} 个)", style = bodyMedium)`
   - `actions` 内 `IconButton` 旁加 `Text("已挂 ${tagsSummary}", style = labelSmall)`(当 `state.tags.isNotEmpty()` 时)
6. `feature/quicknote/edit/QuickNoteEditorViewModel.kt`
   - 加 `val tagsSummary: StateFlow<String> = tagsFlow.map { it.joinToString(" ") { "#$it" } }.stateIn(...)`
7. `res/values/strings.xml` + `values-en/strings.xml`
   - 新增 5 个 key(英文 TODO 占位)

## 测试策略

- 单元测试增量:
  - `QuickNoteListViewModelTest` 加 2 case:`selectTag("灵感")` 后 `observeNotesWithTags` emit 仅含 "灵感" 的笔记;`setQuery("")` 后 emit 全笔记
  - 现有 12 个 M1 测试保留不动(`setQuery` / `selectTag` 已覆盖 emit 路径,本次只补强断言)
- 手工验收:真机跑以下旅程,任一失败即 bug 未修复:
  - 旅程 1(标签筛选):新建笔记 → 加 tag "灵感" → 保存 → 详情页看到 "灵感" chip + SuggestionChip 样式 → back → 列表行内 "灵感" chip 颜色高亮 → 点 chip → 列表只剩该笔记 + 顶部 "当前筛选 #灵感" banner + 列表只剩该条 → 点 banner close → 列表恢复全部
  - 旅程 2(标签保存):新建笔记 → 加 tag "a" "b" → 顶部 label 显示 "标签(2 个)" + 副文案 "已挂 #a #b" → 保存 → 详情页看到 2 个 SuggestionChip + 字体显著 → back → 列表行内 2 个 secondaryContainer chip
  - 旅程 3(搜索清除):搜索框输入 "晨" → trailingIcon 出现 Close → 点 Close → 输入框清空 + trailingIcon 消失 + 列表恢复全部
  - 旅程 4(详情无 tag):新建笔记(不加 tag)→ 保存 → 详情页看到 "无标签" 灰色字

## 风险与回退

- 风险 1:`NoteRow.chip.onClick` 改语义后,已习惯"点 chip 跳详情"用户不适 → 缓解:整行 `Card.onClick` 仍跳详情,行为层不变(只 chip 行为变)
- 风险 2:加 banner / 副文案占用屏高 → 缓解:banner 用 `AssistChip` 紧凑布局;副文案 `labelSmall` 一行
- 风险 3:测试时间(无新 unit test 但需手工 4 旅程验收) → 缓解:journey 1+2+3 各 30s,总 2 分钟

回退:`git revert <commit>` 即可,数据层 0 改动。
