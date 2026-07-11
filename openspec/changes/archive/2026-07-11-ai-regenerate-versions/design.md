# ai-regenerate-versions — 设计

## 1. 设计目标 & 不变量

**目标**:用户对**同一 sourceText + 同一 op** 一次性产出 N 个候选版本,在底部 sheet
横评后挑一个接受;其余未选版本仍落 ai_history,但**不**替换正文。

**不变量**:

- **不变量 1**:`AiGateway.streamWritingOp(...)` 仍是单次单输出,**不改签名**。
  多版本是 VM 层串行调 N 次 `gateway`,不是 gateway 内 fork。
- **不变量 2**:`FakeAiProvider` 不出现在 main 代码路径(`remove-debug-fake-fallback` §5
  守住);多版本走真 provider,debug 与 release 行为一致。
- **不变量 3**:多版本接受 = 单版本的 `acceptReplace`(`NonCancellable` 事务 + diff 高亮
  + AI metadata 写回);只是把"接受哪个版本"由"current"变为"selectedPosition"。
- **不变量 4**:`consent / apikey / provider / model` 在 VM `start()` 入口一次性 verify
  通过后,串行调 N 次 `streamWritingOp` 时**不**重复检查;若中途 `ConsentStore.setAccepted(false)`
  被外部触发,串行循环在下一次 `start()` 才生效(同 M3 行为)。

## 2. 数据模型

### 2.1 `AiVersion` 数据类(新文件)

`feature/aiwriting/streaming/AiVersion.kt`:

```kotlin
@Immutable
data class AiVersion(
    val position: Int,                  // 0-based,版本序号
    val finalText: String,              // AI 输出(完整)
    val usage: AiStreamEvent.Usage?,    // token 用量,可空(provider 未回)
    val state: State,                   // Streaming / Done / Failed(本版本独立)
    val accumulatedLength: Int = 0      // Streaming 中间态用
) {
    enum class State { Streaming, Done, Failed }
}
```

注意:`StreamingPanel` 接收 `AiActionUiState`,**不**直接接收 `AiVersion` 列表。
`AiVersion` 是 `AiActionUiState` 的子结构。

### 2.2 `AiHistoryEntity` schema 改动

**字段新增**(v13 → v14 AutoMigration):

```kotlin
@Entity(
    tableName = "ai_history",
    indices = [
        Index("noteId"),
        Index("createdAt"),
        Index(value = ["noteId", "versionGroupId"])  // 新增联合索引
    ]
)
data class AiHistoryEntity(
    @PrimaryKey val id: String,
    val noteId: String?,
    val providerId: String,
    val model: String,
    val op: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val totalTokens: Int,
    val durationMs: Long,
    val createdAt: Long,
    val inputSnapshot: String,
    val outputSnapshot: String,
    val truncated: Boolean = false,
    val error: String? = null,
    /** 本次操作归属的版本组 id;null = 单版本(M3 行为,向后兼容)。
     *  同一 sourceText + op 一次多版本生成的 N 行共享同一非空 groupId。 */
    val versionGroupId: String? = null
)
```

**Room migration**:`AppDatabase.kt` 加:

```kotlin
AutoMigration(from = 13, to = 14)
```

Room 会自动检测 schema diff(加可空列 + 加索引),生成 ALTER TABLE + CREATE INDEX。
旧库数据无损(旧行 `versionGroupId=null` 视为"单版本",与 M3 行为等价)。

### 2.3 DAO 新增查询

`AiHistoryDao.kt`:

```kotlin
/**
 * 取同一版本组的多行(典型场景:多版本接受某一行后,UI 展示"另 2 个版本预览")
 */
@Query("""
    SELECT * FROM ai_history
    WHERE versionGroupId = :groupId
    ORDER BY createdAt ASC
""")
fun observeByVersionGroup(groupId: String): Flow<List<AiHistoryEntity>>

/**
 * 列某 note 上某 op 的所有版本组(每组取首行)
 * - 给 AI 历史页用,可选 v2 落地
 */
@Query("""
    SELECT * FROM ai_history
    WHERE noteId = :noteId AND op = :op AND versionGroupId IS NOT NULL
    GROUP BY versionGroupId
    ORDER BY createdAt DESC
""")
fun observeVersionGroupsByNote(noteId: String, op: String): Flow<List<AiHistoryEntity>>
```

