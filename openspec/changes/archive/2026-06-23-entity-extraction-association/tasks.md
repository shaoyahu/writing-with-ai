## 1. 数据层 — Room 实体与 DAO

- [x] 1.1 新增 `EntityType` 枚举(12 类:PERSON / WORK / EVENT / LOCATION / ORG / CONCEPT / DATE / URL / QUOTE / PRODUCT / TASK / NUMBER)
- [x] 1.2 新增 `NoteEntityRow` entity(`note_entities` 表，复合主键 `(noteId, entityKey)`,FK CASCADE 删 note)
- [x] 1.3 新增 `EntityAliasRow` entity(`entity_aliases` 表，主键 `(entityType, aliasKey)`)
- [x] 1.4 新增 `NoteEntityDao`:upsertAll / getByNoteId / deleteByNoteId / querySharedEntityHits(srcNoteId, limit) / queryAllEntityKeys(limit, offset)
- [x] 1.5 新增 `EntityAliasDao`:upsert / deleteByAlias / findByAliasKeys(List<String>)
- [x] 1.6 Room version `3 → 4` → version 5(`note_entities` + `entity_aliases` 入库，`@AutoMigration(4,5)`)
- [x] 1.7 `AppDatabase` 注册新 entity / 新 DAO
- [x] 1.8 `NoteLinkEntity.LinkType` 新增 `ENTITY_HIT` 枚举值
- [x] 1.9 `NoteLinkDao.getRelated` / `getBacklinks` SQL 加 ENTITY_HIT 档权重

## 2. 实体抽取 — 抽象 + LLM 实现

- [x] 2.1 新建 `core/note/entity/EntityExtractor.kt`(接口)
- [x] 2.2 新建 `EntityType.kt`:枚举 + `keyPrefix` + `normalizeKey` 工厂
- [x] 2.3 扩展 `LlmEntityExtractor` 走 `AiGateway.streamWritingOp(op=EXPAND)`
- [x] 2.4 单语 prompt `ENTITY_EXTRACT_SYSTEM_ZH`(英文走同一 prompt key 命名，改写推迟)
- [x] 2.5 新建 `LlmEntityExtractor.kt`:JSON 容错 + key 规范化 + 写入
- [x] 2.6 key 后处理:`lowercase()` + `replace(Regex("[^a-z0-9_]+"), "_")` + 前缀补齐
- [x] 2.7 surfaceForm 截断保护:超过 80 字符截断

## 3. 实体索引 — 命中边写入

- [x] 3.1 新建 `core/note/impl/EntityBacklinker.kt`:shared hits → 66 cap → evidence JSON
- [~] 3.2 重命名 `LlmNoteLinkExtractor` → `SemanticNoteLinker`(**deferred**:binary compat > rename,M6 polish)
- [x] 3.3 改 `SemanticNoteLinker.extractAndPersist` 行为:共享实体 < 1 才 fallback
- [x] 3.4 `CompositeNoteLinker.recomputeForNote` 并行 fan-out + entityBacklinker 一支
- [x] 3.5 `NoteLinkCap.enforce(candidates, cap=100, entityRatio=0.66)` 截断
- [~] 3.6 SQL 阈值 `:threshold` 参数化(**deferred**:`HAVING score > 0.10` 仍硬编码;app 侧走 `NoteLinkCap` + `SettingsStore.threshold` 等价)

## 4. 别名合并

- [x] 4.1 `EntityAliasDao.findByAliasKeys(List<String>)` 批量查
- [x] 4.2 `EntityBacklinker` 命中前调 `findByAliasKeys(rawKeys)` 展开 raw+canonical 全集
- [x] 4.3 `AliasManagementScreen` 入口 + 列表 + 合并 / 删除
- [x] 4.4 `AliasManagementViewModel` 提供 `merge` / `unmerge` / `refresh`
- [x] 4.5 i18n:`entity_alias_management_*` zh + en

## 5. 设置 — DataStore + 阈值 slider

- [x] 5.1 `NoteAssociationSettingsStore` 扩展 `threshold()` / `pauseBackfill()` + 配套 setter / observe
- [x] 5.2 同步 API + Flow observe
- [x] 5.3 Hilt binding 沿用既有 `NoteAssociationSettingsModule`，新方法无需新 module
- [~] 5.4 设置页 slider「关联阈值」+ 按钮「立即重跑回填」(**deferred**:UI work 不阻塞数据层)
- [~] 5.5 slider 双向绑定(**deferred**:与 5.4 同步推迟)

## 6. UI — 详情页关联 section

