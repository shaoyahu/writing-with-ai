# Code Review: 全项目 review(working tree 累积 M3-M5 polish)

**Reviewed**: 2026-06-21
**Branch**: main(本地未提交,SHA `915e80eaf92717c7acbcf1c672423e7e470157fb`)
**Decision**: REQUEST CHANGES — 6 项 ≥80 置信度 issue 必须 commit 前修

## Summary

30 modified 文件(+1003/-1164)+ 3 openspec/changes 删除(`fix-m5-blockers` / `ui-redesign-m5-glass` / `widget-1x4-compact`,archive 副本清理)+ 4 spec sync。5 维度并行 review(CLAUDE.md 合规 / diff 浅扫 bug / git history context / 历史 review 交叉 / code comments 合规),36 项 finding 经 haiku 评分,6 项达 ≥80 阈值。

**最关键**:`MainActivity` typed Nav route 字符串匹配错配(back callback 永不 enable)+ release signing 配置无 keystore 时 `assembleRelease` 崩溃 + `api-deepseek.md` 协议描述与 `DeepseekConfig` 实际 OpenAI 协议严重漂移。

## Findings(置信度 ≥80)

### C1 — `MainActivity.kt:83` typed Nav route 字符串匹配错配 [score 85]

- 位置:`app/src/main/java/com/yy/writingwithai/app/MainActivity.kt:83`
- 问题:`dest.route == "com.yy.writingwithai.app.QuicknoteList"` 用 FQN 字符串与 typed Nav `data object` 匹配。Navigation Compose 2.8 实际 emit 的不是 FQN 而是基于 serializer 的 route pattern,`backCallback.isEnabled` 永远不会变 true,主页 back 直接退桌面,2s Toast 防误触机制 dead。
- 修法:`hasRoute(QuicknoteList::class)`(Nav 2.8 API)或 State hoist 由 `AppNav` 显式传 isHome。

### C2 — `app/build.gradle.kts:36-45` release signing 无 keystore 崩溃 [score 90]

- 位置:`app/build.gradle.kts:36-45`
- 问题:`signingConfig = signingConfigs.getByName("release")` 无条件 assign,`storeFile = props.getProperty("RELEASE_STORE_FILE")?.let { file(it) }` 为 null 时 AGP 在 configuration 阶段 fail,开发者没配 keystore 之前 `./gradlew :app:assembleRelease` 直接断。
- 修法:`if (storeFile != null) signingConfig = signingConfigs.getByName("release")`,允许 signingConfig 可选。

### C3 — `docs/usage/api-deepseek.md` 协议描述与 `DeepseekConfig` 实际严重漂移 [score 90]

- 位置:`docs/usage/api-deepseek.md:14` + `app/src/main/java/com/yy/writingwithai/core/ai/provider/deepseek/DeepseekConfig.kt:11-17`
- 问题:`DeepseekConfig` 已切 `endpointPath = "/chat/completions"` + `AuthStyle.AUTHORIZATION` + `ApiFormat.OPENAI`,但 `api-deepseek.md` 整篇仍写 `POST https://api.deepseek.com/anthropic/v1/messages` + `x-api-key` + Anthropic Messages API 格式。`docs/plans/writing-with-ai-mobile-roadmap.md:286` 同样漂移,roadmap §15.1 "三家统一走 Anthropic 兼容" 决策已被实际代码违反。
- 修法:改 `api-deepseek.md` 标题为 "DeepSeek · OpenAI 兼容 API",同步 endpoint / auth / system 字段位置(messages 数组)+ usage 字段映射;同步改 roadmap §6.3 / §15.1;考虑开 OpenSpec change `refactor-deepseek-openai-format` 走完整决策变更流程。

### C4 — 4 处硬编码中文字符串违反 CLAUDE.md "字符串一律走 strings.xml" [score 85]

- 位置:
  - `app/src/main/java/com/yy/writingwithai/feature/settings/model/ModelProviderDetailScreen.kt:171` `"API Key"`
  - `app/src/main/java/com/yy/writingwithai/feature/settings/model/ModelManagementViewModel.kt:75` `"未知错误"`
  - `app/src/main/java/com/yy/writingwithai/feature/settings/model/ModelManagementViewModel.kt:103` `"apikey 未配置"`
  - `app/src/main/java/com/yy/writingwithai/feature/settings/model/ModelManagementViewModel.kt:117` `"未知错误"`
  - `app/src/main/java/com/yy/writingwithai/feature/settings/model/ModelProviderDetailScreen.kt:138` `"保存失败: ${result.reason}"`
