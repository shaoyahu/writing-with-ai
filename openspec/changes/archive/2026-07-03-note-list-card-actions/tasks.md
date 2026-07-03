## 1. 数据层

- [ ] 1.1 `NoteRepository.kt` 新增 `addTagToNote(noteId, tag)` 方法 — 调用 `noteTagDao.add(NoteTagCrossRef(...))` 走 IGNORE 策略，trim + 空过滤，完成后 `widgetUpdater.updateAll(context)` 包 `NonCancellable`,emit `recomputeFlow.tryEmit(noteId)`
- [ ] 1.2 `NoteRepository.kt` KDoc 补一行说明该方法是 note-list-card-actions 引入的幂等挂载入口

## 2. ViewModel 层

- [ ] 2.1 `QuickNoteListViewModel.kt` 新增 `togglePinned(noteId, currentPinned)` — 调 `repository.setPinned(noteId, !currentPinned)`，失败 catch Throwable 后 `Log.w`(CancellationException 透传)
- [ ] 2.2 `QuickNoteListViewModel.kt` 新增 `deleteNote(noteId)` — `withContext(NonCancellable) { repository.delete(noteId) }`，失败 Log.w
- [ ] 2.3 `QuickNoteListViewModel.kt` 新增 `addExistingTag(noteId, tag)` — 调 `repository.addTagToNote(noteId, tag)`，失败 Log.w

## 3. UI 层 NoteRow

- [ ] 3.1 `NoteRow.kt` 新增形参 `onLongClick: () -> Unit = {}`(默认空，向后兼容)
- [ ] 3.2 `NoteRow.kt` Card 改用 `Modifier.combinedClickable(onClick, onLongClick)` 替换 `Card(onClick=...)` slot
- [ ] 3.3 `NoteRow.kt` 添加 `import androidx.compose.foundation.combinedClickable` 和 `import androidx.compose.foundation.ExperimentalFoundationApi`，函数加 `@OptIn(ExperimentalFoundationApi::class)`

## 4. UI 层 AddExistingTagDialog 新建

- [ ] 4.1 新文件 `feature/quicknote/list/AddExistingTagDialog.kt` — Composable `AddExistingTagDialog(allTags, currentTags, onTagSelected, onDismiss)`
- [ ] 4.2 Dialog 内 `LazyColumn` + `items(allTags)`，每项 `ListItem` 渲染 `#tag`
- [ ] 4.3 已挂 tag 显示 Check icon + `clickable.enabled = false`，未挂 tag 可点 → 触发 `onTagSelected(tag) + onDismiss()`
- [ ] 4.4 `allTags` 空时显示空态文案(用 `R.string.quicknote_list_add_tag_dialog_empty`)

## 5. UI 层 QuickNoteListScreen 改造

- [ ] 5.1 引入 3 个屏级 state:`menuExpandedFor: String?` / `confirmDeleteFor: String?` / `showAddTagFor: String?`
- [ ] 5.2 LazyColumn items 内部包 `SwipeToDismissBox`,`rememberSwipeToDismissBoxState(confirmValueChange = { false })`,`positionalThreshold = { it * 0.4f }`
- [ ] 5.3 SwipeToDismissBox 的 `backgroundContent` 渲染 `Row` + 两个图标按钮(置顶 PushPin + 删除 Delete)，点击后 `dismissState.reset()` + 触发 VM 操作 / 打开 confirmDeleteFor
- [ ] 5.4 SwipeToDismissBox 的 `content` 内:NoteRow 接收新形参 `onLongClick = { menuExpandedFor = item.note.id }` + 紧随其后挂 `DropdownMenu(expanded = menuExpandedFor == item.note.id)`
- [ ] 5.5 DropdownMenu 三个 `DropdownMenuItem`:置顶(→ `viewModel.togglePinned`)/ 添加标签(→ `showAddTagFor`)/ 删除(→ `confirmDeleteFor`)
- [ ] 5.6 屏底部渲染 `DeleteConfirmDialog`(`confirmDeleteFor != null` 时显示，确认调 `viewModel.deleteNote(noteId)`)
- [ ] 5.7 屏底部渲染 `AddExistingTagDialog`(`showAddTagFor != null` 时显示，`allTags = state.allTags`,`currentTags = item.tags`,`onTagSelected = { viewModel.addExistingTag(...) }`)

## 6. i18n

- [ ] 6.1 `res/values/strings.xml` 新增 13 个中文 key:
  - `quicknote_list_action_pin` / `quicknote_list_action_unpin`
  - `quicknote_list_action_delete`
  - `quicknote_list_action_add_tag`
  - `quicknote_list_add_tag_dialog_title`
  - `quicknote_list_add_tag_dialog_empty`
  - `quicknote_list_add_tag_dialog_current_cd`(含 `%1$s`)
  - `quicknote_list_swipe_pin_cd` / `quicknote_list_swipe_delete_cd`
  - `quicknote_list_delete_confirm_title`
  - `quicknote_list_delete_confirm_message`
  - `quicknote_list_delete_confirm_ok` / `quicknote_list_delete_confirm_cancel`
- [ ] 6.2 `res/values-en/strings.xml` 对应 13 个英文 key

## 7. 单元测试

- [ ] 7.1 新文件 `app/src/test/java/com/yy/writingwithai/feature/quicknote/list/QuickNoteListViewModelTest.kt`
- [ ] 7.2 用例 `togglePinned_flips_state` — `vm.togglePinned("n1", false)` → verify `repository.setPinned("n1", true)`
- [ ] 7.3 用例 `togglePinned_error_is_logged_not_thrown` — `repository.setPinned` throws → vm 不传播异常
- [ ] 7.4 用例 `deleteNote_calls_repo_delete` — `vm.deleteNote("n1")` → verify `repository.delete("n1")`
- [ ] 7.5 用例 `deleteNote_error_is_logged_not_thrown` — 同 7.3 模式
- [ ] 7.6 用例 `addExistingTag_calls_repo_addTagToNote` — `vm.addExistingTag("n1", "kotlin")` → verify `repository.addTagToNote("n1", "kotlin")`

## 8. 验证

- [ ] 8.1 `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:assembleDebug` — BUILD SUCCESSFUL
- [ ] 8.2 `JAVA_HOME=... ./gradlew :app:ktlintCheck` — 无违规
- [ ] 8.3 `JAVA_HOME=... ./gradlew :app:testDebugUnitTest` — `QuickNoteListViewModelTest` 5 用例通过 + 既有测试无回归
- [ ] 8.4 装到 V2509A 真机验证:长按菜单弹出 / 左滑露出置顶+删除 / 快速加标签成功(USER-OWNED)