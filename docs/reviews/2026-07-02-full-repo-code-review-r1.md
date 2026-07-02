# 2026-07-02 — Full Repository Code Review (R1)

**Subject**: full-repo-review
**Reviewer**: Claude (`code-reviewer` × 7 维度并行 + `adversarial-verify` 3 票制)
**Date**: 2026-07-02
**Status**: completed
**Follow-up change**: `openspec/changes/fix-review-2026-07-02/` (待下次会话起草)

## Context

用户在会话中请求"对整个项目代码进行 code review"。结合 Ultracode 模式开启，本 review 通过 7 维度并行评审 + 3 票制对抗验证（每条发现由 3 个独立 agent 反驳，< 2 票反对的才保留）完成。

- **评审规模**: 250 个 Kotlin 源文件 + 86 个测试文件
- **评审维度**: Architecture & DI · Concurrency · Performance & Memory · Security & Privacy · Compose UI Quality · Test Coverage · Docs/Spec Consistency
- **候选发现**: 86 条 → 去重后 53 条 → 对抗验证保留 53 条

## Severity 量化

| Severity | 数量 |
|----------|------|
| HIGH     | 1    |
| MEDIUM   | 11   |
| LOW      | 41   |
| **总计** | **53 条** |

## 完整发现清单（按严重度排序）

### 🟥 HIGH (1)

**FAKE-001**: Fakes duplicated between main and test source sets
- **File**: `app/src/main/java/com/yy/writingwithai/core/prefs/FakeUserPrefsStore.kt` ↔ `app/src/test/java/com/yy/writingwithai/core/prefs/FakeUserPrefsStore.kt`
- **描述**: `FakeUserPrefsStore` 在 main 与 test 两侧有副本,会随时间漂移腐烂
- **建议**: 删除 main 侧副本,保留 test 侧
- **状态**: ✅ 已修(2026-07-02 删除 main 侧 `FakeUserPrefsStore.kt` + 同包 `FakeConsentStore.kt` / `FakeSecureApiKeyStore.kt`,一并清理)

### 🟧 MEDIUM (11)

| ID | 维度 | 摘要 |
|----|------|------|
| SEC-007 | TLS pinning | `AiModule` OkHttpClient 无 `CertificatePinner` / `network_security_config.xml`,无中间人攻击防护 |
| SEC-016 | apikey fallback | `SecureApiKeyStoreImpl` KeyStore 损坏时 `runCatching{}` 静默吞掉,用户被误导为「未配置 apikey」 |
| DI-005 | DI 架构 | `FeishuModule.kt` 单文件既含 `@Module object` 又含 `abstract class @Binds`,与单文件单 module 惯例不一致 |
| finding-3 | accessibility | `AppSelectionDropdown` 选中态 trailing Check icon `contentDescription = null`,TalkBack 读不到已选中语义 |
| VM-001 | ViewModel 测试覆盖 | `QuickNoteEditorViewModel` 无单测(核心写入路径) |
| VM-003 | ViewModel 测试覆盖 | `AliasManagementViewModel` 无单测(级联到 note linking) |
| VM-004 | ViewModel 测试覆盖 | `NoteAssociationSettingsViewModel` 无单测(toggle 状态) |
| VM-005 | ViewModel 测试覆盖 | `FeishuAuthViewModel` 无单测(OAuth 胶水层) |
| VM-006 | ViewModel 测试覆盖 | `PromptTemplateViewModel` 无单测(CRUD + draft) |
| VM-009 | ViewModel 测试覆盖 | `ResetApikeyPromptViewModel` 无单测 |
| README-003 | README accuracy | `README.md:36` 声称 M0~M6 全部完成,`openspec/changes/` 中多个 change 未归档到 `archive/` |

> VM-002 (QuickNoteDetailViewModel) / VM-008 (CheckUpdateViewModel) 也被标记 LOW,见下表

### 🟨 LOW (41) — 按维度归集

