# Known Issues

> **状态**: v1 internal testing 阶段首版，2026-06-27 汇总。
> **来源**: R5 review(5 项 fix，无 deferred) / R6 review(7 项 fix + 多项 <80 deferred) / entity-extraction-polish R1 review(6 项 fix + 2 LOW) / 国产 ROM widget 限制(roadmap §14 + release-readiness 落档)。
> **状态机**: `[open]` → `[verified]` → `[resolved]` / `[won't fix]` / `[deferred-accepted]`。

## 状态字段说明

每条 issue 必须含 4 字段:
- `severity`: CRITICAL / HIGH / MEDIUM / LOW
- `description`: 问题描述 + 触发场景 + 复现步骤
- `workaround`: 临时降级方案(用户可绕过)
- `fix plan`: 修复计划归属(v1.1 / v2 / won't fix)

## 当前已知问题

### KI-001 · predictive back 在小米 MIUI 上偶发黑屏闪退

- **severity**: MEDIUM
- **description**: 在小米 MIUI 13+ 上，从笔记详情页按返回键时偶发 Activity 黑屏 1~2 秒后闪退;复现率 ~5%，与 predictive back 手势动画在 MIUI 上的兼容性问题有关。
- **workaround**: 关闭系统"手势导航"改用"经典三键导航"，或在内测阶段关闭 `predictive back` 特性开关(`Settings → Developer options → Predictive back animations` off)。
- **fix plan**: v1.1 — 在 `MainActivity` 监听 `OnBackPressedDispatcher` 加 try-catch + 兜底 finish。

### KI-002 · DeepSeek 海外 IP 部分节点不可用

- **severity**: HIGH
- **description**: DeepSeek API 在海外 IP(美西 / 欧洲)下偶发 503 / timeout;roadmap §14 + [real-provider-integration.md](./real-provider-integration.md) 已点。
- **workaround**: 内测人员使用国内 IP(手机 4G/5G / 国内 WiFi)申请 + 调用 DeepSeek;海外出差场景下暂时切到 MiniMax 或本地 mock。
- **fix plan**: won't fix — 这是 DeepSeek 服务侧问题，app 无法干预。

### KI-003 · 后台实体回填(pauseBackfill)Worker 自检缺

- **severity**: LOW
- **description**: entity-extraction-polish R1 review 中 LOW finding: 后台 `Worker` 自检逻辑在 `doWork()` 内未校验 `WorkManager.getInstance(context).getWorkInfoById(id)` 的返回状态，理论上若 worker 被异常 cancel 可能 race。
- **workaround**: 用户侧无影响 — 仅内测人员频繁暂停/恢复回填时偶发;不影响数据正确性。
- **fix plan**: v1.1 — 在 `EntityBackfillWorker.doWork()` 开头加 `runAttemptCount > 0` 校验 + 状态记录。

### KI-004 · 华为 HarmonyOS 后台启动管理需手动关闭

- **severity**: MEDIUM
- **description**: 华为 HarmonyOS / EMUI 设备的"应用启动管理"默认开启"自动管理"，会强制限制 app 后台拉活，导致 widget 拉新不及时;详见 [domestic-rom-widget.md](./domestic-rom-widget.md)。
- **workaround**: 用户在「设置 → 应用 → 应用启动管理 → 小札」改为"手动管理"，勾选「自启动」「关联启动」「后台活动」。
- **fix plan**: v1.1 — 在 onboarding 后显示 1 张华为设备的引导卡片，跳转到对应设置页(deep link);v2 考虑接 HMS 推送。

### KI-005 · 小米 MIUI 自启动默认关闭

- **severity**: MEDIUM
- **description**: 小米 MIUI / HyperOS 的"自启动"权限默认关闭，widget 后台更新被系统冻结;用户需手动到「设置 → 电池 → 自启动」打开。
- **workaround**: 同 KI-004 — 用户手动开启自启动;widget 立刻恢复。
- **fix plan**: v1.1 — onboarding 后检测小米设备时显示引导卡;v2 接入小米推送。

### KI-006 · OPPO ColorOS 睡眠待机优化 kill 后台

- **severity**: MEDIUM
- **description**: OPPO ColorOS 的"睡眠待机优化"会在锁屏 5 分钟后 kill 非系统白名单 app,widget 推送失效。
- **workaround**: 用户到「设置 → 电池 → 睡眠待机优化」关闭小札;v1 内测阶段已经过 [domestic-rom-widget.md](./domestic-rom-widget.md) 流程告知。
- **fix plan**: v1.1 — onboarding 引导 + v2 接入 OPPO 推送。

### KI-007 · vivo OriginOS 后台高耗电默认禁止

- **severity**: MEDIUM
- **description**: vivo OriginOS 的"后台高耗电"默认禁止非系统 app,widget 后台拉新被强制限制。
- **workaround**: 用户到「设置 → 电池 → 后台高耗电」允许小札。
- **fix plan**: v1.1 — onboarding 引导;v2 接入 vivo 推送。

### KI-008 · ktlint 自动修复 drift(M5 polish R1 LOW)

- **severity**: LOW
- **description**: entity-extraction-polish R1 review LOW finding:`./gradlew :app:ktlintFormat` 自动修复 import 顺序后，部分 Kotlin 文件的 import 顺序与 IDE 默认排序不一致，造成代码 review 视觉 diff 噪音。
- **workaround**: 手动维护一份 `imports-blacker.txt`(记录 IDE 与 ktlint 排序差异的文件),review 时忽略;CI 跑 ktlintCheck 仍然 block。
- **fix plan**: v2 — 调研 ktlint 1.0+ 新 `Rule.ENGINE` API 是否支持 import-order 自定义，或切 detekt。

### KI-009 · 5 个 DAO 测试覆盖率为 0

- **severity**: LOW
- **description**: R6 review <80 finding:5 个 DAO(`NoteDao` / `EntityDao` / `AliasDao` / `WorkInfoDao` / `AiHistoryDao`)的 `testDebugUnitTest` 没有针对性 unit test;`./gradlew :app:check` 通过靠 Robolectric + Hilt graph 间接覆盖，真实边界场景未覆盖。
- **workaround**: 当前内测阶段依靠真机验证;开发期靠 code review。
- **fix plan**: v1.1 — 给每个 DAO 加 Room `MigrationTestHelper` 测试 + `runTest` 单测。

### KI-010 · AI error 分级粒度不足(RateLimited 不可区分)

- **severity**: MEDIUM
- **description**: R6 review <80 finding:`AiError` 枚举的 RateLimited / QuotaExhausted / InsufficientBalance 在用户侧文案上都是"账户余额不足"，实际场景下用户被限流(429)与真余额不足(402)需要不同指引。
- **workaround**: 当前 UI 显示统一文案"请稍后再试或切换模型";若用户被误判为余额不足，可手动重试。
- **fix plan**: v1.1 — `AiError` 拆 `RateLimited(retryAfter)` + `QuotaExhausted` + `InsufficientBalance` 三类，UI 分别提示。

### KI-011 · AppShell 的 MeTabTarget 缺 1 个新路由分支

- **severity**: LOW
- **description**: R6 review <80 finding:`AppShell.kt` 中 `MeTabTarget` 的 `when` 分支未对"动画风格"新设置页(`SettingsAnimationStyle`)做处理，fallthrough 到 `Settings` 默认项。
- **workaround**: 当前不影响 — 用户在「我的 → 动画风格」实际跳到 Settings 页根;UI 上点击行为可观察但错误。
- **fix plan**: v1.1 — 添加 `MeTabTarget.SettingsAnimationStyle` → `AppNav.navigate(SettingsAnimationStyle)`;与 animation-style change 一并合入。

### KI-012 · app/build.gradle.kts `release` buildType 缺 prebuild 校验任务

- **severity**: MEDIUM
- **description**: R6 review <80 finding:`release` buildType 尚未挂 `:app:checkReleaseReadiness` Gradle 任务;release preflight 的 4 项检查目前是文档规范，无构建阻断。
- **workaround**: v1 release 前由 CI 手动跑 4 项 grep 校验。
- **fix plan**: v1.1 — 在 `app/build.gradle.kts` 的 `release.buildTypes` 块加 `dependsOn("checkReleaseReadiness")`，通过自定义 Gradle Task 跑 4 项 grep。

## 维护说明

- **维护人**: **AI 主动汇总 + 用户审**。
- **每周巡检一次**: AI 每周扫反馈渠道(邮件 / 飞书 / 微信群)→ 对比当前 `known-issues.md` → 标状态变更 → 用户过目。
- **状态机迁移**: `[open]` → 验证后 → `[resolved]`(已修) / `[won't fix]`(决定不修) / `[deferred-accepted]`(推迟到具体版本，已在 roadmap 注册)。
- **新 issue 录入**: 内测人员按 [feedback-channel.md](./feedback-channel.md) 提单，AI 每周扫一次整合进本表;HIGH / CRITICAL issue 必须当天录入。
- **发布前 checklist**: `release-readiness.spec.md` 4 项 preflight 检查项中"known-issues ≥4 条"是其一;每次 release 前用户必过一遍状态机。
- **历史归档**: 已 `[resolved]` 且连续 2 个 release 未复发的 issue，移到 `docs/reviews/` 下归档(本文件保持精简)。

## 关联文档

- [internal-testing.md](./internal-testing.md) — 内测范围 / 验收标准
- [feedback-channel.md](./feedback-channel.md) — 反馈渠道入口
- [rom-compatibility-notes.md](./rom-compatibility-notes.md) — 国产 ROM 适配细节
- [domestic-rom-widget.md](./domestic-rom-widget.md) — widget 后台拉活限制
- [real-provider-integration.md](./real-provider-integration.md) — 真 provider 联调