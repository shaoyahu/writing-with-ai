## Why

`entity-extraction-association` change 在 2026-06-23 归档时留了 6 项 polish deferred(roadmap §13):
1. 重命名 `LlmNoteLinkExtractor` → `SemanticNoteLinker`(类名与新职责不符)
2. SQL 阈值 `HAVING score > 0.10` 硬编码 — `NoteAssociationSettingsStore.threshold()` 默认 0.25 已就绪但没接进 SQL
3. 设置页缺「关联阈值」slider + 「立即重跑回填」按钮
4. 设置页缺回填进度 UI(`EntityBackfillWorker` 已 `setProgress` 4 字段但没订阅)
5. `pauseBackfill()` 仅阻止调度,Worker 已 enqueue 仍跑到底
6. 4 个 DAO + worker 测试缺(`NoteEntityDao` / `EntityAliasDao` / `NoteLinkDao` / `EntityBackfillWorker` 扩)

不解决 = 用户无法调节阈值,SQL 默认 0.10 与 store 默认 0.25 错位,类名误导,测试缺。趁 polish-and-internal-release 已加 Robolectric 落地 + R3 已收口,集中清掉。

## What Changes

- **重命名** `LlmNoteLinkExtractor` → `SemanticNoteLinker`(同 package `core/note/impl/`,9 处引用全改,Hilt `@Inject class` 不变)
- **SQL 阈值参数化**:`NoteLinkDao.getRelated` / `getBacklinks` 的 `HAVING score > 0.10` 改 `HAVING score > :threshold`,由 `CompositeNoteLinker` 从 `NoteAssociationSettingsStore.threshold()` 读出传入;`NoteLinkCap` 同步用 threshold 取代硬编码比例
- **新增 `NoteAssociationSettingsScreen`**:`Slider` 0.05–0.80 step 0.05,默认 0.10(对齐当前 SQL 值,用户从 store 默认 0.25 调到 SQL 默认 0.10 反向更严)+ 「立即重跑回填」按钮 + 「暂停回填」switch
- **进度 UI**:同屏 `WorkManager.getWorkInfosByTagFlow("entity_backfill")` 订阅,显示 `processed / total / succeeded / failed` 进度条 + 当前态(RUNNING / SUCCEEDED / FAILED)
- **`pauseBackfill()` 增强**:Worker `doWork()` 起跑前再查一次 store,`pauseBackfill=true` 立即 `Result.failure()` 返回,不进入主循环;`BackfillScheduler.scheduleEntityBackfillIfNeeded` 也加 guard
- **测试**:4 个 DAO + 1 个 worker 实跑 Robolectric in-memory Room(`RuntimeEnvironment.application` + `Room.inMemoryDatabaseBuilder`),不再仅 mock DAO;`NoteLinkCap` 阈值联动新增 2 case

## Capabilities

### New Capabilities
- `note-association-settings`:设置页 slider + 立即重跑 + 暂停 + 进度 UI + 设置 store 阈值/暂停操作统一入口(对应 `NoteAssociationSettingsScreen` + `NoteAssociationSettingsViewModel`)

### Modified Capabilities
- `note-entity-link`:`SemanticNoteLinker` 重命名(类名变,接口契约不变);`NoteLinkDao` SQL 阈值参数化(`getRelated` / `getBacklinks` 新增 `threshold: Double` 形参);`NoteLinkCap` 阈值联动
- `note-entity-extraction`:`EntityBackfillWorker.doWork()` 起跑前查 `pauseBackfill()`;测试覆盖扩展(`runBackfillLoop` + pause/fail/empty 路径 + in-memory Room 集成)

## Impact

- **代码**:`core/note/impl/LlmNoteLinkExtractor.kt`(rename → `SemanticNoteLinker.kt`) + `core/data/db/dao/NoteLinkDao.kt` + `core/note/impl/CompositeNoteLinker.kt` + `core/note/backfill/EntityBackfillWorker.kt` + `core/note/NoteLinkCap.kt`(如有独立文件)+ 新 `feature/settings/association/{NoteAssociationSettingsScreen, NoteAssociationSettingsViewModel}.kt` + `app/AppNav.kt` 新 route + `feature/settings/SettingsScreen.kt` 新入口
- **测试**:4 新 test class(`NoteEntityDaoTest` / `EntityAliasDaoTest` / `NoteLinkDaoTest` / `EntityBackfillWorkerTest` 扩 — 已有 R3 加的 `EntityBackfillWorkerTest`);扩 `NoteLinkCapTest` 加 threshold 联动 2 case;扩 `LlmEntityExtractorTest` 用 in-memory Room 替代 mock
- **i18n**:8-10 新 key(`note_association_settings_*` / `entity_backfill_progress_fmt` / `entity_backfill_status_*` 等)
- **依赖**:Robolectric 已在 polish-and-internal-release 落地,`testImplementation libs.robolectric.core` + `androidx.test.runner` 已配
- **不破坏**:所有改动向后兼容,`NoteLinkDao` 新增形参有默认值,旧 caller 可传 `0.25`(取 store 默认);`pauseBackfill` 仅加宽语义,不收紧