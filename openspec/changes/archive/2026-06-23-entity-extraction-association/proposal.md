## Why

当前笔记关联是「无差别信号聚合」(wikilink / tag jaccard / content LIKE / LLM_EXTRACT),召回靠语义相似度,无法解释「为什么这条和那条有关」。用户看一条 note 时,想知道的是「原来我之前提过三国演义和小明」。本次完全重做关联层:用 LLM 抽取笔记中的关键实体(人名 / 书名 / 事件 / 地点 / 概念 …),建立实体级反向索引,新笔记按共享实体命中产生可解释的关联;LLM 语义相似度降级为「无共享实体时的兜底」。

## What Changes

- **新增** `note_entities(noteId, entityType, entityKey, surfaceForm, spanStart, spanEnd, lastExtractedAt)` 表:一篇笔记被抽取出的所有实体
- **新增** `entity_aliases(entityType, aliasKey, canonicalEntityKey)` 表:用户手动合并的别名(如「小明」/「小名」/「XiaoMing」 → `person::xiaoming`)
- **新增** `EntityExtractor` 抽象 + LLM 实现 + (后续可选) HanLP 本地实现
- **新增** `NoteEntityLinker`:按本笔记实体集合查共享实体的其他笔记,落 `LinkType.ENTITY_HIT` 边
- **新增** `EntityBackfillWorker`:升级首次启动后台回填历史笔记(可暂停 + 断点续跑 + 5s/条限频)
- **新增** 12 类实体枚举:PERSON / WORK / EVENT / LOCATION / ORG / CONCEPT / DATE / URL / QUOTE / PRODUCT / TASK / NUMBER
- **新增** `NoteAssociationPrompt.buildExtractEntities(title, content)`,双语 prompt(中/英),输出严格 JSON
- **保留** `LinkType.WIKILINK` / `TAG_OVERLAP` / `CONTENT_SIM` / `LLM_EXTRACT` 4 信号
- **调整** `CompositeNoteLinker.recomputeForNote` 流程:先写实体 → 实体命中 → 语义兜底(LLM 仅在共享实体 < 1 时跑)
- **新增** `LinkType.ENTITY_HIT`,`NoteLinkDao.getRelated` / `getBacklinks` SQL 聚合加此信号权重 1.50
- **新增** `NoteAssociationSettings` DataStore:实体抽取开关 / 关联阈值 slider / 实体别名管理入口
- **新增** 关联数量上限:**单笔记 100 条,ENTITY_HIT : LLM_EXTRACT ≈ 2 : 1(取实体命中 66 + 语义命中 34,不足按实际取,score desc 截断)**
- **关联阈值默认 0.25**(用户可调),`HAVING score > :threshold` 应用在 SQL 聚合上
- **新增** 详情页「关联笔记」section:命中项 hover 显示 `sharedEntities:["三国演义","小明"]`
- **新增** 列表 / 编辑器无变化(关联在详情页展示,不影响主流程)
- **BREAKING**:旧 `LlmNoteLinkExtractor` 重命名为 `SemanticNoteLinker`,触发条件由「每篇都跑」改为「仅当共享实体 < 1」

## Capabilities

### New Capabilities

- `note-entity-extraction`:实体抽取、实体存储、实体命中关联、别名合并、双语 prompt、回填工作流
- `note-entity-link`:实体命中边的写入与读取(`LinkType.ENTITY_HIT` + 100 条 2:1 上限 + 阈值过滤)

### Modified Capabilities

无。`quick-note` / `ai-gateway` / `ai-history` / `secure-prefs` 数据模型零修改;`note-association`(旧 change,未归档为 stable spec)的语义将由 `note-entity-link` 替代。`onboarding-consent` 的 apikey 提示部分由独立 change `onboarding-apikey-prompt` 覆盖。

## Impact

- **代码**:`core/note/extractor/`(新包:`EntityExtractor` / `LlmEntityExtractor` / `EntityType`)+ `core/note/impl/EntityBacklinker.kt` + `core/note/impl/SemanticNoteLinker.kt`(原 `LlmNoteLinkExtractor` 改名)+ `core/data/db/` 增 2 entity + 2 DAO + AppDatabase 升 version + AutoMigration + `core/ai/prompt/NoteAssociationPrompt.kt` 增 `buildExtractEntities()` + `core/prefs/NoteAssociationSettingsStore.kt` + `feature/quicknote/detail/` 增关联 section + 设置页 1 个 section
- **Room schema**:`version 3 → 4`,AutoMigration 2 → 3 升 1 档;`notes` / `note_links` 表 0 改动;**新增** `note_entities` + `entity_aliases` 2 表;既有数据零丢失(`LLM_EXTRACT` 历史行保留,score 不再计入)
- **依赖**:无新增三方库;`note_entities` 全文匹配走 `LIKE`(v1 roadmap §5.2 拍板,不上 FTS)
- **apikey / 成本**:实体抽取走现有 `AiGateway.streamWritingOp(op=EXPAND, systemPrompt=...)`;单笔记 ~300-600 input tokens;沿用 `RATE_LIMIT_MS = 24h` 限频;语义兜底 LLM 调用沿用现有限频
- **UI**:Material 3 既有 token;新增 1 个 section composable(关联笔记) + 1 个设置页 section(实体抽取 toggle + 阈值 slider + 别名管理入口);**不**改 list / editor
- **测试**:`LlmEntityExtractor` 100% 单测覆盖(双语 prompt / JSON 解析 / 失败回退);`NoteEntityLinker` 单测覆盖命中 / 上限 / 阈值 / 2:1 比例;`EntityBackfillWorker` 单测覆盖断点续跑;集成测试 save → 实体抽取 → 命中建边
- **不在范围**:本地 NER 模型(HanLP) — 留 v2;实体可视化图谱 — 留 v2;实体本体分类(同义词词典 / ConceptNet) — 留 v2