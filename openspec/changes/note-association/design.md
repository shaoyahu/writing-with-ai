## Context

随手记 v1(M1 落地)只做单条 note 的增删改查、tag、列表。信息存得住但"连接"丢:用户积累 N 条之后,回想/再发现靠手翻列表或 tag 筛,效率随 N 指数下降。已有基础设施:

- `core/data/db/entity/NoteEntity`(id/title/content/timestamps/isPinned/lastAiOp)
- `NoteTagCrossRef` + `NoteTagDao`(note ↔ tag 多对多)
- `core/ai/` 抽象层(M2 落地,Anthropic-compatible + 三个 provider),apikey 走 `core/prefs/SecureApiKeyStore`(EncryptedSharedPreferences)
- `core/data/db/entity/AiHistoryEntity`(M2 用于成本/次数可观测)
- `app/build.gradle.kts` 用 Room 2.6+ 版本,`@Fts5` 可用

借鉴 Karpathy《LLM Wiki》gist 的核心原则:**编一次、增量维护**,view 路径不重算,写路径 fan-out 把边算清楚。借鉴 llm_wiki 的 4 信号打分思路(因 GPL-3.0 不 fork,只取算法思想)。本 design 是技术决策归档,具体 API/字段走 `specs/note-association/spec.md`。

## Goals / Non-Goals

**Goals:**
- 详情页能看"相关笔记"和"反向链接",零 LLM、零延迟、可离线
- 边表支持多信号融合,每种信号独立行,读路径 SQL 聚合
- wikilink `[[Note Title]]` 显式语法 + 编辑器 `[[` 自动补全
- LLM 抽取默认关、opt-in,失败回退到纯本地,绝不阻塞保存
- 既有 `notes` 表 schema 零修改,AutoMigration 升 Room version
- 测试覆盖核心算法 100%

**Non-Goals:**
- 图可视化(Android 复杂,留 v2)
- 本体/分类体系(过重,初版不要)
- 三层架构 raw/wiki/schema(用 note + note_links 两层)
- Chrome 剪藏 / 多格式导入
- 多设备同步 / 冲突解决
- 修改 `quick-note` / `ai-gateway` / `ai-history` 等既有 spec-level 行为

## Decisions

### D1. 边表独立于 note 表,信号分行

**决策**:`note_links` 是独立表,每种信号类型(WIKILINK/TAG_OVERLAP/CONTENT_SIM/LLM_EXTRACT)单独成行,复合主键 `(srcNoteId, dstNoteId, linkType)`。

**为什么**:
- 各信号语义不同(显式 / 统计 / 文本 / 模型),weight 公式各异,不能混存
- 单一信号可独立增删、独立调试、独立回滚
- 读路径一次 SQL 聚合即可,无需 join 多表

**备选**:
- 单行存所有信号(weight 拆列):否。列固定 4 个不可扩展,新增信号要 schema 迁移
- JSON blob 存所有信号:否。失去 SQL 聚合能力,得在应用层算

### D2. FTS5 虚表走 Room `@Fts5(contentEntity = NoteEntity::class)`

**决策**:用 Room 原生 `@Fts5` 注解建虚表,自动跟 `notes` 同步(contentEntity 模式),不带外部内容(content-less table)。

**为什么**:
- Room 2.6+ 原生支持,集成度最高
- 自动 mirror `notes.content` / `notes.title`,CASCADE 跟 `notes` 走
- FTS5 比 FTS4 排序准(tokenizer 灵活)

**备选**:
- 手写 SupportSQLiteOpenHelper 建 FTS5:控制力强但维护成本高
- 走 LanceDB 之类外部向量库:Android 上复杂、过重

### D3. 写路径 fan-out 在保存时同步触发(应用 scope IO 协程,debounce 500ms)

**决策**:`NoteRepository.save()` 末尾触发 `noteLinker.recomputeForNote(noteId)`,在 `applicationScope` IO 协程跑;同一 note 500ms 内的连续 save 通过 `Flow.debounce` 合并一次。

**为什么**:
- Karpathy 原则:编一次,不要每次读都重算
- IO 协程不阻塞 UI(预算 <200ms for 1k notes)
- debounce 500ms 防用户连续键入导致的重复计算

**备选**:
- WorkManager OneTimeWorkRequest:可靠但延迟高(秒级),体感差
- 立即同步:UI 卡顿,不可接受

### D4. 读路径纯本地 SQL,聚合在 DB 层

**决策**:`LinkReadDao.getRelated(noteId, limit)` 一次 SQL 完成多信号聚合 + 排序 + 阈值过滤,见 `specs/note-association/spec.md` §"Read Query"。

**为什么**:
- 应用层聚合要拉所有行到内存,N 大时不可接受
- SQL `MAX(CASE WHEN linkType=...)` + `GROUP BY` 是标准模式,Room 透明支持
- `HAVING score > 0.10` 在 DB 层去噪声,减少应用层处理

### D5. wikilink 解析用正则,不引外部 Markdown 库

**决策**:`\[\[([^\[\]\n]+?)\]\]` 单行非贪婪匹配,不嵌套、不跨行。

**为什么**:
- v1 wikilink 用法简单,正则足够
- 现有 markdown 渲染链路无依赖(目前是 raw markdown 字符串)
- 避免引入 `commonmark-java` 之类的完整 Markdown 解析器(APK 体积 + 维护成本)