- 问题:CLAUDE.md 明文"字符串一律走 `res/values/strings.xml`,**不**在 Composable 里硬编码中文"。VM 层硬编码中文文案,UI 层 hardcoded Snackbar/Toast 文案,英文系统穿透显示中文。
- 修法:全部走 R.string 引用,加对应 `values-en/strings.xml` 条目;VM 改 `SaveResult.Failed(messageRes: Int, rawDetail: String? = null)` 让 UI 层 `stringResource` 渲染。

### C5 — `ModelManagementScreen.kt:204-205` 硬编码 hex 颜色违反 CLAUDE.md [score 100]

- 位置:`app/src/main/java/com/yy/writingwithai/feature/settings/model/ModelManagementScreen.kt:204-205`
- 问题:`Color(0xFF4CAF50)` / `Color(0xFF2E7D32)` 直接写 hex。CLAUDE.md 明文:"颜色、字体、间距等一律走 `MaterialTheme.colorScheme.*` / `MaterialTheme.typography.*`,不直接写 hex 或 sp 值"。
- 修法:加 `app/ui/theme/Color.kt` `SuccessGreen` / `SuccessGreenDark` token,走 `MaterialTheme.customColors.success`(CompositionLocal)或主题层 ColorScheme 扩展。

### C6 — 多处 `Log.d` 无 `BuildConfig.DEBUG` 门控,release 包打用户数据 [score 80]

- 位置:
  - `app/src/main/java/com/yy/writingwithai/feature/quicknote/edit/QuickNoteEditorViewModel.kt:163` `Log.d("EditorVM", "save noteId=${note.id} tags=$tagsToSave")`
  - `app/src/main/java/com/yy/writingwithai/core/data/repo/NoteRepository.kt:88` `Log.d("NoteRepo", "upsert noteId=${note.id} tags=$tags")`
  - `app/src/main/java/com/yy/writingwithai/core/data/repo/NoteRepository.kt:98` `Log.d("NoteRepo", "cleaned tags to insert: $cleaned")`
- 问题:noteId + 用户 tag 内容在 release 包 release-build 仍输出到 logcat,adb logcat 可抓取。CLAUDE.md §"AI 集成约定" apikey 不进 logcat 的同级别硬规则适用于用户隐私数据。
- 修法:全部 `if (BuildConfig.DEBUG) Log.d(...)` 或抽 `Logger.d { ... }` helper(release stub 掉)。

---

## Findings(置信度 50-75,建议 commit 后 polish)

