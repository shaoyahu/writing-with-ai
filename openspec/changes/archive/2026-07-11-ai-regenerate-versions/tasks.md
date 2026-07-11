# ai-regenerate-versions Tasks

按 schema → DAO → gateway → VM → UI 顺序;**严禁并行**(上游不到位下游写不动)。

## Phase 1:schema + DAO(0.5 天)

### T1.1 `AiHistoryEntity` 加 `versionGroupId` 字段 + 联合索引
- **文件**:`app/src/main/java/com/yy/writingwithai/core/data/db/entity/AiHistoryEntity.kt`
- **改动**:新增 `val versionGroupId: String? = null`;`@Index` 列表新增
  `Index(value = ["noteId", "versionGroupId"])`
- **验证**:`./gradlew :app:compileDebugKotlin` 通过
- **状态**:done

### T1.2 `AppDatabase.kt` bump v14 + AutoMigration(13, 14)
- **文件**:`app/src/main/java/com/yy/writingwithai/core/data/db/AppDatabase.kt`
- **改动**:`version = 13` → `version = 14`;`autoMigrations` 列表新增
  `AutoMigration(from = 13, to = 14)`(让 Room 自动检测 schema diff 生成 ALTER)
- **验证**:`./gradlew :app:assembleDebug` 通过 +
  `app/schemas/com.yy.writingwithai.core.data.db.AppDatabase/14.json` 自动生成
- **状态**:done

### T1.3 `AiHistoryDao.kt` 加 `observeByVersionGroup` + `observeVersionGroupsByNote` 查询
- **文件**:`app/src/main/java/com/yy/writingwithai/core/data/db/AiHistoryDao.kt`
- **改动**:新增 2 个 `@Query` 方法,SQL 见 design.md §2.3
- **验证**:`./gradlew :app:compileDebugKotlin` 通过
- **状态**:done

## Phase 2:gateway 透传 versionGroupId(0.5 天)

### T2.1 `AiHistoryRepository.record()` 加 `versionGroupId` 形参
- **文件**:`core/data/repo/AiHistoryRepository.kt`(M2 既有,先 find 实际路径)
- **改动**:`record(...)` 形参列表新增 `versionGroupId: String? = null`,内部
  `dao.insert(AiHistoryEntity(..., versionGroupId = versionGroupId))`
- **验证**:`./gradlew :app:compileDebugKotlin` 通过
- **状态**:done

### T2.2 `CoreAiGateway.streamWritingOp()` 加 `versionGroupId` / `versionPosition` 形参
- **文件**:`app/src/main/java/com/yy/writingwithai/core/ai/CoreAiGateway.kt`
- **改动**:`streamWritingOp(...)` 形参列表追加 `versionGroupId: String? = null,
  versionPosition: Int? = null`;`onCompletion { historyRepo.get().record(..., versionGroupId,
  versionPosition) }` 透传给 `record()`
- **验证**:`./gradlew :app:compileDebugKotlin` 通过
- **状态**:done

### T2.3 `AiGateway` 接口签名同步
- **文件**:`core/ai/api/AiGateway.kt`(M2 既有)
- **改动**:抽象接口 `streamWritingOp(...)` 同步加 2 个可空形参
- **验证**:`./gradlew :app:compileDebugKotlin` 通过(无 caller 报错)
- **状态**:done

## Phase 3:AiVersion 数据类 + UiState 扩展(0.5 天)

### T3.1 新建 `AiVersion.kt` 数据类
- **文件**:`app/src/main/java/com/yy/writingwithai/feature/aiwriting/streaming/AiVersion.kt`(新文件)
- **改动**:见 design.md §2.1
- **验证**:`./gradlew :app:compileDebugKotlin` 通过
- **状态**:done

### T3.2 `AiActionUiState.kt` 加 `versions` / `selectedPosition` + 新增 `PartialDone`
- **文件**:`app/src/main/java/com/yy/writingwithai/feature/aiwriting/streaming/AiActionUiState.kt`
- **改动**:`Streaming` / `Done` 加 `versions: List<AiVersion>` + `selectedPosition: Int`;
  新增 `PartialDone` 态;`Failed` 不变;`Replaced` 不变
- **验证**:`./gradlew :app:compileDebugKotlin` 通过
- **状态**:done

## Phase 4:ViewModel 状态机扩展(1 天)

### T4.1 `AiActionViewModel.kt` 加 `lastVersionGroupId` 字段 + generation 计数
- **文件**:`app/src/main/java/com/yy/writingwithai/feature/aiwriting/streaming/AiActionViewModel.kt`
- **改动**:
  - 新增 `private var lastVersionGroupId: String? = null`
  - 新增 `private val currentVersionPosition = AtomicInteger(0)`(追踪当前在跑第几个)
- **验证**:`./gradlew :app:compileDebugKotlin` 通过
- **状态**:done

