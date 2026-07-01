## Context

- M5 polish 收口(M0~M6 全绿，R6 review 7 项 fix 全落地，`./gradlew :app:check` 通过)
- release-readiness change 已落 R8 / 签名 / Robolectric / ROM 适配要求，但**真机端到端未跑过**
- `docs/usage/real-provider-integration.md` 已写，内容完整但**没有真机 verify 过**
- `values-en/strings.xml` 残留大量 `__TODO__` 占位(roadmap §0/§15.1 多语言硬规则违规)
- 内测反馈渠道 / known issues / ROM 真机适配矩阵 三份文档未建
- 单人项目，弱可观测性(roadmap §14 风险表中已点)
- 5 人内测范围(自用 + 朋友)，分发走 `nananxue.cn/app/debug/`(用户 SSH key 持有)

## Goals / Non-Goals

**Goals:**
- 文档 / i18n 收尾，不动业务代码
- 真 provider 联调 runbook 每条 checklist 跑过
- 内测反馈渠道 + bug 提单模板建立
- Known issues 首版建立(从 R5/R6 review + polish deferred + ROM 限制汇总)
- 5 台真机 ROM 适配矩阵(小米/华为/OPPO/vivo/其它)建立
- 首版 internal testing APK 发布到 debug 通道

**Non-Goals:**
- 改 Kotlin/Java 业务代码
- 接 bug 自动归集(CI/issue tracker 集成)
- 接崩溃监控(Sentry/Firebase Crashlytics)
- 用户量统计 / 评分系统
- 上架应用市场(roadmap §0 拍板:只发 APK)

## Decisions

### D1 · 内测规模 5 人 + debug 通道分发，不上 release

- **Rationale**:roadmap §0 拍板"只发 APK 文件 + 不上架"。debug 通道用户已确认可装;release 通道留待 v1.1 内测稳定后再切。5 人内测足够覆盖 ROM 适配(小米/华为/OPPO/vivo 各 1 + 自用 1)。
- **Alternatives**:`-playtest-bucket` 灰度 — 不上 Firebase，放弃。`-internal-testing` Gradle flavor — 引入 flavor 污染，放弃。

### D2 · 反馈渠道用邮件 + 飞书 + 微信群，不用 issue tracker

- **Rationale**:单人维护，引入 GitHub Issues / Jira 反而增加管理负担;邮件 + 飞书 + 微信群是 5 人内测范围最轻量路径，反馈落到对话流，AI 手动整理到 `known-issues.md`。
- **Alternatives**:GitHub Issues — 不开公仓，放弃。Notion / 飞书文档表格 — 5 人规模过度，放弃。

### D3 · i18n TODO 占位全部补完，不留 v1 尾巴

- **Rationale**:roadmap §0 拍板 v1 双语硬规则，残留 `__TODO__` 等于 release 不达标。预估 30~60 条，机械化翻译 + 上下文校验(部分专有名词如 "DeepSeek" / "MiniMax" / "MiMo" 保留原文不译)。
- **Alternatives**:留 v1.1 补 — 违反 CLAUDE.md "字符串一律走 strings.xml，不硬编码" + roadmap §15.1 拍板，放弃。

### D4 · 真 provider 联调用 DeepSeek 1 家先跑通，MiniMax/MiMo 走 placeholder

- **Rationale**:M3 已确认三家均 Anthropic 兼容 + ProviderConfig 数据驱动(roadmap §14 风险),provider 切换零代码成本。DeepSeek apikey 申请门槛最低(MiniMax / MiMo 需地域限制 / 白名单，roadmap runbook 已知限制已点)。内测阶段只验 1 家真 provider 即可验证端到端路径，3 家全跑留给 v1.1 稳定性测试。
- **Alternatives**:3 家全跑 — 内测人员 5 人各自配 apikey 摩擦大，放弃。Mock 跑 — 跟 M3 FakeProvider 等效，无新增价值，放弃。

### D5 · Known issues 首版由 AI 主动汇总，用户审一遍后发布

