# ai-actions Delta Spec — ai-regenerate-versions

## MODIFIED Requirements

### Requirement: AiActionViewModel.start generates N candidate versions serially

`AiActionViewModel.start(op, sourceText, noteId)` MUST 加默认 4 参 `versionCount: Int = 3`
(范围 1..3)。当 `versionCount > 1`,VM MUST **串行**调 `aiGateway.streamWritingOp(...)` N 次
(`Anthropic Messages API` 不支持 `n > 1` 单次多采样,多版本必须在客户端串行实现);
每次调用共享同一 `providerId` / `apikey` / `modelName` / `systemPrompt` / `apiFormatOverride`,
仅 `versionGroupId`(同一组共享的 UUID)+ `versionPosition`(0..N-1)不同,
让 ai_history 表能按 groupId 聚合查询。

`start()` MUST 在调用 N 次 `streamWritingOp` 之前**一次性**完成
consent / apikey / provider / model 检查;后续 N 次不再重复 verify,
与 M3 `start()` 已有 consent / apikey / provider gate 路径共享。

`start()` MUST 为整组 N 次调用共享一个 `streamGeneration` generation 计数器;
同时维护 `currentVersionPosition: AtomicInteger` 跟踪当前在跑第几个版本,
旧版本滞后到达的 `Delta` / `Failed` 事件 MUST NOT 覆盖新版本的 state(generation 比对后丢弃)。

#### Scenario: start() 默认生成 3 个版本
- **WHEN** UI 调用 `viewModel.start(EXPAND, sourceText="晨跑", noteId="n1")`(默认 versionCount=3)
- **THEN** `aiGateway.streamWritingOp(EXPAND, "晨跑", ..., versionGroupId=<同一 UUID>, versionPosition=0/1/2)` 依次被订阅 3 次;每次的 `versionGroupId` 相同,`versionPosition` 0 → 1 → 2

#### Scenario: start() 串行版本 1 失败不阻塞 2/3
- **WHEN** `start(versionCount=3)` 调用,版本 1 第 2 个 Delta 后 emit `Failed(Network)`
- **THEN** VM 继续订阅版本 2 的 `streamWritingOp(...)`,版本 1 状态标记 `Failed`,不影响版本 2/3 的进度

#### Scenario: start() versionCount=1 退化为单版本(M3 行为)
- **WHEN** UI 调用 `viewModel.start(EXPAND, ..., versionCount=1)`
- **THEN** VM 仅订阅 1 次 `streamWritingOp(...)`,`versionGroupId=null`(向后兼容 M3 行为);`AiActionUiState.Done` 的 `versions` 列表 size=1

#### Scenario: versionCount 越界拒绝
- **WHEN** UI 调用 `viewModel.start(EXPAND, ..., versionCount=0)` 或 `versionCount=4`
- **THEN** VM 立即抛 `IllegalArgumentException("versionCount must be 1..3")`,不发起任何 AI 调用

#### Scenario: 串行版本间 consent 不重复检查
- **WHEN** `start(versionCount=3)` 已在入口 verify `consentFlow.value.accepted=true`,版本 2 进行中用户调 `ConsentStore.setAccepted(false)`
- **THEN** 版本 2/3 的 `streamWritingOp` 不会被中断(本组 generation 仍有效);只有下一次 `start()` 才生效新 consent 状态

### Requirement: AiActionUiState exposes versions list and selectedPosition

`AiActionUiState.Streaming` / `PartialDone` / `Done` MUST 携带字段:
- `versions: List<AiVersion>`(任一版本可能处于 `Streaming` / `Done` / `Failed`)
- `selectedPosition: Int`(0..N-1,UI 当前选中的版本号)

新增状态 `PartialDone`(N 次版本中有部分完成、部分进行中、部分失败,用户可挑已完成的
版本提前接受,不必等全部跑完)。`Done` 是全部跑完的终态(全部 Done / 部分 Done +
部分 Failed)。

`AiVersion` 是新 `data class`(`feature/aiwriting/streaming/AiVersion.kt`),字段:
`{ position: Int, finalText: String, usage: AiStreamEvent.Usage?, state: State, accumulatedLength: Int }`
(`State` 枚举 `Streaming / Done / Failed`)。

#### Scenario: Done 态含 N 个版本列表
- **WHEN** `start(versionCount=3)` 完成后 3 个版本均 Done
- **THEN** `state.value = Done(originalText, op, versions=[v0, v1, v2], selectedPosition=0)`,`versions.size == 3`

