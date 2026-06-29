## Context

`QuickNoteListScreen` 当前每个 `NoteRow` 只响应 `onClick`(进详情)。详情页内通过 dropdown menu + 编辑器完成置顶/删除/挂标签,但路径太长,随手记类产品的核心诉求是「快速捕捉 + 快速整理」。

仓库里已有完整基础:
- `NoteRepository.setPinned(id, pinned)`(NoteDao 单 SQL)
- `NoteRepository.delete(id)`(事务清理 attachment + tag + note)
- `NoteTagDao.add(NoteTagCrossRef)` IGNORE 策略 → 重复挂自动 no-op
- `NoteListUiState.Content.allTags` 已经观察全表 tag
- M3 `SwipeToDismissBox` / `rememberSwipeToDismissBoxState`(1.2+ 稳定)
- `Modifier.combinedClickable`(Foundation,稳定)

CLAUDE.md 硬规则(本 change 涉及):
- "feature 必须自包含" — 新组件 `AddExistingTagDialog` 放 `feature/quicknote/list/`,不跨 feature 引用
- "字符串一律走 strings.xml" — 13 个新 key 双语

## Goals / Non-Goals

**Goals:**
- 卡片支持长按 → DropdownMenu(置顶 / 添加标签 / 删除)
- 卡片支持左滑 → SwipeToDismissBox 背景渲染两个图标按钮(置顶 / 删除)
- 长按菜单中"添加标签" → AddExistingTagDialog → 点未挂 tag 触发挂载
- 数据走现有 `NoteRepository` 方法,新建 `addTagToNote(noteId, tag)` 幂等挂载
- VM 暴露 `togglePinned` / `deleteNote` / `addExistingTag` 3 个公开方法,错误吞 + Log.w
- 5 个新增 JVM 单测覆盖 VM 新方法 delegation + error swallow

**Non-Goals:**
- 不动详情页 dropdown menu(已稳定)
- 不动编辑器 `TagInputRow`(新建 tag 仍走详情页)
- 不动 select mode(已存在 `toggleSelect`)
- 不做后台同步 / 不引入新依赖
- 不改 Room schema / 不改 Entity
- `SwipeToDismissBox` 不会真 dismiss(见 D1)

## Decisions

### D1: SwipeToDismissBox.confirmValueChange = 永远 false(背景按钮模式)

**选 false 模式**:`confirmValueChange` 永远返回 `false` → 卡片**永不消失**,用户松手后弹回原位,只让 backgroundContent 露出来。backgroundContent 渲染两个图标按钮,用户点按钮才执行操作 + reset state。

**否决自实现 swipe**:用 `anchoredDraggable` 自定义需要重做:
- 触觉反馈(AccessibilityService HapticFeedbackConstants)
- a11y(swipe action 的 content description / TalkBack 朗读)
- 状态机(reset / snap)
- 视觉曲线(`spring()` spec)

预计 200+ 行样板,且易出错。`SwipeToDismissBox` 是 M3 官方组件,Gmail / Material 3 示例 app 都用 `confirmValueChange = false` 模式做"露出操作按钮",UX 业界已认可。

### D2: DropdownMenu 锚点 = SwipeToDismissBox(content)

**选 SwipeToDismissBox(content) 内**:M3 DropdownMenu 在 Box 内只能锚到 Box 内 Composable,SwipeToDismissBox 内含 content 子组合,DropDownMenu 自然以卡片右下角为锚点。

**用户长按位置 ≠ 菜单弹出位置**:Android 列表长按菜单的标准行为(系统设置 / Gmail / 微信 均如此),用户已习惯。

**否决外层 Box 包整个 SwipeToDismissBox**:会让 DropdownMenu 锚到 LazyColumn item 整体,菜单距离卡片过远。

### D3: 「添加已有标签」用 Dialog 而非 Nested DropdownMenu

**选 AlertDialog**:M3 `DropdownMenuItem.submenu` 是 `@ExperimentalMaterial3Api`,且嵌套菜单 UX 在小屏列表场景下不直观(子菜单会被卡片遮)。

**否决 Nested DropdownMenu**:
- API 不稳定(M3 1.4 还在改)
- 视觉上子菜单不醒目,容易错过

### D4: 屏级 state 而非每个 item state

