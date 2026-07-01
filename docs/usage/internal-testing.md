# v1 Internal Testing

> **状态**: M5 polish 收口后启动;首版 APK 走 debug 通道，**release 通道不在本阶段使用**。
> **范围**: 5 人内测(自用 + 朋友)，覆盖 5 类设备角色。
> **周期**: 4 周(从首版 APK 发布日起算)，期满且达标则切 release candidate。

## TL;DR

- v1 内测仅在 debug 通道发布(APK 走 `nananxue.cn/app/debug/`)，不走 `./gradlew :app:assembleRelease`。
- 5 名内测人员覆盖 5 类设备角色:小米 MIUI / 华为 HarmonyOS / OPPO ColorOS / vivo OriginOS / 其它。
- 内测反馈渠道见 [feedback-channel.md](./feedback-channel.md);提单前先查 [known-issues.md](./known-issues.md)。
- 真 provider(DeepSeek)真机 verify checklist 见 [real-provider-integration.md](./real-provider-integration.md) DeepSeek 段。

## 测试范围

| 角色 | 设备 OS | 覆盖重点 |
|---|---|---|
| 自用机 | Android 13+ Pixel / 一加类原生 | 主流程、predictive back、AI op 端到端 |
| 小米 | MIUI / HyperOS | widget 后台拉活限制、autostart |
| 华为 | HarmonyOS / EMUI | 后台启动管理、widget 推送限制 |
| OPPO | ColorOS | 电池优化、睡眠待机 |
| 其它 | vivo OriginOS / 三星 OneUI / 其它 | 跨厂商兜底，覆盖 predictive back / IME |

> 5 类角色足够暴露国内 ROM 差异;每类 1 人即满足 coverage。M2 设计约束:**不接 Firebase playtest bucket**，所以本阶段**不**走灰度。

## 内测参与方式

- 首版 APK 发布到 `nananxue.cn/app/debug/`，内测人员直接下载安装(Android 11+ 需要"未知来源应用"授权)。
- 安装后首次启动按 onboarding 流程走完(条款页 → API Key 说明)，无需主动申请，作者本人配名单。
- 4 周内按反馈渠道提 bug;期满前一周做 release candidate 决定(见验收标准)。

## 真机 verify 清单

### 国产 ROM 适配矩阵

详见 [rom-compatibility-notes.md](./rom-compatibility-notes.md) 4 列验证矩阵(本页表格与该文档保持同步)。每台真机 verify 后把对应行从 `[pending]` 改成 `[verified]` 或 `[deferred]`，并记录时间戳。

### DeepSeek 真 provider 端到端

详见 [real-provider-integration.md](./real-provider-integration.md) DeepSeek 段(8 步用户操作链)。**所有 checklist 项**必须由内测人员在真机跑过并标 `[verified]`;v1 release preflight 会卡这一项。

> **MiniMax / MiMo 占位说明**: 内测阶段仅 DeepSeek 1 家真 provider 端到端验证;MiniMax / MiMo 走 placeholder，真机 verify 留待 v1.1 稳定性测试。roadmap §14 已点 MiniMax / MiMo 的地域限制 / 白名单问题。

## MVP 验收标准

4 周内测期满时，以下 3 项**全部**达成才能切 release 通道:

1. **期限 4 周** — 从首版 APK 发布日起算。
2. **所有 known issues 状态非 `[open]`** — 标 `[resolved]` / `[won't fix]` / `[deferred-accepted]` 三态之一(详见 [known-issues.md](./known-issues.md))。
3. **反馈流 0 阻塞性 bug** — severity = CRITICAL 的 bug 计数 = 0。

未达成 MUST NOT 升级到 release 通道;达成 MUST 由用户决定是否切 release(单人项目决策权归用户)。

## 升级 release 条件

> **MVP 未达成不切 release 通道**。

具体 release 通道开启条件，见 [release-checklist.md](./release-checklist.md) + [real-provider-integration.md](./real-provider-integration.md) 的 v1 release preflight 4 检查:

1. `docs/usage/known-issues.md` ≥4 条 issues，字段齐(severity / workaround / fix plan)
2. `docs/usage/feedback-channel.md` 含 bug report 模板(7 字段)
3. `app/src/main/res/values-en/strings.xml` TODO 占位 ≤5 条
4. `docs/usage/real-provider-integration.md` DeepSeek 段 checklist 全 `[verified]`

任一未达 → `publish-release.sh release` fail + exit 1。

## 维护说明

- 维护人: **AI 主动汇总 + 用户审**(CLAUDE.md "AI 角色 5 · 项目进度维护"覆盖范围)。
- 每周巡检一次: AI 扫反馈渠道 → 更新 `known-issues.md` 状态 → 用户过目。
- 内测期满做一次 release 决策评估，本 change 的"MVP 验收标准"段是决策 checklist。
- ROM 矩阵 / DeepSeek checklist 状态变更由真机验证者(用户 / 内测人员)本人改。

## 关联文档

- [feedback-channel.md](./feedback-channel.md) — 反馈渠道与 bug 提单模板
- [known-issues.md](./known-issues.md) — 已知问题汇总与状态
- [rom-compatibility-notes.md](./rom-compatibility-notes.md) — 国产 ROM 适配矩阵
- [real-provider-integration.md](./real-provider-integration.md) — 真 provider 联调 runbook
- [release-checklist.md](./release-checklist.md) — release 通道前置检查
- [domestic-rom-widget.md](./domestic-rom-widget.md) — 国产 ROM widget 后台拉活细节