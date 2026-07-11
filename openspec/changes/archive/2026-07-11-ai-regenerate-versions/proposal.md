# ai-regenerate-versions:AI 操作多版本生成 + 横评接受

## Why

M3 起 `AiActionViewModel.regenerate()` 已存在(`feature/aiwriting/streaming/AiActionViewModel.kt:373`),
用户每次点"再生成"会用同一 `op` / `sourceText` / `noteId` 重跑 `start(...)`,**只保留最后
一次结果**,中间几次直接被覆盖。用户实际体验痛点:

- LLM 单次输出方差大,第一次不满意只能反复点"再生成"猜测;
- 多次再生成之间无法横向对比哪条更合适;
- 同一 sourceText 上同一 op 跑了 3 次,只能看到最后 1 次,前 2 次被静默丢;
- ai_history 表虽然每次都落库,但只通过"AI 历史"页可见,详情屏完全感知不到。

本 change 让用户对**同一 sourceText + 同一 op** 一次性产出 2-3 个候选版本,
在底部 sheet 内**横评**后挑一个接受(或不接受)。Anthropic Messages API **不支持
`n > 1` 单次多采样**,所以多版本是**客户端串行调 gateway 多次**实现的。

## What Changes

- **新增候选版本数据模型** `AiVersion`:`{ id, noteId, op, sourceText, output, usage, createdAt, position }`
  - 在 `AiActionUiState` 新增 `versions: List<AiVersion>` 字段 + `selectedPosition: Int` 字段
  - `Done` 态扩为带列表的多版本展示
- **VM 状态机扩展**(`AiActionViewModel.kt`):
  - `start(op, sourceText, noteId, versionCount=2)` 启动后**串行**调 `AiGateway.streamWritingOp` N 次
  - 每完成 1 次 emit 一个 candidate,UI 实时累加显示(N=2~3 推荐默认 3)
  - 任一 candidate 失败 → 继续跑后续版本(用户看到"第 1 个失败但第 2、3 个成功"),全部失败 → Failed
  - `acceptReplace(position)` 接受指定版本的输出(`acceptReplace()` 接受 current 默认 position 0)
  - `reject()` 拒绝全部版本
- **UI 改造**(`StreamingPanel.kt`):
  - Done 态从"单 finalText"改为"多版本切换 Tab + 接受任意版本的按钮组"
  - HeaderRow 加 SegmentedButton 或 Tab Row 切版本(2-3 个版本 chip + 当前选中加粗 + token 用量)
  - 底部按钮:接受(选中版本)/ 拒绝全部 / 再生成(整体重跑)
  - 新增"对比模式"toggle(可选 v1 不实现):切换 diff 显示两版本之间差异
- **数据库 schema 改动**(`core/data/db/entity/AiHistoryEntity.kt`):
  - 新增 `versionGroupId: String?` 字段(同一 sourceText+op 一次多版本生成的 N 行共享同一 groupId)
  - 联合索引 `(noteId, versionGroupId)` 用于快速拉取"同一 sourceText+op 的所有版本"
  - Room schema bump 到 v14 + AutoMigration(13, 14)
- **DAO 新增查询**(`AiHistoryDao.kt`):
  - `observeByVersionGroup(groupId: String): Flow<List<AiHistoryEntity>>` — 取整组多版本
  - `observeVersionGroupsByNote(noteId: String, op: String): Flow<List<AiHistoryEntity>>` — 列出 note 上某 op 的所有版本组(AI 历史页用,可选 v2 实现)
- **i18n** 新增 8 个 key(均落到 `R.string.aiwriting_version_*`,禁止硬编码中文)
- **脱敏不变**:`FakeAiProvider` 不再 main 代码用;只在 JVM 单测出现(`remove-debug-fake-fallback` 守住);多版本调用走真 provider
- **不动**:`AiGateway.streamWritingOp` 签名 / `AiError` / `WritingOp` / `DefaultPrompts` / 详情屏 FAB 触发流程

## Non-Goals

- 不实现 LLM 端 `n > 1` 单次多采样(Anthropic Messages API 不支持,只能串行调)
- 不实现候选版本之间的"打分 / 排序"(纯 LLM judge 太重,且 v1 用户人工挑)
- 不实现"对比模式"(v1 每个版本单独 diff highlight,横排对比留 v2)
- 不在多版本场景下切换 model/provider(同一组共享同一 provider + model)
- 不撤回已生成但被拒绝的某个版本(用户拒绝 = 全部清掉,落库仍保留供历史回看)
- 不为多版本单独加 cost 估算(每行已有 `inputTokens/outputTokens/totalTokens`,header token 用量保持为当前选中版本)
- 不实现"撤销接受"(已有 M3 的 `undo()` 接受后撤回,行为不变)

## Out of Scope

