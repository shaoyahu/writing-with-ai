# remove-debug-fake-fallback · tasks

## 1. LlmEntityExtractor 删 DEBUG 兜底 fake

- [x] 1.1 `app/src/main/java/com/yy/writingwithai/core/note/entity/LlmEntityExtractor.kt:56` 删 `?: if (com.yy.writingwithai.BuildConfig.DEBUG) "fake" else return@withContext 0`,改成 `val providerId = providers.firstOrNull() ?: return@withContext 0`
- [x] 1.2 `LlmEntityExtractor.kt:57` 删 `if (providerId == "fake") "" else ...`,改成统一 `val apikey = secureApiKeyStore.get(providerId) ?: return@withContext 0`
- [x] 1.3 删掉 line 52-53 注释里关于「review r1 C1:release 构建 fake provider 不在 map 中」的过时说明(行为已变更,重新写注释说明「无 provider 直接返 0」)

## 2. AiModule 不再注册 FakeAiProvider 到 Hilt

- [x] 2.1 `app/src/main/java/com/yy/writingwithai/core/ai/di/AiModule.kt` 删 `provideFakeAiProvider()` 函数(line 48-54)及 `FakeAiProvider` import
- [x] 2.2 `provideAiProviderMap(fake: FakeAiProvider?)` 函数签名删 `fake` 形参;函数体删 `if (fake != null) put("fake", fake)` 块(line 85)
- [x] 2.3 删 `@Provides @Singleton fun provideAiProviderMap` 上 `fake` 相关的 import / 注释
- [x] 2.4 `FakeAiProvider.kt` 类本身**保留**(JVM 单测用);`FakeConfig.kt` / `FakeConfigHolder.kt` 同保留

## 3. QuickNoteDetailViewModel.decompose 删 `|| DEBUG` 兜底

- [x] 3.1 `app/src/main/java/com/yy/writingwithai/feature/quicknote/detail/QuickNoteDetailViewModel.kt:653` 改 `val hasProvider = providers.isNotEmpty() || com.yy.writingwithai.BuildConfig.DEBUG` 为 `if (providers.isEmpty())`
- [x] 3.2 `if (!hasProvider)` 改成 `if (providers.isEmpty())`,返回路径不变(`_decomposeState.value = DecomposeState.ApiKeyMissing`)

## 4. CoreAiGateway 删 fake 特殊处理

- [x] 4.1 `app/src/main/java/com/yy/writingwithai/core/ai/CoreAiGateway.kt:231` 删 `if (provider.id == FakeAiProvider.PROVIDER_ID) { ... }` 整段
- [x] 4.2 删 `FakeAiProvider` import(if 仅 fake 特殊处理用);删相关注释

## 5. AiActionViewModel 删 PROVIDER_ID_FAKE 常量

- [x] 5.1 `app/src/main/java/com/yy/writingwithai/feature/aiwriting/streaming/AiActionViewModel.kt:71` 删 `const val PROVIDER_ID_FAKE = "fake"`
- [x] 5.2 grep 确认 `PROVIDER_ID_FAKE` 在整个 `app/src/main/` 无其他引用;如有调用点,改成不依赖 fake 常量的判断
- [x] 5.3 line 252,254,258,303,340 的 `BuildConfig.DEBUG` log 保留(跟 fake 无关,不动)

## 6. ModelManagementViewModel / Screen 清理 fake 残留

- [x] 6.1 `app/src/main/java/com/yy/writingwithai/feature/settings/model/ModelManagementViewModel.kt:82` 删 `selected != FakeAiProvider.PROVIDER_ID` 守卫
- [x] 6.2 `ModelManagementViewModel.kt:395,399` 删 `providerPrefsStore.setSelectedProviderId(FakeAiProvider.PROVIDER_ID)` + `_state.update { it.copy(selectedProviderId = FakeAiProvider.PROVIDER_ID) }` 调用路径(由 secureApiKeyStore 空集自动走"请先配置"引导,不再显式选 fake)
- [x] 6.3 `app/src/main/java/com/yy/writingwithai/feature/settings/model/ModelManagementScreen.kt:153-178` 删 `descriptors.filter { it.id != "fake" }` + `state.selectedProviderId != "fake"` 判断(provider map 不含 fake,守卫冗余)
- [x] 6.4 删 `FakeAiProvider` import(if 不再被引用)

