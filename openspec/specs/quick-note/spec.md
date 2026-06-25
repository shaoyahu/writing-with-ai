# quick-note Specification

## Purpose
TBD - created by archiving change quick-note-feature. Update Purpose after archive.
## Requirements
### Requirement: Note entity schema

系统 MUST 在 Room `notes` 表中持久化 `Note` 实体,M1 已有 `lastAiOp` / `lastAiAt` 字段但始终为 null。M2 修改行为:**当 AiGateway 完成一次 stream 后,系统 MUST 更新该 Note 的 `lastAiOp` 为操作类型字符串(`"expand"` / `"polish"` / `"organize"`) 和 `lastAiAt` 为当前 epoch millis**。

#### Scenario: AI operation completes, metadata written
- **WHEN** `AiGateway.streamWritingOp(op=EXPAND, sourceText="...", ...)` 完成(Done event)
- **THEN** `notes` 表中对应行的 `lastAiOp="expand"` 且 `lastAiAt=<completionTime>`

#### Scenario: AI operation fails, metadata not written
- **WHEN** `AiGateway.streamWritingOp(...)` 只收到 Failed(未收到 Done)
- **THEN** `notes` 表的 `lastAiOp` / `lastAiAt` 保持原值(不被 Failed 覆盖)

### Requirement: Note CRUD via Repository

系统 MUST 在 `NoteRepository` 中新增 `updateAiMetadata(noteId: String, op: String, at: Long)`。此方法与 `streamWritingOp(...)` 的 Done 事件配对调用:Gateway 在完成时调 repo 写字段。其余 CRUD 行为不变。

#### Scenario: updateAiMetadata sets fields
- **WHEN** 调用 `NoteRepository.updateAiMetadata(noteId="abc", op="polish", at=1700000000000L)`
- **THEN** `notes` 表中 `id="abc"` 的行 `lastAiOp="polish"`,`lastAiAt=1700000000000L`

### Requirement: Tag many-to-many

系统 MUST 在 `note_tags` 表中以 `(noteId, tag)` 联合主键存储标签交叉引用;同一 tag 可挂在多篇笔记,同一笔记可挂多个 tag。

#### Scenario: 给笔记加 tag
- **WHEN** 用户在编辑页输入"灵感"并回车提交
- **THEN** `note_tags` 新增一行 `(noteId=<当前笔记>, tag="灵感")`

#### Scenario: 从笔记移除 tag
- **WHEN** 用户点击已挂 tag "灵感" 的 chip 上的删除图标
- **THEN** `note_tags` 中 `(noteId, "灵感")` 行被删除;笔记本身保留

#### Scenario: 列表按 tag 筛选
- **WHEN** 列表页用户点击"灵感"标签 chip
- **THEN** 列表只展示 `note_tags` 中存在 `(noteId, "灵感")` 的笔记,且保持"固定优先 + 时间倒序"

#### Scenario: NoteRow chip 点击触发 tag 筛选
- **WHEN** 列表行内 `NoteRow` 渲染的 `AssistChip("#$tag")` 被点击
- **THEN** `QuickNoteListViewModel.selectTag(tag)` 被调用;`selectedTag = tag`;`observeNotesWithTags(query, tag)` emit 仅含该 tag 的笔记;顶部"当前筛选 #tag" banner 渲染

### Requirement: List ordering pinned-first and newest-first

系统 MUST 让列表(无搜索 / 无 tag 筛选时)以 `isPinned DESC, updatedAt DESC` 顺序返回;被固定的笔记排在前部,同组内按更新时间倒序。

#### Scenario: 固定笔记置顶
- **WHEN** 数据库中有 A(updatedAt=t1, pinned=false)、B(updatedAt=t2<t1, pinned=true)、C(updatedAt=t3<t2, pinned=false)
- **THEN** 列表顺序为 [B, A, C]

#### Scenario: 同 pinned 组按 updatedAt 倒序
- **WHEN** 数据库中两条笔记都是 `pinned=true`,`updatedAt` 不同
- **THEN** 较新的(updatedAt 较大)在前

#### Scenario: 选中 tag 后顶部 banner 显示 + 一键清除
- **WHEN** 用户在 `TagFilterRow` 点选某个 tag(`selectedTag != null`)
- **THEN** `QuickNoteListScreen` 在 search 与 TagFilterRow 中间渲染 "当前筛选 #tag" `AssistChip` + trailing close;点 close → `viewModel.selectTag(null)` → banner 消失 + 列表恢复全部

### Requirement: Search by title or content with LIKE

系统 MUST 在搜索框非空时,按 `title LIKE '%query%' OR content LIKE '%query%'`(大小写不敏感)返回匹配的笔记;空查询视为无搜索条件。

#### Scenario: 搜索匹配 title
- **WHEN** 用户在搜索框输入"晨跑",且数据库中存在 `title="晨跑计划"` 的笔记
- **THEN** 列表展示该笔记

#### Scenario: 搜索匹配 content
- **WHEN** 用户在搜索框输入"晨跑",但 `title="周末总结"` 的笔记 `content` 含 "周日晨跑记录"
- **THEN** 列表展示该笔记

#### Scenario: 搜索为空
- **WHEN** 搜索框为空字符串
- **THEN** 列表返回全部笔记(尊重固定排序与 tag 筛选)