| 维度 | 数量 | 重点问题 |
|------|------|----------|
| lifecycle | 3 | `CustomProviderEditScreen` 死代码 `DisposableEffect(Unit)` + 空 `onDispose` ;`AppNav` `LaunchedEffect` key 列表不稳态;`Theme.kt` `DisposableEffect` 多 key + 空 onDispose |
| proposal-implementation | 3 | `language-switcher` / `app-tab-bar-redesign` / `custom-provider-api-format` 三个 change 的 proposal vs impl 漂移 |
| apikey storage | 3 | ✅ 验证通过项:EncryptedSharedPreferences + AES256_GCM + 无明文落盘 + fallback 日志脱敏 |
| AI consent | 2 | ✅ 验证通过:`CoreAiGateway` 入口强制 consent 门控;ResetApikeyPrompt / ApikeyPrompt 入口 consent 校验 |
| Network auth header | 2 | ✅ 验证通过:apikey 走 header 不进 URL;Provider 5xx 错误页脱敏 Bearer/x-api-key |
| code-quality | 2 | `AppDropdownMenu.kt` `@file:Suppress("FunctionNaming")` 多余;`SettingsLanguageScreen` 静默吞掉非 Activity context |
| ViewModel coverage | 2 | `QuickNoteDetailViewModel` / `CheckUpdateViewModel` 缺单测(VM-002 / VM-008) |
| Edge cases | 2 | Markdown docx / xml converters 边界分支覆盖空缺;`PlaceholderTest` 是占位 |
| Test naming | 2 | 测试命名 / 类命名规范需要源码层审计 |
| concurrency / coroutine-scope | 2 | `NoteAssociationSettingsViewModel` 多处用 `Eagerly` 而非 `WhileSubscribed`;`ModelProviderDetailScreen` 三处兄弟 `LaunchedEffect` 重叠 keys 无序文档 |
| Auto Backup / HTTPS / Prompt 注入 / FileProvider / WebView / OAuth redirect / TLS pinning | 7 | ✅ 验证通过项:`allowBackup=false` + EncryptedSharedPreferences 不参与备份 + `usesCleartextTraffic=false` + prompt 注入 sanitize + FileProvider `exported=false` + 无 WebView + OAuth deep link 收窄 + Manifest queries 不扩大攻击面 |
| hardcoded-dim | 1 | `SettingsLanguageScreen` 用裸 `dp` 而非 `LocalSpacing` |
| theme-contrast | 1 | `MyScreen SectionCard` `defaultElevation = 0.dp` 在暗色模式可能融入背景 |
| Fake quality | 1 | `FakeProviderPrefsStore` 仅在 test 侧(本次发现时已同源清理,见 HIGH 状态) |
| Flaky patterns | 1 | `SecureApiKeyStoreRobolectricTest` 运行成本高 |
| Coverage tooling | 1 | 无 Kover / Jacoco 配置 → 分支覆盖未度量 |
| docs drift | 4 | `api-anthropic-compatible.md` 与各 provider 文档可能重复;`openspec/specs/ai-gateway/` 与 `custom-provider-api-format` 漂移风险;`feature/settings/i18n/` 跨 feature 复用嫌疑 |

## 验证通过项（明确无问题）

作为本次 review 的"已确认正确"清单，作为后续 review 的 baseline：

- ✅ 全部 VM 用 `viewModelScope`,无 `GlobalScope` 残留
- ✅ 无 WebView,无 XSS / JS bridge 暴露面
- ✅ 无明文 apikey 写 Room / BuildConfig / logcat
- ✅ Provider 5xx 错误页脱敏 Bearer / x-api-key
- ✅ FileProvider authorities + `exported=false` + `grantUriPermissions=true` 齐全
- ✅ 无开放 OAuth 重定向风险
- ✅ consent 在 `CoreAiGateway` 入口强制门控
- ✅ apikey 走 `Authorization` / `x-api-key` header,绝不出现在 URL

## 处理建议

按 OpenSpec「跨方向调整需要停」原则，本次 review **不做一次性大批量修改**，改拆为独立 change 分批推：

### 推荐 change 方案

1. **`fix-review-2026-07-02`**（本次 review 的下一棒）
   - 覆盖：11 MEDIUM + 高 ROI 的 8 个 VM 单测
   - 预计文件修改：5-8 个 + 8 个新测试文件
   - 子任务分组：
     - A. SECURITY: TLS pinning (SEC-007) + keystoreHealth observability (SEC-016)
     - B. DI: 拆 `FeishuModule` 为 `FeishuModule` + `FeishuBindsModule` (DI-005)
     - C. A11Y: `AppSelectionDropdown` Check icon `semantics { stateDescription }` (finding-3)
     - D. TEST: 补 8 个 VM 单测（VM-001/002/003/004/005/006/008/009）
     - E. DOCS: README.md 描述与 openspec/changes/ 实际状态对齐 (README-003)

2. **`chore-review-2026-07-02-low-followup`**
   - 覆盖：41 LOW 中真正需要动作的部分（跳过"已经是正确状态"的 16 条）
   - 范围：
     - lifecycle 死代码清理（3 条）
     - proposal-vs-impl 漂移纠正（3 条）
     - 代码质量 suppress 清理（1 条）
     - hardcoded-dim 修复（1 条）
     - theme-contrast 修复（1 条）
     - 测试添加 Kover 覆盖率（1 条）
     - 文档去重复 + SPEC drift 修正（4 条）

3. **直接验证项**（无需 change,在 issue tracker 备案）：
   - 16 条 ✅ 验证项作为 CI gate 保留,防止未来退化

### 优先级

| 优先级 | 工作 | 估算 |
|---|---|---|
| P0 | `fix-review-2026-07-02` | 1-2 个完整会话 |
| P1 | `chore-review-2026-07-02-low-followup` | 1 个会话 |
| P2 | CI gate 加 16 条 ✅ 的回归保护 | 1 个会话 |

## 数据附件

完整 53 条发现 JSON 存于 `/tmp/review-final.json`（本会话产物，临时）。后续 OpenSpec change 起草时直接复用本文件。

## 评审元数据

- Workflow 任务 ID: `wny9ipa2z`
- Agent 总数: 163
- Subagent token 总消耗: 5,666,669
- 工具调用总次数: 915
- 持续时间: 2,800,486 ms (≈ 47 min)
- 原始 transcript 目录: `/Users/cc/.claude/projects/-Users-cc-Desktop-vscode-writing-with-ai/c94082bf-0616-41ad-bc74-3f75026c13c0/subagents/workflows/wf_bb124e48-c9b`
