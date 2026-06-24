## ADDED Requirements

### Requirement: This change modifies existing capability spec semantics

`feishu-user-oauth` MUST 切换 token 类型为 user_access_token;`feishu-auth` capability 的 requirements 改变(token 类型、刷新机制)。本 change 只产出 proposal/design/tasks 与此 noop spec,具体要求合并到 `feishu-auth` 的 delta spec 后续展开。

#### Scenario: 现有 spec 内容不直接归档
- **WHEN** 本 change archive 时
- **THEN** `feishu-auth` 与 `feishu-api-client` spec 的现有 REQUIREMENTS 不变(本 change 不直接编辑这些 spec)

#### Scenario: 后续 change 处理能力差分
- **WHEN** 后续 follow-up change 要写 delta spec
- **THEN** 该 follow-up change 应合并 `feishu-auth` + `feishu-api-client` 的 spec delta,不与本 change 冲突