## 7. 编译 + ktlint + JVM 单测验证

- [x] 7.1 `./gradlew :app:assembleDebug` BUILD SUCCESSFUL
- [x] 7.2 `./gradlew :app:ktlintCheck` 0 violation
- [x] 7.3 `./gradlew :app:testDebugUnitTest` 全绿(`app/src/test/` 单测用 `FakeAiProvider` 直接 `new` 或 `@TestInstallIn` 注入,**不走** main 的 AiModule `provideAiProviderMap`,本 change 不阻塞单测;若有单测失败,通过 `@TestInstallIn` 注入 fake map 修)

## 8. grep 收尾

- [x] 8.1 `grep -rE "(BuildConfig.DEBUG.*fake|\"fake\".*BuildConfig.DEBUG)" app/src/main/` → 0 匹配
- [x] 8.2 `grep "FakeAiProvider" app/src/main/` → 0 匹配(类保留在 `app/src/main/java/.../core/ai/fake/FakeAiProvider.kt`,但**没有任何 main 代码 import / 引用**;单测通过 `app/src/test/` 直接 new)
- [x] 8.3 `grep "\"fake\"" app/src/main/` → 0 匹配(字符串字面量 "fake" 全清;`FakeAiProvider.PROVIDER_ID = "fake"` 在类内,不在 main 引用链上)
- [x] 8.4 单点 `BuildConfig.DEBUG` 引用仍在的合法位置(不要清):
  - `core/data/repo/NoteRepository.kt:172,185,238`(logcat gate)
  - `feature/quicknote/edit/QuickNoteEditorViewModel.kt:243`(调试 log)
  - `feature/quicknote/detail/QuickNoteDetailViewModel.kt:529,557`(调试 log)
  - `feature/aiwriting/streaming/AiActionViewModel.kt:252,254,258,303,340`(调试 log)
  - `core/prefs/UserPrefsStore.kt:153`(隐私 gate)

## 9. 真机 e2e 验证(USER-OWNED)

- [ ] 9.1 装 debug APK 到 Pixel_Test(已完成 device 连接)
- [ ] 9.2 不配 apikey → 详情屏拆解 → 期望弹「请先配置 AI 模型」错误对话框(**不**再走 fake 返回「未发现实体」)
- [ ] 9.3 不配 apikey → 选中文本 → AI chip → 期望弹「请先配置」Snackbar + 「去设置」action
- [ ] 9.4 配 deepseek 真实 apikey → ping 通过 → 拆解 → 期望真 LLM 抽实体 + UI 蓝色字体 + ✦ 十字星高亮
- [ ] 9.5 配 apikey → 选中文本 → 扩写 → 期望真 LLM 流式 + ai_history 表写入一条记录
- [ ] 9.6 关闭 apikey → 再拆解 → 期望回到 9.2 错误对话框(走空集分支)

## 10. spec 同步 + 归档

- [ ] 10.1 跑 `/opsx:sync remove-debug-fake-fallback` 合入主 spec
  - `ai-gateway` 追加 1 条 Requirement `AI 调用不允许走 fake 兜底(debug 包同 release 行为)` + 4 scenario
  - `ai-decompose-implementation` 追加 1 条 Requirement `Entity 抽取必须使用真 AI provider` + 4 scenario
  - `ai-actions` 追加 1 条 Requirement `AI op 调用必须使用真 AI provider` + 5 scenario
- [ ] 10.2 跑 `/opsx:archive remove-debug-fake-fallback` 收口
- [ ] 10.3 `docs/progress.md` 加 1 条"2026-07-08 · debug 包走真 AI provider 兜底全清"进度条目
- [ ] 10.4 (可选) 提交 commit:`refactor(ai): remove BuildConfig.DEBUG fake fallback per CLAUDE.md AI convention`