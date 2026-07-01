## Why

`QuickNoteListScreen` 当前每个 NoteRow 只有「点击进详情」一个交互，置顶/删除/挂标签都得点进详情再操作，路径过长。随手记类产品的核心诉求是「快速捕捉 + 快速整理」，列表屏缺失高频操作入口(置顶 / 删除 / 加标签)是真实可用性短板。

## What Changes

- 笔记列表卡片支持 **长按弹功能菜单**(DropdownMenu 锚定卡片)，提供置顶 / 添加已有标签 / 删除三个动作。
- 笔记列表卡片支持 **向左滑出背景按钮**(M3 SwipeToDismissBox)，露出置顶 + 删除两个图标按钮。
- 菜单中的「添加标签」动作打开 **AddExistingTagDialog**，列出当前数据库中所有 tag;已挂的 tag 显示 Check icon + 不可点，未挂的可点触发挂载(走 IGNORE 策略幂等)。
- `NoteRepository` 新增 `addTagToNote(noteId, tag)` 方法;`QuickNoteListViewModel` 新增 `togglePinned` / `deleteNote` / `addExistingTag` 3 个公开方法。

## Capabilities

### New Capabilities
(无 — 新功能扩展现有 quick-note capability，不引入独立 capability)

### Modified Capabilities
- `quick-note`:新增 3 个 requirements 描述列表卡片长按菜单 + 左滑操作 + 快速加标签行为(spec 行为级变化，不是纯实现细节)。

## Impact

**代码**:
- `core/data/repo/NoteRepository.kt` — 新增 `addTagToNote(noteId, tag)` 1 方法
- `feature/quicknote/list/QuickNoteListViewModel.kt` — 新增 `togglePinned` / `deleteNote` / `addExistingTag` 3 公开方法
- `feature/quicknote/list/NoteRow.kt` — Card 改用 `Modifier.combinedClickable`，新增 `onLongClick` 形参
- `feature/quicknote/list/QuickNoteListScreen.kt` — items 包 `SwipeToDismissBox`，屏级 3 个 dialog/menu state，渲染 DropdownMenu + 2 个 AlertDialog
- `feature/quicknote/list/AddExistingTagDialog.kt` — 新文件，AlertDialog + LazyColumn(ListItem)
- `app/src/test/java/com/yy/writingwithai/feature/quicknote/list/QuickNoteListViewModelTest.kt` — 新文件，5 用例

**i18n** — `res/values/strings.xml` + `res/values-en/strings.xml` 新增 13 个 string key

**API**:
- `NoteRow` 形参新增 `onLongClick: () -> Unit = {}`(默认空，向后兼容)
- `NoteRepository` 新增 1 公开 suspend 方法
- `QuickNoteListViewModel` 新增 3 公开方法

**依赖** — 无新依赖。`SwipeToDismissBox` / `rememberSwipeToDismissBoxState` 已在 `androidx.compose.material3:material3` 1.2+ 稳定。

**风险** — 列表数据规模 + 新 state 极小(~10 个可见 item × 3 个 state)，无性能影响。