(后者 v1 暂不消费,留给 AI 历史页改造;先加 SQL,UI 暂不渲染。)

### 2.4 状态机扩展

`AiActionUiState.kt`:

```kotlin
sealed interface AiActionUiState {
    data object Idle : AiActionUiState

    /** 多版本共流式期间。任一版本到达新 Delta 都 emit 一次新的 Streaming
     *  (整体列表累加),UI 渲染当前选中版本的 partialText + tab 角标进度。 */
    data class Streaming(
        val op: WritingOp,
        val versions: List<AiVersion>,           // 任一可能处于 Streaming 中间态
        val selectedPosition: Int = 0,
        val actualModel: String = ""
    ) : AiActionUiState

    /** 多版本生成中至少一个 Done,可继续等剩余版本。 */
    data class PartialDone(
        val op: WritingOp,
        val versions: List<AiVersion>,
        val selectedPosition: Int = 0
    ) : AiActionUiState

    /** 全部版本 Done(全部成功) 或 部分 Failed 但用户可挑剩余版本接受。 */
    data class Done(
        val originalText: String,
        val op: WritingOp,
        val versions: List<AiVersion>,           // size 1~3,至少 1 个 Done
        val selectedPosition: Int = 0
    ) : AiActionUiState

    data class Failed(val op: WritingOp, val error: AiError) : AiActionUiState

    /** acceptReplace 后:内容已部分替换,用户可撤回。 */
    data class Replaced(val op: WritingOp) : AiActionUiState
}
```

**新状态引入动机**:串行调 N 次,前 1~N-1 次完成时不能让 UI 一直卡在 `Streaming`
(用户得能挑已完成的版本);用 `PartialDone` 切到"可交互等剩余"。所有版本 Done → `Done`。

## 3. ViewModel 状态机扩展

`AiActionViewModel.kt` 主要改动:

### 3.1 `start(op, sourceText, noteId, versionCount = 3)`

保留 3 参 + 加默认 4 参(默认 3 个版本,UI 暂不暴露 N 选择)。

```kotlin
fun start(
    op: WritingOp,
    sourceText: String,
    noteId: String,
    versionCount: Int = 3
) {
    require(versionCount in 1..3) { "versionCount must be 1..3" }
    streamJob?.cancel()
    val currentGeneration = streamGeneration.incrementAndGet()
    lastOp = op
    lastSourceText = sourceText
    lastNoteId = noteId
    lastUsage = null
    lastOriginalContent = null
    val groupId = UUID.randomUUID().toString()
    lastVersionGroupId = groupId
    val versions = mutableListOf<AiVersion>()
    streamJob = viewModelScope.launch {
        // ... consent / apikey / provider 既有检查,不动 ...
        // 一次性 verify 后,串行跑 N 次:
        repeat(versionCount) { idx ->
            val builder = StringBuilder()
            aiGateway.streamWritingOp(op, sourceText, providerId, apikey, actualModel, systemPrompt, apiFormatOverride)
                .collect { event ->
                    // generation 比对:旧协程 race emit 丢
                    if (streamGeneration.get() != currentGeneration) return@collect
                    when (event) {
                        is AiStreamEvent.Started -> {
                            updateVersion(versions, idx, AiVersion(idx, "", null, AiVersion.State.Streaming))
                            emitPartial(versions, op, idx)
                        }
                        is AiStreamEvent.Delta -> {
                            builder.append(event.text)
                            updateVersion(versions, idx, AiVersion(idx, builder.toString(), null, AiVersion.State.Streaming))
                            emitPartial(versions, op, idx)
                        }
                        is AiStreamEvent.Usage -> updateVersion(versions, idx, AiVersion(idx, builder.toString(), event, AiVersion.State.Streaming))
                        is AiStreamEvent.Done -> {
                            updateVersion(versions, idx, AiVersion(idx, builder.toString(), lastUsage, AiVersion.State.Done))
                            emitPartial(versions, op, idx)
                        }
                        is AiStreamEvent.Failed -> {
                            updateVersion(versions, idx, AiVersion(idx, builder.toString(), null, AiVersion.State.Failed))
                            emitPartial(versions, op, idx)
                            // 不 return,继续下一个版本
                        }
                    }
                }
        }
        // 全部跑完
        val finalState = if (versions.all { it.state == AiVersion.State.Done }) {
            AiActionUiState.Done(sourceText, op, versions, selectedPosition = 0)
        } else if (versions.any { it.state == AiVersion.State.Done }) {
            AiActionUiState.Done(sourceText, op, versions, selectedPosition = versions.indexOfFirst { it.state == AiVersion.State.Done })
        } else {
            // 全部失败
            AiActionUiState.Failed(op, AiError.Unknown(null, "全部 N 个版本生成失败"))
        }
        _state.value = finalState
    }
}
```

