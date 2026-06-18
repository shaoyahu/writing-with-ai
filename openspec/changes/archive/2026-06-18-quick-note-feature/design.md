## Context

M0 已经落地:Hilt + Compose + Navigation + Room + DataStore + ktlint + JUnit5 + MockK + Turbine 全部跑通;`WritingApp` / `MainActivity` / `AppNav` 占位 NavHost 已就绪(`AppNav.kt` 仅一个 greeting 目的地);`AppDatabase` 类存在但 schema 为空(`@Database(entities = [], version = 1)`),Hilt 模块留了 `DatabaseModule` 占位。

需求进入 M1 — 让用户在不依赖任何 AI 的情况下完整使用随手记(创建 / 编辑 / 删除 / 搜索 / 标签 / 固定 / 详情 / 单条导出)。本 change 是 M2 AI 抽象层的前置:`Note` 实体的字段(`id` / `createdAt` / `updatedAt` / `lastAiOp` 等)会被 M2 `AiHistory` 引用,字段定形后尽量不动。

roadmap 拍板项(2026-06-18):
- 笔记量预期 < 1k,**搜索走 LIKE**,不上 FTS / 中文分词(§5.2)
- 单 Activity + NavHost + 类型安全路由(§7)
- apikey 暂不涉及(M2),不需 EncryptedSharedPreferences
- 单条 Markdown 导出走系统 `ACTION_SEND`,**不**写文件、不需权限(§5.3)

## Goals / Non-Goals

**Goals:**
- 在手机上完成随手记的完整 CRUD 闭环(不依赖 AI)
- `Note` / `Tag` 数据层字段定形,给 M2 `AiHistory` 留好关联主键
- 列表 / 详情 / 编辑三个屏幕 i18n 完整(中文 + 英文),UI 文案全部走 `strings.xml`
- 核心状态机单测覆盖(in-memory Room + Turbine);Compose UI 单测可选,先把核心单测跑绿

**Non-Goals:**
- AI 任何动作(扩写 / 润色 / 整理)— M2 / M3
- 桌面 widget — M4
- 整库 JSON / Markdown zip 导入导出 — M4(本次只做"单条分享")
- 首次启动同意页 — M4
- 笔记富文本所见即所得(用纯文本 / Markdown 源码展示,渲染留给 M3+)
- 图片 / 语音 / 手写输入(v2+)
- 多账号 / 协作 / 云同步
- 自定义 tag 颜色 / emoji
- 中文分词搜索 / FTS

## Decisions

### 1. 单一 capability:`quick-note`(数据 + UI 一起)
**Why**:M1 是一个完整的"用户可感知功能",数据层和 UI 层有强耦合(Room Entity ↔ UI State ↔ Repository),拆 `note-data-model` + `quick-note-ui` 两个 spec 会让 sync / review 都做两次且容易漂移。M0 的 5 个 spec 都是基础设施维度,本 change 业务属性更强,合并更合适。

### 2. 搜索走 LIKE,不上 FTS
**Why**:roadmap §5.2 拍板;笔记量 < 1k,`Note.content LIKE '%query%'` + `Note.title LIKE '%query%'` 完全够用;Room FTS4 中文分词是 unicode61,效果差且需要额外迁移。
**替代方案**:FTS4 `MATCH` — 复杂度高,效果差,放弃。

### 3. 单 Activity + 类型安全 Compose Navigation
**Why**:M0 `app-shell` spec 已固化;`navigation-compose` 2.8+ 支持 `@Serializable` 路由,`AppNav` 加三个目的地即可。
**替代方案**:多 Activity / Fragment — 单人项目无必要,且和 Compose Material 3 集成差。

### 4. 详情页 Markdown 渲染用 Markwon 还是只展示纯文本
**Why**:本期 M1 详情页只展示纯文本 / Markdown 源码(用户保存什么显示什么,不实时渲染);Markwon 在 M3 `ai-writing-actions` 才需要(展示 AI 整理的 Markdown 结果)。**降低 M1 范围,加快闭环**。
**替代方案**:M1 就集成 Markwon + 实时预览 — 增加依赖调试,价值不大,推迟。

### 5. 单条导出走系统 `ACTION_SEND`
**Why**:用户单条分享给其它 App(笔记类工具 / IM),`Intent(Intent.ACTION_SEND).setType("text/markdown").putExtra(EXTRA_TEXT, md)` 一行,无需 FileProvider / 写文件权限 / manifest 变更。roadmap §5.3 拍板。
**替代方案**:FileProvider + 真存到 Download — 留 M4 `data-export-import`,本 M1 不做。

### 6. Tag 输入:Chip 选择器 + 自由输入(逗号 / 回车提交)
**Why**:轻量、Material 3 现有组件可直接组合(`FilterChip` / `InputChip` / `AssistChip`);不需要复杂的 tag 管理 UI。
**替代方案**:全屏 Tag 管理页 — 单条笔记 tag 数量有限(预期 < 5),过度设计。