### T4.2 `start()` 重写为串行 N 次版本
- **文件**:同上
- **改动**:`fun start(op, sourceText, noteId, versionCount: Int = 3)` 重写主体:
  - `require(versionCount in 1..3)`
  - `val groupId = UUID.randomUUID().toString()`
  - 一次性 verify consent/apikey/provider(model 复用既有逻辑)
  - `repeat(versionCount) { idx -> streamWritingOp(..., versionGroupId=groupId, versionPosition=idx).collect { ... } }`
  - 每版本 emit 实时 `Streaming` / `PartialDone` / `Done`
  - 全部跑完判断:`all Done` → Done / `any Done` → Done(部分 Failed)/ `none Done` → Failed
- **验证**:`./gradlew :app:compileDebugKotlin` 通过 + JVM 单测通过(见 T7.x)
- **状态**:done

### T4.3 `selectVersion(position: Int)` + `acceptReplace(position: Int = 0)` 加 position 形参
- **文件**:同上
- **改动**:
  - 新增 `fun selectVersion(position: Int)`(越界或非 Done/PartialDone → no-op)
  - `acceptReplace()` 重载 + 加 `position` 形参;选非 Done 版本 → no-op;复用既有 NonCancellable 事务
- **验证**:`./gradlew :app:compileDebugKotlin` 通过 + JVM 单测通过
- **状态**:done

### T4.4 `regenerate()` / `retry()` 复用 `versionCount`
- **文件**:同上
- **改动**:`regenerate()` / `retry()` 内 `start(...)` 调用透传 `versionCount` 默认 3
- **验证**:`./gradlew :app:compileDebugKotlin` 通过
- **状态**:done

## Phase 5:UI 多版本 Tab + 接受任意版本(1 天)

### T5.1 新建 `VersionTabs.kt` Composable
- **文件**:`app/src/main/java/com/yy/writingwithai/feature/aiwriting/streaming/VersionTabs.kt`(新文件)
- **改动**:`@Composable private fun VersionTabs(versions, selectedPosition, onSelect)` —
  `SecondaryTabRow` + 3 个 `Tab`,tab 文案 = `版本 $position` + 角标;选中 tab 加粗
- **验证**:`./gradlew :app:compileDebugKotlin` 通过
- **状态**:done

### T5.2 `StreamingPanel.kt` 渲染 VersionTabs + 多状态按钮组
- **文件**:`app/src/main/java/com/yy/writingwithai/feature/aiwriting/streaming/StreamingPanel.kt`
- **改动**:
  - `Streaming` 态:HeaderRow 加进度副标题(走 `R.string.aiwriting_version_progress_fmt`),
    中部插入 `VersionTabs`,底部仅"取消"
  - `PartialDone` 态:HeaderRow + VersionTabs + 中部 diff(选中版本)+ 底部"取消 / 接受此版本"
    (选中 Done 时 enable)
  - `Done` 态:HeaderRow + VersionTabs + 中部 diff + 底部"拒绝全部 / 再生成 / 接受此版本"
    (选中 Failed 时 enable=false)
  - `Failed` 态:保留 M3 既有;若 error 含"全部 N 个版本生成失败"走 `R.string.aiwriting_version_all_failed_fmt`
- **验证**:`./gradlew :app:compileDebugKotlin` 通过
- **状态**:done

### T5.3 接受按钮 onClick 接 `acceptReplace(position)` 透传
- **文件**:同上
- **改动**:Done 态"接受此版本"按钮 `onClick = { onAccept(selectedPosition) }`;
  `StreamingPanel` 形参 `onAccept: (Int) -> Unit` 替代既有 `onAccept: () -> Unit`;
  详情屏侧 `AiwritingEntry` 包装层同步更新签名
- **验证**:`./gradlew :app:compileDebugKotlin` 通过
- **状态**:done

## Phase 6:i18n 8 个 key(0.25 天)

### T6.1 `values/strings.xml` 加 8 个 key(中文权威)
- **文件**:`app/src/main/res/values/strings.xml`
- **改动**:追加 8 个 `aiwriting_version_*` key(内容见 design.md §7)
- **验证**:`./gradlew :app:ktlintCheck` 0 violations
- **状态**:done

### T6.2 `values-en/strings.xml` 同步 TODO 占位
- **文件**:`app/src/main/res/values-en/strings.xml`
- **改动**:8 个 key 同步加,值为 `TODO(en): aiwriting_version_xxx`
- **验证**:`./gradlew :app:assembleDebug` 仍能构建
- **状态**:done

## Phase 7:测试(0.5 天)

### T7.1 `AiActionViewModelTest`:versionCount=3 全成功
- **文件**:`app/src/test/java/com/yy/writingwithai/feature/aiwriting/streaming/AiActionViewModelTest.kt`
- **改动**:新增用例 `start(versionCount=3) + 3 个 fake Done` → 终态 = `Done(versions.size=3, selectedPosition=0)`
- **验证**:`./gradlew :app:testDebugUnitTest` 通过
- **状态**:done

### T7.2 `AiActionViewModelTest`:部分版本失败
- **改动**:新增用例 `start(versionCount=3) + 第 2 个 Failed` → 终态 = `Done(versions=[Done,Failed,Done], selectedPosition=0)`
- **验证**:`./gradlew :app:testDebugUnitTest` 通过
- **状态**:done

