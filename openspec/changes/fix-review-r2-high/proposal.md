# fix-review-r2-high

## Why

2026-06-21 全项目 review r2(`docs/reviews/2026-06-21-full-project-review-r2.md`)发现 9 项 HIGH(commit-block)+ 16 项 MEDIUM + 12 项 LOW。本 change 落地 9 项 HIGH,其余 28 项在后续 `polish-review-r2` change 处理。

9 项 HIGH:
- **H1** `CoreAiGateway.streamWritingOp` 主线程 `runBlocking` → ANR
- **H2** `LlmNoteLinkExtractor` SQL `LIKE` 不转义反斜杠 → M1 r1 H4 regression
- **H3** `CoreAiGateway.onCompletion` 持久化 `sourceText` + `error.detail` 不脱敏 → apikey/PII 进 Room
- **H4** `QuickNoteDetailScreen` 直接 import 5 个 `feature/aiwriting` internals → 违反 CLAUDE.md 硬规则
- **H5** `CompositeNoteLinker` 反向依赖 `feature/settings.NoteAssociationSettings` → 删 feature 编译挂
- **H6** `AiActionViewModel.acceptReplace` `String.replace` 静默 no-op → AI replace 无反馈
- **H7** `AiActionViewModel.acceptReplace` `delay(150)` + `tryEmit` → M1 r1 H1 anti-pattern 复发
- **H8** `QuickNoteDetailViewModel` 双 `viewModelScope.launch` race → UI 闪旧内容
- **H9** `CustomProviderEditViewModel.pingFromForm` 明文 apikey + 绕过 gateway history

## What Changes

按 plan `/Users/bytedance/.claude/plans/optimized-wiggling-muffin.md` 实施:

1. **H5**:新建 `core/prefs/NoteAssociationSettingsStore.kt`;删 `feature/settings/NoteAssociationSettings.kt`;改 4 处引用 + test
2. **H4**:扩 `AiwritingEntry` surface(`ActionSheetRoute` / `StreamingPanelRoute` / `copyToClipboard` / `AiActionUiState` re-export);`QuickNoteDetailScreen` 只用 `AiwritingEntry`
3. **H1**:删 `CoreAiGateway.runBlocking` + `resolveProviderFlow`;`AiGateway.streamWritingOp` 加 `apiFormatOverride` 参数;`AiActionViewModel.start` 在 suspend 上下文读 prefs
4. **H9**:`CustomProviderEditViewModel.pingFromForm` 不再持有 `s.apiKey`;改走 `CoreAiGateway.ping()`(扩签名);apikey 临时取不入 state
5. **H2**:`LlmNoteLinkExtractor` LIKE 查询转义 `\` `%` `_`
6. **H3**:`AiHistoryRepository.record` 入口加脱敏 pass(apikey pattern + error 截断)
7. **H6**:`AiActionViewModel.acceptReplace` 用 `indexOf` + `replaceRange`,缺失/多匹配 → emit `Failed`
8. **H7**:删 `AiActionViewModel.acceptReplace` 内 `delay(150)` + `tryEmit` + 误导 Log
9. **H8**:`QuickNoteDetailViewModel` 合并双 `viewModelScope.launch`;删 `noteUpdateEvents` listener 路径

## Capabilities

### Modified Capabilities

- `ai-gateway`:apikey 透传 gateway,不再由 gateway 内部读 prefs(对应 H1 + H9 扩 ping)
- `quick-note`:cross-feature 引用走 Entry(对应 H4);详情 VM single-source-of-truth(对应 H8)
- `note-association`:LIKE escape 与 Repository 主路径对齐(对应 H2)
- `secure-prefs`:新增 `NoteAssociationSettingsStore` 落 `core/prefs/`(对应 H5)

## Impact

| 维度 | 影响 |
| --- | --- |
| 代码 | 新建 1 个文件(`NoteAssociationSettingsStore`)+ 1 个 test(`AiHistoryRepositoryRedactionTest`);改 ~13 个 main 文件 + 3 个 test 文件;删 1 个文件 |
| 数据 | apikey / error 字段入 Room 前脱敏 |
| 依赖 | 无新增 |
| spec | 4 份 spec delta,archive 阶段 sync |
| 测试 | JVM 单测 +4~6 case;Robolectric 不需要 |
| i18n | 无新增 key |

**不开自动 commit / push**(CLAUDE.md 硬规则)。所有 commit 等用户指令。
