## Why

M5 polish 收口 + R6 review 修复后，工程侧进入"准 v1 内测"阶段:release-readiness change 已落地 R8 / 签名 / Robolectric / ROM 适配要求，但**真机端到端联调尚未跑过**(代码侧 0 改动但需要 5 人内测 + 真实 provider 跑通);`values-en/strings.xml` 仍残留大量 `__TODO__` 占位翻译(违反 roadmap §0/§15.1 多语言硬规则);**内测反馈渠道未建立**(文档侧缺"如何提 bug / 反馈路径"),**已知问题清单缺失**(单人维护弱可观测性，roadmap §14 风险表中已点)。**Why now**:M6 entity-extraction 已收口，roadmap §13 M0~M6 全绿，无新增 change 在跑，空窗期正好做内测启动。

## What Changes

- **`values-en/strings.xml` 完成翻译**:扫描所有 `__TODO__` / 占位英文，基于上下文补完;新增内测相关文案(`internal_testing_*` 5~10 key)
- **`docs/usage/internal-testing.md`** 新建:内测范围、参与方式、反馈渠道、已知限制、真 provider 联调 checklist、ROM 适配矩阵(小米/华为/OPPO/vivo 真机验证清单)
- **`docs/usage/known-issues.md`** 新建:首版 known issues 清单(从 R5/R6 review + entity-extraction polish deferred 收口 + 国产 ROM widget 限制等汇总)，每条标 severity + workaround + 修复计划
- **`docs/usage/real-provider-integration.md` 验证补全**:runbook 已在，本 change 把每条 checklist 跑一遍、补全截图/示例输出，落到 archived 状态
- **`docs/usage/feedback-channel.md`** 新建:内测反馈入口(邮件 / 飞书 / 微信群二维码，占位文本由用户后续替换)+ bug report 模板 + 提单流程
- **首版 internal testing APK 走发布流程**:走 `publish-release.sh debug` 把首版 APK 推到 `nananxue.cn/app/debug/` + 双通道 index.html 同步更新

> **非目标(留给 v1.1+ / v2)**:bug 自动归集(CI/issue tracker 集成);崩溃监控(Sentry/Firebase Crashlytics);用户量统计;评分系统。

## Capabilities

### New Capabilities

- `internal-testing-program`:v1 内测运行机制 —— 范围定义、参与流程、反馈渠道、已知问题维护、真 provider 联调 checklist、ROM 适配矩阵、首版发布流程
- `localization-completion`:v1 双语完整化 —— 完成 `values-en/strings.xml` 残留 TODO 翻译，新增内测相关双语 key，与 `localization` 主 spec 的双语硬规则对齐

### Modified Capabilities

- `release-readiness`:扩展内测阶段要求 —— 在 release variant 构建 / R8 / 签名 / 冷启 / mapping 输出要求之外，新增"内测前必须 verify 真 provider 联调 runbook 全部步骤通过" + "values-en TODO 占位必须 ≤5 条" + "known issues 文档首版已建"

## Impact

- **新增文档**(`docs/usage/`):`internal-testing.md` / `known-issues.md` / `feedback-channel.md`
- **新增双语 key**(`app/src/main/res/values/strings.xml` + `values-en/strings.xml`):5~10 条 internal testing 相关文案(参与方式 / 反馈渠道 / 已知问题引导 / 真机验证 step 文案)
- **完善双语翻译**:`values-en/strings.xml` 残留 TODO 占位补完(预估 30~60 条，具体看 R5 verify 时的 i18n gap 统计)
- **不发新代码**(本 change 主体是文档 + i18n 收尾，不动 Kotlin/Java 业务代码)
- **发布动作依赖用户**:首版 internal testing APK 发布走 `publish-release.sh`(用户本机持有 SSH key，沙箱无法代发);runbook 真机 verify 依赖用户手头 5 台真机(小米 / 华为 / OPPO / vivo / 其它)
- **回滚**:本 change 主体是文档 + i18n，回滚成本低 —— `git revert` 即可，不影响运行时