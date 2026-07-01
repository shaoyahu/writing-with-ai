# Full Project Code Review R4

**日期**: 2026-06-27
**范围**: 全项目代码，基于 `entity-extraction-polish` 归档后状态 + 未提交 working tree(32 改 + 5 新)
**触发**: 用户指令"开启 R4 review，要求循环 review 至少 2 遍"
**方法**: 5 个并行 review agent(CLAUDE.md 合规 / bug 扫 / 历史 / 之前 review 复用 / 注释一致性)
**基线**: HEAD = `c10aef7`,working tree 含 entity-extraction-polish 产出

## 评分门槛(≥80 才算真实 issue)

## R4-1 Findings

### R4-1-1 · HIGH · `onPauseToggle(false)` 不会真重排回填

- **文件**:`app/src/main/java/com/yy/writingwithai/core/note/backfill/BackfillScheduler.kt:42-48` + `app/src/main/java/com/yy/writingwithai/feature/settings/association/NoteAssociationSettingsViewModel.kt:93-99`
- **问题**:
  1. 用户 pause → unpause
  2. `setPauseBackfill(false)` 写 false
  3. `onPauseToggle(false)` 调 `scheduleEntityBackfillIfNeeded()`
  4. `pauseBackfill()` 返 false → guard pass
  5. `PREF_ENTITY_BACKFILL_DONE == true`(回填跑过一次) → return
  6. **结果:无 Worker enqueue，用户期望的"恢复重排"静默失败**
- **冲突**:VM 注释 line 96 "从暂停恢复时，触发一次轻量调度(KEEP 策略，无 flag 跳过的会继续)" — 实际 flag 跳过了所有
- **修法**:`onPauseToggle(false)` 改调 `scheduleEntityBackfillNow(force = false)`(不走 PREF_DONE guard);或 `scheduleEntityBackfillIfNeeded()` 加 `resetDoneFlag` 参数

### R4-1-2 · HIGH · `force=true` paused 时 UI 立刻显示 red FAILED

- **文件**:`app/src/main/java/com/yy/writingwithai/core/note/backfill/BackfillScheduler.kt:69-80` + `app/src/main/java/com/yy/writingwithai/core/note/backfill/EntityBackfillWorker.kt:108`
- **问题**:
  1. 用户 pause → tap "立即重跑回填"
  2. `scheduleEntityBackfillNow(force=true)` bypass pause guard → enqueue `REPLACE`
  3. Worker 起跑 → `shouldRun(store)` 查 `pauseBackfill()=true` → `Result.failure("reason"="paused")`
  4. UI 显示 progress block + 红色 FAILED "已暂停"
- **冲突**:R4-1 Agent #2 评分 80
- **修法**:`ReRunSection` 在 `paused == true` 时按钮 `enabled = false`(现在只 check `isRunning`);或 `scheduleEntityBackfillNow` 在 paused 时直接 return + 弹 toast "先取消暂停"

### R4-1-3 · MEDIUM · Slider `onValueChangeFinished` 捕获 stale outer `value`

- **文件**:`app/src/main/java/com/yy/writingwithai/feature/settings/association/NoteAssociationSettingsScreen.kt:142-149`
- **问题**:
  - `Slider.onValueChangeFinished = { onValueChangeFinished(value) }` 里的 `value` 是 outer 形参(snapshot)
  - Material3 Slider 在用户拖动时通过 `onValueChange` 实时 emit，但 `onValueChangeFinished` 调时，M3 lambda 内部仍持有拖动完成那一刻的最终值 — 这个值不会反映到 outer `value`(那要等 recomposition)
  - 用户拖 0.10 → 0.45 release，外层 `value` 仍是 0.10(没 recompose)，写入 store 的是 0.10
- **修法**:用 `remember { mutableFloatStateOf(value) }` 在 `onValueChange` 更新拖动值，`onValueChangeFinished` 写拖动状态值，然后复位到 outer `value`

## R4-1 <80

