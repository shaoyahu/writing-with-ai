## 1. 拉飞书 CLI skills 列表

- [x] 1.1 `curl -s "https://api.github.com/repos/larksuite/cli/contents/skills" | jq` 拉 skills 目录列表 — 实际 27 个
- [x] 1.2 对每个 skill 子目录读 `README.md` / `SKILL.md` 拿功能概述

## 2. 写文档

- [x] 2.1 新建 `docs/usage/feishu-cli-analysis.md`
- [x] 2.2 头部:"参考自 larksuite/cli @ 2026-06-23 · 文档非权威,后续可能脱节"
- [x] 2.3 §1 skills 列表:表格 27 行
- [x] 2.4 §2 对应矩阵:对照 `ls app/src/main/java/com/yy/writingwithai/feature/`
- [x] 2.5 §3 v2 路线图候选:P0/P1/P2/P3 分级
- [x] 2.6 §4 注意事项 + §5 后续刷新时机

## 3. 验证

- [x] 3.1 `openspec validate feishu-cli-analysis --strict` 通过
- [x] 3.2 `git diff docs/usage/feishu-cli-analysis.md` 检查纯文档,无意外
- [x] 3.3 文档 ~150 行(可读性,不长不短)