#### Scenario: PartialDone 态 1 个 Done + 2 个 Streaming
- **WHEN** `start(versionCount=3)` 进行中,版本 0 已 Done,版本 1/2 仍在 Streaming
- **THEN** `state.value = PartialDone(op, versions=[Done, Streaming, Streaming], selectedPosition=0)`,UI 允许用户"接受版本 0"提前退出

#### Scenario: Done 态部分失败
- **WHEN** `start(versionCount=3)` 完成,版本 0 Done / 版本 1 Failed / 版本 2 Done
- **THEN** `state.value = Done(versions=[Done, Failed, Done], selectedPosition=0)`(默认选第 1 个 Done);Failed 版本 tab 显示 ✗ 角标,**不**可接受

#### Scenario: 全部 Failed 走 Failed 态
- **WHEN** `start(versionCount=3)` 完成,3 个版本均 Failed
- **THEN** `state.value = Failed(op, AiError.Unknown(detail="全部 3 个版本生成失败"))`,UI 显示"全部 3 个版本生成失败" + 重试按钮(走 `retry()` 重新跑整组)

### Requirement: AiActionViewModel.selectVersion and acceptReplace(position)

`AiActionViewModel` MUST 暴露:
- `fun selectVersion(position: Int)` — 在 `Done` 或 `PartialDone` 态下切换 `selectedPosition`;
  越界或非 Done/PartialDone 态 MUST no-op
- `fun acceptReplace(position: Int = 0)` — 接受指定版本的输出替换正文;
  越界或该版本非 `Done` 态 MUST no-op(防止用户点 Failed 版本 tab 的接受按钮)

`acceptReplace(position)` MUST 复用 M3 `acceptReplace()` 的 `withContext(NonCancellable)`
事务路径:读 note → `note.copy(content=version.finalText, updatedAt=now)` →
`noteRepository.upsert(updated, tags)` → `noteRepository.updateAiMetadata(noteId, op.name.lowercase(), now)` →
`widgetUpdater.updateAll(context)` → state 转 `Replaced(op)`。
仅把替换文本从 `current.finalText` 改为 `versions[position].finalText`。

#### Scenario: acceptReplace(0) 默认接受第 1 个版本
- **WHEN** state = `Done(versions=[v0, v1, v2], selectedPosition=0)`,用户点"接受此版本"(button 不带 position)
- **THEN** `acceptReplace()`(position 默认 0)被调,笔记 content 替换为 `v0.finalText`,`lastAiOp="expand"` 写入

#### Scenario: acceptReplace(2) 接受第 3 个版本
- **WHEN** state = `Done(versions=[v0, v1, v2], selectedPosition=2)`,用户点 tab 3 + "接受此版本"
- **THEN** `acceptReplace(position=2)` 被调,笔记 content 替换为 `v2.finalText`

#### Scenario: selectVersion 越界 no-op
- **WHEN** state = `Done(versions=[v0, v1, v2])`,用户调 `viewModel.selectVersion(99)`
- **THEN** `_state.value` 不变,UI 仍渲染 `selectedPosition=0`

#### Scenario: Failed 版本 tab 的接受按钮 no-op
- **WHEN** state = `Done(versions=[Done, Failed, Done], selectedPosition=1)`,用户点"接受此版本"
- **THEN** `acceptReplace(position=1)` 检测到 versions[1].state == Failed,no-op,UI 不替换正文

### Requirement: StreamingPanel renders version tabs and accept-this-version actions

`StreamingPanel` MUST 在 `Streaming` / `PartialDone` / `Done` 态下渲染 `VersionTabs` Composable
(放 HeaderRow 与中部 ScrollableBody 之间),`VersionTabs` MUST 是 `TabRow` 或 `SecondaryTabRow`,
每个 tab MUST 包含:
- 标题(`版本 %d`,走 `R.string.aiwriting_version_tab_label_fmt`)
- 角标:Done = ✓(走 `R.string.aiwriting_version_tab_done`)/ Failed = ✗(走 `R.string.aiwriting_version_tab_failed`)
  / Streaming = ⏳(走 `R.string.aiwriting_version_tab_streaming`)
- 当前 `selectedPosition` 加粗 + underline 表达选中态

中部 MUST 渲染**当前 selectedPosition 版本**的 finalText + diff highlight(`diffHighlight`
函数复用 M3 既有逻辑)。

底部按钮 MUST 区分状态:
| 状态 | 底部按钮 |
| --- | --- |
| `Streaming` | `取消` |
| `PartialDone` 且 selectedPosition 版本 = Done | `取消` + `接受此版本`(Button,enable) |
| `PartialDone` 且 selectedPosition 版本 = Failed | `取消` |
| `Done` 且 selectedPosition 版本 = Done | `拒绝全部` + `再生成` + `接受此版本`(Button,enable) |
| `Done` 且 selectedPosition 版本 = Failed | `拒绝全部` + `再生成`(接受按钮 enable=false) |