#### Scenario: 搜索框 trailingIcon 一键清除
- **WHEN** 搜索框 `query` 非空
- **THEN** `OutlinedTextField` `trailingIcon` 渲染 `IconButton(Close)`;点 close → `viewModel.setQuery("")` → 输入框清空 + trailingIcon 消失 + 列表恢复全部
- **WHEN** `query` 为空
- **THEN** `trailingIcon` 不渲染(节省屏宽)

### Requirement: Pin and unpin a note

系统 MUST 提供 `NoteRepository.setPinned(noteId, isPinned)`;调用后列表顺序按 Requirement "List ordering" 立即重排。

#### Scenario: 固定一条笔记
- **WHEN** 详情页用户点击"固定"按钮
- **THEN** 该笔记 `isPinned=true`,列表中它出现在未固定笔记之上

#### Scenario: 取消固定
- **WHEN** 已固定笔记的详情页用户再次点击"固定"按钮
- **THEN** `isPinned=false`,回到正常时间倒序

### Requirement: Word count and reading time on detail page

系统 MUST 在详情页根据 `Note.content` 计算字数(中文按字符、英文按空格分词求和)与预估阅读时间(中文 300 字 / 分钟、英文 200 词 / 分钟,取较慢值向上取整为分钟数),并展示在页脚;`Note.content` 为空时显示 0 字 / 0 分钟。

#### Scenario: 中文内容字数
- **WHEN** `content = "今天天气很好"`(6 个汉字)
- **THEN** 详情页页脚显示"6 字 · 1 分钟"

#### Scenario: 英文内容字数与阅读时间
- **WHEN** `content = "Hello world this is a test"`(6 词)
- **THEN** 字数显示 6,阅读时间显示 1 分钟(6 / 200 < 1,向上取整)

#### Scenario: 空内容
- **WHEN** `content = ""`
- **THEN** 显示"0 字 · 0 分钟"

### Requirement: Single-note Markdown share via system Intent

系统 MUST 在详情页提供"分享"入口,触发 `Intent.ACTION_SEND` 且 `type = "text/markdown"`,`EXTRA_TEXT` 为该笔记渲染后的 Markdown(标题 + 空行 + 正文,标题为空则省略标题行);不写文件、不申请存储权限。

#### Scenario: 分享带标题的笔记
- **WHEN** 详情页用户点击"分享"按钮,`title="晨跑计划"`,`content="# 晨跑\n- 6:30 起床"`
- **THEN** 系统弹出应用选择器,接收 Intent `type="text/markdown"`,`EXTRA_TEXT="晨跑计划\n\n# 晨跑\n- 6:30 起床"`

#### Scenario: 分享无标题笔记
- **WHEN** `title=""`(用户未填),`content="随手记一段话"`
- **THEN** `EXTRA_TEXT="随手记一段话"`(不输出空标题行)

### Requirement: Navigation routes for quick-note feature

`AppNav.kt` MUST 注册以下类型安全(`@Serializable`)目的地:

- `quicknote/list` — 列表入口(应用启动默认目的地)
- `quicknote/detail/{id}` — 详情页,`id` 为 `Note.id`
- `quicknote/edit?id={id}` — 编辑页,`id` 缺省或为 `NEW` 时视为新建

#### Scenario: 启动跳到列表
- **WHEN** 应用启动(冷启动 / 从 widget 之外的入口)
- **THEN** NavHost 默认展示 `quicknote/list`

#### Scenario: 列表点条目进详情
- **WHEN** 列表项被点击
- **THEN** NavController 导航到 `quicknote/detail/{id}`,系统返回手势可回到列表

#### Scenario: 新建入口
- **WHEN** 列表 FAB 或空状态"新建"按钮被点击
- **THEN** NavController 导航到 `quicknote/edit?id=NEW`

#### Scenario: 编辑既有笔记
- **WHEN** 详情页"编辑"按钮被点击
- **THEN** NavController 导航到 `quicknote/edit?id=<当前 id>`

#### Scenario: 详情页无 tag 显示空状态文案
- **WHEN** `note.tags.isEmpty()`
- **THEN** `QuickNoteDetailScreen` 在 tag 区域渲染 `Text("无标签", style = labelSmall, color = onSurfaceVariant)`,替代原 `if (tags.isNotEmpty())` 的空白

### Requirement: i18n coverage

所有 quick-note UI 文案 MUST 出现在 `values/strings.xml`(中文,权威)与 `values-en/strings.xml`(英文);Composable 内**禁止**硬编码中文 / 英文文案。英文未翻译条目以 `TODO(en):` 前缀占位,M5 polish 阶段补齐。

#### Scenario: 列表空状态文案来自 strings.xml
- **WHEN** 列表无数据且无搜索条件
- **THEN** 显示 `R.string.quicknote_list_empty` 资源对应的中文"还没有笔记,点 + 创建第一条";系统语言为英文时显示对应英文

#### Scenario: 英文占位不阻断构建
- **WHEN** 某个 string 在 `values-en/strings.xml` 中值为 `TODO(en): quicknote_list_empty`
- **THEN** APK 仍能正常构建并启动(运行时显示该占位文本),`./gradlew :app:assembleDebug` 与 `:app:check` 全部通过

### Requirement: Note database schema is exportable

`AppDatabase` MUST 设置 `exportSchema = true`,schema JSON 输出目录 MUST 由 `app/build.gradle.kts` 的 KSP arg `room.schemaLocation` 指定为 `app/schemas`,且 `app/schemas/` 目录 MUST 提交到 git。

