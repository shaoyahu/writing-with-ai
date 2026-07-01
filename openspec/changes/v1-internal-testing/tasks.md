## 1. i18n 收尾

- [x] 1.1 AI 扫 `app/src/main/res/values-en/strings.xml` 所有 `__TODO__` / 占位英文，统计 N 条
- [x] 1.2 AI 按上下文补完翻译(模型名 `DeepSeek` / `MiniMax` / `MiMo` / `Anthropic` / `Claude` MUST 保留原文不译，UI 文案 `保存/取消/设置` MUST 翻)
- [x] 1.3 AI 双语加 5~10 条 `internal_testing_*` key + 4~6 条 `provider_step_*` key(对齐 `real-provider-integration.md` DeepSeek checklist 项数)
- [x] 1.4 AI 校验 `values/strings.xml` + `values-en/strings.xml` 新 key 集合完全一致(0 missing in either side)

## 2. 内测文档新建

- [x] 2.1 AI 新建 `docs/usage/internal-testing.md`(内测范围 5 人 / debug 通道 / 反馈渠道入口 / 验收 4 周标准 / 升级 release 条件)
- [x] 2.2 AI 新建 `docs/usage/known-issues.md`(从 R5 review / R6 review / entity-extraction-polish deferred / 国产 ROM widget 限制 4 源汇总，每条 severity + description + workaround + fix plan)
- [x] 2.3 AI 新建 `docs/usage/feedback-channel.md`(反馈入口占位 / bug report 模板 7 字段 / 提单流程)

## 3. ROM 适配矩阵 + runbook 验证状态

- [x] 3.1 AI 改 `docs/usage/rom-compatibility-notes.md`，在 4 大 OEM 段后**新增** 4 列 markdown 表(OEM / 限制项 / 验证状态 / 降级方案)，至少 5 行(4 OEM + 其它)
- [x] 3.2 AI 标每行初始状态 `[pending]`(用户真机跑后改 `[verified]` / `[deferred]`)
- [x] 3.3 AI 在 `docs/usage/real-provider-integration.md` DeepSeek 段每条 checklist 加 verify 状态位，初始 `[pending]`

## 4. 已知问题首版汇总

- [x] 4.1 AI 整理 R5 review 5 项 fix 中 deferred 项 → `known-issues.md`
- [x] 4.2 AI 整理 R6 review 7 项中非 fix 项(架构反向依赖已修，但余下 <80 项 deferred)
- [x] 4.3 AI 整理 entity-extraction-polish 6 项 deferred → `known-issues.md`
- [x] 4.4 AI 整理国产 ROM widget 限制(MIUI / HarmonyOS / ColorOS / OriginOS)→ `known-issues.md`
- [x] 4.5 AI 在 `known-issues.md` 末尾加维护说明(维护人: AI 主动汇总 + 用户审，每周巡检一次)

## 5. 验证

- [x] 5.1 `export JAVA_HOME=/opt/homebrew/opt/openjdk@17` + `./gradlew :app:ktlintCheck` 全绿
- [x] 5.2 `./gradlew :app:testDebugUnitTest` 全绿(本 change 不改业务代码，确保无回归)
- [x] 5.3 `./gradlew :app:assembleDebug` 全绿(`values-en/strings.xml` TODO 占位不阻塞 debug)
- [x] 5.4 AI 人工 grep `values-en/strings.xml` 含 `__TODO__` 数 ≤5(本 change 应远低于此阈值;release 通道再触发 preflight)

## 6. 用户侧动作(AI 不代，仅跟踪清单)

- [ ] 6.1 用户替换 `feedback-channel.md` 反馈入口占位为真实联系方式(邮箱 / 飞书机器人 / 微信群)
- [ ] 6.2 用户拉 5 人内测名单 + 每人 ROM 角色(小米 / 华为 / OPPO / vivo / 其它)
- [ ] 6.3 用户跑 5 台真机 verify，把 `rom-compatibility-notes.md` 矩阵从 `[pending]` 改 `[verified]` / `[deferred]`
- [ ] 6.4 用户跑 `real-provider-integration.md` DeepSeek 段 checklist 真机端到端，从 `[pending]` 改 `[verified]`
- [ ] 6.5 用户走 `publish-release.sh debug`(沙箱不可代发)首版 internal testing APK 到 `nananxue.cn/app/debug/`
- [ ] 6.6 用户邀请 5 名内测人员走反馈渠道

## 7. 进度维护

- [x] 7.1 AI 收口本 change 后，在 `docs/progress.md` 顶部按倒序加一条 v1-internal-testing entry(M0~M6 + R6 修复后 + i18n 收口 + 内测启动)
- [x] 7.2 AI 在 entry 内列:本 change 文档/i18n 收尾 + 5 项用户侧动作待完成 + release preflight 实现推迟到 v1.1