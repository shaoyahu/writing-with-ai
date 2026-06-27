## Context

`entity-extraction-association` change(2026-06-23 归档)在 tasks.md 明确留 6 项 polish deferred。重写本 change 集中收口。现状:

- `NoteLinkDao.getRelated/getBacklinks` SQL `HAVING score > 0.10` 硬编码
- `NoteAssociationSettingsStore.threshold()` 默认 0.25 已存,但 SQL 与 store 默认值错位(0.10 vs 0.25),且 DAO 不读 store
- `LlmNoteLinkExtractor` 类名与 M6 §3.3 改后的"语义兜底"职责不符(共享实体 < 1 才 fallback)
- `EntityBackfillWorker.setProgress` 已 emit 4 字段(proc/total/ok/failed),但设置页无订阅
- `pauseBackfill()` 只在 `BackfillScheduler.scheduleEntityBackfillIfNeeded` 阶段查,Worker 已 enqueue 后无法中止
- 4 DAO + worker 测试缺(Robolectric 已由 polish-and-internal-release 落地)

## Goals / Non-Goals

**Goals:**
- 用户可在设置页调节关联阈值(0.05–0.80),立即生效(下一次 `extractAndPersist` / `recomputeForNote` 调用即用新值)
- SQL 阈值 = store threshold,默认值对齐 0.10(收窄默认,减少低分噪声)
- 回填进度在设置页实时显示
- 暂停开关 = "未来不再调度" + "Worker 起跑前再查一次"
- 类名 `SemanticNoteLinker` 反映"共享实体 < 1 才 LLM 兜底"的语义
- 4 DAO + worker 测试实跑 in-memory Room(代替 mock),覆盖核心契约

**Non-Goals:**
- 不重写 EntityExtractor 算法(走 EXPAND prompt 不变)
- 不改 entity_aliases 数据模型
- 不动 feishu 同步链
- 不做 slider 自动保存节流(throttle 200ms 内仅 1 次写,避免 observeThreshold 反复 emit)

## Decisions

### D1 · 重命名:`LlmNoteLinkExtractor` → `SemanticNoteLinker`

- **做法**:同 package `core/note/impl/` rename 文件 + class,@Inject 注入不变,9 处引用全改(prod 1 + tests 2 + Hilt module 隐式)
- **理由**:M6 §3.3 改后职责 = "共享实体 >= 1 → 走 entity + cap;不足才 LLM 兜底",LLM 是兜底不是主路径,旧名误导
- **替代**:保留旧名 + 加 `@Deprecated` 注释 — 否,二进制层已 archive,M6 polish 没必要保留兼容层

### D2 · SQL 阈值来源:store 同步 vs DAO 接受形参

- **做法 A**(选):`NoteLinkDao.getRelated/getBacklinks` 加 `threshold: Double` 形参,`CompositeNoteLinker.recomputeForNote` / `QuickNoteDetailViewModel` 等 caller 在调用前 `noteAssociationSettingsStore.threshold().first()` 读出传入
- **理由**:Room DAO 是数据访问层,放 `SharedPreferences` 读取会引入 IO 依赖 + 单测要 mock Context;形参注入保持 DAO 纯函数性,test 直接传常量
- **替代**:DAO 内 `runInTransaction` 读 store — 否,违反 CLAUDE.md "core/data 不依赖 core/prefs" 单向依赖

### D3 · Slider 范围与默认值

- **做法**:Slider `valueRange = 0.05f..0.80f`,`steps = 14`(0.05 step,共 16 档:0.05/0.10/0.15/.../0.80),`default = 0.10f`(对齐当前 SQL 默认值)
- **理由**:SQL 当前 0.10 是生产实测合理阈值(过低 → 噪声条目淹没问题);store 默认 0.25 是早期估计偏高;用 0.10 作新默认一次性收口
- **替代**:保留 0.25 默认 — 否,与 SQL 错位导致用户调 slider 不生效困惑
- **节流**:Slider `onValueChangeFinished` (而非 `onValueChange`) 触发 `setThreshold`,避免滑动时高频写盘

### D4 · 进度 UI 数据源

