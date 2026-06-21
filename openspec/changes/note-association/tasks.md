## 1. P1 — 数据层 + SPI + 读路径

- [x] 1.1 在 `core/data/db/entity/` 加 `NoteLinkEntity.kt` + `LinkType` enum
- [x] 1.2 在 `core/data/db/entity/` 加 `NoteFtsEntity.kt`(Room `@Fts5(contentEntity = NoteEntity::class)`,tokenizer `unicode61 remove_diacritics 2`)
- [x] 1.3 在 `core/data/db/dao/` 加 `NoteLinkDao.kt`(CRUD + 聚合 SQL `getRelated` / `getBacklinks`)
- [x] 1.4 在 `core/data/db/dao/` 加 `NoteFtsDao.kt`(title 前缀匹配 + 内容 bm25 查询)
- [x] 1.5 升 `AppDatabase` version 2 → 3,加 `@AutoMigration(2 → 3)`,注册新 entities
- [x] 1.6 加 `core/note/config/LinkWeights.kt`(权重常量 + 阈值)
- [x] 1.7 加 `core/note/NoteLinker.kt` SPI interface + `RelatedNote` data class
- [x] 1.8 实现 `core/note/impl/LocalNoteLinker.kt`(jaccard + FTS top-K = 20,只算不算)
- [x] 1.9 实现 `core/note/impl/CompositeNoteLinker.kt`(组合 LocalNoteLinker + 写 `note_links`)
- [x] 1.10 加 `core/note/di/NoteLinkerModule.kt`(Hilt,绑 `NoteLinker` → `CompositeNoteLinker`)
- [x] 1.11 加 `core/note/LocalNoteLinkerTest.kt`(jaccard 单测、bm25 归一化、SQL 聚合用 in-memory Room)
- [x] 1.12 加 `core/note/CompositeNoteLinkerTest.kt`(集成:save→recompute→getRelated 端到端)
- [x] 1.13 加 `core/note/backfill/BackfillWorker.kt`(WorkManager `OneTimeWorkRequest`,`NetworkType.NOT_REQUIRED`,分批 50)
- [x] 1.14 加 `core/note/backfill/BackfillScheduler.kt`(首次启动延后 5s 调度,完成后置 SharedPreferences flag)
- [ ] 1.15 跑 `./gradlew :app:assembleDebug :app:ktlintCheck :app:testDebugUnitTest` 全绿 **(blocked: JDK 17 未安装)**
- [ ] 1.16 真机 / 模拟器跑一次,验证 backfill 触发、getRelated 查到数据(手动塞 5 条带重叠 tag 的 note) **(blocked: 无编译产物)**

## 2. P2 — 写路径联通

- [x] 2.1 改 `core/data/repo/NoteRepository.kt`,`save()` 末尾 fire-and-forget 触发 `noteLinker.recomputeForNote(noteId)`
- [x] 2.2 用 `MutableSharedFlow` + `debounce(500)` 合并同一 note 的连续 save
- [ ] 2.3 改 `core/data/repo/NoteRepositoryTest.kt`:save 成功 → `note_links` 行存在;save 抛异常 → 无 recompute **(pending: test)**
- [ ] 2.4 改 `core/data/repo/NoteRepositoryTest.kt`:5 次 save 同一 note in 500ms → 1 次 recompute **(pending: test)**
- [ ] 2.5 跑 `./gradlew :app:testDebugUnitTest` 全绿 **(blocked: JDK 17 未安装)**

## 3. P3 — Wikilink 语法

- [x] 3.1 实现 `core/note/wikilink/WikilinkParser.kt`(regex `\[\[([^\[\]\n]+?)\]\]`)
- [x] 3.2 加 `core/note/wikilink/WikilinkParserTest.kt`(resolved / dangling / 大小写 / 多匹配取最新 / 跨行不解析)
- [x] 3.3 实现 `core/note/impl/WikilinkIndexer.kt`(解析 + `SELECT id ... LOWER(title)=LOWER(?) ORDER BY updatedAt DESC LIMIT 1` + 写 WIKILINK 行)
- [x] 3.4 把 `WikilinkIndexer` 接入 `CompositeNoteLinker.recomputeForNote`(并行 fan-out 一支,via `lateinit var wikilinkIndexer`)
- [x] 3.5 在 `feature/quicknote/edit/` 加 `WikilinkAutocomplete.kt`,检测 `[[` 触发,FTS title 前缀查 top 8
- [x] 3.6 把 autocomplete 接入 `QuickNoteEditorScreen`,选中后插入 `[[<title>]]` 到光标
- [x] 3.7 在 `feature/quicknote/detail/` 加 `RelatedNotesSection.kt` + `BacklinksSection.kt`
- [x] 3.8 改 `feature/quicknote/detail/QuickNoteDetailScreen.kt`,NoteLinker EntryPoint + sections 挂到正文下方
- [ ] 3.9 加 WikilinkAutocomplete / WikilinkText 单元测试 **(pending: test)**
- [ ] 3.10 跑 `./gradlew :app:assembleDebug :app:ktlintCheck :app:testDebugUnitTest` 全绿 **(blocked: JDK 17 未安装)**
- [ ] 3.11 真机验证 **(blocked: 无编译产物)**

## 4. P4 — LLM 抽取(opt-in)

- [x] 4.1 加 `core/ai/prompt/note_association_prompt.kt`(prompt 模板 + JSON 输出 schema)
- [x] 4.2 实现 `core/note/impl/LlmNoteLinkExtractor.kt`(`AiGateway.streamWritingOp`,解析 `{"links":[{"id","confidence","reason"}]}`,写 LLM_EXTRACT 行)
- [x] 4.3 `CompositeNoteLinker` 接收 `LlmNoteLinkExtractor` via `lateinit var llmExtractor`;仅在 opt-in + apikey 配齐时跑
- [x] 4.4 失败回退:catch Exception in `LlmNoteLinkExtractor.extractAndPersist` + `CompositeNoteLinker.runLlmIfEnabled`
- [x] 4.5 成功 / 失败都写 `ai_history`(AiHistoryDao.insert,action=`note-association-extract`,含 token + duration + error)
- [x] 4.6 加 `feature/settings/NoteAssociationSettings.kt`,暴露 toggle(默认 OFF,SharedPreferences 持久化)
- [x] 4.7 把 toggle 接入 `feature/settings/SettingsScreen.kt`(Switch + EntryPoint + NoteAssociationSettings)
- [x] 4.8 在详情页空态加 "用 AI 找关联" 按钮(showAiButton 按 settings.isEnabled 动态,onAiTrigger → extractAndPersist bypassRateLimit=true)
- [x] 4.9 加 `core/note/backfill/LlmBackfillWorker.kt`(WorkManager,约束 `NetworkType.UNMETERED` + `requiresCharging`)
- [x] 4.10 限频存 SharedPreferences `note_assoc_llm_rate`(每 note 24h);手动按钮 bypassRateLimit=true
- [x] 4.11 加 `LlmNoteLinkExtractorTest.kt`(mock AiGateway:no apikey/not found/error→aiHistory/rate limit)
- [ ] 4.12 跑 `./gradlew :app:assembleDebug :app:ktlintCheck :app:testDebugUnitTest` 全绿 **(blocked: JDK 17 未安装)**
- [ ] 4.13 真机验证 **(blocked: 无编译产物)**