**备选**:
- 引 commonmark-java + 自定义 inline parser:灵活但过重,留 v2

### D6. wikilink 失败(标题不存在)走 dangling link,Markdown 原文保留

**决策**:不存 `pending_links` 表。Markdown 里的 `[[未找到的标题]]` 原文保留,read 时 resolve 失败就在 UI 提示"未找到"+ "创建新笔记"按钮。

**为什么**:
- 简单稳,无新表无新 schema
- 标题改名/删 note 不需要清理
- 跟 GitHub / Obsidian 行为对齐,用户认知成本低

### D7. LLM 抽取走现有 `AiGateway`,不引入新抽象

**决策**:`LlmNoteLinkExtractor` 调 `AiGateway.chat(prompt, schema)`,prompt 模板放 `core/ai/prompt/note_association_prompt.kt`。不新建 provider、不改 gateway 行为。

**为什么**:
- `core/ai/` 是项目 AI 集成硬约束的唯一入口
- 既有 provider 配置 / apikey 加密 / 流式 / 错误降级全部复用
- 不破坏 `ai-gateway` spec-level 行为

### D8. LLM 抽取默认关,opt-in 双触发(用户按钮 + 后台周期)

**决策**:
- 设置页 toggle:"保存时使用 AI 找关联"(默认关)
- 详情页按钮:"用 AI 找关联"(单条手动,随时)
- WorkManager 周期任务:仅在 Wi-Fi + 充电 + apikey 已配 + 限频(每 note 每天 ≤ 1 次)时跑

**为什么**:
- apikey 成本可控(用户主动开关)
- 网络/电量感知,后台不偷流量
- 限频避免反复抽同一 note

### D9. 失败回退:LLM 抽取出错 → 静默,纯本地结果保留

**决策**:`LlmNoteLinkExtractor.extract()` 抛任何异常都被 `CompositeNoteLinker` catch,记 `ai_history`(status=FAIL),**不**写 `note_links` LLM_EXTRACT 行,**不**影响 P2 纯本地结果。

**为什么**:
- AI 集成硬约束:错误降级,绝不允许阻塞核心流程
- 写路径 fan-out 整体在 IO 协程,用户已离开编辑器,失败也无所谓

### D10. 包结构:`core/note/` 独立 module,`feature/` 仅消费

**决策**:
- `core/note/` 含 SPI、所有实现、parser、config、di
- `feature/quicknote/` 只通过 `NoteLinker` 接口拿数据,不直接 import 内部
- `core/data/db/` 加 2 个 entity + 1 个 DAO

**为什么**:
- CLAUDE.md 硬约束:跨 feature 复用代码放 `core/`,feature 不互相 import
- `core/note/` 跟 `core/ai/` / `core/data/` 同层,都是基础设施

## Risks / Trade-offs

- **FTS5 + Room AutoMigration 坑** → 升级前在真机测 migration;若失败,fallback 手写 SupportSQLiteOpenHelper 建虚表
- **tag jaccard 一次拉 N 行 tags 内存爆** → N>5k 时分批(代码留 hook,初版不实现);1k 内 <10MB
- **LLM 抽取 apikey 成本失控** → 默认关 + 限频(每 note 每天 ≤ 1 次) + `ai_history` 写记录 + 设置页展示最近 30 天成本
- **wikilink 标题重名** → resolve 时按 `updatedAt DESC` 取最新一条;UI 提示"多个匹配"待 v2 多候选选择
- **改 note 标题致 wikilink 失效** → 失效即 dangling,UI 提示 + 跳转创建新笔记;跟 GitHub 行为对齐
- **WorkManager backfill 大库耗时** → 首次启动延后 5s 触发,分批每 50 条 commit 一次,显示进度通知
- **FTS5 tokenizer `unicode61 remove_diacritics 2` 对中文支持有限** → 中文走 unigram 分词(FTS5 词长默认 1)够用;精确中文分词留 v2

## Migration Plan

- Room version 2 → 3,`@AutoMigration(2 → 3)`,加 `note_links` + `notes_fts` 虚表
- 既有 `notes` / `tags` / `note_tag_cross_ref` / `ai_history` 0 改动
- 旧 note 进入应用 → `noteLinker.recomputeAll()` 在 WorkManager 后台跑,首次启动延后 5s
- backfill 进度可观察:WorkManager 通知 + 设置页"关联计算中 N/M"
- 回滚:删除 `note_links` / `notes_fts`,降 Room version,UI 隐藏相关 section(`if (noteLinkerReady) RelatedSection else Empty`)

## Open Questions

- **`CoreAiGateway` 在 P4 的依赖注入边界**:`LlmNoteLinkExtractor` 走 `AiGateway` interface 还是 `CoreAiGateway` 具体类?目前两个并存(看 `core/ai/` 目录),需要 apply 阶段确认
- **wikilink 解析跨段落支持**:v1 限定单行非贪婪,`[[多行\n内容]]` 不解析。可接受
- **链接面板的 UI 形态**:LazyRow chip(初版)还是展开 list?初版 LazyRow,空态 + 创建按钮
- **"反向链接"是否包含 wikilink 自动建边的 src notes**:包含,跟 WIKILINK 边走同一表
