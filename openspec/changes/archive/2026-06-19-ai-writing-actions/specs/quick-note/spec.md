# quick-note

## Purpose

随手记(M1)的完整数据模型与 UI 行为契约;定义 `Note` / `Tag` 实体形状、CRUD / 搜索 / 标签 / 固定 / 单条分享导出的端到端行为,以及 Nav 路由契约。本 spec 是 M2 AI 抽象层(`AiHistory` 关联 `Note.id` 与 `Note.lastAiOp`)的前置。

TBD — synced from OpenSpec change `quick-note-feature`(2026-06-18)。原 change 在 `openspec/changes/quick-note-feature/`。

## REMOVED Requirements

无(M1 全部要求保留)。

## RENAMED Requirements

无。

## ADDED Requirements

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