### T7.3 `AiActionViewModelTest`:全部版本失败
- **改动**:新增用例 `start(versionCount=3) + 全部 Failed` → 终态 = `Failed(op, Unknown("全部 3 个版本生成失败"))`
- **验证**:`./gradlew :app:testDebugUnitTest` 通过
- **状态**:done

### T7.4 `AiActionViewModelTest`:acceptReplace(position=2)
- **改动**:新增用例 `acceptReplace(position=2)` → `noteRepository.upsert` 收到的 content = 第 3 个版本的 finalText
- **验证**:`./gradlew :app:testDebugUnitTest` 通过
- **状态**:done

### T7.5 `AiActionViewModelTest`:acceptReplace 越界 / Failed 版本 no-op
- **改动**:新增用例 `acceptReplace(position=99)` + `acceptReplace(Failed 版本位置)` → upsert 0 次,state 不变
- **验证**:`./gradlew :app:testDebugUnitTest` 通过
- **状态**:done

### T7.6 `AiHistoryDaoTest`:observeByVersionGroup
- **文件**:`app/src/test/java/com/yy/writingwithai/core/data/db/AiHistoryDaoTest.kt`(若不存在则新建)
- **改动**:插入 3 行同 groupId + 2 行另一 groupId → `observeByVersionGroup("g-abc").first().size == 3`
- **验证**:`./gradlew :app:testDebugUnitTest` 通过
- **状态**:done

## Phase 8:review + 真机验证(0.5~1 天)

### T8.1 ktlint 全清
- **命令**:`./gradlew :app:ktlintCheck`
- **修复**:`./gradlew :app:ktlintFormat` 自动修不可读项 + 手动修剩余
- **验证**:`./gradlew :app:ktlintCheck` 0 violations
- **状态**:done

### T8.2 全量编译 + check
- **命令**:`./gradlew :app:assembleDebug :app:check`
- **验证**:BUILD SUCCESSFUL
- **状态**:done

### T8.3 真机验证多版本
- **前置**:真机连真 provider(deepseek 廉价模型)
- **步骤**:
  1. 详情屏选中文本点扩写 → 等 3 个版本生成完
  2. VersionTabs 3 个 tab 可见,默认 tab 1 选中
  3. 切到 tab 3 → 中部文本 / token chip 同步更新
  4. 点"接受此版本"→ 笔记正文换成第 3 个版本输出
  5. 拒绝路径:再跑一次,点"拒绝全部" → sheet 关闭,正文不变
  6. 重复 run,中途 cancel → ai_history 落库行保留
- **验证**:截图 + 操作日志写进 review report
- **状态**:done

### T8.4 旧库升级 v13 → v14 验证
- **前置**:用 v13 数据初始化 Room(旧 APK 跑一次插几条 ai_history)+ 安装 v14 APK
- **步骤**:启动 → Room AutoMigration 自动迁移;进入 AI 历史页,既有 ai_history 行 `versionGroupId=null` 显示正常
- **验证**:无 crash;`app/schemas/.../14.json` 与 `13.json` diff 符合预期(只加可空列 + 索引)
- **状态**:done

### T8.5 提交 + 归档
- **步骤**:
  1. `./gradlew :app:ktlintCheck :app:assembleDebug :app:testDebugUnitTest :app:check` 全绿
  2. 用户 review 反馈(等用户主动 review)
  3. 走 `/opsx:archive` 把 change 归档到 `openspec/changes/archive/2026-07-10-ai-regenerate-versions/`
- **状态**:done

---

## 总耗时估计

| Phase | 任务数 | 估计耗时 |
| --- | --- | --- |
| 1: schema + DAO | 3 | 0.5 天 |
| 2: gateway 透传 | 3 | 0.5 天 |
| 3: 数据类 + UiState | 2 | 0.5 天 |
| 4: ViewModel 状态机 | 4 | 1 天 |
| 5: UI Tab + 按钮 | 3 | 1 天 |
| 6: i18n | 2 | 0.25 天 |
| 7: 测试 | 6 | 0.5 天 |
| 8: review + 真机 | 5 | 0.5-1 天 |
| **合计** | **28** | **3-4 天** |

## 风险 & 缓解(tasks 视角)

| 风险 | 涉及 task | 缓解 |
| --- | --- | --- |
| T1.1/T1.2 Room schema bump 后编译失败 | T1.1, T1.2 | 跑 `./gradlew :app:assembleDebug` 后检查 `app/schemas/` 是否有 v14.json;若 Room 检测 schema diff 失败,手动写 `Migration(13, 14)` |
| T4.2 串行 N 次协程取消 | T4.2 | `streamJob?.cancel()` 仍能终止循环;每次 `collect` 末尾检查 `streamGeneration.get() != currentGeneration` 即 return |
| T5.2 TabRow 与 ModalBottomSheet 嵌套 | T5.2 | 用 `SecondaryTabRow`(M3 Compose-bom 已有);不下沉到 `LazyColumn`,避免 recompose 频繁 |
| T8.4 旧库升级失败 | T8.4 | AutoMigration(13,14) 是加可空列 + 加索引,Room 100% 兼容;若失败 fallback 写 `Migration(13, 14)` 手写 ALTER TABLE |