#### Scenario: Schema 文件生成
- **WHEN** `./gradlew :app:assembleDebug` 跑完
- **THEN** `app/schemas/com.yy.writingwithai.core.data.db.AppDatabase/1.json` 存在并被 git 追踪

#### Scenario: 后续 schema 变更可走 AutoMigration
- **WHEN** 未来某次 OpenSpec change 修改 `Note` 字段并把 `version` 升到 2
- **THEN** Room 能在 `1.json` 与 `2.json` 之间生成 AutoMigration spec(M2+ 关注,本 change 不实现)

### Requirement: Detail screen supports text selection for AI actions(M3 新增,扩展自 M1 "Single-note Markdown share via system Intent")

详情屏 MUST 把原 M1 的 `SelectionContainer { Text(content) }` 替换为 `BasicTextField(value = textFieldValue, onValueChange = ..., readOnly = true)`,持有 `TextFieldValue`(含 `selection: TextRange`),以便把选中文本传给 `feature/aiwriting/` 的 `ActionSheet`;`onValueChange` 仅在 `selection` 变化时更新 ViewModel 持有的 `selectionState`(不修改 content)。

#### Scenario: 选中文本后 FAB 出现
- **WHEN** 详情屏用户长按选中 5 个字符(任意范围)
- **THEN** ViewModel 的 `selectionState` 更新为非空 `TextRange`;UI 渲染 AutoAwesome FAB(参见 ai-actions Requirement "ActionSheet");若用户清空选区(点空白处),FAB 重新隐藏,Share FAB 恢复

#### Scenario: 无选区时仅 Share FAB
- **WHEN** 详情屏进入后用户未选中任何文本
- **THEN** `selectionState == TextRange.Zero`(空);UI 只显示 Share FAB(M1 既有)

#### Scenario: 选中文本持久到 ViewModel
- **WHEN** 详情屏旋转屏幕 / 临时进 background
- **THEN** `selectionState` 由 ViewModel 持有(不是 Compose `remember`),不丢失;下次回到前台仍可继续触发 ActionSheet

### Requirement: FAB on detail screen shows Share or AI action affordance(M3 新增)

详情屏 MUST 渲染一个 `Scaffold.floatingActionButton`,根据 `selectionState.isEmpty` 在两个 FAB 之间切换:

| 选区 | FAB 图标 | 行为 |
| --- | --- | --- |
| 空 | `Icons.Filled.Share`(M1 既有) | 触发 `Intent.ACTION_SEND` |
| 非空 | `Icons.Filled.AutoAwesome`(sparkle) | 弹出 `DropdownMenu`(`ai-actions` ActionSheet) |

#### Scenario: 无选区点 Share FAB
- **WHEN** 详情屏无选区,用户点 Share FAB
- **THEN** 系统弹出应用选择器,Intent `type="text/markdown"`,`EXTRA_TEXT` 走 M1 既有 share 行为(不变化)

#### Scenario: 有选区点 AutoAwesome FAB
- **WHEN** 详情屏选区非空,用户点 AutoAwesome FAB
- **THEN** DropdownMenu 弹出,4 项菜单(扩写/润色/整理/复制)齐全(参见 ai-actions "ActionSheet")

### Requirement: Detail screen observes note content and AI metadata together(M3 新增)

详情屏 MUST 订阅 `NoteRepository.observeNoteWithTags(noteId)`,在 `acceptReplace()` 完成后(`Note.content` / `Note.lastAiOp` / `Note.lastAiAt` 被更新)自动刷新 UI;`WordCount` / `ReadingTime` / 顶部 metadata 区域随之更新。

#### Scenario: 接受后字数重算
- **WHEN** state = `Done(op=EXPAND, finalText="扩写后文本(80 字)")`,用户点"接受"
- **THEN** 详情屏 `content` 区域从原正文切到"扩写后文本(80 字)";页脚字数从旧值变为 80;页脚阅读时间同步重算

#### Scenario: 接受后 lastAiOp 显示
- **WHEN** 接受完成后
- **THEN** 详情屏顶部 metadata 区域显示 `R.string.aiwriting_meta_ai_fmt` 文案(中文:上次 AI 操作 · %1$s · %2$s),`%1$s` = "扩写",`%2$s` = `<formatDateTime(lastAiAt)>`;接受前不显示该行(因为 `lastAiOp == null`)

### Requirement: Editor screen does not modify AI metadata on manual overwrite(M3 新增)

编辑器屏(`QuickNoteEditorScreen` / `QuickNoteEditorViewModel`) MUST 在用户保存笔记时(走 M1 既有 `repo.upsert` 路径),**不**主动修改 `Note.lastAiOp` / `Note.lastAiAt`;若用户从详情屏进入编辑器手动改稿,`lastAiOp` 仍保留(历史 AI 痕迹),但 `updatedAt` 会更新。

#### Scenario: 手动编辑不擦除 AI 元数据
- **WHEN** 笔记 `lastAiOp="expand"`,用户从详情屏进入编辑器,修改 `content` 后保存
- **THEN** Room 更新该行 `content=<新值>`、`updatedAt=<newNow>`,`lastAiOp="expand"` / `lastAiAt=<原值>` 保持不变

#### Scenario: 编辑器不调用 AiGateway
- **WHEN** grep `feature/quicknote/edit/**/*.kt`
- **THEN** 0 个 import 出现 `core.ai.*`;编辑器与 AI 模块零耦合