| # | 文件 | 描述 | 评分 |
|---|---|---|---|
| R4-1-4 | NoteLinkCap.kt:28 | raw weight vs DAO aggregated score 两阶段 filter 阈值语义不一致 | 75 |
| R4-1-5 | NoteAssociationSettingsViewModel.kt:67 | `workInfo.maxByOrNull { state.ordinal }` 取 SUCCEEDED 优先于新 ENQUEUED | 65 |
| R4-1-6 | runMigrationCheck 仅 >0.50 | 0.25 旧值不会触发 banner | 70 |
| R4-1-7 | CompositeNoteLinker.kt:63-65 | LLM fallback catch 静默(无 Log.w) | 70 |
| R4-1-8 | EntityBackfillWorker 不写 PREF_DONE on paused | re-enqueue spam | 75 |
| R4-1-9 | NoteAssociationSettingsStore KDoc "默认 0.25" | doc drift | 75 |
| R4-1-10 | association 包缺 *Entry | CLAUDE.md 自包含 | 70 |
| R4-1-11 | BackfillScheduler + Worker 重复 PREFS 常量 | DRY | 65 |
| R4-1-12 | AiConstants.kt:6 注释仍写 LlmNoteLinkExtractor | doc | 60 |
| R4-1-13 | EntityBackfillWorker.observeAll().first() 全列含 content | perf | 50 |
| R4-1-14 | BackfillWorker doWork 后 `apply()` flag 写入可能丢 | 已知 apply 语义 | 50 |
| R4-1-15 | ReRunSection `paused` 仅 hint，未 disable button | UX | 60 |

## R4-1 PASS

