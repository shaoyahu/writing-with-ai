## 1. 数据层 — Room 实体与 DAO

- [x] 1.1 新增 `EntityType` 枚举(12 类:PERSON / WORK / EVENT / LOCATION / ORG / CONCEPT / DATE / URL / QUOTE / PRODUCT / TASK / NUMBER)
- [x] 1.2 新增 `NoteEntityRow` entity(`note_entities` 表,复合主键 `(noteId, entityKey)`,FK CASCADE 删 note)
- [x] 1.3 新增 `EntityAliasRow` entity(`entity_aliases` 表,主键 `(entityType, aliasKey)`)
- [x] 1.4 新增 `NoteEntityDao`:upsertAll / getByNoteId / deleteByNoteId / querySharedEntityHits(srcNoteId, limit) / queryAllEntityKeys(limit, offset)
- [x] 1.5 新增 `EntityAliasDao`:upsert / deleteByAlias / resolveCanonicalKeys(List<String>): Set<String>
- [x] 1.6 Room version `3 → 4` + AutoMigration(不写 SQL,只声明 `@AutoMigration(from=3, to=4)`)
- [x] 1.7 `AppDatabase` 注册新 entity / 新 DAO
- [x] 1.8 `NoteLinkEntity.LinkType` 新增 `ENTITY_HIT` 枚举值(同 enum 文件,Room TypeConverter 自动支持)
- [x] 1.9 `NoteLinkDao.getRelated` / `getBacklinks` SQL 加 ENTITY_HIT 档权重 + 接 `:threshold` 参数

## 2. 实体抽取 — 抽象 + LLM 实现

- [x] 2.1 新建包 `core/note/extractor/EntityExtractor.kt`(接口):`suspend fun extractAndPersist(noteId: String, bypassRateLimit: Boolean = false): Int`
- [x] 2.2 新建 `EntityType.kt`:枚举 + `keyPrefix: String` 字段 + `companion fromKey(type, key): NoteEntityRow` 工厂
- [x] 2.3 扩展 `NoteAssociationPrompt.kt`:增 `buildExtractEntities(title, content, locale): PromptBundle`(分 system / user)
- [x] 2.4 双语 prompt 常量:`ENTITY_EXTRACT_SYSTEM_ZH` / `ENTITY_EXTRACT_SYSTEM_EN`(写死,过 review)
- [x] 2.5 新建 `LlmEntityExtractor.kt`:复用 `AiGateway.streamWritingOp(op=WritingOp.EXPAND, systemPrompt=...)`;`@Serializable` DTO + `Json.decodeFromString` 容错
- [x] 2.6 key 后处理:`lowercase()` + `replace(Regex("[^a-z0-9_]"), "_")` + 前缀补齐(`PERSON` 实体 key 必须以 `person::` 开头,自动补)
- [x] 2.7 surfaceForm 截断保护:超过 80 字符截断 + `...`,避免 SQLite 单字段过大

## 3. 实体索引 — 命中边写入

- [x] 3.1 新建 `core/note/impl/EntityBacklinker.kt`:基于 `NoteEntityDao.querySharedEntityHits` 命中 → 按 score desc 取前 66 条 → 写 `NoteLinkEntity(linkType = ENTITY_HIT, weight=1.0, evidence = sharedEntitiesJson)`
- [x] 3.2 重命名 `LlmNoteLinkExtractor` → `SemanticNoteLinker`(保留 binary 兼容,改名 + 文件 mv,等价的 compose 重命名)
- [x] 3.3 改 `SemanticNoteLinker.extractAndPersist` 行为:不再「每篇都跑」,改为「由 CompositeNoteLinker 在共享实体 < 1 时调用」
- [x] 3.4 改 `CompositeNoteLinker.recomputeForNote` 流程:并行 fan-out 包含 `EntityBacklinker`;`EntityBacklinker` 完成后查询共享实体数;< 1 才 fallback 调 `SemanticNoteLinker`
- [ ] 3.5 2:1 cap 逻辑封装到 `NoteLinkCap.enforce(candidates, cap = 100, entityRatio = 0.66)`:返回按 score desc 截断后的列表
- [x] 3.6 删 `NoteLinkDao` 旧的硬编码阈值 0.10,改由 caller 传 `:threshold`(默认 0.25 来自 `NoteAssociationSettingsStore`)

## 4. 别名合并

- [ ] 4.1 扩展 `NoteEntityDao` 或新建 `EntityAliasDao`:对 aliasKey → canonicalKey 查询支持 batch
- [ ] 4.2 `NoteEntityLinker` 命中查询前调 `resolveCanonicalKeys(allEntityKeys)`,得到展开后的全集再查
- [ ] 4.3 设置页「别名管理」入口:`AliasManagementScreen` 列出所有 type + alias + canonical,支持单条合并 / 删除
- [ ] 4.4 `AliasManagementViewModel` 提供 `merge(aliasType, aliasKey, canonicalKey)` / `unmerge(aliasType, aliasKey)` / `listAll(): List<AliasGroup>`
- [ ] 4.5 i18n:`alias_management_*` 字符串 zh + en

## 5. 设置 — DataStore + 阈值 slider

- [x] 5.1 新建 `core/prefs/NoteAssociationSettingsStore.kt`(DataStore Preferences):
  - key `entity_extraction_enabled` (default true)
  - key `association_threshold` (default 0.25, step 0.05)
  - key `backfill_paused` (default false)
- [x] 5.2 `NoteAssociationSettings.isEnabled()` / `threshold()` / `pauseBackfill()` 同步 API
- [x] 5.3 Hilt module 注入 `NoteAssociationSettingsStore`
- [ ] 5.4 设置页新增「笔记关联」section:toggle「实体抽取」+ slider「关联阈值」+ 按钮「立即重跑回填」+ 跳转「别名管理」
- [ ] 5.5 slider 双向绑定 `NoteAssociationSettingsStore.threshold`,改值即时落盘