HeaderRow 进度副标题:`<op> · 已生成 X/N` 走 `R.string.aiwriting_version_progress_fmt`
(%1$d=已完成 Done 数,%2$d=总版本数)。

`reject()` MUST 走 M3 既有路径(`_state.value = Idle`),不删除 ai_history 行;
`regenerate()` MUST 复用 `lastOp / lastSourceText / lastNoteId / versionCount` 重跑(默认 3)。

#### Scenario: Done 态默认选第 1 个版本 + diff 高亮
- **WHEN** state = `Done(versions=[v0, v1, v2], selectedPosition=0)`,v0.finalText="扩写后 A"
- **THEN** HeaderRow 显示"扩写 · 完成 · 1/3"(X=已完成,N=3);VersionTabs 3 个 tab,tab 0 加粗;中部渲染 v0 与 originalText 的 diff 高亮;底部"拒绝全部 / 再生成 / 接受此版本"3 个按钮

#### Scenario: 用户切到版本 2 + 接受
- **WHEN** 用户点 tab 2 → `viewModel.selectVersion(position=2)` 被调,`state.value = Done(... selectedPosition=2)`
- **THEN** HeaderRow progress 不变;VersionTabs tab 2 加粗;中部重渲 v2 的 finalText + diff;底部接受按钮仍 enable

#### Scenario: Failed 版本 tab 显示 ✗ + 接受按钮 disabled
- **WHEN** state = `Done(versions=[Done, Failed, Done], selectedPosition=1)`
- **THEN** VersionTabs tab 1 显示 ✗ 角标(非 ✓);底部"接受此版本"按钮 enable=false(灰);点击 no-op

#### Scenario: PartialDone 态 1/3 完成可提前接受
- **WHEN** state = `PartialDone(versions=[Done, Streaming, Streaming], selectedPosition=0)`
- **THEN** HeaderRow 显示"扩写 · 已生成 1/3";VersionTabs tab 0 ✓ / tab 1,2 ⏳;底部"接受此版本"Button enable(选中 tab 是 Done);用户点接受 → 第 1 个版本的 finalText 替换正文,剩余 2 个版本继续后台跑(协程不强制取消,本组 generation 仍有效)

#### Scenario: 拒绝全部回到 Idle
- **WHEN** state = `Done(versions=[v0, v1, v2])`,用户点"拒绝全部"
- **THEN** `viewModel.reject()` 被调,`_state.value = Idle`,ModalBottomSheet 关闭;3 个版本的 ai_history 行保留(用户后续可在 AI 历史页查)

### Requirement: AiHistoryEntity tracks versionGroupId for cross-version queries

`AiHistoryEntity` MUST 新增可空字段 `versionGroupId: String?`;同一 sourceText + op
一次多版本生成的 N 行 MUST 共享同一非空 `versionGroupId`(UUID 格式);单版本
(M3 行为,`versionCount=1`)的 `versionGroupId` MUST 为 `null`,与既有数据兼容。

`AiHistoryEntity` MUST 新增联合索引 `@Index(value = ["noteId", "versionGroupId"])`
加速按 note + group 查询。

`AiHistoryDao` MUST 新增:
- `fun observeByVersionGroup(groupId: String): Flow<List<AiHistoryEntity>>` —
  按 groupId 升序返回所有同组版本行(典型场景:多版本接受某行后,UI 展示"另 2 个版本预览")
- `fun observeVersionGroupsByNote(noteId: String, op: String): Flow<List<AiHistoryEntity>>` —
  列某 note 上某 op 的所有版本组首行(AI 历史页聚合展示用,v1 UI 暂不渲染但 SQL 已落)

`Room AutoMigration(13, 14)` MUST 自动添加 `versionGroupId` 可空列 + 联合索引,
既有 v13 数据无损(旧行 `versionGroupId=null`,视为"单版本")。

#### Scenario: 3 个版本共享同一 groupId
- **WHEN** `start(EXPAND, "晨跑", "n1", versionCount=3)` 完成,3 次 `streamWritingOp` 各 emit Done
- **THEN** `ai_history` 表新增 3 行,3 行 `versionGroupId` 字段**完全相同**(同一 UUID),`noteId="n1"`,`op="expand"`;每行 `id` 独立 UUID

#### Scenario: 单版本 v1 兼容(groupId=null)
- **WHEN** UI 调用 `viewModel.start(EXPAND, ..., versionCount=1)`(M3 行为)
- **THEN** ai_history 新增 1 行,`versionGroupId=null`(与既有 v13 单版本数据 schema 一致)