### Requirement: Note domain model is unchanged(M3 验证项)

M3 MUST NOT 修改 `Note` 数据类(`core/data/model/Note.kt`)的任何字段;`lastAiOp` / `lastAiAt` 是 M1 已预置的占位字段,本 change 仅首次写入并读取,schema 不变。

#### Scenario: Note 字段集合保持 v1
- **WHEN** `git diff openspec/changes/ai-writing-actions/ feature/quicknote/ core/data/model/Note.kt`
- **THEN** 0 个字段新增 / 0 个字段类型变更;仅 `lastAiOp` / `lastAiAt` 从 `null` 变成可被赋值

#### Scenario: Room migration 不变
- **WHEN** 数据库从 M2 v2 schema 升到 M3 v2 schema(无迁移)
- **THEN** AppDatabase 仍为 `version = 2`,无新 MIGRATION 类;现有用户数据无损

### Requirement: Navigation routes unchanged(M3 验证项)

`AppNav.kt` MUST NOT 新增 AI 操作的 Nav 路由;`StreamingPanel` 通过 `ModalBottomSheet` 在详情屏 / 编辑器屏之上覆盖,不走 NavController(理由:流式面板生命周期与 noteId 强绑定,Hilt scoped ViewModel 比 Nav scoped 更稳)。

#### Scenario: 详情屏直接展开 ModalBottomSheet
- **WHEN** 详情屏触发 AI 操作
- **THEN** `ModalBottomSheet` 出现在详情屏之上(不是新 Nav destination);返回 / sheet 外点击仅关闭 sheet,不回退 Nav 栈

#### Scenario: 编辑器屏直接展开 ModalBottomSheet
- **WHEN** 编辑器屏触发 AI 操作(若支持,M3 可选;本 change 暂不在编辑器屏集成)
- **THEN** 行为同上;M3 主体仅在详情屏集成,编辑器屏扩展留 M5

### Requirement: Detail screen integrates ai-actions via feature Entry

详情屏 MUST 通过 `com.yy.writingwithai.feature.aiwriting.AiwritingEntry` 拿到 `AiActionViewModel`(Hilt),**不**直接 import `feature.aiwriting.streaming.AiActionViewModel`。`AiwritingEntry` 暴露 `@Composable fun rememberAiActionViewModel(noteId: String): AiActionViewModel`(封装 HiltViewModel + Saver)。

#### Scenario: Entry 暴露统一 API
- **WHEN** 详情屏 Composable 调用 `AiwritingEntry.rememberAiActionViewModel(currentNoteId)`
- **THEN** 返回 `AiActionViewModel` 实例;内部走 `hiltViewModel<AiActionViewModel>()`

#### Scenario: 详情屏不 import 内部文件
- **WHEN** grep `feature/quicknote/detail/QuickNoteDetailScreen.kt`
- **THEN** 唯一对 `feature.aiwriting` 的 import 是 `import com.yy.writingwithai.feature.aiwriting.AiwritingEntry`;不出现 `feature.aiwriting.streaming.*` / `feature.aiwriting.action.*`

### Requirement: NoteRepository supports observeRecent for widget(M4-1 新增)

`NoteRepository` MUST 新增 `fun observeRecent(limit: Int): Flow<List<Note>>`,内部走 `noteDao.observeAll().map { it.take(limit).map { e -> e.toModel() } }`。**不**新增 DAO SQL(`take(n)` 内存截断足够,单用户笔记数 < 10k)。

#### Scenario: observeRecent(3) emit 最多 3 条
- **WHEN** 数据库有 5 条笔记(按 `updatedAt` desc)
- **THEN** `observeRecent(3)` Flow emit 头 3 条最新笔记

#### Scenario: observeRecent(1) emit 1 条
- **WHEN** 数据库有 5 条笔记
- **THEN** `observeRecent(1)` Flow emit 最新 1 条

#### Scenario: observeRecent 无笔记
- **WHEN** 数据库为空
- **THEN** `observeRecent(N)` Flow emit 空列表

#### Scenario: 笔记增删改后 observeRecent 自动 emit
- **WHEN** 用户新建一条笔记(`upsert` 后) → `observeRecent` 自动 emit 新列表(含新笔记)
- **THEN** Glance widget 收到新数据,触发 `provideContent` 重渲染

### Requirement: Note schema unchanged by widget(M4-1 验证项)

M4-1 MUST NOT 修改 `NoteEntity` / `NoteDao` / `Note` 字段;`observeRecent` 是 `NoteRepository` 新方法,schema 完全不变。

#### Scenario: Note 字段集合保持 v2
- **WHEN** `git diff openspec/changes/home-screen-widget/ core/data/`
- **THEN** 0 个字段新增 / 0 个字段类型变更;`AppDatabase` 仍 `version = 2`,无新 MIGRATION

#### Scenario: observeRecent 不改 DAO SQL
- **WHEN** `git diff openspec/changes/home-screen-widget/ core/data/db/NoteDao.kt`
- **THEN** 0 个 `@Query` 新增;只改 `NoteRepository.kt`

### Requirement: AppNav route supports prefillFocus param(M4-1 新增)

`app/AppNav.kt` 中 `QuicknoteEdit` data class MUST 加 `val prefillFocus: Boolean = false` 字段;`composable<QuicknoteEdit>` block 解析 `prefillFocus` 并透传给 `QuickNoteEditorScreen`。