## 6. UI — 详情页关联 section

- [ ] 6.1 详情页底部新增 `RelatedNotesSection` composable(在 `QuickNoteDetailScreen.kt`)
- [ ] 6.2 `RelatedNotesViewModel`:订阅 `NoteLinker.getRelated(noteId, threshold)`,转 `UiState`
- [ ] 6.3 每条关联显示:title / preview / score 信号 chip(WIKILINK / TAG_OVERLAP / ENTITY_HIT …)
- [ ] 6.4 长按 row 弹 bubble:解析 `evidence` JSON → 显示 `sharedEntities` chip group
- [ ] 6.5 「还有 N 条被隐藏」提示:总候选数 > 显示数时显示
- [ ] 6.6 空态文案:无关联时显示「写更多笔记让 AI 发现关联」+ 设置入口
- [ ] 6.7 i18n:`related_notes_*` 字符串 zh + en

## 7. 历史回填 Worker

- [x] 7.1 新建 `core/note/backfill/EntityBackfillWorker.kt`(`CoroutineWorker`):uniqueWorkName `entity_backfill_v4`,tag `entity_backfill`
- [ ] 7.2 trigger:`AppDatabase.onMigrate` 完成后由 `WritingApp.onCreate` enqueue `OneTimeWorkRequestBuilder<EntityBackfillWorker>().setConstraints(...)`(无需 network,Constraints 仅要求设备 idle 不强制)
- [x] 7.3 worker 逻辑:`SELECT id FROM notes WHERE lastExtractedAt = 0` → 逐条调 `LlmEntityExtractor.extractAndPersist(noteId, bypassRateLimit=true)`
- [x] 7.4 节奏:`delay(5_000L)` 每条之间
- [x] 7.5 进度:`setProgressAsync(Data.Builder().putInt("processed", n).putInt("total", t).putString("currentNoteId", id).build())`
- [ ] 7.6 暂停:设置页「取消回填」按钮 → `WorkManager.getInstance(ctx).cancelAllByTag("entity_backfill")`;已处理的实体保留
- [x] 7.7 续跑:`enqueueUniqueWork(..., ExistingWorkPolicy.KEEP)` — WorkManager 已支持断点续跑,worker 内部从 `lastExtractedAt = 0` 查询自然实现
- [ ] 7.8 设置页「回填进度」展示:`WorkManager.getWorkInfosByTagLiveData("entity_backfill")` 订阅进度,显示 `processed / total`

## 8. Prompt 注入防御 + 安全

- [ ] 8.1 review `ENTITY_EXTRACT_SYSTEM_ZH` / `ENTITY_EXTRACT_SYSTEM_EN` 文案,确认无「忽略之前所有指令」类可被覆盖写法
- [ ] 8.2 prompt 注入检测:user content 包含 `ignore previous instructions` / `忽略之前指令` 时,extractor 跳过抽取 + 记一行 warn log,不让恶意 prompt 污染 system

## 9. i18n

- [ ] 9.1 `values/strings.xml` 增:实体抽取 toggle / 阈值 slider / 别名管理 / 关联 section / 回填进度 / 取消回填 / 立即重跑回填 / 12 个实体类型名(用于设置页「别名管理」列表显示)
- [ ] 9.2 `values-en/strings.xml` 同步英文翻译

## 10. 单测 / 集成测试

- [ ] 10.1 `LlmEntityExtractorTest`:双语 prompt 正确构造 + JSON 解析 + key 规范化 + 非 JSON 容错
- [ ] 10.2 `EntityBacklinkerTest`:共享实体命中 + 上限 66 条 + evidence JSON 格式
- [ ] 10.3 `NoteLinkCapTest`:2:1 比例 + 不足 66 实体 fallback + 不足 100 总数截断
- [ ] 10.4 `NoteEntityDaoTest`(@Robolectric / Room in-memory):upsert + cascade delete + 复合主键去重
- [ ] 10.5 `EntityAliasDaoTest`:canonical 展开正确性
- [ ] 10.6 `EntityBackfillWorkerTest`:进度回调 + 取消行为 + 续跑自然实现
- [ ] 10.7 `NoteLinkDaoTest`:SQL 加权公式 0.25 阈值下精确命中数 + 阈值变化
- [ ] 10.8 集成测试:`NoteRepository.upsert` → `CompositeNoteLinker.recomputeForNote` → `note_entities` 写入 + `note_links.ENTITY_HIT` 写入(用 FakeAiProvider 控制返回)

## 11. 编译 + ktlint

- [ ] 11.1 `./gradlew :app:assembleDebug` 通过
- [ ] 11.2 `./gradlew :app:ktlintCheck` 通过,违反项先 `ktlintFormat`
- [ ] 11.3 `./gradlew :app:testDebugUnitTest` 全绿
- [ ] 11.4 `./gradlew :app:check` 全绿

## 12. 文档 + archive 准备

- [ ] 12.1 更新 `docs/plans/writing-with-ai-mobile-roadmap.md` §3.1 / §13:加 entity-association 进 M2 后续 / 新增 M「实体关联增强」条目
- [ ] 12.2 更新 `docs/progress.md`:M 完成 / 关键 bug 节点(本 change 完成 + archive 时)
- [ ] 12.3 准备 archive:旧 `note-association` change 在本 change 合并后由 `opsx:archive` 处理冲突 spec 同步
- [ ] 12.4 `docs/reviews/<date>-entity-extraction-association-code-review-r1.md` 起一份自审记录