#### Scenario: observeByVersionGroup 取出整组
- **WHEN** ai_history 表有 groupId="g-abc" 的 3 行,UI collect `dao.observeByVersionGroup("g-abc")`
- **THEN** Flow emit List 大小为 3,按 `createdAt ASC` 排序(第 1 个版本最早)

#### Scenario: observeVersionGroupsByNote 取每组首行
- **WHEN** noteId="n1" + op="expand" 在 ai_history 有 2 组(groupA 3 行、groupB 2 行),共 5 行
- **THEN** `observeVersionGroupsByNote("n1", "expand")` emit List 大小为 2(每组最早一行,按 `createdAt DESC` 排序)

#### Scenario: Room v13 → v14 AutoMigration 无损升级
- **WHEN** 既有用户从 v13 升到 v14 安装新版 APK
- **THEN** `ai_history.versionGroupId` 列被添加(默认 NULL);既有行的 `versionGroupId=null`;
  Room AutoMigration(13,14) 自动完成,无数据丢失

### Requirement: CoreAiGateway passes versionGroupId through to history record

`CoreAiGateway.streamWritingOp(...)` MUST 新增 2 个可空形参:
- `versionGroupId: String? = null`(默认 null,向后兼容 M3 调用方)
- `versionPosition: Int? = null`(默认 null,0..N-1,落库 traceability)

调用方(`AiActionViewModel`)在第 N 次调用 `streamWritingOp` 时把 `versionGroupId`
(同组共享 UUID)+ `versionPosition`(0/1/2)传入;`AiHistoryRepository.record()` MUST
把这些字段写入对应 `AiHistoryEntity` 行;旧 caller(单版本调用,M3 既有路径)不传,默认
`versionGroupId=null`,行为不变。

#### Scenario: 多版本落库带 groupId
- **WHEN** VM 第 2 次调 `streamWritingOp(..., versionGroupId="g-abc", versionPosition=1)`
- **THEN** `AiHistoryRepository.record(...)` 写入 ai_history 表的 1 行,`versionGroupId="g-abc"`,该行其它字段同 M3 行为(providerId/model/inputTokens/outputTokens/...)

#### Scenario: 单版本 caller 不传 groupId
- **WHEN** 旧 caller(若有)调 `streamWritingOp(..., versionGroupId=null, versionPosition=null)`(默认值)
- **THEN** ai_history 表的对应行 `versionGroupId=null`,`versionPosition=null`,与 v13 schema 行为一致

### Requirement: i18n coverage for version tabs

所有多版本相关 UI 文案 MUST 出现在 `values/strings.xml`(中文权威)+ `values-en/strings.xml`(TODO 占位),
命名空间 `aiwriting_version_*`,至少 8 个 key:

| key | 中文 | 用途 |
| --- | --- | --- |
| `aiwriting_version_progress_fmt` | `%1$d/%2$d` | "1/3" 进度副标题 |
| `aiwriting_version_tab_label_fmt` | `版本 %1$d` | Tab 标题 |
| `aiwriting_version_tab_done` | `✓` | Tab 角标(Done) |
| `aiwriting_version_tab_failed` | `✗` | Tab 角标(Failed) |
| `aiwriting_version_tab_streaming` | `⏳` | Tab 角标(Streaming) |
| `aiwriting_version_all_failed_fmt` | `全部 %1$d 个版本生成失败` | Failed 态文案 |
| `aiwriting_version_accept_this` | `接受此版本` | 接受按钮 |
| `aiwriting_version_reject_all` | `拒绝全部` | 拒绝按钮 |

Composable 内 MUST 通过 `stringResource(R.string.aiwriting_version_*)` 引用;**禁止**硬编码中文 / 英文。

#### Scenario: 中文文案来自 R.string
- **WHEN** 系统语言为中文,`StreamingPanel` 渲染 VersionTabs tab 0
- **THEN** tab 标题 `Text("版本 1")`,走 `stringResource(R.string.aiwriting_version_tab_label_fmt, 1)`,源码 grep 不到中文字面量

#### Scenario: 英文 TODO 占位不阻断构建
- **WHEN** 系统语言为英文,`values-en/strings.xml` 中 `aiwriting_version_tab_label_fmt = "TODO(en): aiwriting_version_tab_label_fmt"`
- **THEN** APK 仍能正常构建并启动,运行时显示占位文本;`./gradlew :app:assembleDebug` 与 `:app:check` 全部通过

#### Scenario: Failed 态文案带版本数
- **WHEN** state = `Failed(op, AiError.Unknown(detail="全部 3 个版本生成失败"))`,versionCount=3