### 3.2 新增字段

```kotlin
private var lastVersionGroupId: String? = null
```

### 3.3 `acceptReplace(position: Int = 0)`

```kotlin
fun acceptReplace(position: Int = 0) {
    val current = _state.value as? AiActionUiState.Done ?: return
    val version = current.versions.firstOrNull { it.position == position && it.state == AiVersion.State.Done } ?: return
    val noteId = lastNoteId ?: return
    val sourceText = lastSourceText ?: return
    val op = current.op
    val aiText = version.finalText
    // ... 既有 NonCancellable 事务不变,把 current.finalText 改为 aiText ...
    // 落库:noteRepository.upsert(...)
    // ai_history 落库(已有,CoreAiGateway.onCompletion 里 record() 自动落,
    //   本 change 在 record() 时把 versionGroupId 传进去 — 见 §5.2)
    // Note.lastAiOp 写入
    // widget 刷新
    // _state.value = Replaced(op)
}
```

### 3.4 `selectVersion(position: Int)` (新)

```kotlin
fun selectVersion(position: Int) {
    val current = _state.value as? AiActionUiState.Done
        ?: _state.value as? AiActionUiState.PartialDone
        ?: return
    if (position !in current.versions.indices) return
    _state.value = current.copy(selectedPosition = position)
}
```

`regenerate()` / `reject()` / `cancel()` / `dismiss()` / `retry()` 行为:
**重跑时 versionCount 仍默认 3**(行为不变);`reject` 同时把 `lastVersionGroupId` 置空但
**不删** ai_history 行(用户可在 AI 历史页查);`cancel` 跟既有语义一致(终止串行循环,
已完成的版本落库保留)。

## 4. UI 改造(`StreamingPanel.kt`)

### 4.1 Streaming 态渲染

- 顶部 HeaderRow:`<op> · 进行中... · (1/3)` 进度提示(基于 `versions.filter { Done }.size`)
- Tab Row:展示 3 个 tab,每个 tab:
  - 标题"版本 1 / 版本 2 / 版本 3"
  - 角标显示状态(✓ 完成 / ✗ 失败 / ⏳ 进行中)
  - 当前选中 tab 加粗 + underline
- 中部:`ScrollableAnnotatedBody(diffHighlight(currentVersion.finalText, originalText))`
- 底部按钮:`取消`

### 4.2 PartialDone 态渲染(新增)

- 顶部:`<op> · 已生成 X/N`
- Tab Row 同上
- 中部:选中版本 finalText + diff highlight
- 底部按钮:`取消` / `接受此版本`(若选中版本 = Done,enable)

### 4.3 Done 态渲染

- 顶部:`<op> · 完成`
- Tab Row + 中部 + 底部按钮:`拒绝全部` / `再生成` / `接受此版本`(Button)
- `reject()` 把当前 Done 状态整个重置为 Idle(不删 ai_history)

### 4.4 Failed 态

