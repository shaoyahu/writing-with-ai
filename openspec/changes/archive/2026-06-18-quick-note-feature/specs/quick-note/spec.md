# quick-note

## Purpose

随手记(M1)的完整数据模型与 UI 行为契约;定义 `Note` / `Tag` 实体形状、CRUD / 搜索 / 标签 / 固定 / 单条分享导出的端到端行为,以及 Nav 路由契约。本 spec 是 M2 AI 抽象层(`AiHistory` 关联 `Note.id` 与 `Note.lastAiOp`)的前置。

TBD — synced from OpenSpec change `quick-note-feature`(2026-06-18)。原 change 在 `openspec/changes/quick-note-feature/`。

## ADDED Requirements

### Requirement: Note entity schema

系统 MUST 在 Room `notes` 表中持久化 `Note` 实体,且字段集合为下表所列;`Note.id` 是 UUID 字符串主键,`title` 在用户未填时由正文前 30 字派生(UI 层处理,不强制落表);`content` 是 Markdown 源码;`isPinned` 标记固定;`lastAiOp` 与 `lastAiAt` 是为 M2 AI 抽象层预留的元数据占位字段,M1 不写入但允许落表。

#### Scenario: 必填字段持久化
- **WHEN** 用户保存一条新笔记,`title = "晨跑计划"`,`content = "# 晨跑\n- 6:30 起床"`,`isPinned = false`
- **THEN** Room 写入一行 `notes(id=<UUID>, title="晨跑计划", content="# 晨跑\n- 6:30 起床", createdAt=<now>, updatedAt=<now>, isPinned=false, lastAiOp=null, lastAiAt=null)`

#### Scenario: 更新触发 updatedAt
- **WHEN** 用户编辑同一笔记并保存,`content` 变更,`isPinned` 不变
- **THEN** Room 更新该行 `content` 与 `updatedAt=<newNow>`,`createdAt` 保持原值

#### Scenario: lastAiOp 字段保留 null 占位
- **WHEN** 用户新建笔记(M1 阶段,无 AI 操作)
- **THEN** `lastAiOp=null` 且 `lastAiAt=null`;字段存在以便 M2 写入

### Requirement: Note CRUD via Repository

系统 MUST 提供 `NoteRepository`,封装 `NoteDao`,暴露 upsert / getById / delete / observeAll / observeByTag / search / setPinned 接口;调用方(列表 / 详情 / 编辑 ViewModel)只通过 Repository 访问数据,不直接持有 `NoteDao`。

#### Scenario: 新建笔记通过 upsert
- **WHEN** `editor` ViewModel 调用 `repo.upsert(Note(id=<新 UUID>, title=..., content=...))`
- **THEN** 数据库新增一行,且返回的 `id` 与传入一致

#### Scenario: 删除笔记
- **WHEN** `detail` ViewModel 调用 `repo.delete(noteId)`
- **THEN** 该行从 `notes` 表移除,且 `note_tags` 表中所有 `noteId = <id>` 的交叉引用也被级联删除

#### Scenario: 编辑页读取既有笔记
- **WHEN** 用户进入 `quicknote/edit?id=<existingId>`,`editor` ViewModel 初始化
- **THEN** ViewModel 调用 `repo.getById(existingId)` 取得 `Note`,并把 `title` / `content` 预填到输入框

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

### Requirement: List ordering pinned-first and newest-first

系统 MUST 让列表(无搜索 / 无 tag 筛选时)以 `isPinned DESC, updatedAt DESC` 顺序返回;被固定的笔记排在前部,同组内按更新时间倒序。

#### Scenario: 固定笔记置顶
- **WHEN** 数据库中有 A(updatedAt=t1, pinned=false)、B(updatedAt=t2<t1, pinned=true)、C(updatedAt=t3<t2, pinned=false)
- **THEN** 列表顺序为 [B, A, C]

#### Scenario: 同 pinned 组按 updatedAt 倒序
- **WHEN** 数据库中两条笔记都是 `pinned=true`,`updatedAt` 不同
- **THEN** 较新的(updatedAt 较大)在前

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
