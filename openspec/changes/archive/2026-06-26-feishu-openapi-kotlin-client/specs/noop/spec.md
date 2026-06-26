## ADDED Requirements

### Requirement: This change introduces no behavior or spec deltas

`feishu-openapi-kotlin-client` MUST 保持为**纯调研 / 决策** change,仅产出 `docs/usage/feishu-openapi-generator-eval.md` 评估报告与决策结论;不修改任何现有 capability 的 REQUIREMENTS,不引入新 capability。后续若决策"继续",新开独立 change 做实际生成与替换。

#### Scenario: 无现有 spec 被修改
- **WHEN** `openspec archive feishu-openapi-kotlin-client`
- **THEN** 现有 `openspec/specs/` 下任一 spec 文件的 REQUIREMENTS 不变

#### Scenario: 无新 capability 引入
- **WHEN** grep `openspec/specs/`
- **THEN** 没有新增 `<capability>/spec.md` 文件

#### Scenario: 评估报告落地
- **WHEN** 本 change archive 时
- **THEN** `docs/usage/feishu-openapi-generator-eval.md` MUST 存在,含 5 维评估表 + 决策