**选屏级 `menuExpandedFor: String?` / `confirmDeleteFor: String?` / `showAddTagFor: String?`**:
- 同一时刻全屏只能显示 1 个菜单 / 1 个 dialog,屏级 state 天然唯一
- LazyColumn 重组时屏级 state 不会随 item 创建 / 销毁丢失
- 用 `noteId` 做"哪个 item 的菜单打开"的判定

**否决 item 级 `mutableStateOf`**:同 state 在多个 item 上同时为 true 会出 N 个菜单竞态。

### D5: VM 错误吞策略

**选 `try / catch (Throwable) { Log.w }`**:
- 列表屏不弹 toast / snackbar(详情页模式),操作失败静默(数据库本地操作,失败概率极低)
- 与 `QuickNoteDetailViewModel.delete` / `.togglePinned` 错误处理模式一致
- 列表 observeAll 自动反映 DB 状态 — 失败时行仍显示,不会让用户卡在错误态

**否决 SnackbarHost**:列表屏不挂 SnackbarHost(沿用现有架构),加 Snackbar 是更大改动。

### D6: addTagToNote 不走事务

**选单条 INSERT**:`NoteTagDao.add` 是 `@Insert(onConflict = IGNORE)` 单 SQL,无副作用,不走事务。

**否决 `db.withTransaction {}`**:单条 INSERT 包事务是 overhead,Repository 内已有模式 upsert/delete 走事务是因为多表操作。

### D7: NoteRow 形参新增 onLongClick(默认空,向后兼容)

**选默认空 lambda**:`NoteRow` 已被 `QuickNoteListScreen` 调用,新增形参给默认值 `{}` 保证现有调用方无 break。

**否决修改既有调用方传 onLongClick**:本 change 就是要让列表屏传 onLongClick,保留默认空只是防御性兼容,未来其他屏(搜索历史 / widget)用 NoteRow 不受影响。

## Risks / Trade-offs

- **[R1] SwipeToDismissBox 在 LazyColumn 中状态管理** → 每个 item 创建自己的 `rememberSwipeToDismissBoxState`,item 滑出视口 → 重组回 viewport 时 state 会重置(`remember` 绑 item composition)。**接受**:用户每次滑出屏幕再回来,卡片会重新关闭背景;这是 Android RecyclerView 标准行为,用户无感知。

- **[R2] 长按 + 左滑冲突** → `combinedClickable` 长按和 SwipeToDismissBox 的 swipe 互斥(Material3 内部已处理 pointer event 分发)。**验证**:真机实测。

- **[R3] DropdownMenu 与 SwipeToDismissBox 同框** → 同时显示会重叠。**处理**:屏级 `menuExpandedFor != null` 时,长按其他 item 会先 `menuExpandedFor = null`(实际是 set 为新 id),原菜单自动 dismiss。SwipeToDismissBox 当前用户操作完成(点背景按钮)→ dismissState.reset() → 卡片回原位。

- **[R4] addTagToNote 在 widget 刷新失败时不传播错误** → widget 更新失败会 log warning 但不阻塞业务。**接受**:与现有 `upsert` / `delete` widget 刷新失败行为一致,SharedPreferences 写失败概率极低。

- **[R5] Delete 操作无 undo** → 滑动背景点删除 + AlertDialog 二次确认是兜底,用户取消即不删。**接受**:v1 不做 undo(spec scope 外)。

- **[R6] VM 错误静默 → 用户感知不到失败** → log 已留,可观测。**接受**:列表屏失败最坏情况是行不消失(置顶/标签)/ 行不消失(删除),用户重新尝试即可。

## Migration Plan

- 本 change 是纯新增 + 局部 UI 改造,无 schema 迁移、无依赖升级
- 部署顺序:`/opsx:apply` 实施 tasks.md → `assembleDebug` → `ktlintCheck` → `testDebugUnitTest` → 用户装到 V2509A 真机验证
- 回滚:删 `AddExistingTagDialog.kt` + revert 4 个修改文件(`NoteRepository.kt` / `QuickNoteListViewModel.kt` / `NoteRow.kt` / `QuickNoteListScreen.kt`)+ 删 13 个 string key。无可破坏性 schema/接口变化(只新增方法,`NoteRow.onLongClick` 默认空)。