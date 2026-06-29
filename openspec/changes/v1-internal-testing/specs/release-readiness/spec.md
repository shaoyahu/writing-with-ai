# release-readiness Specification (delta)

## ADDED Requirements

### Requirement: v1 release preflight verifies internal testing artifacts before release channel

release-readiness 阶段(走 `publish-release.sh release` 前)MUST 验证 4 项 v1 internal testing 准备就绪:

1. `docs/usage/known-issues.md` 首版已建 + ≥4 条 issues(severity + workaround + fix plan 字段齐)
2. `docs/usage/feedback-channel.md` 首版已建 + 含 bug report 模板(7 字段齐)
3. `app/src/main/res/values-en/strings.xml` TODO 占位 ≤5 条
4. `docs/usage/real-provider-integration.md` 中 DeepSeek 段 checklist 全 `[verified]`

任一未达 MUST fail preflight + exit code 非 0;release APK 构建阻断。

#### Scenario: Preflight fails on missing known-issues
- **WHEN** `docs/usage/known-issues.md` 不存在 或 issues < 4 条
- **THEN** preflight 任务 fail + 输出 `ERROR: known-issues.md missing or insufficient(< 4 issues)` + exit 1

#### Scenario: Preflight fails on TODO count
- **WHEN** 扫 `values-en/strings.xml` 含 `__TODO__` > 5 处
- **THEN** preflight 任务 fail + 输出 `ERROR: values-en TODO count N > 5` + exit 1

#### Scenario: Preflight fails on unverified DeepSeek checklist
- **WHEN** `real-provider-integration.md` DeepSeek 段任一条目非 `[verified]` / `[won't fix]`
- **THEN** preflight 任务 fail + 输出 `ERROR: DeepSeek checklist has unverified items` + 列出未 verify 项

#### Scenario: Preflight passes when all 4 checks succeed
- **WHEN** 4 项检查全 pass
- **THEN** preflight 任务 pass + 输出 `OK: v1 release preflight all green` + exit 0

#### Scenario: Preflight is debug-only gate
- **WHEN** 跑 `./gradlew :app:assembleDebug`
- **THEN** preflight 不参与(debug 通道允许 known-issues 缺失 / TODO > 5 / DeepSeek 未 verify)