- core/note/* 无 feature/ 反向依赖(单向 ✓)
- AI 集成走 AiGateway 抽象层(✓)
- i18n 双语对齐(✓)
- 无 wildcard import(✓)
- OpenSpec 流程合规(✓)

---

# Full Project Code Review R4-2

**日期**: 2026-06-27
**范围**: R4-1 三项 fix 后的 working tree
**触发**: 用户要求"循环 review 至少 2 遍"
**方法**: 5 个并行 review agent
**基线**: R4-1 review 后，3 fix 已 apply + `:app:check` 全绿

## R4-1 三项 fix 验证

| # | Fix | 验证结论 |
|---|---|---|
| R4-1-1 | `onPauseToggle(false)` → `scheduleEntityBackfillNow(force=false)` | ✅ correct，绕过 PREF_DONE 检查 |
| R4-1-2 | `ReRunSection` button `enabled = !isRunning && !paused` | ✅ correct |
| R4-1-3 | `ThresholdSection` `mutableFloatStateOf` 拖动追踪 | ✅ correct |

## R4-2 新 ≥80 findings

### R4-2-A · HIGH (88) · `scheduleEntityBackfillNow` 无条件 REPLACE 取消 in-flight RUNNING worker

- **文件**:`BackfillScheduler.kt:79` + `NoteAssociationSettingsViewModel.kt:101`
- **场景**:unpause 路径走 `scheduleEntityBackfillNow(force=false)` → REPLACE 取消 in-flight RUNNING → 丢 N 笔记进度
- **修法**:拆 `scheduleEntityBackfillResume()` (KEEP，绕 PREF_DONE) + `scheduleEntityBackfillNow(force=true)` (REPLACE，只给按钮)

### R4-2-B · HIGH (82) · `onPauseToggle(true)` 不 cancel 已 enqueue worker

- **文件**:`VM.kt:93-103` + `BackfillScheduler.kt`
- **场景**:App 冷启入队 ENTITY_BACKFILL(5s delay)。5s 内用户点暂停 → Worker 5s 后起跑 → red FAILED "已暂停"
- **修法**:`onPauseToggle(true)` 调 `pauseEntityBackfill()` cancel tag + 写 PREF_DONE

### R4-2-C · MED (80-82) · LLM fallback catch 静默吞错

- **文件**:`CompositeNoteLinker.kt:62-65`
- **修法**:catch 内加 `Log.w`

### R4-2-D · MED (80-92) · `workInfo.maxByOrNull { state.ordinal }` REPLACE 后选错 worker

- **文件**:`VM.kt:67`
- **修法**:filter non-terminal first

## R4-2 <80

- NoteLinkCap raw weight vs DAO aggregated
- runMigrationCheck 仅 >0.50
- CompositeNoteLinker LLM catch 静默 (R4-2-C)
- workInfo maxByOrNull (R4-2-D)
- EntityBackfillWorker PREF_DONE 不写 on paused (R4-2-B 修)
- NoteAssociationSettingsStore KDoc "0.25"
- association 包缺 *Entry
- BackfillScheduler + Worker PREFS 常量 DRY
- AiConstants.kt:6 注释 LlmNoteLinkExtractor
- EntityBackfillWorker.observeAll() perf
- BackfillWorker apply() flag 写入
- ReRunSection paused hint vs disable (R4-1-2 修)

---

# Full Project Code Review R5

**日期**: 2026-06-27
**范围**: R4-2 四项 fix 后的 working tree
**触发**: 用户要求"循环 review 至少 2 遍"(已完成 R4-1 + R4-2 + R5)
**方法**: 5 个并行 review agent
**基线**: R4-2 4 项 fix 已 apply,`:app:check` 全绿

## R4-2 4 项 fix 验证

| Fix | 验证 |
|---|---|
| R4-2-A 拆 scheduleEntityBackfillResume(KEEP) | ✅ 正确，KEEP policy 保留 in-flight worker |
| R4-2-B pauseEntityBackfill cancel + 写 PREF_DONE | ⚠️ 落地正确，但与 R2 H9 contract 冲突(见 R5-1) |
| R4-2-C LLM fallback Log.w | ✅ 正确 |
| R4-2-D workInfo 优先 non-terminal | ✅ 正确 |

## R5 ≥80 findings

### R5-1 · CRITICAL (92) · `pauseEntityBackfill` 写 PREF_DONE 违反 R2 H9 contract

- **文件**:`BackfillScheduler.kt:110-118`
- **历史**:R2 fix `e9465c0` 明确将 `PREF_ENTITY_BACKFILL_DONE` 写标志**移到 `EntityBackfillWorker.doWork` 的 success 路径**，原话:"成功后落盘完成标志，避免 BackfillScheduler.scheduleEntityBackfillIfNeeded 每次调用都发现标志为 false 而反复调度 Worker"
- **冲突**:R4-2-B 让用户在 UI 切到 pause=true 时直接写 PREF_DONE=true，此时 Worker 可能根本没跑
- **后果**:用户 pause 一次 → PREF_DONE 永远 true → `scheduleEntityBackfillIfNeeded` 冷启动永远 early-return → **用户再也没自动 backfill**
- **修法**:`pauseEntityBackfill()` 不写 PREF_DONE，只 cancel tag。`pauseBackfill()` store flag 已经在 `scheduleEntityBackfillIfNeeded` line 45 拦住冷启动 pause 路径
- **优先级**:P0

### R5-2 · HIGH (88) · `scheduleEntityBackfillNow` REPLACE 是 backfill 链路首次引入

- **历史**:backfill 链从未用过 REPLACE。`scheduleEntityBackfillNow(force=true)` 仍 REPLACE，显式按钮可接受
- **优先级**:P1

### R5-3 · HIGH (85) · `paused` StateFlow lag 允许 tap → red FAILED

- **文件**:`Screen.kt:224-230`
- **场景**:用户切暂停开关瞬间，paused StateFlow 未 propagate，按钮 enabled，用户立即点「立即重跑」→ `force=true` bypass → Worker `shouldRun=false` → red FAILED
- **修法**:VM `onReRunClick` 加 `if (assocSettings.pauseBackfill()) return`(同步读)
- **优先级**:P1

### R5-4 · MED (80) · `cancelEntityBackfill` 死代码

- **修法**:删除或重命名 `pauseEntityBackfill` 为 `cancelEntityBackfill`
- **优先级**:P1

### R5-5 · MED (80) · `getWorkInfosByTagFlow` UI fallback stale CANCELLED

- **修法**:fallback 加 `?.takeIf { it.state != CANCELLED }`
- **优先级**:P1

### R5-6 · HIGH (82) · PREF_DONE-on-pause 副作用(R5-1 的另一面)

- **修法**:R5-1 修好

### R5-7 · MED (80) · 新方法无 test

- **修法**:补 `BackfillSchedulerTest.kt`
- **优先级**:P2

## R5 <80

- #2 KEEP vs REPLACE 语义
- #3 暂停切换后 unpause 立即 tap
- #4 Worker FAILED 显示"已失败"通用文案
- #5 PREF_DONE write-on-pause 无 reset path (R5-1 修好)
- #8 Cancel 后 WorkInfo 短时间残留 flicker
- #9 prefs file naming 困惑
- #10 重复 PREFS 常量

## R5 收口

- R4-2 4 项 fix 正确落地，但 R5-1(CRITICAL)暴露 fix 设计有缺陷
- **建议立即修 R5-1**，后跟 R5-3
- 用户已达成"循环 review 至少 2 遍"要求(R4-1 + R4-2 + R5)，可停可继续