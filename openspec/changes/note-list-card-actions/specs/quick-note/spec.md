## ADDED Requirements

### Requirement: Long-press action menu on note list cards

`QuickNoteListScreen` MUST 在每个 `NoteRow` 上支持长按手势,长按后弹出 `DropdownMenu`,菜单 MUST 至少包含三个动作:**置顶 / 添加已有标签 / 删除**。

#### Scenario: User long-presses a note card
- **WHEN** 用户在 `QuickNoteListScreen` 长按任意一条 note 卡片
- **THEN** 该卡片位置弹出 `DropdownMenu`,显示"置顶"、"添加标签"、"删除"三个 `DropdownMenuItem`

#### Scenario: User taps Pin in long-press menu
- **WHEN** 用户点击菜单中的"置顶"项
- **THEN** 菜单关闭,**`Note` 的 `isPinned` 翻转**(`false → true` 或 `true → false`)
- **AND** 列表顺序更新(置顶行排到顶部,详见既有 `Pin and unpin a note` requirement)

#### Scenario: User taps Add tag in long-press menu
- **WHEN** 用户点击菜单中的"添加标签"项
- **THEN** 菜单关闭,**`AddExistingTagDialog` 打开**,列出全表 tag(`state.allTags`)

#### Scenario: User taps Delete in long-press menu
- **WHEN** 用户点击菜单中的"删除"项
- **THEN** 菜单关闭,**`DeleteConfirmDialog` 打开**,包含确认/取消两个按钮

#### Scenario: User taps outside the menu
- **WHEN** 菜单已展开,用户点击菜单外区域
- **THEN** 菜单关闭,无任何数据写入

### Requirement: Swipe-to-action on note list cards

`QuickNoteListScreen` MUST 在每个 `NoteRow` 上支持左滑手势,卡片向左滑出时 **MUST** 在 `SwipeToDismissBox.backgroundContent` 渲染两个图标按钮:**置顶 / 删除**。

#### Scenario: User swipes a note card left
- **WHEN** 用户在 `QuickNoteListScreen` 把任意一条 note 卡片向左滑出 40% 视宽
- **THEN** 卡片右侧背景露出两个图标按钮:置顶(主色)+ 删除(错误色)
- **AND** 卡片本身 **MUST NOT** 自动消失(`SwipeToDismissBox.confirmValueChange` 永远返回 `false`)

#### Scenario: User taps the pin button on swiped background
- **WHEN** 用户在卡片左滑状态下点击背景上的置顶按钮
- **THEN** 卡片自动回弹(`dismissState.reset()`)
- **AND** 该 note 的 `isPinned` 翻转,列表顺序更新

#### Scenario: User taps the delete button on swiped background
- **WHEN** 用户在卡片左滑状态下点击背景上的删除按钮
- **THEN** 卡片自动回弹
- **AND** **`DeleteConfirmDialog` 打开**

#### Scenario: User releases swipe without tapping background buttons
- **WHEN** 用户左滑卡片但未点击背景按钮,直接松手
- **THEN** 卡片自动回弹到原位,无任何数据写入

### Requirement: Quick add existing tag from list card

`QuickNoteListScreen` 的长按菜单 MUST 提供"添加已有标签"动作,触发 `AddExistingTagDialog`。用户在 dialog 中选择未挂的 tag → 系统 MUST 调用 `NoteRepository.addTagToNote(noteId, tag)` 挂载。已挂的 tag MUST 显示已挂标识且不可点。

#### Scenario: User opens add-tag dialog
- **WHEN** 用户点击长按菜单中的"添加标签"
- **THEN** **`AddExistingTagDialog` 打开**,显示全表 tag 列表(取自 `state.allTags`)

#### Scenario: User selects an unadded tag
- **WHEN** dialog 打开,用户点击列表中一个**未挂**的 tag
- **THEN** 系统调用 `NoteRepository.addTagToNote(noteId=当前 note, tag=选中 tag)` 挂载
- **AND** dialog 关闭
- **AND** 列表 card 的 tag chip 区更新(因 `noteTagDao.add` 触发 `noteTagDao.observeAllCrossRefs()` Flow 重发)

#### Scenario: User attempts to select an already-added tag
- **WHEN** dialog 打开,用户点击列表中一个**已挂**的 tag
- **THEN** 该项 MUST 显示 Check icon
- **AND** 该项 **MUST NOT** 触发挂载(视觉禁用 + clickable.enabled=false)

#### Scenario: User opens dialog with no tags in database
- **WHEN** 用户点击"添加标签"但 `state.allTags` 为空(全库没有任何 tag)
- **THEN** dialog 内显示空态文案(`quicknote_list_add_tag_dialog_empty`)
- **AND** dialog 仍可关闭(右上"关闭"按钮)

#### Scenario: User dismisses dialog without selecting
- **WHEN** dialog 打开,用户点击 dialog 外区域或点"关闭"
- **THEN** dialog 关闭,无任何数据写入