保留 M3 行为;若 N 个版本**全失败**才走 Failed + 提示文案"全部 N 个版本生成失败"。

### 4.5 Replaced 态

保留 M3 行为;`undo()` 不变。

### 4.6 新 Composable

`feature/aiwriting/streaming/VersionTabs.kt`:`@Composable private fun VersionTabs(versions, selectedPosition, onSelect)`

## 5. AI Gateway 改动

### 5.1 `CoreAiGateway` 改动

`streamWritingOp(...)` 签名**不变**。唯一改动:`onCompletion { cause -> historyRepo.get().record(...) }`
新增 `versionGroupId: String? = null` 形参,默认 null(向后兼容,旧 caller 不感知):

```kotlin
override suspend fun streamWritingOp(
    op: WritingOp,
    sourceText: String,
    providerId: String,
    apikey: String,
    modelName: String?,
    systemPrompt: String?,
    apiFormatOverride: ApiFormat?,
    versionGroupId: String? = null,  // 新增,可空
    versionPosition: Int? = null     // 新增,版本序号 0..N-1(落库 traceability)
): Flow<AiStreamEvent> { ... }
```

调用方(`AiActionViewModel`)在第 N 次调 `streamWritingOp` 时把 `groupId + position` 传过去;
旧 single-shot caller(M3 行为)不传,默认 null。

### 5.2 `AiHistoryRepository.record()` 改动

`AiHistoryRepository.record(...)` 已有形参列表新增:

```kotlin
suspend fun record(
    noteId: String?,
    providerId: String,
    model: String,
    op: String,
    inputTokens: Int,
    outputTokens: Int,
    totalTokens: Int,
    durationMs: Long,
    createdAt: Long,
    inputSnapshot: String,
    outputSnapshot: String,
    error: String?,
    versionGroupId: String? = null  // 新增
)
```

entity 落库时填入 `versionGroupId`。`AiHistoryEntity.id` 仍是 UUID,3 个版本 = 3 行
独立 id,共享 `versionGroupId`。

## 6. provider 协议层验证

**Anthropic Messages API 不支持 `n > 1`**(已确认):
- request body 无 `n` 字段
- response body 无 `choices[]` 数组(M3 只读 `delta.text`,单 output)

**结论**:多版本 = 客户端串行调 N 次 `gateway.streamWritingOp(...)`。每调一次都是独立
HTTP 请求,各自一套 `Started → Delta* → Usage → Done` 事件流。串行实现简单清晰。

**Latency 估算**:3 个版本串行,平均每次 ~5-15s(典型 LLM 流),总耗时 15-45s。v1
接受这个延迟,UX 已有 typing indicator + 进度提示(4.1)。

**Cost 估算**:3 版本 = 3× input_tokens(同 sourceText,每次重发)+ 3× output_tokens。
v1 接受代价;后续可加"单版本模式" 切换(用户主动选)。

## 7. i18n 策略(8 个 key 估计)

`values/strings.xml`(中文权威)+ `values-en/strings.xml`(TODO 占位):

| key | 中文 | 用途 |
| --- | --- | --- |
| `aiwriting_version_progress_fmt` | `%1$d/%2$d` | "1/3" 进度副标题 |
| `aiwriting_version_tab_label_fmt` | `版本 %1$d` | Tab 标题 |
| `aiwriting_version_tab_done` | `✓` | Tab 角标(Done) |
| `aiwriting_version_tab_failed` | `✗` | Tab 角标(Failed) |
| `aiwriting_version_tab_streaming` | `⏳` | Tab 角标(Streaming) |
| `aiwriting_version_all_failed` | `全部 %1$d 个版本生成失败` | Failed 文案 |
| `aiwriting_version_accept_this` | `接受此版本` | 接受按钮 |
| `aiwriting_version_reject_all` | `拒绝全部` | 拒绝按钮 |

Composable 内 MUST `stringResource(R.string.aiwriting_version_*)`;**禁止**硬编码中文。

## 8. 数据保留策略

