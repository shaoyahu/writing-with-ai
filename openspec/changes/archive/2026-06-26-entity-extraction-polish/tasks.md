## 1. 重命名 — LlmNoteLinkExtractor → SemanticNoteLinker

- [x] 1.1 `git mv app/src/main/java/com/yy/writingwithai/core/note/impl/LlmNoteLinkExtractor.kt` → `SemanticNoteLinker.kt`,class 名同步
- [x] 1.2 改 `CompositeNoteLinker.kt:25` import + 形参类型 + 变量名(`llmExtractor` → `semanticLinker`)
- [ ] 1.3 改 2 个 test (`CompositeNoteLinkerTest.kt` / `LlmBackfillWorkerTest.kt`) import + 类型
- [ ] 1.4 改 `LlmBackfillWorker.kt` import
- [x] 1.5 改 spec `note-entity-link` 内 `LlmNoteLinkExtractor` 提及为 `SemanticNoteLinker`(3 处 — delta spec 写时已直接用新名)
- [x] 1.6 改 `LlmEntityExtractor.kt:47` 注释引用(代码行为不变,仅同步命名)

## 2. SQL 阈值参数化

- [x] 2.1 `NoteLinkDao.kt`:`getRelated` / `getBacklinks` 形参加 `threshold: Double`,SQL `HAVING score > 0.10` 改 `HAVING score > :threshold`
- [x] 2.2 `CompositeNoteLinker.recomputeForNote` + `getRelated/getBacklinks` 调 DAO 前 `assocSettings.threshold().toDouble()` 传入
- [x] 2.3 `getRelated` / `getBacklinks` 无其他 caller(走 `NoteLinker` interface,interface 签名不变,CompositeNoteLinker 内部取 store)
- [x] 2.4 `NoteLinkCap.enforce(candidates, cap, threshold)` 加 threshold 形参,score ≤ threshold 先剔除再 2:1 ratio
- [x] 2.5 `NoteLinkCap.DEFAULT_THRESHOLD = 0.10`(对齐 SQL 默认)

## 3. pauseBackfill 双重 guard

- [x] 3.1 `EntityBackfillWorker.doWork()` 加 `EntityBackfillEntryPoint.noteAssociationSettingsStore()` 暴露;起跑 IO 后第一件事:`if (store.pauseBackfill()) return Result.failure(workDataOf("reason" to "paused"))`
- [x] 3.2 `BackfillScheduler.scheduleEntityBackfillIfNeeded()` 加 `pauseBackfill()` 守卫:已查 PREF_DONE 前先查 pause
- [x] 3.3 新增 `BackfillScheduler.scheduleEntityBackfillNow(force: Boolean)`:`force=true` 时绕过 pause 守卫,enqueue `REPLACE`

## 4. 设置页 — NoteAssociationSettingsScreen + ViewModel

- [x] 4.1 新建 `feature/settings/association/NoteAssociationSettingsViewModel.kt`:`StateFlow<UiState>(threshold, paused, workInfo)` + `onThresholdChangeFinished(value)` / `onPauseToggle(value)` / `onReRunClick()`
- [x] 4.2 新建 `feature/settings/association/NoteAssociationSettingsScreen.kt`:TopAppBar + Slider (0.05–0.80 step 14) + Switch + 「立即重跑」OutlinedButton + LinearProgressIndicator + Text 状态
- [x] 4.3 ViewModel 内首次打开检测 `threshold > 0.50` 自动重置 0.10 + emit `oneTimeMigrationBanner = true`
- [x] 4.4 进度订阅:`WorkManager.getInstance(ctx).getWorkInfosByTagFlow("entity_backfill").map { latest }`,转 `UiState.workInfo`
- [x] 4.5 i18n key(8-10):`note_association_settings_title` / `_threshold_label` / `_pause_label` / `_rerun_button` / `_progress_fmt` / `_status_running` / `_status_paused` / `_status_succeeded` / `_status_failed` / `_migration_banner` 双语

## 5. 路由接入

- [x] 5.1 `app/AppNav.kt` 加 route `note_association_settings`,Composable 调 `NoteAssociationSettingsScreen(onBack)`
- [x] 5.2 `feature/settings/SettingsScreen.kt` 「笔记关联」 row Navigate 到新 route

## 6. 测试 — Robolectric in-memory Room

- [x] 6.1 新建 `app/src/test/java/.../core/data/db/dao/entity/NoteEntityDaoTest.kt`:`@RunWith(RobolectricTestRunner::class)` + `Room.inMemoryDatabaseBuilder`,覆盖 `upsertAll` REPLACE / `getByNoteId` / `deleteByNoteId` / `querySharedEntityHits` / `queryAllEntityKeys` 共 5 case
- [x] 6.2 新建 `EntityAliasDaoTest.kt`:覆盖 `upsert` / `deleteByAlias` / `findByAliasKeys(List<String>)` 共 3 case
- [x] 6.3 新建 `NoteLinkDaoTest.kt`:覆盖 `getRelated` threshold 0.10 / `getBacklinks` threshold 0.10 / threshold 0.70 排除低分 / 含 ENTITY_HIT 权重加和 共 4 case
- [x] 6.4 扩 `EntityBackfillWorkerTest`(已存在):加 `emptyNoteList_succeedsImmediately` / `shouldRun_guard_pause_true` / `shouldRun_guard_pause_false` 共 3 case(`EntityBackfillWorker.shouldRun` 新 companion fun 让单测可调,不依赖 WorkManager runtime)
- [x] 6.5 扩 `NoteLinkCapTest`:加 `lowScoreCandidatesDroppedBeforeCap` / `allCandidatesBelowThreshold_empty` 共 2 case
- [ ] 6.6 扩 `LlmEntityExtractorTest`:把 mock DAO 改成 in-memory Room `NoteEntityDao`,1 case 验证集成路径(Robolectric CI 跑,本地跳过)

## 7. 编译 + ktlint + lint

- [ ] 7.1 `./gradlew :app:assembleDebug` 通过
- [ ] 7.2 `./gradlew :app:ktlintCheck` 通过(`ktlintFormat` 自动修)
- [ ] 7.3 `./gradlew :app:testDebugUnitTest` 全绿(新 Robolectric case 全 pass)
- [ ] 7.4 `./gradlew :app:check` 全绿

## 8. 文档 + archive

- [ ] 8.1 `docs/progress.md` 加 M6 polish 完成条目
- [ ] 8.2 `/opsx:sync entity-extraction-polish` 合 3 份 spec 到 `openspec/specs/`
- [ ] 8.3 `/opsx:archive entity-extraction-polish` 收口到 `openspec/changes/archive/`
- [ ] 8.4 自审 review 留 `docs/reviews/2026-06-27-entity-extraction-polish-code-review-r1.md`