# 飞书 CLI Skills 对照分析

参考自 [larksuite/cli @ 2026-06-23](https://github.com/larksuite/cli/tree/main/skills) · OpenSpec change `feishu-cli-analysis`

## 1. 飞书 CLI 27 skills 列表

按仓库 `skills/` 目录字母排序，本项目评估时共 **27** 个 AI Agent skills(早期估算为 26，后增 `lark-event` / `lark-vc-agent` 等，总数更新为 27)。

| # | skill | 1-2 句功能概述 |
| --- | --- | --- |
| 1 | `lark-approval` | 飞书审批流:创建/查询/审批/拒绝实例 |
| 2 | `lark-apps` | 飞书应用管理:增删查改企业内部应用 |
| 3 | `lark-attendance` | 考勤打卡记录:签到/签退/请假/补卡 |
| 4 | `lark-base` | 多维表格(bitable):表 / 字段 / 记录 / 视图 / 表单 / 仪表盘 |
| 5 | `lark-calendar` | 日历事件:创建 / 查询 / 提醒 / 与会人 |
| 6 | `lark-contact` | 通讯录:用户 / 部门 / 群组成员 |
| 7 | `lark-doc` | 云文档(docx)CRUD + block 操作 |
| 8 | `lark-drive` | 云空间:文件 / 文件夹 / 权限 / 上传下载 |
| 9 | `lark-event` | 事件订阅与回调:WebSocket 长连接 |
| 10 | `lark-im` | 即时消息:发送 / 接收 / 群聊 / 单聊 |
| 11 | `lark-mail` | 邮件:草稿 / 发送 / 收件箱 |
| 12 | `lark-markdown` | 云空间原生 `.md` 文件操作 |
| 13 | `lark-minutes` | 妙记(会议记录):语音转写 + AI 摘要 |
| 14 | `lark-note` | 笔记(原 `lark-doc` 子集):快速笔记 |
| 15 | `lark-okr` | OKR 目标管理 |
| 16 | `lark-openapi-explorer` | 飞书 OAPI 元数据浏览:动态查询 endpoint spec |
| 17 | `lark-shared` | 跨 skill 共享工具(常用辅助) |
| 18 | `lark-sheets` | 电子表格:单元格读写 / 公式 / 筛选 / 导出 |
| 19 | `lark-skill-maker` | 创建自定义 skill 的元工具 |
| 20 | `lark-slides` | 演示文稿(PPT) |
| 21 | `lark-task` | 任务管理(原 TODO，跨项目) |
| 22 | `lark-vc-agent` | 视频会议 AI 助手 |
| 23 | `lark-vc` | 视频会议:会议室 / 预约 / 录制 |
| 24 | `lark-whiteboard` | 画板:协作绘图 |
| 25 | `lark-wiki` | 知识库:空间 / 节点 / 文档层级 |
| 26 | `lark-workflow-meeting-summary` | 工作流:会议纪要自动生成 |
| 27 | `lark-workflow-standup-report` | 工作流:站会日报自动生成 |

## 2. 对应矩阵(对照本项目 `feature/` 包)

| skill | 功能 | 我们 `feature/` | 状态 | 备注 |
| --- | --- | --- | --- | --- |
| `lark-doc` | 云文档CRUD | `feature/quicknote` (Feishu 同步层) | **已有** | `core/feishu/` 全套实现;`feishu-doc-service-refactor` 刚落地 |
| `lark-contact` | 通讯录 / 实体别名 | `feature/settings` (实体别名管理) | **部分有** | 仅实体别名，缺通讯录 API(用户/部门) |
| `lark-mail` | 邮件 | 无 | **不需要** | 我们 App 不发邮件，纯笔记场景 |
| `lark-im` | 即时消息 | 无 | **不需要** | 我们 App 不发消息 |
| `lark-event` | 事件订阅 WS | `core/feishu/sync/SyncWorker` 是 WorkManager 轮询 | **架构不同** | WS 长连接不适合移动端 background |
| `lark-base` | 多维表格 | 无 | **缺失** | P1 候选:AI 写作结果自动进表格 |
| `lark-sheets` | 电子表格 | 无 | **缺失** | P1 候选:同 lark-base |
| `lark-wiki` | 知识库 | 无 | **缺失** | **P0** 候选:用户已多次问过"知识库同步" |
| `lark-calendar` | 日历事件 | 无 | **缺失** | P2 候选:笔记 → 日历提醒(中期) |
| `lark-task` | 任务管理 | 无 | **缺失** | P2 候选:笔记转 TODO 跨项目(产品边界) |
| `lark-drive` | 云空间 | 无 | **可不需要** | 文档 CRUD 已通过 lark-doc 覆盖 |
| `lark-markdown` | `.md` 原生文件 | 无 | **可不需要** | 与 lark-doc 重叠 |
| `lark-minutes` | 妙记(会议) | 无 | **可不需要** | v1 笔记场景无会议转写 |
| `lark-note` | 笔记(quick note) | `feature/quicknote` | **已有** | 我们的 quicknote 即对应 |
| `lark-okr` | OKR | 无 | **可不需要** | v1 边界外 |
| `lark-openapi-explorer` | OAPI 元数据查询 | `feishu-openapi-kotlin-client` 评估中 | **评估中** | 暂不落地 |
| `lark-shared` | 跨 skill 共享 | 无 | **可不需要** | 仅 CLI 内部用 |
| `lark-skill-maker` | 自定义 skill 元工具 | 无 | **可不需要** | CLI 用户用 |
| `lark-slides` | PPT | 无 | **可不需要** | v1 边界外 |
| `lark-vc-agent` | 视频会议 AI 助手 | 无 | **可不需要** | v1 边界外 |
| `lark-vc` | 视频会议 | 无 | **可不需要** | v1 边界外 |
| `lark-whiteboard` | 画板 | 无 | **可不需要** | v1 边界外 |
| `lark-approval` | 审批流 | 无 | **缺失** | P3 候选:笔记 → 审批(企业场景) |
| `lark-apps` | 应用管理 | 无 | **可不需要** | 我们已是企业内部应用，不管理 app |
| `lark-attendance` | 考勤 | 无 | **可不需要** | v1 边界外 |
| `lark-workflow-meeting-summary` | 会议纪要工作流 | 无 | **P3 候选** | v1 边界外 |
| `lark-workflow-standup-report` | 站会日报工作流 | 无 | **P3 候选** | v1 边界外 |

## 3. v2 路线图候选

按 ROI 排序。每个候选对应一个未来 OpenSpec change + `feature/<name>/` 包。

### P0(必做 · 用户已多次要求过)

- **`feishu-wiki-sync`**:知识库双向同步
  - 触发:用户曾明确说"想同步到知识库"
  - 工程量:2-3 周(reuse `FeishuDocService` 模式，加 `FeishuWikiService`)
  - 价值:知识库是企业笔记的"上级"容器，集成后用户能直接把随手记归档到正式 wiki

### P1(应做 · 与现有 feature 强协同)

- **`feishu-base-export`**:AI 写作结果自动进多维表格
  - 触发:用户多次要求"AI 整理后的笔记导出结构化数据"
  - 工程量:1-2 周(reuse FeishuDocService，加多维表格 SDK)
  - 价值:把"随手记"和"项目管理"打通，商业用户场景
- **`feishu-sheets-export`**:同上，电子表格
  - 触发:同 lark-base，适合"日报 / 周报"场景
  - 工程量:1 周
  - 价值:轻量 BI 输出

### P2(可做 · 远期)

- **`feishu-calendar-reminder`**:笔记 → 日历事件
  - 触发:用户笔记含"明天 3 点开会"等时间引用时自动创建事件
  - 工程量:2 周(需 NLP 时间抽取 + calendar API)
  - 价值:把"记录"延伸到"行动"
- **`feishu-task-bridge`**:笔记转 TODO
  - 触发:checkbox 语法或显式 #task 标签
  - 工程量:1 周
  - 价值:任务管理
- **`feishu-approval-bridge`**:笔记 → 审批流
  - 触发:企业用户场景
  - 工程量:3 周
  - 价值:企业付费转化

### P3(可做 · 边界探索)

- `lark-minutes` 妙记集成
- `lark-vc` 会议预约 + 笔记自动记录
- `lark-workflow-meeting-summary` 会议纪要 AI 摘要
- `lark-workflow-standup-report` 站会日报

## 4. 注意事项

- 本文档基于 `larksuite/cli` 仓库 @ 2026-06-23 snapshot，后续 CLI 新增 / 删除 skill 时需刷新
- 评估标准偏保守(本项目 v1 是"个人 / 轻量企业笔记"，不是完整办公套件)，部分 skill 在我们场景下"不需要"
- "对应矩阵"中的状态是基于本项目 v1 现状，后续 archive 一个 P0/P1 change 后状态会更新
- `lark-openapi-explorer` 对应我们 `feishu-openapi-kotlin-client` 评估 change，已单独文档化(`docs/usage/feishu-openapi-generator-eval.md`)

## 5. 后续刷新时机

- 飞书 CLI 发布 major 版本(skill 数量或 API 重大变化)
- 本项目 archive 一个 P0/P1 后，更新矩阵的"状态"列
- 半年一次例行 review(防止文档与现实脱节)