Widget 通过 `PendingIntent.getActivity` 启动 MainActivity 时,`intent` extra 含 `"prefillFocus"=true` + `"route"="quicknote/edit"`;`MainActivity.onCreate(intent)` 解析后 navigate 到 `QuicknoteEdit(prefillFocus = true)`。

#### Scenario: 编辑器 route 默认 prefillFocus=false
- **WHEN** 用户从列表 FAB / 列表空状态"新建"按钮进编辑器
- **THEN** `prefillFocus = false`(默认),输入框**不**自动 focus(行为同 M1)

#### Scenario: 编辑器 route 显式 prefillFocus=true
- **WHEN** 用户从 widget 点"+",MainActivity 启动并解析 route 含 `prefillFocus=true`
- **THEN** `QuickNoteEditorScreen` 接收 `prefillFocus = true`,输入框自动 focus,用户可立即键入

#### Scenario: 编辑器 route 不影响编辑器其他行为
- **WHEN** `prefillFocus = true`
- **THEN** 编辑器加载既有笔记(如编辑现有笔记)的逻辑不变;只是输入框自动 focus,无副作用

### Requirement: Editor screen supports prefillFocus param(M4-1 新增)

`QuickNoteEditorScreen` MUST 接受 `prefillFocus: Boolean = false` 参数,`LaunchedEffect(prefillFocus)` 内若 `prefillFocus == true` 则调 `FocusRequester.requestFocus()`(参考 Compose Focus API)。

`QuickNoteEditorViewModel` 不需要新增字段(参数是 UI 一过性行为,不持久化)。

#### Scenario: prefillFocus=true 触发 FocusRequester
- **WHEN** 用户从 widget 进编辑器,`prefillFocus=true`
- **THEN** `LaunchedEffect` 触发,`focusRequester.requestFocus()` 被调,输入框获得焦点

#### Scenario: prefillFocus=false 不触发
- **WHEN** 用户从列表 FAB 进编辑器,`prefillFocus=false`(默认)
- **THEN** `LaunchedEffect` 不触发 focus,用户需手动点击输入框

#### Scenario: prefillFocus 不改保存逻辑
- **WHEN** 用户在编辑器输入文字并保存(`upsert`)
- **THEN** 走 M1 既有 `NoteRepository.upsert(...)`,`prefillFocus` 仅影响首次 focus 行为

### Requirement: WidgetIntent launcher routes pass through MainActivity(M4-1 联动)

`core/widget/WidgetIntents.kt` 的 `PendingIntent.getActivity` MUST 启动 `MainActivity`(`.app.MainActivity`),`Intent` 含 `extra`:
- `"route" = "quicknote/edit?prefillFocus=true"`(新建按钮)或 `"quicknote/detail/{noteId}"`(笔记项)
- `FLAG_ACTIVITY_NEW_TASK`(从 widget host process 启动需要)

`MainActivity.onCreate(intent)` MUST 解析 `intent.getStringExtra("route")` 并 navigate 到对应 route;若 `route == null` 走默认 `quicknote/list`(M1 既有)。

#### Scenario: MainActivity 解析 widget extra
- **WHEN** 用户从 widget 点"+",MainActivity 启动并收到 `intent.extra("route") = "quicknote/edit?prefillFocus=true"`
- **THEN** `MainActivity` navigate 到 `QuicknoteEdit(prefillFocus = true)`,输入框自动 focus

#### Scenario: MainActivity 解析 widget extra detail
- **WHEN** 用户从 widget 点笔记项,MainActivity 收到 `intent.extra("route") = "quicknote/detail/n1"`
- **THEN** `MainActivity` navigate 到 `QuicknoteDetail(id = "n1")`,显示该笔记详情

#### Scenario: MainActivity 默认 route
- **WHEN** 用户从 launcher 图标启动 App(无 widget)
- **THEN** `intent.extra("route") == null`,走 M1 既有 `quicknote/list` 默认 destination

### Requirement: Widget tap back returns to launcher, not app list(M4-1 联动)

`WidgetIntents.createNotePendingIntent` / `openNotePendingIntent` MUST 用 `TaskStackBuilder` 构造 `PendingIntent`,**不**用裸 `PendingIntent.getActivity`。`TaskStackBuilder` 保证 back 行为:widget tap → MainActivity → back → launcher 桌面(不是 App 内列表页)。

#### Scenario: 新建按钮 back 回桌面
- **WHEN** 用户在 widget 点"+" → MainActivity 启动到编辑页 → 用户按 back
- **THEN** 返回到 launcher 桌面,**不**进入 App 内列表页

#### Scenario: 笔记项 back 回桌面
- **WHEN** 用户在 widget 点笔记项 → MainActivity 启动到详情 → 用户按 back
- **THEN** 返回到 launcher 桌面

#### Scenario: 编辑器保存后返回(launcher 行为)
- **WHEN** 用户在 widget 启动的编辑器保存笔记
- **THEN** 走 M1 既有 `popBackStack()` 行为 → launcher 桌面(因为任务栈独立)

### Requirement: AndroidManifest declares predictive back flags(M4-2 新增)

`AndroidManifest.xml` MUST 在 `<application>` 与 `MainActivity <activity>` 双重声明 `android:enableOnBackInvokedCallback="true"`(M4-2 新增);`<activity>` 增 `android:windowSoftInputMode="adjustResize"` 配合编辑器键盘。