- AI 历史页(我的 tab → AI 历史)改造 — 暂不动 v1 已有实现
- 拆解/重组 / 实体提取的多版本 — 本 change 只扩写/润色/整理/摘要/翻译 5 个 writing op,拆解是 LlmEntityExtractor 单独路径,后续 change 评估
- 流式 UI 帧率优化(M3 已修 `LaunchedEffect(state.delta)` H13 修,本 change 复用)
- 把 candidates 跨进程持久化(`viewModelScope` 内持有足够,进程死 = 重新生成,v1 接受)

## Capabilities

### Modified Capabilities

- `ai-actions`:状态机加 `versions: List<AiVersion>` 字段 + acceptReplace(position) + 横评 UI
- `quick-note`:无 schema 改动,详情屏 Flow 自动 reload 多版本接受的正文(M3 既有路径)
- `ai-gateway`:无 API 变更,`streamWritingOp` 仍单次单输出,由 VM 串行调 N 次

### New Tables / Migrations

- `ai_history.versionGroupId`(v13 → v14 AutoMigration):SQLite 加可空列 + 联合索引

## Affected Modules

- `app/src/main/java/com/yy/writingwithai/feature/aiwriting/streaming/AiActionViewModel.kt` — 状态机扩展
- `app/src/main/java/com/yy/writingwithai/feature/aiwriting/streaming/AiActionUiState.kt` — sealed interface 新字段
- `app/src/main/java/com/yy/writingwithai/feature/aiwriting/streaming/StreamingPanel.kt` — 多版本 Tab UI
- `app/src/main/java/com/yy/writingwithai/feature/aiwriting/streaming/AiVersion.kt` — 新文件,数据类
- `app/src/main/java/com/yy/writingwithai/core/data/db/entity/AiHistoryEntity.kt` — 加 `versionGroupId` 字段
- `app/src/main/java/com/yy/writingwithai/core/data/db/AiHistoryDao.kt` — 加 `observeByVersionGroup` 查询
- `app/src/main/java/com/yy/writingwithai/core/data/db/AppDatabase.kt` — version = 14 + AutoMigration(13,14)
- `app/src/main/res/values/strings.xml` + `values-en/strings.xml` — 8 个新 key

## Risks

1. **`AiHistoryEntity` 无 `versionGroupId` 字段,Room schema 必须 bump 到 v14**:AutoMigration(13,14) 加可空列 + 联合索引,既有数据无损(旧行 groupId 为 null,新多版本操作才填 groupId)。需要在 `app/schemas/` 落 v14.json。
2. **多版本串行调 = 串行烧 token**:每个版本独立算 `inputTokens` + `outputTokens`,3 个版本 = 3 倍费用 + 3 倍 latency。v1 接受代价,UX 收益大于成本;后续可加 "单版本模式" 切换(用户主动选)缓解。
3. **`streamWritingOp` 是单次流;串行 N 次需要 N 次 `consent / apikey / provider` 检查**:本 change 把 N 次 check 提到 `start()` 入口,串行调时不重复走 consent gate(consent 已在入口 verify),但 `providerId / apikey / modelName` 在 VM 内缓存复用。
4. **中途取消(用户 back / dismiss)**:取消 → `streamJob.cancel()` 同时让串行循环退出;已完成的 candidates 仍落库(走 `record()` 在 `onCompletion`);不接受也不撤回。
5. **多版本 race**:串行调用同一个 `streamGeneration` 计数器无法跨多次复用(M3 设计是 1 个流 1 个 generation);本 change 把 generation 拆为 `streamGeneration` (本次总操作) + `currentVersionIndex` (本次第几个版本),后者 bump 后只丢旧版本的滞后事件。
6. **DetailScreen 已有 Flow 自动 reload**(M3 `QuickNoteDetailScreen` 观察 note),acceptReplace 后 Flow 自然 emit 新 content;多版本接受与单版本走完全相同路径,无需额外联调。

## Acceptance Criteria

- 用户点扩写 → 1 次操作产生 3 个候选版本,Sheet 内可见 3 个 tab,默认选中第 1 个
- 切换 tab → 中间 `finalText` 与 token 用量同步更新;diff highlight 按当前选中版本重算
- 接受第 2 个版本 → 笔记正文换成第 2 个版本的输出,`Note.lastAiOp="expand"` 写入,落库成功
- 拒绝 → 笔记正文不变,3 个版本的 ai_history 记录仍在(用户后续可在 AI 历史页查)
- 再生成 → 重新跑 N 次,旧版本不保留(新 groupId 取代旧 groupId)
- 任 1 版本网络失败 → 其他版本继续显示,UI 标"第 X 次生成失败,请重试该项" + 单项重试按钮(可选 v1 不做则跳过,直接当 Failed 全失败)
- 数据库 v13 → v14 升级自动迁移,旧库数据不丢

## Estimated Timeline

- 1 day — schema 改动(AutoMigration + DAO 查询 + v14.json)
- 1 day — VM 状态机扩展 + 单测
- 1 day — UI 多版本 Tab + 接受任意版本按钮
- 1 day — review / ktlint / 真机验证
- 共 **3-4 天**(含 review buffer)