### 7. Pin(固定)用 `Note.isPinned: Boolean` + Repository 层 `ORDER BY isPinned DESC, updatedAt DESC`
**Why**:单字段布尔即可,UI 列表用 pinned 排前 + 时间倒序;不动用单独的 `pinnedAt` 时间戳(避免增加无价值字段)。
**替代方案**:拆 `NotePinCrossRef` 表 — 笔记和 pin 是一对一关系,过度设计。

### 8. 状态机:`StateFlow<QuickNoteListUiState>`(sealed),数据层用 `Flow<List<Note>>` 直通
**Why**:ViewModel 只做"搜索词 / 当前 tag 过滤 / loading flag"的轻量状态;真正的列表数据从 Repository `Flow` 出来直接 collectAsState — 避免数据双写 / 同步问题。
**替代方案**:ViewModel 缓存 `List<Note>` — 数据陈旧风险,且 Room 已经会推送变更。

### 9. 数据层 schema export 路径固定
**Why**:Room `exportSchema = true` 必须指定 schema 目录,放 `app/schemas/`(git tracked),KSP 通过 `room.schemaLocation` arg 传路径。M0 build.gradle.kts 留了 Room 依赖但没开 schema export,本期打开。
**替代方案**:不 export schema — 后续 schema 变更无法做 AutoMigration,违反 roadmap 风险项。

### 10. Hilt 模块:`DatabaseModule` 提供 `AppDatabase` / `NoteDao` / `NoteTagDao`;`RepositoryModule` 提供 `NoteRepository`(单例)
**Why**:标准 Hilt 模式;`@Singleton` 限定,避免多实例。
**替代方案**:`@Provides` 直接散在 `WritingApp` — 不符合既有约定。

### 11. 测试数据构造用 fixture 函数,不放 `src/main`
**Why**:单测需要构造 `Note(... long)` 测试数据;放 `src/test/java/.../fixtures/`,`internal` 可见性,生产代码不可见。
**替代方案**:data class 默认值 — 影响生产代码整洁。

## Risks / Trade-offs

- **[Risk] 字段定形后改 schema 成本高** → M1 把 `Note` 字段(M2 会用到的 `id` / `createdAt` / `updatedAt` / `lastAiOp` / `lastAiAt`)全部定齐;`AiHistory` 字段参考 roadmap §5.1,本 change 不引入 AiHistory 表(M2 才加),只留好外键语义。Schema 一旦发版,后续变更走 `AutoMigration`。
- **[Risk] LIKE 搜索在 > 5k 笔记时变慢** → 单人项目,笔记量预期 < 1k;过 1k 时再评估 FTS / 中文分词(roadmap §5.2)。M5 polish 时跑性能基线(roadmap §11)。
- **[Risk] StateFlow 在 Compose recomposition 频繁时丢帧** → Room `Flow` 已经做 `distinctUntilChanged`;ViewModel 用 `stateIn(SharingStarted.WhileSubscribed(5000), initialValue = Loading)` 避免重复订阅。
- **[Risk] Hilt 在 instrumented test 上慢启动** → 单测不用 Hilt,直接 `Room.inMemoryDatabaseBuilder`;仪器测试才上 Hilt。
- **[Risk] i18n 双语同步漏翻** → 写中文为权威源,英文先放占位 `<string name="en_placeholder">TODO(en): ...</string>`,build 不报错(运行时显示 TODO);M5 polish 阶段统一补齐英文。
- **[Risk] 列表 / 详情 / 编辑三个屏幕 state 同步** → 详情页只读,通过 `collectAsState` 监听 Repository `Flow`,删除 / 编辑后 Repository 推送新数据,详情自动更新;无需手动事件总线。

## Migration Plan

无(数据库首次创建,无既有数据)。`AppDatabase` 从"空 entities"升级到"包含 Note / NoteTagCrossRef",version 仍为 1(因为还没发版),后续变更走 `AutoMigration` 或 `Migration` 对象 + 版本递增。

回滚策略:本 change 还没发版,git revert 即可,不需要额外回滚预案。

## Open Questions

- **Edit 页保存后是否回到详情 / 列表?** 倾向:保存后回到详情(已存在则更新;新建则跳到详情)。新建取消:不保存 + 返回列表。
- **列表为空时是否引导新建?** 倾向:是(显示大按钮"新建第一条笔记")。
- **搜索时是否包含 tag 匹配?** 倾向:否 — 搜索框只搜 title + content;tag 筛选走单独的 chip 行(更明确)。
- **删除是软删除还是硬删除?** 倾向:硬删除(roadmap §3.1 未要求回收站;v1 简化)。M5 polish 再考虑 soft delete。