#### Scenario: AndroidManifest enableOnBackInvokedCallback 在 application
- **WHEN** grep `AndroidManifest.xml` `<application`
- **THEN** `android:enableOnBackInvokedCallback="true"` 在 `<application>` 属性列表内

#### Scenario: AndroidManifest enableOnBackInvokedCallback 在 activity
- **WHEN** grep `AndroidManifest.xml` `<activity.*MainActivity`
- **THEN** `android:enableOnBackInvokedCallback="true"` 在 `<activity>` 属性列表内

#### Scenario: windowSoftInputMode 配合编辑器
- **WHEN** grep `AndroidManifest.xml` `<activity.*MainActivity`
- **THEN** `android:windowSoftInputMode="adjustResize"` 在 `<activity>` 属性列表内

#### Scenario: targetSdk = 35 保持
- **WHEN** grep `app/build.gradle.kts` `targetSdk`
- **THEN** `targetSdk = 35`(M0 已配,M4-2 不改)— Android 14+ predictive back 强制要求

### Requirement: MainActivity honors enableOnBackInvokedCallback(M4-2 验证)

`MainActivity` MUST 在 `onCreate` 不写自定义 `BackHandler { enabled = ... }`(M4-2 spec 要求"不自定义手势拦截");让 `NavHost` + `OnBackPressedDispatcher` 自管 back 行为,触发 predictive back 系统动画。

#### Scenario: MainActivity 不自定义 BackHandler
- **WHEN** grep `MainActivity.kt`
- **THEN** 0 个 import `androidx.activity.compose.BackHandler`;0 个 `BackHandler { ... }` 调用

#### Scenario: AppNav 不自定义 BackHandler
- **WHEN** grep `AppNav.kt`
- **THEN** 0 个 import `androidx.activity.compose.BackHandler`

#### Scenario: NavHost 自带 back 集成
- **WHEN** `androidx.navigation:navigation-compose:2.8.4`(M1 已配) + `enableOnBackInvokedCallback="true"` 启用
- **THEN** NavHost 在系统 back 触发时自动 `popBackStack()`,并触发 predictive back 系统动画(Android 14+)

### Requirement: AppNav LaunchedEffect initialRoute MUST 不动(M4-2 保留 M4-1 修)

M4-1 r2 MUST 已加 `popUpTo(QuicknoteList) { inclusive = true }` 在 `LaunchedEffect(initialRoute)` 内;**M4-2 MUST 不重复改 AppNav**。

#### Scenario: LaunchedEffect 仍含 popUpTo
- **WHEN** grep `AppNav.kt` "popUpTo"
- **THEN** 至少 1 个 `popUpTo(QuicknoteList)` 在 `LaunchedEffect(initialRoute)` block 内(M4-1 修后保留)

#### Scenario: widget Intent 走 TaskStackBuilder(M4-2 新要求)
- **WHEN** `core/widget/QuickNoteWidget.kt createNoteIntent(context)` 调用 `TaskStackBuilder`
- **THEN** AppNav LaunchedEffect 内 `popUpTo(QuicknoteList) { inclusive = true }` 仍生效(双保险)— widget Intent 走 TaskStackBuilder 构造的栈,**AppNav 内的 popUpTo 是兜底**,即使某天 TaskStackBuilder 出问题,popUpTo 仍能清理栈

### Requirement: QuickNoteListScreen TopAppBar overflow menu 含 settings 入口(M4-3 新增)

`feature/quicknote/list/QuickNoteListScreen.kt` TopAppBar `actions` MUST 含 overflow menu(`Icons.Filled.MoreVert` icon → DropdownMenu),菜单项 "数据迁移"(R.string.settings_data_title)点击后 `navController.navigate(SettingsData)`(M4-3 新增 nav route)。

#### Scenario: overflow menu 含数据迁移
- **WHEN** grep `QuickNoteListScreen.kt` "MoreVert"
- **THEN** 至少 1 个 `IconButton(onClick = { ... DropdownMenu(...) { DropdownMenuItem(text = { Text(stringResource(R.string.settings_data_title)) }, onClick = { navController.navigate(SettingsData) }) } })`

#### Scenario: 点数据迁移跳 settings
- **WHEN** 用户点 "数据迁移" 菜单项
- **THEN** `navController.navigate(SettingsData)` 跳 SettingsDataScreen

#### Scenario: 数据迁移仅在 overflow menu,不在 TopAppBar 显眼位置
- **WHEN** grep `QuickNoteListScreen.kt` `navigate(SettingsData)`
- **THEN** 0 个直接 `IconButton(onClick = { navController.navigate(SettingsData) })`(只能在 overflow menu 内)— TopAppBar 不被数据迁移按钮占据

### Requirement: Note schema 不变(M4-3 验证项)

M4-3 MUST NOT 修改 `NoteEntity` / `NoteDao` / `Note` 字段;`NoteExporter` 读 M1 既有 schema,导出字段 = 数据库字段。

#### Scenario: Note 字段保持 v2
- **WHEN** `git diff openspec/changes/data-export-import/ core/data/db/`
- **THEN** 0 个字段新增 / 0 个字段类型变更;`AppDatabase` 仍 `version = 2`

#### Scenario: 导出字段集 = Note data class
- **WHEN** `NoteExporter.exportToJsonZip` 序列化 `Note` 实例
- **THEN** `notes.json` 元素的字段集 = `Note(id, title, content, createdAt, updatedAt, isPinned, lastAiOp, lastAiAt)`(M1 schema,无缺无多)

