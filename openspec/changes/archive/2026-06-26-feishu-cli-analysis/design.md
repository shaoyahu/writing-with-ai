## Context

飞书 CLI 仓库(larksuite/cli)有 26 个 AI Agent skills，是飞书业务域的成熟拆分。我们 `feature/` 包结构部分对应、部分缺失。把这 26 skills 对照现有 feature 包，产出 v2 路线图候选，供后续 OpenSpec changes 决策。

## Goals / Non-Goals

**Goals**
- 写一份分析文档 `docs/usage/feishu-cli-analysis.md`，含:
  - 26 skills 完整列表 + 每个 1-2 句功能概述
  - 与本项目 `feature/` 包的对应矩阵
  - v2 路线图候选(每个缺失 skill 对应一个潜在 feature 包，标优先级)
- 不引入新代码，纯文档产物

**Non-Goals**
- 不实际开发任何新 feature
- 不改现有 `feature/` 包

## Decisions

### 1. 文档结构

```
# 飞书 CLI skills 对照分析

## 1. 飞书 CLI 26 skills 列表
表格:skill 名 | 功能概述

## 2. 对应矩阵
表格:skill 名 | 我们的 feature 包 | 状态(已有 / 部分有 / 缺失 / 不需要)

## 3. v2 路线图候选
列表:按优先级 P0/P1/P2 排序的潜在 feature 包

## 4. 注意事项
- 文档基于 larksuite/cli @ 2026-06-23
- skill 列表可能随 CLI 版本变化
```

### 2. 对应矩阵的核心字段

| skill | 功能 | 我们 feature/ | 状态 |
| --- | --- | --- | --- |
| lark-doc | 云文档 CRUD | `feature/quicknote` (Feishu 同步层) | 已有 |
| lark-im | 消息 | 无 | 不需要(我们不发消息) |
| lark-base | 多维表格 | 无 | **缺失 v2 候选** |
| lark-sheets | 电子表格 | 无 | **缺失 v2 候选** |
| lark-wiki | 知识库 | 无 | **缺失 v2 候选** |
| lark-calendar | 日历 | 无 | **缺失 v2 候选** |
| lark-contact | 通讯录 | `feature/settings` (实体别名) | 部分 |
| ... | ... | ... | ... |

### 3. 路线图优先级判定

- **P0(必做)**:用户已要求过的功能(飞书知识库同步是常见诉求)
- **P1(应做)**:与现有 feature 强协同(多维表格 ↔ AI 写作结果)
- **P2(可做)**:远期扩展(日历提醒、消息推送)

## Risks / Trade-offs

[Risk] **文档脱节** — 飞书 CLI 后续新增/删除 skill，本分析过期
→ Mitigation:文档头部标注"参考自 larksuite/cli @ 2026-06-23",v2 时回看是否需要刷新

[Risk] **路线图变成 wishlist** — v2 候选 ≠ 必做，需 OpenSpec change 单独评估
→ Mitigation:文档明确"候选"，不直接进 roadmap;roadmap 仍由用户决策

## Migration Plan

1. M1 — 拉飞书 CLI 仓库 `skills/` 目录列表(用 GitHub API)
2. M2 — 对每个 skill 写 1-2 句功能概述(读 README + skills/<name>/ 内部)
3. M3 — 填对应矩阵(对照 `ls app/src/main/java/.../feature/`)
4. M4 — 标注 v2 路线图候选 + 优先级
5. M5 — `openspec validate feishu-cli-analysis --strict` 通过

**回退**:文档可整段删除，零代码影响。

## Open Questions

- Q1:26 个 skills 是否完整?仓库结构里 `skills/` 子目录可能只列 10-20 个，其他可能是 `shortcuts/` 或 `internal/`
- Q2:每个 skill 的成熟度?有些可能只是 stub
- Q3:是否需要在分析文档里给每个候选 feature 写"为什么值得做"与"复杂度估计"?