- **Rationale**:R5/R6 review findings 5+7 项、entity-extraction polish 6 项 deferred、国产 ROM widget 限制(roadmap §14)都是已知问题源。AI 整理节省用户时间;用户过一遍确认事实 + 标 severity + 给 workaround = 文档首版定稿。
- **Alternatives**:用户手写 — 5 件事分头找麻烦，放弃。内测跑一段时间再写 — 推迟发现路径，放弃。

### D6 · ROM 适配矩阵用 4 列 markdown 表格，每行一条 limit + workaround

- **Rationale**:docs/usage/rom-compatibility-notes.md(release-readiness 已建)只列 4 大 OEM 段落，**没有验证清单**。本次新增 4 列矩阵:OEM / 限制项 / 验证状态 / 降级方案。验证状态由用户真机跑后填。
- **Alternatives**:每 OEM 一份 markdown — 5 份文件过度拆分，放弃。内联 5 张图 — 文档仓不放图，放弃。

## Risks / Trade-offs

- **R1 · 5 人内测样本不足，统计意义弱** → 接受:roadmap 已知"单人项目 + 自用 + 朋友内测"，定量统计留给 v2+;首版聚焦"有没有跑通"而非"通过率"
- **R2 · DeepSeek 海外 IP 部分节点不可用(runbook 已知)** → 用户必须用国内 IP 申请 + 内测人员都得是国内 IP;首版 APK 文档明确标注
- **R3 · 内测反馈不一定及时回(5 人业余时间)** → known-issues.md 每周 AI 主动扫一遍反馈流 + 标状态，避免"反馈沉底"
- **R4 · 文档工作量大于实现(3 篇文档 + i18n 30~60 条 + 真机 verify)** → 接受:本 change 设计意图就是"实现侧 0 改动"，质量门在文档 + 真机验证
- **R5 · publish-release.sh 走用户本机 SSH key，沙箱无法代发** → 首版发布动作明确划到用户侧，tasks.md 标 `owner=user`
- **R6 · 真机 verify 5 台不一定凑齐** → 部分真机 verify 标 `[deferred]`，文档明确缺口;v1.1 补完
- **R7 · 单点 AI 整理 known-issues 可能漏掉边缘 case** → 文档首版明示"维护人:AI + 用户审"，每月巡检一次

## Migration Plan

本 change 不涉及 schema / API / 代码 schema 变更，**无需迁移步骤**。回滚:`git revert <commit>` 即可，仅删除新增 3 份文档 + i18n 占位翻译回退，运行时零影响。

部署:
1. AI 完成 `internal-testing.md` / `known-issues.md` / `feedback-channel.md` 三份文档 + `values-en/strings.xml` TODO 补完
2. AI 标 `[verified]` / `[pending]` 真机 verify 状态
3. 用户过一遍三份文档 + 跑 5 台真机 verify，把状态从 `[pending]` 改 `[verified]`
4. 用户走 `publish-release.sh debug` 发首版 APK(沙箱不可代)
5. 用户邀请 5 名内测人员走反馈渠道
6. 后续每周 AI 扫反馈 + 更新 `known-issues.md`

## Open Questions

- **Q1 · 5 名内测人员名单 / 联系方式由谁拉?** → 用户侧决策，AI 不介入(隐私 + 关系维护)
- **Q2 · 内测期限多久?MVP 验收标准?** → 暂定 4 周;验收标准=所有已知 issues 标记 `[resolved]` 或 `[won't fix]`，反馈 0 阻塞性 bug
- **Q3 · 反馈渠道占位文本(邮箱 / 飞书机器人 / 微信群二维码)由谁填?** → 用户侧决策;文档首版用 `TODO(替换为实际联系方式)` 占位
- **Q4 · 首版 internal testing APK 走 debug 还是 release 通道?** → debug(基于 D1);release 通道待 v1.1 切
- **Q5 · ROM 适配矩阵真机 verify 状态是否要在 v1 release 前全部 `[verified]`?** → 不阻塞 v1;部分 `[deferred]` 在 known-issues.md 公开即可