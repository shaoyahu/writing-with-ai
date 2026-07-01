## Why

飞书 CLI(larksuite/cli)仓库的 26 个 AI Agent skills(`lark-doc` / `lark-im` / `lark-base` / `lark-sheets` / `lark-wiki` / `lark-calendar` / ...)是对飞书业务域的成熟拆分，与本项目 v1 的 `feature/` 包结构有部分对应(quicknote ↔ lark-doc、settings ↔ contact entities)，也有部分缺失(知识库 / 多维表格 / 日历)。把这 26 个 skills 列表对照我们 feature 包，产出 v2 路线图候选，作为后续 OpenSpec changes 的输入。

## What Changes

- **写一份分析文档** `docs/usage/feishu-cli-analysis.md`，含:
  - 26 个 skills 列表 + 每个的 1-2 句功能概述
  - 与本项目 `feature/` 包的对应矩阵(已有 / 缺失 / 可借鉴)
  - v2 路线图候选(每个缺失 skill 对应一个新 feature 包，标优先级)
- **不引入新代码**，纯文档产物

## Capabilities

### New Capabilities

无。

### Modified Capabilities

无。

## Impact

- **新增文档**:`docs/usage/feishu-cli-analysis.md`(纯 markdown，无代码)
- **代码**:无
- **依赖**:无
- **风险**:文档可能与飞书 CLI 后续版本脱节，需在文档头部注明"参考自 larksuite/cli @ 2026-06-23"
- **回退**:文档可整段删除，零代码影响