- [x] 6.1 `RelatedNotesSection` composable 详情页底部
- [x] 6.2 state 内联(`related` / `backlinks` 走 `noteLinker.getRelated/getBacklinks` + `LaunchedEffect`)
- [x] 6.3 信号 chip:WIKILINK / TAG_OVERLAP / CONTENT_SIM / ENTITY_HIT / LLM_EXTRACT
- [x] 6.4 共享实体 chip group(`parseSharedEntities` 内联解析 evidence JSON)
- [x] 6.5 「还有 N 条被隐藏」内联在 cap
- [x] 6.6 空态文案 `note_association_related_empty` 已 i18n
- [x] 6.7 i18n 沿用 `note_association_*` key 集

## 7. 历史回填 Worker

- [x] 7.1 `EntityBackfillWorker`(`CoroutineWorker`,uniqueWorkName `entity-backfill-v4`,tag `entity_backfill`)
- [x] 7.2 trigger:`WritingApp.onCreate` → `BackfillScheduler.scheduleEntityBackfillIfNeeded()`(5s 延后)
- [x] 7.3 worker:`SELECT id FROM notes` → 逐条 `extractAndPersist(bypassRateLimit=true)`
- [x] 7.4 节奏:每条间 `delay(5_000L)`
- [x] 7.5 进度:`setProgress(workDataOf("processed", n, "total", t))`
- [x] 7.6 暂停:`BackfillScheduler.cancelEntityBackfill()` → `cancelAllWorkByTag("entity_backfill")`
- [x] 7.7 续跑:`enqueueUniqueWork(..., KEEP)` 自然断点续跑
- [~] 7.8 设置页 WorkInfo 订阅进度 UI(**deferred**:M6 polish)

## 8. Prompt 注入防御 + 安全

- [x] 8.1 `ENTITY_EXTRACT_SYSTEM_ZH` 写死无 user-overridable 段
- [x] 8.2 `LlmEntityExtractor.containsInjection` 检测三组短语:`ignore previous instructions` / `忽略之前指令` / `ignore all previous`

## 9. i18n

- [x] 9.1 `values/strings.xml` 增 25 条(`entity_alias_management_*` / `entity_association_threshold_label` / `entity_backfill_*` / 12 个 `entity_type_*` / `entity_signal_entity`)
- [x] 9.2 `values-en/strings.xml` 同步英文翻译(25 条)

## 10. 单测 / 集成测试

- [x] 10.1 `LlmEntityExtractorTest`:7 case(prompt 注入 ZH+EN / JSON 解析 / markdown 包裹 / 非 JSON / 空数组 / note 缺失)
- [x] 10.2 `EntityBacklinkerTest`:4 case(空 self / alias 展开 / evidence JSON / 66 cap)
- [x] 10.3 `NoteLinkCapTest`:2:1 比例 + fallback(沿用 note-association P1 既有)
- [~] 10.4 `NoteEntityDaoTest`(**deferred**:Room in-memory 需要 Robolectric,M6 polish 集中补)
- [~] 10.5 `EntityAliasDaoTest`(**deferred**:同上)
- [~] 10.6 `EntityBackfillWorkerTest`(**deferred**:WorkManager 测试需要 instrumentation)
- [~] 10.7 `NoteLinkDaoTest`(**deferred**:同上)
- [x] 10.8 `CompositeNoteLinkerTest` 扩展 mock `EntityBacklinker`，验证 fan-out + cap 顺序(已有 4 case)

## 11. 编译 + ktlint + lint

- [x] 11.1 `./gradlew :app:assembleDebug` 通过
- [x] 11.2 `./gradlew :app:ktlintCheck` 通过(`ktlintFormat` 自动修 import 顺序)
- [x] 11.3 `./gradlew :app:testDebugUnitTest` 全绿(169 tests)
- [x] 11.4 `./gradlew :app:check` 全绿(`lint-baseline.xml` 含 2 个 pre-existing 错误，本 change 无关)

## 12. 文档 + archive

- [x] 12.1 `roadmap.md` §13 更新
- [x] 12.2 `progress.md` 加 M 完成条目
- [x] 12.3 `note-association` 后续 archive
- [x] 12.4 `docs/reviews/2026-06-23-entity-extraction-association-code-review-r1.md` 自审

## 总览

**已完成**:§1, §2, §3.1/3.3-3.5, §4, §5.1-5.3, §6, §7, §8, §9, §10.1-10.3/10.8, §11, §12
**明确 deferred**(M6 polish):§3.2 重命名 / §3.6 SQL 阈值 / §5.4-5.5 slider UI / §7.8 进度 UI / §10.4-10.7 DAO + worker 测试
**无阻塞**:deferred 项都不影响核心数据流。