| ID | 分数 | 文件:行 | 简述 |
| --- | --- | --- | --- |
| F-03 | 75 | `docs/progress.md:22-50` | 4 条新条目违反"1-3 行 / 不写实现细节 / 不写 commit hash / 不写行号 / 不写 review 细节" |
| F-05 | 75 | `openspec/specs/ai-gateway/spec.md:11` | `ping(...): Boolean` 与实际 `String?` 漂移 |
| F-10 | 75 | `CoreAiGateway.kt:104` | `ping()` 走真实 stream(EXPAND + "ping"),触发 history 写入 / 旁路 consent |
| F-13 | 75 | `docs/plans/writing-with-ai-mobile-roadmap.md:286` | 决策记录与代码漂移(同 C3) |
| F-17 | 75 | `ModelManagementViewModel.kt:109-117` | `runCatching.fold` 吞 CancellationException(M1 r1 同款已修过) |
| F-18 | 75 | `ModelManagementViewModel.kt:18-26` | KDoc 引用不存在的 `m6-ping-real-error` change,违反 OpenSpec 第一原则 |
| F-22 | 75 | `ModelProviderDetailScreen.kt:67-79` | `snackbarHostState` + `SnackbarHost` 全 dead code,只走 Toast |
| F-23 | 75 | `ModelProviderDetailScreen.kt:75-78` | `LaunchedEffect(Unit)` 旋转屏重启 collect Channel 重复 onBack |
| F-34 | 75 | `app/src/main/res/values-en/strings.xml` | 116 行 `TODO(en):` 占位,违反 v1 双语决策 |
| F-07 | 70 | `SecureApiKeyStore.kt:147-166` | `observeConfiguredProviders` 在 listener 线程读 prefs.all IO;prefs==null 静默 emit emptySet 掩盖 Keystore 不可用 |
| F-08 | 70 | `MainActivity.kt:51-72` | `OnBackPressedCallback` 默认 enabled=true,navController 未 ready 时 onboarding 屏死锁风险 |
| F-14 | 65 | `AnthropicCompatibleAdapter.kt` | 类名已名不副实(支持 ANTHROPIC + OPENAI 双格式),roadmap 决策违反 |
| F-09 | 50 | `AppNav.kt:65` | `SideEffect` 每次 recompose register 新 listener,累积泄漏 |
| F-11 | 50 | `openspec/changes/*` | 3 个 change 用 `git rm` 直接删绕过 `/opsx:archive` 流程,无明文禁止但流程违规 |
| F-15 | 50 | `QuickNoteListScreen.kt:153-162` | `AssistChip(onClick = {})` dead affordance,close icon 单点 |
| F-20 | 50 | `ProviderConfig.kt:24` | `ApiFormat` 默认 ANTHROPIC,minimax/mimo 靠默认"工作" |
| F-21 | 50 | `ModelManagementViewModel.kt:140-143` | `SaveResult.InProgress` dead state,声明未 emit |
| F-24 | 50 | `PromptTemplateScreen.kt:79-92` | `tabs.indexOfFirst` 找不到返回 -1,TabRow 抛异常(latent) |
| F-25 | 50 | `ModelManagementViewModel.kt:43-48` | init 首帧 emptySet 闪"未配置" |
| F-28 | 50 | `PromptTemplateViewModel.kt:85-94` | `save()` 先清 UI dirty 再异步写 IO,失败错位 |
| F-29 | 50 | `NoteRow.kt:135-152` | inline tag chip `Box + clickable` 无 a11y semantics |
| F-32 | 50 | `MainActivity.kt:65-66` | 2s 退出窗口 vs `Toast.LENGTH_SHORT` 2s 几乎相等 |
| F-33 | 50 | `ModelManagementViewModel.kt:58-66` | `selectProvider` 乐观 update,DataStore 失败时 UI 与持久化错位 |
| F-35 | 50 | `app/proguard-rules.pro:33-34` | KDoc 写"5 段"实际 6 段(多 App 入口段) |

## Findings(置信度 ≤25,排掉)

F-16 / F-19 / F-26 / F-27 / F-30 / F-31 / F-36 — 全部 nitpick / Kotlin 惯用模式 / 已用标准 API,CLAUDE.md 未明文要求,commit 后 polish。

---

## fix-m5-blockers 验收(progress.md 自报 "全修" 对账)

| 项 | 自报 | 实际 |
| --- | --- | --- |
| C1 Glance API | 已修 | ✅ 不在 working tree diff |
| C2 apikey 透传 | 已修 | ⚠️ 签名修但 `ping()` 内部副作用未审(见 F-10) |
| H2 ktlint 全绿 | 全绿 | ⚠️ working tree 又新增 ~16 string key,TODO(en) 占位累积,新增 ktlintCheck 阻断风险(未实测) |
| H3 `.editorconfig` obsolete | warning 全消 | ✅ |

---

## 后续建议

1. **commit 前必修**(C1-C6):影响 release 构建 / 隐私 / typed Nav / 协议文档真实性。
2. **commit 后 polish**(75 分项):progress.md 维护规则严守、ping 签名走 OpenSpec、Channel 重放 + OnBackCallback 初始 enabled false、`m6-ping-real-error` change 起草或删除 KDoc 引用。
3. **跨方向调整**:deepseek 切 OpenAI 协议是 roadmap §15.1 决策变更,建议开 `refactor-deepseek-openai-format` change 走 OpenSpec 流程,而不是 polish 直接落地。
4. **下次 review 重点**:`MainActivity` typed Nav route 验证、`assembleRelease` 本地 keystore 配齐后端到端、`api-deepseek.md` 文档漂移修复闭环。
