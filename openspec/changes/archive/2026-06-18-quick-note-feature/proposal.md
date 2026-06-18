## Why

M0 已经把工程脚手架(Gradle / Hilt / Compose / Navigation / Room / DataStore / ktlint + 测试框架)落地,接下来要进入 **M1 · 随手记闭环**:让用户能在手机上创建、编辑、搜索、删除笔记,并能在不依赖任何 AI 的情况下完整使用核心写作用例。这是用户最早能感知到的业务价值,也决定后续 M2 / M3 的实体形状(`Note` 字段是 AI 历史的关联主键,M2 直接复用)。

## What Changes

- 新增 **Note 实体与持久化层**:`Note` / `NoteTagCrossRef` Entity + `NoteDao`(Room,FTS/LIKE 搜索)+ `NoteRepository` + DataStore 兜底最近浏览位置(非敏感)
- 新增 **随手记 UI 闭环**(全部 Compose + Material 3):
  - **List**:时间倒序、固定到顶、标签筛选、搜索框、字数与更新时间、空状态
  - **Detail**:只读视图,展示字数 / 阅读时间 / 标签 / AI 元数据占位
  - **Editor**:新建 + 编辑(同一 Composable + ViewModel,通过 route 参数区分);保存 / 取消 / 删除
  - **Tag 选择**:内置 chip 选择器 + 自由输入新 tag(逗号或回车提交)
- 新增 **Nav 路由**:`quicknote/list` / `quicknote/detail/{id}` / `quicknote/edit?id={id}`(id 缺省即新建);由 `AppNav.kt` 接入空 NavHost
- 新增 **单条 Markdown 导出**:详情页 "分享" 入口 → ACTION_SEND 文本分享给系统其它应用
- 新增 **i18n**:`values/strings.xml`(中文)+ `values-en/strings.xml`(英文);新增条目走 `strings.xml`,**禁止**硬编码中文
- 新增 **测试**:
  - 单元:`NoteDaoTest`(in-memory Room)、`NoteRepositoryTest`、`QuickNoteViewModelTest`(含搜索 / pin / delete 状态机)
  - 仪器:Compose UI 跑通"新建 → 列表 → 详情 → 删除"闭环(可选,先在 `testDebugUnitTest` 把核心单测跑绿)
- **BREAKING**:无外部依赖,纯新增
- **不引入**:AI 抽象层(M2)、widget(M4)、导入/导出(M4)、首次启动同意页(M4)、自定义 tag 颜色(留 v2+)

## Capabilities

### New Capabilities
- `quick-note`:随手记完整闭环 — Note 数据模型(Room)+ Repository + 列表/详情/编辑 Compose 屏幕 + tag + pin + 搜索(LIKE)+ 单条 Markdown 分享导出 + i18n + 单测

### Modified Capabilities
无修改。`app-shell` 当前 spec 已声明 NavHost 为 "placeholder route, ready for subsequent changes to add real destinations"(见 `openspec/specs/app-shell/spec.md` §"AppNav defines an empty NavHost"),本 change 仅按其允许行为接入新目的地,不修改 spec 级要求。

## Impact

- **新增 package**:
  - `core/data/db/` — AppDatabase(M0 已建,但仅空 schema)+ NoteDao + NoteTagDao + AiHistoryDao(M0 占位)+ 相关 Entity
  - `core/data/repo/` — NoteRepository / TagRepository
  - `feature/quicknote/list/` — QuickNoteListScreen + ViewModel
  - `feature/quicknote/detail/` — QuickNoteDetailScreen + ViewModel
  - `feature/quicknote/edit/` — QuickNoteEditorScreen + ViewModel(与 detail 共享状态机类型)
  - `feature/quicknote/model/` — Note / Tag 领域模型(UI 层用,Room Entity 在 `core/data` 不外漏)
- **修改**:
  - `app/AppNav.kt` — 加 quicknote 三个目的地
  - `app/src/main/res/values/strings.xml` + `values-en/strings.xml` — 新增条目(quicknote 全部 UI 文案)
  - `app/build.gradle.kts` — 启用 Room schema export 目录 + ksp arguments;已在 M0 留接口
- **新增依赖**:无(M0 已把 Room / Compose / Hilt / Navigation / kotlinx-serialization 全配齐)
- **风险**:
  - Room schema 升级路径:M0 数据库为空,本 change 直接定义 v1 schema,不需要做迁移;之后变更必须走 `AutoMigration` 或 exportSchema 落版本
  - 单条 Markdown 导出走系统 `ACTION_SEND`,无文件读写权限问题
  - 搜索性能:笔记量预期 < 1k,LIKE 足够,无需 FTS(roadmap §5.2 拍板)