### Requirement: ai_history schema 不变(M4-3 验证项)

M4-3 MUST NOT 修改 `AiHistoryEntity` / `AiHistoryDao` / `AiHistory` 字段;`NoteExporter` 读 M2 既有 schema。

#### Scenario: ai_history 字段保持 v2
- **WHEN** `git diff openspec/changes/data-export-import/ core/data/db/`
- **THEN** 0 个 `ai_history` 相关字段变更

### Requirement: note_tags schema 不变(M4-3 验证项)

M4-3 MUST NOT 修改 `NoteTagCrossRef` / `NoteTagDao` 字段;`tags.json` 导出格式 `Map<noteId, List<String>>` 是 M1 `note_tags` 表的反查投影。

#### Scenario: tags.json 投影自 note_tags
- **WHEN** `NoteExporter` 调 `noteTagDao.observeAllCrossRefs().first()` 拿 `(noteId, tag)` 行列表
- **THEN** 转 `Map<noteId, List<String>>` 写入 `tags.json`(导入时按 map 写回 `note_tags` 表)

### Requirement: AppNav 加 SettingsData route(M4-3 新增)

`app/AppNav.kt` MUST 加:
- `@Serializable data object SettingsData`
- `composable<SettingsData> { SettingsDataScreen(onBack = { navController.popBackStack() }) }`

#### Scenario: SettingsData route 注册
- **WHEN** grep `AppNav.kt` "SettingsData"
- **THEN** 至少 1 个 `data object SettingsData` + 1 个 `composable<SettingsData> {`

#### Scenario: back 行为回 QuickNoteListScreen
- **WHEN** 用户从 SettingsDataScreen 按 back
- **THEN** `navController.popBackStack()` 回 QuickNoteListScreen(非退出 App)— launchSingleTask + M4-2 popUpTo 兜底

#### Scenario: SettingsData 不在 widget Intent 启动路径
- **WHEN** `core/widget/QuickNoteWidget.kt createNoteIntent(context)` route = "quicknote/edit?prefillFocus=true" 或 "quicknote/detail/{id}"
- **THEN** 0 个 "quicknote/settings" route — widget 启动不到 SettingsDataScreen

### Requirement: NoteRoomSchema ExportModels 不重(M4-3 验证项)

`core/data/export/ExportModels.kt` 内 `ExportNote` / `ExportAiHistory` 等 Serializable data class 字段 MUST 与 `Note` / `AiHistory` 一一对应(无缺无多);导入时字段缺失用默认值。

#### Scenario: ExportNote 字段 = Note 字段
- **WHEN** grep `ExportModels.kt` "data class ExportNote"
- **THEN** 字段集 `(id, title, content, createdAt, updatedAt, isPinned, lastAiOp, lastAiAt)` 与 `Note` 一致

#### Scenario: 旧版本 zip 导入新版本字段兼容
- **WHEN** zip `notes.json` 缺 `lastAiOp` / `lastAiAt`(M1 老版本导)
- **THEN** `ImportReport` 验 `meta.schema_version`,字段缺失用默认值 `null`(`lastAiOp` / `lastAiAt` 本就是可空)

### Requirement: NoteListScreen 不被 M4-3 改其他功能(M4-3 验证项)

M4-3 MUST NOT 改 QuickNoteListScreen 其他功能(搜索 / 列表 / 分享 / pin),只增 overflow menu 数据迁移入口。

#### Scenario: 列表屏搜索保留
- **WHEN** grep `QuickNoteListScreen.kt` "search"
- **THEN** M1 既有搜索框仍在 TopAppBar(代码未改)

#### Scenario: 列表屏分享保留
- **WHEN** grep `QuickNoteListScreen.kt` "Share"
- **THEN** M1 既有分享按钮仍在每条 row(代码未改)

#### Scenario: pin 固定保留
- **WHEN** grep `QuickNoteListScreen.kt` "PushPin"
- **THEN** M1 既有 pin/unpin 逻辑保留(代码未改)

### Requirement: Note editor delegates voice input to system IME

`QuickNoteEditorScreen` MUST 走标准 Compose `OutlinedTextField` / `TextField` 落地标题 + 正文输入(已 M1 落地),不修改其 `Modifier` / `value` / `onValueChange` / `keyboardOptions` / `keyboardActions` 等属性以"屏蔽 IME 语音输入";系统 IME 自带的"麦克风"按钮触发 ASR 出文字后,通过 `InputConnection.commitText()` 注入到光标位置,应用层零感知。

App MUST NOT 在 `AndroidManifest.xml` 声明 `<uses-permission android:name="android.permission.RECORD_AUDIO" />`;MUST NOT 集成任何 on-device / 三方 STT provider(Whisper.cpp / Vosk / 讯飞 / 百度 ASR);MUST NOT 在编辑器屏新增"语音输入"专属 UI 按钮(v1 委托 IME 自然提供)。

#### Scenario: TextField 走标准 IME(无 STT 集成)
- **WHEN** 用户在 `QuickNoteEditorScreen` 的正文 `OutlinedTextField` 长按 / 点击唤起系统 IME
- **THEN** IME 弹出;若用户当前 IME 支持 ASR(搜狗 / 讯飞 / 百度 / Gboard 等),"麦克风"按钮可见;用户点"麦克风"说出语音 → IME 内部 ASR 出文字 → 文字通过 InputConnection 注入到光标位置 → `OutlinedTextField` 的 `onValueChange` 收到新 `TextFieldValue` → `editorViewModel.setContent(newValue)` 走 M1 既有 upsert 路径

