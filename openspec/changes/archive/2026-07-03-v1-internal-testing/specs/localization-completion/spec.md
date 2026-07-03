# localization-completion Specification (delta)

## ADDED Requirements

### Requirement: values-en TODO 占位 ≤5 条 before release

`app/src/main/res/values-en/strings.xml` 在 release-readiness 阶段 MUST 含 ≤5 条 `__TODO__` / 占位英文翻译未完成条目;超 5 条 MUST NOT 升 release 通道。

debug 通道允许 ≥5 条 TODO(内测阶段由用户 + 内测人员反馈 + AI 主动补完);release-readiness 走 `publish-release.sh release` 前 MUST 检查 TODO 数。

#### Scenario: Release build blocked on too many TODOs
- **WHEN** 跑 `./gradlew :app:assembleRelease` 前 AI 扫 `values-en/strings.xml` TODO 数
- **THEN** TODO > 5 时 build 必须 fail(MUST 在 `app/build.gradle.kts` 的 `release` buildType 加 prebuild 任务或 `:app:checkReleaseReadiness` 校验任务)

#### Scenario: Debug build allows TODO
- **WHEN** 跑 `./gradlew :app:assembleDebug`
- **THEN** TODO 数无上限;只 warn 不 fail

### Requirement: Provider model names kept verbatim in translation

`values-en/strings.xml` 中 AI provider 模型名(`DeepSeek` / `MiniMax` / `MiMo` / `Anthropic` / `Claude`) MUST 保留原文不译;同款 key 在 zh 侧 MUST 也保留原文(中文段落里直接用英文字面)。

产品/UI 文本(如按钮 "保存" / "取消" / "设置" / "AI 助手") MUST 翻译;模型名/品牌名/技术术语 MUST NOT 翻译。

#### Scenario: DeepSeek preserved in both locales
- **WHEN** grep `values/strings.xml` + `values-en/strings.xml` 任意 key 包含 `DeepSeek`
- **THEN** 两个文件对应 key 字符串均含字面 `DeepSeek`(无 `深度求索` / `深度搜索` / `Deep Search` 替换)

#### Scenario: MiniMax preserved
- **WHEN** grep `values/strings.xml` + `values-en/strings.xml` 任意 key 包含 `MiniMax`
- **THEN** 两文件均含字面 `MiniMax`

#### Scenario: UI verbs translated
- **WHEN** grep `values-en/strings.xml` `保存` / `取消` / `设置` / `AI 助手`
- **THEN** 对应英文 `Save` / `Cancel` / `Settings` / `AI Assistant` 存在

### Requirement: Internal testing strings added in both locales

`values/strings.xml` + `values-en/strings.xml` MUST 新增 5~10 条 `internal_testing_*` 文案 key，覆盖:
- 内测参与方式(`internal_testing_howto`)
- 反馈渠道提示(`internal_testing_feedback`)
- 已知问题引导(`internal_testing_known_issues_hint`)
- 真机验证 step 文案(每条对应 `real-provider-integration.md` checklist,4~6 条 `provider_step_*`)

#### Scenario: internal_testing keys present in both locales
- **WHEN** grep `values/strings.xml` + `values-en/strings.xml` `internal_testing_`
- **THEN** 两文件均含 ≥5 条 `internal_testing_*` key

#### Scenario: provider_step keys mirror runbook checklist
- **WHEN** `grep -c "provider_step_" values-en/strings.xml`
- **THEN** 数 ≥ `real-provider-integration.md` 中 DeepSeek checklist 项数

#### Scenario: Bilingual parity for new keys
- **WHEN** 比较 `values/strings.xml` 与 `values-en/strings.xml` 的 `internal_testing_*` key 集合
- **THEN** 完全相同(0 missing in either side)