## Context

`core/note/` 当前已有 `CompositeNoteLinker`(`LocalNoteLinker` + `WikilinkIndexer` + `LlmNoteLinkExtractor` 三路扇出写 `note_links`)。`LlmNoteLinkExtractor` 每次都是「把 source + 20 条 candidate 全 prompt 给 LLM 一次性判相似」,结果存 `LinkType.LLM_EXTRACT`。召回无解释、token 重、需 provider 在线。

本 change 完全重做关联信号:用实体抽取替代语义判相似;LLM 仅在「无共享实体」时做兜底。

约束(roadmap §0 / §4 / CLAUDE.md):
- 纯本地数据;provider 三家(deepseek / minimax / mimo)+ 自定义 Anthropic 兼容
- AI 调用统一经 `AiGateway.streamWritingOp(op, sourceText, ...)`;不新增 op 枚举,复用 `EXPAND` + 自定义 `systemPrompt`
- 24h/篇 限频(沿用 `RATE_LIMIT_MS`)
- 包大小 ≤ 20MB,不引入 HanLP 等大依赖
- Room 版本号升级走 AutoMigration
- 中文 + 英文双语,UI 走 `strings.xml` + `values-en/`

## Goals / Non-Goals

**Goals:**
- 实体抽取 → `note_entities` 表 → 共享实体命中建 `LinkType.ENTITY_HIT` 边
- 详情页能看到「我之前提过 X,关联到 2024-03 那条」
- 语义相似度仅作兜底,token 消耗降一个量级
- 双语 prompt(中文用户优先 + 英文用户 fallback)
- 历史回填可控(可暂停 / 断点续 / 进度条)

**Non-Goals:**
- 本地 NER 模型(HanLP / LAC)— 留 v2
- 实体图谱可视化 — 留 v2
- 实体本体分类 / 同义词词典(ConceptNet / HowNet)— 留 v2
- 实体类型自定义 — 12 类硬编码
- 自动合并同名异形(只能手动合并) — v1 体验够用

## Decisions

### D1 · 实体表设计

```kotlin
@Entity(
    tableName = "note_entities",
    primaryKeys = ["noteId", "entityKey"],
    indices = [Index("entityKey"), Index("entityType"), Index("noteId")],
    foreignKeys = [ForeignKey(
        entity = NoteEntity::class,
        parentColumns = ["id"],
        childColumns = ["noteId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class NoteEntityRow(
    val noteId: String,
    val entityType: EntityType,    // 枚举 12 类
    val entityKey: String,         // 规范化 key,如 "person::xiaoming"
    val surfaceForm: String,       // 原文形式,如 "小明"
    val spanStart: Int,            // 在 content 中的字节偏移(预留,UI 高亮用)
    val spanEnd: Int,
    val lastExtractedAt: Long
)
```

**Why key+surface 双字段**:key 用于命中聚合,surface 用于 UI 展示「三国演义」而不是 `sanguoyanyi`。复合主键 `(noteId, entityKey)` 防同 note 内重复抽取。

### D2 · 抽取时机与限频

- **同步触发**:note 保存后 5s debounce → `EntityExtractor.extractAndPersist(noteId)`
- **限频**:`lastExtractedAt` 距今 < 24h 跳过(沿用 `LlmNoteLinkExtractor.RATE_LIMIT_MS`)
- **离线 / 无 provider**:静默跳过,本地信号(WIKILINK / TAG_OVERLAP / CONTENT_SIM)照常写
- **失败 fallback**:LLM 返回非 JSON / 超时 → 留旧实体不动,记录 toast「实体抽取失败,稍后重试」

### D3 · 关联边上限 100 条,2:1 比例算法

```kotlin
// NoteEntityLinker.compute(noteId)
val entityHits = queryEntityHits(noteId).take(66)  // SQL: shared entity, score desc
val semanticHits = if (entityHits.size < 66 || /* 共享实体 < 1 */) {
    semanticLinker.extractAndPersist(noteId).take(34)
} else emptyList()
return (entityHits + semanticHits).take(100)
```

**为什么不严格 2:1**:实体命中可能不足 66(老笔记 / 无实体),按实际取满后再补语义。「不足按实际取」写进 spec 的 Scenario。

### D4 · 关联阈值 0.25

`HAVING score > :threshold`,`score` = ENTITY_HIT×1.50 + WIKILINK×1.00 + TAG_OVERLAP×1.50×jaccard + CONTENT_SIM×1.00×weight + LLM_EXTRACT×0.80×confidence。

阈值从 DataStore `NoteAssociationSettingsStore.threshold` 读;`getRelated` SQL 改成 `HAVING score > :threshold`,`getBacklinks` 同。

### D5 · 双语 prompt

`NoteAssociationPrompt.buildExtractEntities(title, content, locale)`:
- `locale == "zh"`:system 中文,user 中文
- `else`:system 英文,user 原文(中文笔记走英文 system + 中文 user,模型支持)

