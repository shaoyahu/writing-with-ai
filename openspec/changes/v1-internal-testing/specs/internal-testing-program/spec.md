# internal-testing-program Specification (delta)

## ADDED Requirements

### Requirement: Internal testing scope is 5 testers + debug channel only

v1 internal testing MUST 限制在 5 人范围(自用 + 朋友),APK 分发 MUST 走 debug 通道(`./gradlew :app:assembleDebug` 产物),MUST NOT 走 release 通道(`./gradlew :app:assembleRelease` 需 R8 签名凭据,本阶段不允许)。

`docs/usage/internal-testing.md` MUST 明文标注 testers 范围 + 通道限定 + 升级到 release 的条件(留待 v1.1 稳定性验证后切)。

#### Scenario: Test scope is 5 testers
- **WHEN** 读 `docs/usage/internal-testing.md` 内测范围段
- **THEN** 含 "5 人内测" + "自用 + 朋友" + 每人 ROM 角色(小米/华为/OPPO/vivo/其它)

#### Scenario: APK only debug channel
- **WHEN** 文档检查 release APK 在内测阶段是否允许
- **THEN** 明文 "release 通道不在本阶段使用" 或等价表述

#### Scenario: No Firebase playtest bucket
- **WHEN** grep `docs/usage/internal-testing.md` / `app/build.gradle.kts` / 发布脚本
- **THEN** 0 个 `playtest` / `firebase` 内测灰度引用

### Requirement: Real provider integration runbook verified end-to-end before first APK

`docs/usage/real-provider-integration.md` 列出的每条 checklist 项 MUST 在首版 internal testing APK 发布前由用户**真机跑过**并标 `[verified]`;未跑过的项 MUST 标 `[pending]` 或 `[deferred]` 并附真实机凑不齐原因。

首版 internal testing MUST 至少跑通 DeepSeek 1 家真 provider 端到端(申请 apikey → 配置 ProviderConfig → AI 扩写/润色/整理调用 → SSE 流式响应 → 落 ai_history 表);MiniMax / MiMo 走 placeholder 即可(roadmap §14 已知地域限制 / 白名单)。

#### Scenario: DeepSeek end-to-end verified
- **WHEN** 检查 `docs/usage/real-provider-integration.md` 中 DeepSeek 段每条 checklist
- **THEN** 全部项标 `[verified]`(用户真机跑过并附时间戳)

#### Scenario: Unverified items explicitly marked
- **WHEN** grep `real-provider-integration.md` 所有 checkbox 项
- **THEN** 0 个 `[ ]` 未标注项;所有项均为 `[verified]` / `[pending]` / `[deferred]` 三态之一

#### Scenario: MiniMax / MiMo placeholder documented
- **WHEN** 读 runbook MiniMax / MiMo 段
- **THEN** 显式说明 "内测阶段 placeholder,真机 verify 留待 v1.1"

### Requirement: ROM compatibility matrix covers 4 OEMs with verify state

`docs/usage/rom-compatibility-notes.md` MUST 在 release-readiness 已落档的 4 大 OEM(小米 MIUI / 华为 HarmonyOS / OPPO ColorOS / vivo OriginOS)段落之外,**新增**一张 4 列 markdown 验证矩阵:

| 列 | 含义 |
|---|---|
| OEM | 4 大厂商 + 其它 |
| 限制项 | widget / predictive back / IME / 后台限制等 |
| 验证状态 | `[verified]` / `[pending]` / `[deferred]` |
| 降级方案 | app 内快捷入口 / 通知栏快捷 / etc |

#### Scenario: ROM matrix table present
- **WHEN** 读 `docs/usage/rom-compatibility-notes.md`
- **THEN** 含 4 列 markdown 表 + 至少 5 行(4 OEM + 其它)

#### Scenario: All rows have verify state
- **WHEN** grep `rom-compatibility-notes.md` 表格每行
- **THEN** 0 行缺 `[verified]` / `[pending]` / `[deferred]` 标签

### Requirement: Known issues document exists with severity + workaround

`docs/usage/known-issues.md` MUST 首版存在,包含从 R5 review / R6 review / entity-extraction-polish deferred / 国产 ROM widget 限制 4 类源汇总的 known issues,每条 MUST 含:
- `severity`: CRITICAL / HIGH / MEDIUM / LOW
- `description`: 问题描述 + 触发场景 + 复现步骤
- `workaround`: 临时降级方案(用户可绕过)
- `fix plan`: 修复计划归属(v1.1 / v2 / won't fix)

#### Scenario: Known issues file exists with 4+ items
- **WHEN** `cat docs/usage/known-issues.md`
- **THEN** 文件存在 + 至少 4 条 issues(MIN 取 R5 + R6 各 1 条 + entity-extraction polish 1 条 + ROM widget 1 条)

#### Scenario: Every item has severity tag
- **WHEN** grep `known-issues.md` 每条 issue
- **THEN** 0 条缺 `severity:` / `workaround:` / `fix plan:` 任一字段

#### Scenario: AI maintenance loop documented
- **WHEN** 读 `known-issues.md` 末尾维护段
- **THEN** 含 "维护人: AI 主动汇总 + 用户审" + "每周巡检一次" 或等价表述

### Requirement: Feedback channel documented with bug report template

`docs/usage/feedback-channel.md` MUST 存在,包含:
1. 反馈入口占位(邮件 / 飞书机器人 / 微信群二维码,`TODO(替换为实际联系方式)` 占位文本由用户后续替换)
2. bug report 模板(设备型号 / 系统版本 / app versionCode / 复现步骤 / 期望 vs 实际 / 截图 / logcat)
3. 提单流程(截图 → 模板填 → 发到对应渠道 → AI 每周扫一次 → 落到 known-issues.md)

#### Scenario: Feedback channel file exists with 3 sections
- **WHEN** `cat docs/usage/feedback-channel.md`
- **THEN** 文件存在 + 3 段(反馈入口 / bug report 模板 / 提单流程)

#### Scenario: Bug report template has required fields
- **WHEN** grep `feedback-channel.md` 模板段
- **THEN** 含 设备型号 / 系统版本 / app versionCode / 复现步骤 / 期望 vs 实际 / 截图 / logcat 全部 7 字段

#### Scenario: No issue tracker required
- **WHEN** grep `feedback-channel.md` 整文
- **THEN** 0 个 GitHub Issues / Jira / Sentry 强制要求引用(可有"如需升级到 issue tracker"留 v2 说明,但 MUST NOT 是当前必需路径)

### Requirement: Internal testing MVP acceptance criteria is 4 weeks + zero blocker

v1 internal testing MVP 验收标准 MUST 为:
- 期限 4 周(从首版 APK 发布日起)
- 所有 known issues 状态为 `[resolved]` / `[won't fix]` / `[deferred-accepted]`
- 反馈流 0 个阻塞性 bug(severity = CRITICAL)

未达成 MUST NOT 升级到 release 通道;达成 MUST 由用户决定是否切 release。

#### Scenario: MVP criteria documented
- **WHEN** 读 `docs/usage/internal-testing.md` 验收段
- **THEN** 含 "4 周" 期限 + "0 阻塞性 bug" + "known issues 全部 resolved / won't fix / deferred-accepted" 三条标准

#### Scenario: Release gate tied to MVP
- **WHEN** 读 `docs/usage/internal-testing.md` 升级 release 段
- **THEN** 明文 "MVP 未达成不切 release 通道"