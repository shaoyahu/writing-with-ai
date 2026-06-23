## Why

随手记目前是孤立的 — 看一条 note,看不到它跟其他 note 的关系,看不到谁引用了它。用户积累 N 条之后,信息虽然存住了但"连接"丢了,回想与再发现全靠手翻列表 + 标签筛。本 change 引入笔记关联能力:基于 wikilink、tag 重叠、内容相似度(本地)、LLM 抽取(可选)四个信号,建立轻量链接图。看 detail 时能看到"相关笔记"和"反向链接",不需要每次 query 都打 apikey。

## What Changes

- 新增 `note_links` 边表 + `notes_fts` FTS5 虚表(Room 升 version,AutoMigration)
- 新增 `core/note/` module:`NoteLinker` SPI、tag jaccard / FTS top-K / wikilink 解析 / LLM 抽取四个实现,组合后写入 `note_links`
- note 保存时 fan-out 一次算清该 note 的所有边;读路径纯本地 SQL,零 LLM
- 编辑器支持 `[[Note Title]]` wikilink 语法 + `[[` 自动补全
- 详情页新增"相关笔记"与"反向链接"两个 section
- P4(可选,默认关闭):设置页 toggle + 详情页按钮触发 LLM 抽取关联,写 `ai_history` 表(M2 已有),失败回退到纯本地,绝不阻塞保存
- 首次启动后台 backfill:为所有已有 note 计算初始边(WorkManager)

## Capabilities

### New Capabilities
- `note-association`:笔记间链接图的建立、查询、UI 呈现、wikilink 语法、可选 LLM 抽取

### Modified Capabilities
无。`quick-note` 数据模型零修改,`note_links` 是独立边表;`ai-gateway` / `ai-history` / `app-shell` 行为零修改,note-association 通过既有 SPI 复用,不引入新 spec-level 行为变化。

## Impact

- **代码**:`core/note/`(新 module)+ `core/data/db/`(2 个新 entity + 1 个新 DAO + AppDatabase 升 version)+ `feature/quicknote/detail|edit|settings` 改/加文件
- **Room schema**:version 2 → 3,AutoMigration;`notes` 表 0 改动,加 `note_links` + `notes_fts` 虚表;既有数据零丢失
- **依赖**:Room `@Fts5`(2.6+ 已就位)、WorkManager(若未引入则需加);P4 不新增依赖,复用 `core/ai/`
- **apikey / 成本**:P4 默认关,即使开也走 `ai-history` 记录,后台周期任务限频(每 note 每天 ≤ 1 次)
- **UI**:Material 3 既有 token,新增 1 个 section composable + 1 个 autocomplete popup
- **测试**:核心算法 100% 单测覆盖;集成测试覆盖 save→边全流程;P4 走 mock `AiGateway` 验 prompt/JSON/失败回退
- **不在范围**:图可视化(Android 复杂、留 v2)、本体分类、三层架构、Chrome 剪藏、多设备同步