输出严格 JSON:
```json
{"entities":[
  {"type":"WORK","key":"work::sanguoyanyi","surface":"《三国演义》"},
  {"type":"PERSON","key":"person::xiaoming","surface":"小明"}
]}
```

**key 规则写进 prompt**:`person::<lowercase_no_space>` / `work::<pinyin_or_english_slug>` 等。LLM 不一定严格遵守,后处理做 `lowercase + replace space/dash to _`。

### D6 · 别名合并

`entity_aliases(entityType, aliasKey, canonicalEntityKey)` 表,UI「设置 → 笔记关联 → 别名合并」入口:列同类型所有 alias,用户勾选合并 → 写一行 `("person::xiaoming_alias", "person::xiaoming")`。

命中时先 union:`SELECT DISTINCT noteId FROM note_entities WHERE entityKey IN (canonical + aliases)`。

### D7 · 历史回填

`EntityBackfillWorker`(CoroutineWorker):
- 触发:Room 升级到 v4 完成后一次性 enqueue
- 节奏:串行处理,每条 `delay(5s)`,失败重试 3 次
- 进度:`WorkInfo.progress` 报 `{processed, total, currentNoteId}`
- 暂停:用户从设置页「取消回填」→ `WorkManager.cancelAllByTag("entity_backfill")`
- 续跑:从 `SELECT id FROM notes WHERE lastExtractedAt IS NULL OR lastExtractedAt < 0` 开始,断点由 `lastExtractedAt` 自然记录
- 注意:`NoteEntityRow` 表的 `lastExtractedAt` 列就是这个断点;**不**复用 `Note.lastAiAt`(那个语义不同)

### D8 · LinkType.ENTITY_HIT 加入 SQL 聚合

`NoteLinkDao.getRelated` SQL 在 `note-association` change 已有结构上加一档:

```sql
(1.50 * COALESCE(MAX(CASE WHEN nl.linkType = 'ENTITY_HIT' THEN nl.weight ELSE 0 END), 0)) + ...
```

`evidence` 字段存 JSON `{"sharedEntities":["三国演义","小明"]}`(便于 UI hover 显示)。

### D9 · UI

- 详情页底部新增「关联笔记」section,标题旁显示总数
- 每条关联显示 title + preview + score,hover(长按)弹 bubble 显示 `sharedEntities`
- 「关联阈值」slider 在设置页;0.10 ~ 1.00,步进 0.05,默认 0.25
- 「实体抽取」toggle 默认开
- 「别名管理」入口在设置页,跳到列表页(后续 v2 可做)

## Risks / Trade-offs

| Risk | Mitigation |
| --- | --- |
| 实体歧义(三国演义 = 书 vs 电视剧) | entityType 区分 + 别名合并 UI |
| 12 类枚举不够用 | v1 硬编码,保留 v2 自定义扩展点 |
| LLM 返回 key 不规范 | 后处理正则 `lowercase + [^a-z0-9_] → _`;失败行丢弃 |
| LLM JSON 截断 | 已沿用 `parseResponse` 容错:截断 / 非 JSON 返回空链接 |
| 历史回填 token 成本 | 5s/条 + 进度条 + 可暂停 + 设置页预估「~X token」 |
| 旧 `LLM_EXTRACT` 数据迁移 | 保留旧行不删,但 score 权重 0(不显示,后台兼容) |
| 双语 prompt 中文 user 笔记 + 英文 system | 测过 4 家模型均支持,fallback prompt 写在 `NoteAssociationPrompt` 常量 |
| 升级前已有 LLM_EXTRACT 数据,但旧 notes 还没实体抽取 | 回填 worker 跑一次,顺手把没实体的笔记也写实体 |
| 单笔记 100 条 cap 在极端情况下会丢高分关联 | score 排序后取,丢的是低分;UI 显示「还有 N 条被隐藏」可点开 |
| `lastExtractedAt` 复用 Note.lastAiAt 列语义混淆 | 列名不变,注释加 KDoc 说明同时记录 AI 操作和实体抽取 |

## Migration Plan

1. Room 升 `version 3 → 4`,AutoMigration 升 1 档
2. `note_entities` + `entity_aliases` 表新增
3. 旧 `LLM_EXTRACT` 行保留,score 权重 0(默认不显示,后台兼容)
4. 升级首次启动 → `EntityBackfillWorker` enqueue,设置页进度条可见
5. **回滚策略**:AutoMigration 失败 → 旧 APK 装回 → 数据保留(只是新表空)
6. **不删旧表**:旧数据 0 改动,新表 0 依赖,降级平滑

## Open Questions

- 实体高亮 UI(spanStart/spanEnd 用 markdown 渲染层加颜色)— 留 v2,本 change 只存不渲染
- 实体抽取走哪个 provider 默认? — 沿用 `apikeyStore.observeConfiguredProviders().first()` 取第一个,失败 fallback 跳过
- 设置页是否要给「立即重跑回填」按钮? — 是,放设置页「实体抽取」section 底部