- **ai_history 行**:**永久保留**,不做时间窗口清理(已有 `deleteOlderThan(cutoffMs)` 是
  AI 历史页定期清理功能,不归本 change 管)。
- **versionGroupId 索引**:Room AutoMigration 自动建;旧行 groupId=null,索引"不命中"
  不影响查询性能。
- **AI 历史页(v1 暂不动 UI)**:v2+ change 可加"按版本组聚合"展示,把同 groupId 多行
  折叠成一张卡,展开看 N 个版本。

## 9. 测试策略

### 9.1 JVM 单测(`app/src/test/...`)

- `AiActionViewModelTest`:
  - `start(EXPAND, ..., versionCount=3)` + 3 个 fake Done → state 终态 = `Done(versions.size=3)`
  - `start(EXPAND, ..., versionCount=3)` + 第 2 个 Failed → state 终态 = `Done(versions=Done,Failed,Done)`
  - `start(EXPAND, ..., versionCount=3)` + 全部 Failed → state 终态 = `Failed`
  - `acceptReplace(position=1)` → noteRepository.upsert 收到的 content = 第 2 个版本的 finalText
  - `acceptReplace(position=99)` 越界 → no-op
  - generation bump 测试:`start()` 后立刻再 `start()` → 第 1 次的滞后 Delta 不会覆盖第 2 次的 state

### 9.2 仪器测试(`app/src/androidTest/...`)

- 真机 + 真实 apikey(deepseek 廉价模型)+ versionCount=2 → 验证 sheet 内 2 tab + accept 第 2 个

### 9.3 静态检查

- ktlint:`./gradlew :app:ktlintCheck`(零容忍)
- Room schema export:`./gradlew :app:assembleDebug` 后 `app/schemas/com.yy.writingwithai.core.data.db.AppDatabase/14.json` 存在

## 10. 已知风险与缓解

| 风险 | 严重度 | 缓解 |
| --- | --- | --- |
| 多版本 = 3× token cost | 中 | UI 显示"将生成 3 个版本"提示(可选 v2);用户可改 N=1 |
| 串行 N 次总 latency 长(15-45s) | 中 | Streaming 态 progress 提示 + tab 角标 |
| 旧库数据迁移失败 | 低 | AutoMigration(13,14) 只加可空列 + 索引,Room 100% 兼容 |
| UI 多 tab + diff highlight 大文本性能 | 低 | 复用 M3 `LazyColumn`/滚动,每次只渲染选中版本 |
| 部分版本失败时 UI 行为 | 中 | 设计:Done 列表中 Failed 版本 tab 标 ✗,不可接受;Failed 版本单独 retry 按钮留 v2 |
| M3 `regenerate()` 现在隐式变多版本重跑 | 中 | API 兼容(`versionCount` 有默认 3),但 release note 必须标:旧"再生成"按钮现在生成 3 个版本而非 1 个 |

## 11. 落地顺序

1. **schema 先行**:`AiHistoryEntity` 加 `versionGroupId` + 索引 + `AppDatabase.kt` bump v14 + AutoMigration(13,14);Room 跑 `:app:assembleDebug` 验证 v14.json 生成
2. **DAO 查询**:`observeByVersionGroup` / `observeVersionGroupsByNote` 加 SQL + 单测
3. **AiHistoryRepository.record()** 加 `versionGroupId` 形参 + `CoreAiGateway.streamWritingOp` 透传
4. **AiVersion 数据类** + `AiActionUiState` 加新字段(Streaming / PartialDone / Done 三态都加 versions + selectedPosition)
5. **AiActionViewModel** 重写 `start(versionCount=3)` 串行循环 + `acceptReplace(position=0)` + `selectVersion(position)`
6. **StreamingPanel.kt** + 新 `VersionTabs.kt` 多 tab 渲染 + 接受按钮组
7. **i18n** 8 个 key 加 strings.xml(中英文)
8. **单测 + 仪器测试**
9. **review + 真机验证 + ktlint**

按 1→9 顺序,**严禁并行**(schema 不到位 VM 写不动,VM 不到位 UI 写不动)。