#### Scenario: BasicTextField action 模式走 IME 标准协议
- **WHEN** 详情屏 `BasicTextField(value = textFieldValue, onValueChange = ..., readOnly = true)`(M3 既有)显示 content
- **THEN** 用户长按选中 5 个字符后,系统弹出含"剪切 / 复制 / 分享 / 语音输入(由 IME 提供)"的浮动工具栏;点"语音输入"后行为同上一个 Scenario(IME 处理 ASR + InputConnection 注入)— app 不感知

#### Scenario: v2+ STT 路径占位
- **WHEN** v2+ OpenSpec change 决定集成 STT(Whisper.cpp / 讯飞 / 百度 ASR)
- **THEN** 该 change MUST:
  1. 新建 capability `voice-stt` (或 `voice-input-v2`)对应 `openspec/specs/<capability>/spec.md`
  2. bump `R.integer.consent_version` 触发同意门强制重同(v1 隐私条款不涉及录音,需更新条款)
  3. 加 `<uses-permission android:name="android.permission.RECORD_AUDIO" />` 到 manifest + 加运行时权限申请 UI(详情页/编辑器页首次进入录音前)
  4. 在编辑器屏新增"麦克风"专属按钮,触发选定的 STT provider
  5. IME 路径仍保留,两条路径共存(用户选)

#### Scenario: 源码 grep 验证无 STT 依赖
- **WHEN** `grep -rE "(RECORD_AUDIO|Whisper|Vosk|讯飞|百度|腾讯).*STT" app/src/main/`
- **THEN** 0 个匹配(当前 v1 无 STT 集成,v2+ change 引入后才会有)

#### Scenario: 源码 grep 验证无 RECORD_AUDIO 权限
- **WHEN** `grep "RECORD_AUDIO" app/src/main/AndroidManifest.xml`
- **THEN** 0 匹配(manifest 不声明录音权限)

#### Scenario: 编辑器 TextField 不绕过 IME
- **WHEN** `grep -rE "(interceptKey|onKeyEvent|InputConnection.*rawInput)" app/src/main/java/com/yy/writingwithai/feature/quicknote/edit/`
- **THEN** 0 匹配(编辑器不拦截 IME 事件,IME 协议完整透传)

### Requirement: Wikilink autocomplete prefix recomputed on content change

`feature/quicknote/edit/QuickNoteEditorScreen` wikilink autocomplete MUST recompute `lastOpen` (the index of `[[` in the current content) when `content` state changes via `remember(content) { content.lastIndexOf("[[") }`. The `onSelect` callback MUST derive the prefix from the *current* `content` snapshot at click time, not from any captured `lastOpen` value at recompute time. This prevents stale-prefix corruption when user types between recompute and selection.

#### Scenario: Content typed between recompute and select
- **WHEN** `lastOpen` is computed for content "abc [[" and then user types "def" before clicking an autocomplete suggestion
- **THEN** `onSelect` derives prefix from current content "abc [[def" (not from stale "abc [[")

### Requirement: QuickNoteDetailViewModel exposes feishuRef as StateFlow

`feature/quicknote/detail/QuickNoteDetailViewModel` MUST expose `_feishuRef: StateFlow<FeishuRefEntity?>` derived from `refDao.observeForNote(noteId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)` rather than a one-shot `getRef(id)` call. The chip in the detail UI MUST update when sync / pull / push operations modify the ref row.

#### Scenario: Push changes ref state visible
- **WHEN** user taps "同步到飞书" and `FeishuSyncService.push(noteId)` writes a new `feishu_ref` row
- **THEN** the detail screen's feishu chip transitions from "未同步" to "已同步" without manual refresh

### Requirement: Quick note list screen uses new design tokens
QuickNoteListScreen SHALL use the filled capsule search bar (surfaceVariant background, xl 24dp corner radius). NoteRow SHALL use border-card style (12dp corner radius, 1dp outlineVariant border, 0 elevation). EmptyState SHALL show 64dp icon + brand tagline + primary CTA.

#### Scenario: Note list uses capsule search and border cards
- **WHEN** the note list screen is displayed
- **THEN** the search bar is a capsule (surfaceVariant background, 24dp corner radius) and note rows are border cards (12dp radius, no shadow)

### Requirement: Quick note detail screen uses new layout
QuickNoteDetailScreen SHALL display headlineLarge title, FlowRow tags, and a fixed bottom bar with Share + AutoAwesome icons. RelatedNotesSection SHALL be wrapped in Surface(surfaceVariant, 12dp radius).

#### Scenario: Note detail uses new layout
- **WHEN** note detail screen is displayed
- **THEN** title uses headlineLarge, bottom bar shows Share + AI icons, related notes in Surface card

### Requirement: Quick note editor uses borderless TextFields
QuickNoteEditorScreen SHALL use BasicTextField for title (headlineMedium, no border) and content (bodyLarge, weight(1f)). Tag section SHALL be wrapped in Surface(surfaceVariant).

#### Scenario: Editor uses borderless fields
- **WHEN** the editor is displayed
- **THEN** title field has no border outline, content field fills remaining height, tags in Surface card

