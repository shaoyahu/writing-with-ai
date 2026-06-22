# fix-review-r2-high — tasks

## 1. H5 — NoteAssociationSettings 挪到 core/prefs/

- [ ] 新建 `core/prefs/NoteAssociationSettingsStore.kt`(interface + impl)
- [ ] 删除 `feature/settings/NoteAssociationSettings.kt`
- [ ] 改 `core/note/impl/CompositeNoteLinker.kt` import + 依赖类型
- [ ] 改 `feature/settings/SettingsScreen.kt:120` 类型
- [ ] 改 `feature/quicknote/detail/QuickNoteDetailScreen.kt:88` 类型
- [ ] 改 `core/note/di/NoteLinkerModule.kt` Hilt bind(若需要)
- [ ] 改 `core/note/CompositeNoteLinkerTest.kt` import + mockk 类型
- [ ] 改 `FakeProviderPrefsStore.kt`(配合,若影响)
- [ ] 跑 `assembleDebug` 通过

## 2. H4 — AiwritingEntry 扩 public surface

- [ ] `feature/aiwriting/AiwritingEntry.kt` 新增 `ActionSheetRoute` / `StreamingPanelRoute` / `copyToClipboard` 包装 + `AiActionUiState` typealias
- [ ] `feature/quicknote/detail/QuickNoteDetailScreen.kt` 删 5 个 internal import,改用 AiwritingEntry
- [ ] 跑 `assembleDebug` 通过
- [ ] grep 验证 QuickNoteDetailScreen.kt 不再 import feature/aiwriting 内部

## 3. H1 — CoreAiGateway.runBlocking ANR

- [ ] `core/ai/api/AiGateway.kt` `streamWritingOp` + `ping` 加 `apiFormatOverride: ApiFormat?` 参数
- [ ] `core/ai/CoreAiGateway.kt` 删 L114-117 runBlocking + L167-178 resolveProviderFlow 整个函数
- [ ] `feature/aiwriting/streaming/AiActionViewModel.kt:109-126` 在 suspend 上下文 await 4 个 read
- [ ] 跑 `assembleDebug` 通过
- [ ] grep 验证 `runBlocking` 全项目 0 匹配

## 4. H9 — pingFromForm 重构

- [ ] `feature/settings/model/CustomProviderEditViewModel.kt:162-200` 改 pingFromForm 签名(去除 s,显式 form 字段),apikey 临时取不入 state
- [ ] 走 `coreAiGateway.ping()`(扩签名)
- [ ] 跑 `assembleDebug` 通过

## 5. H2 — LIKE 转义

- [ ] `core/note/impl/LlmNoteLinkExtractor.kt:50-52` 加 `\` `%` `_` 转义
- [ ] `test/.../LlmNoteLinkExtractorTest.kt` 加 case `extractAndPersist_escapesBackslashAndWildcards`
- [ ] 跑 `testDebugUnitTest` 通过

## 6. H3 — AiHistoryRepository 脱敏

- [ ] `core/data/repo/AiHistoryRepository.kt:25-59` 加 redact pass + MAX_ERROR_LEN
- [ ] 新 `test/.../AiHistoryRepositoryRedactionTest.kt` 3 case
- [ ] 跑 `testDebugUnitTest` 通过

## 7. H6 — acceptReplace indexOf 校验

- [ ] `feature/aiwriting/streaming/AiActionViewModel.kt:188` 用 `indexOf` + `replaceRange`,缺失/多匹配 → Failed
- [ ] 加 test case `acceptReplace_missingSourceText_emitsFailed`
- [ ] 跑 `testDebugUnitTest` 通过

## 8. H7 — 删 delay + tryEmit

- [ ] `feature/aiwriting/streaming/AiActionViewModel.kt:195-199` 删 `delay(150)` + `tryEmit` + Log.d
- [ ] 跑 `testDebugUnitTest` 通过

## 9. H8 — DetailVM 单 collector

- [ ] `feature/quicknote/detail/QuickNoteDetailViewModel.kt:46-84` 合并双 launch;删 `noteUpdateEvents` listener 路径;删 Log.d
- [ ] 跑 `testDebugUnitTest` 通过

## 10. 全量验收

- [ ] `./gradlew :app:assembleDebug` SUCCESSFUL
- [ ] `./gradlew :app:ktlintCheck` 0 violations
- [ ] `./gradlew :app:lintDebug` 0 errors
- [ ] `./gradlew :app:testDebugUnitTest` 全绿
- [ ] grep 自检 3 条全 0 匹配
- [ ] 真机 smoke(AI 替换 + 自定义 provider ping + LLM 关联)

## 注意

- **不开自动 commit / push**(CLAUDE.md 硬规则)
- 顺序:H5 → H4 → H1 → H9 → H2 → H3 → H6 → H7 → H8(高风险 / 架构改动先行)
- 中间任意一步 `assembleDebug` 不通过 → 停下报告,不继续