- **做法**:`WorkManager.getInstance(ctx).getWorkInfosByTagFlow("entity_backfill")` 订阅 `LiveData<MutableList<WorkInfo>>`,`ViewModel.stateIn` 转 StateFlow
- **理由**:已有 tag + setProgress,UI 层订阅成本低,无需新 Worker
- **替代**:新增 `progressStore` 单独存进度 — 否,与 WorkManager 数据双源不一致

### D5 · `pauseBackfill()` 双重 guard

- **做法**:
  - 入口 1:`BackfillScheduler.scheduleEntityBackfillIfNeeded()` 调用前 `if (store.pauseBackfill()) return` — 已有
  - 入口 2:`EntityBackfillWorker.doWork()` 第一个 IO 操作前 `if (store.pauseBackfill()) return Result.failure(workDataOf("reason" to "paused"))` — 新加
- **理由**:用户在工作进行中开关"暂停",已 enqueue 的 WorkRequest 在 Android 14+ 不会自动 cancel;必须 Worker 自检
- **替代**:观察 DataStore 一旦 paused 立即 `cancelAllWorkByTag` — 否,WorkManager cancel 是 async,Worker 已在跑的协程来不及响应;自检更可靠

### D6 · 测试范围:实跑 in-memory Room

- **做法**:用 `Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)` 构建;DAO 直接调用(不 mock);Worker 用 `runBackfillLoop` companion fun 测(已抽)+ 整 `doWork()` 用 `TestListenableWorkerBuilder`
- **理由**:Mock 测试不覆盖 SQL 行为(HAVING / GROUP BY / 自连接);实跑覆盖 SQL + 索引 + 事务
- **替代**:继续 mock — 否,已知 SQL 有 0.10/0.25 错位 bug,只有实跑能抓
- **性能权衡**:Robolectric 首次启动 ~500MB 下载已在 CI 预缓存,本地 dev 跳过该批 test 即可(用 `-PexcludeRobolectric` 或 test filter)

### D7 · 新 route 入口策略

- **做法**:`AppNav` 加 `note_association_settings` route;`SettingsScreen` 「笔记关联」入口 Navigate 到该 route;不放在主 settings 列表(避免列表膨胀)
- **理由**:M6 已把"实体别名管理"放在独立 screen(`AliasManagementScreen`),关联阈值与回填进度同主题,聚合成一个 settings 子页

## Risks / Trade-offs

- [Slider 0.10 默认收紧] → 用户从老版本升级后阈值从 0.25 跳到 0.10,关联条目可能突然变少 → 首次进入设置页 banner 提示「默认已从 0.25 收紧到 0.10,如感觉过严可上调」
- [pauseBackfill Worker 自检只在 doWork 起始点] → 已在主循环的 note 无法中止(需跑完当前条) → 文档说明"暂停 = 不开始新条目"
- [Robolectric test 慢] → 单测初次跑 ~30s(test 启动 + 引擎) → 在 CI 单独跑,本地可 `-PexcludeRobolectric` 跳过
- [Rename `LlmNoteLinkExtractor` 是同 package 但 binary 不兼容] → 仓库内无外部二进制依赖,纯源码替换;但任何 down-stream fork 会断 → M6 polish 集中改,无遗留
- [SQL 阈值从硬编码 0.10 改成 store 可配] → 用户滑到 0.05 极松,可能性能塌方(笔记库大) → slider 下限锁 0.05 + slider label 显示当前值

## Migration Plan

- **无需 DB migration**:纯 schema 不变,只改 SQL 形参与类名
- **默认值迁移**:`association_threshold` SharedPreferences 已存 0.25 的用户,在首次进入新设置页时检测 store 值 > 0.50 则重置为 0.10(只重置异常值,正常用户不动)
- **回滚**:新 slider + 进度 UI 在新 route,旧 `SettingsScreen` 不破坏;SQL 形参化通过 `NoteLinkDao` 新增可选形参(默认 0.10)实现 caller 零侵入回滚
- **部署顺序**:同 commit 包含所有 6 项,无需分批;R3 已收口,本 change 是独立 polish 单元

## Open Questions

- 全部已闭环(用户确认走推荐方案)