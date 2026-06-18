# OpenSpec 使用记录

> 这份文档是"我（Claude）"对当前仓库里 OpenSpec 工作流的理解笔记，目的是让以后接手这个项目的工程师（包括未来的我自己）能快速看明白 OpenSpec 在这个项目里**到底怎么用**。
>
> 资料来源（按权威性从高到低）：
> 1. 本仓库里 `.claude/skills/openspec-*/SKILL.md` 和 `.claude/commands/opsx/*.md` 的实际定义 —— 这是我们这套 OpenSpec 的"权威实现"，所有行为以它们为准。
> 2. 联网抓取的 OpenSpec 公开仓库（[Fission-AI/OpenSpec on GitHub](https://github.com/Fission-AI/OpenSpec)）的 README，用于交叉验证整体形态。
> 3. 我自己的归纳总结。
>
> 抓取说明：`https://www.openspec.dev/` 当时 `ECONNREFUSED`，部分 `WebSearch` 调用返回 400 错误，所以本节以本地 skill 文件为主，外部链接仅作参考。

---

## 1. 一句话定义

**OpenSpec = 给 AI 编程助手用的"轻量规约层"（Spec-Driven Development, SDD）。** 在写代码之前先以机器可读的形式把"要做什么、为什么、怎么做、验收什么"记录下来，等人/AI 都同意之后，再按任务清单逐项实现。

它不替代设计文档，也不替代 issue tracker —— 它解决的是 **"AI 写代码前需要一个明确的契约"** 这件事。

---

## 2. 一个 change 的生命周期

OpenSpec 的核心概念是 **change**（一次变更）。一个 change 走完以下五个阶段就结束，可以归档：

```
┌────────────┐   ┌────────────┐   ┌────────────┐   ┌────────────┐   ┌────────────┐
│  explore   │──▶│  propose   │──▶│  apply     │──▶│  sync      │──▶│  archive   │
│ (想清楚)   │   │ (写契约)   │   │ (实现)     │   │ (并入主规格)*│   │ (归档)     │
└────────────┘   └────────────┘   └────────────┘   └────────────┘   └────────────┘
      │                                                  ▲
      └──────── (可随时再进入 explore) ───────────────────┘
```

\* `sync` 是把 change 里的 delta spec 并入 `openspec/specs/` 下的"主规格"。这个步骤**不是强制的**，归档时可以选择跳不跳。

每个阶段在 Claude Code / Claude.ai 中对应一个 skill + 一个 slash command，二者功能等价：

| 阶段 | Skill（自动调用，名字触发） | Slash command（手动 `/` 触发） | 目的 |
| --- | --- | --- | --- |
| explore | `openspec-explore` | `/opsx:explore` | 思考模式：澄清需求、对比方案、画图，不写代码 |
| propose | `openspec-propose` | `/opsx:propose` | 一次性生成 `proposal.md` / `design.md` / `tasks.md` + 可选的 `specs/` |
| apply | `openspec-apply-change` | `/opsx:apply` | 照 `tasks.md` 逐项实现，过程中勾掉已完成的 |
| sync | `openspec-sync-specs` | `/opsx:sync` | 把 change 的 delta spec 智能合并进 `openspec/specs/` |
| archive | `openspec-archive-change` | `/opsx:archive` | 把整个 change 移进 `openspec/changes/archive/YYYY-MM-DD-<name>/` |

注意：本仓库目前**只装了上面 5 个 skill**。OpenSpec 还有别的命令（`openspec init`、`openspec config profile`、`openspec update`、`openspec list` 等），它们来自外部 CLI，**不是 Claude 的 skill**，要靠 shell 调。

---

## 3. 目录布局

```
openspec/
├── config.yaml                 # schema 选择（spec-driven / 其它）、项目级 context、artifact 规则
├── specs/                      # 主规格（source of truth）
│   └── <capability>/
│       └── spec.md
└── changes/                    # 活动中的 change
    ├── archive/                # 已归档的 change
    │   └── YYYY-MM-DD-<name>/
    └── <change-name>/
        ├── proposal.md         # 为什么做、改什么
        ├── design.md           # 怎么做
        ├── tasks.md            # 实施清单（复选框）
        └── specs/              # delta spec（可选）
            └── <capability>.md
```

要点：

- `specs/<capability>/spec.md` 是**已经并入主线的规约**，被多个 change 引用。
- `changes/<change-name>/specs/<capability>.md` 是 **delta**，表达"这次变更相对主规约的差异"，由 `sync` 阶段合并。
- 归档时整个 `changes/<change-name>/` 目录被移到 `changes/archive/YYYY-MM-DD-<change-name>/`。
- `openspec/changes/` 这个根目录**不**是固定的 —— 实际位置由 CLI 从 `.openspec.yaml` 里解析（`planningHome.changesDir`），跟着 CLI 走，不要硬编码。

---

## 4. 五个阶段的具体行为

> 以下每段都先讲"这个阶段的**目的**"，再列"skill 实际执行的步骤"。命令名均照搬本地 skill 文件原文。

### 4.1 `explore` —— 思考模式

**目的**：在想动手之前把问题想清楚。可以看代码、可以对比方案，**不能写实现代码**。可以画 ASCII 草图、列权衡表、挑战假设。

入口：

- 给我一个**模糊想法**（"我想加实时协作"）
- 给我一个**具体问题**（"认证系统太乱了"）
- 给我一个**change 名**（"add-dark-mode"）—— 配合现有 change 上下文一起想
- 给我一个**对比题**（"Postgres vs SQLite？"）

skill 的 stance：

- 好奇而不是照本宣科，问顺势冒出来的问题。
- 用 ASCII 图可视化（状态机、数据流、对比表）。
- 把发现的东西和真实代码对照，不空想。
- 不要自动保存洞察，**先问**用户要不要写到哪个 artifact。
- 发现不清晰时，提议"是否要进入 propose 阶段"。

捕获洞察的归属（建议在 explore 末尾主动问）：

| 洞察类型 | 写到哪 |
| --- | --- |
| 新需求 | `specs/<capability>/spec.md` |
| 需求改动 | `specs/<capability>/spec.md` |
| 设计决策 | `design.md` |
| 范围变化 | `proposal.md` |
| 新工作项 | `tasks.md` |
| 假设被推翻 | 上述对应 artifact |

> 重要约束：explore 阶段**绝不**写实现代码。如果用户在 explore 中要求"先实现一下"，要提醒他先退出 explore、再开一个 propose。

### 4.2 `propose` —— 一次性生成所有 artifact

**目的**：输入一个需求（或一个 kebab-case 的 change 名），AI 自动创建 `proposal.md` / `design.md` / `tasks.md`，并按 schema 串行生成所有"实现前需要齐备"的 artifact。

skill 实际流程（来自 `openspec-propose/SKILL.md`）：

1. **如果没有明确输入**，调用 `AskUserQuestion` 问"你想构建/修复什么"。从描述里提炼 kebab-case 名（例如 "add user authentication" → `add-user-auth`）。
2. **创建 change 目录**：
   ```bash
   openspec new change "<name>"
   ```
   CLI 根据 `.openspec.yaml` 决定放哪，不要自己猜路径。
3. **查询构建顺序**：
   ```bash
   openspec status --change "<name>" --json
   ```
   解析出：
   - `applyRequires`：实现前必须齐备的 artifact id 列表（例如 `["proposal", "design", "tasks", "specs"]`）。
   - `artifacts`：所有 artifact 的当前状态与依赖关系。
   - `planningHome` / `changeRoot` / `artifactPaths` / `actionContext`：路径和作用域。
4. **按依赖顺序串行生成 artifact**：
   - 对每个"依赖已就绪"的 artifact，跑 `openspec instructions <id> --change "<name>" --json`。
   - instructions 返回 `context` / `rules` / `template` / `instruction` / `resolvedOutputPath` / `dependencies`。
   - **关键**：`context` 和 `rules` 是给 AI 的约束，**不要**复制到输出文件里。
   - 用 `template` 作为骨架，填上 `instruction` 指导的内容，写到 `resolvedOutputPath`。
5. **逐个勾掉进度**，每写完一个就重跑 `status` 验证，直到 `applyRequires` 全部 done。

artifact 写什么（spec-driven schema 的默认形态）：

- **proposal.md**：做什么 & 为什么、范围（in/out of scope）、影响面。
- **design.md**：怎么做（架构、关键决策、权衡、依赖）。
- **tasks.md**：分步骤的实施清单，每条以 `- [ ]` 开头，完成后改为 `- [x]`。
- **specs/<capability>.md**（可选但常见）：用 delta 格式表达"对主规约的差异"，包含 `## ADDED Requirements` / `## MODIFIED Requirements` / `## REMOVED Requirements` / `## RENAMED Requirements` 四种 section。

完成后给用户的提示：

> "All artifacts created! Ready for implementation. Run `/opsx:apply` to start."

---

### 4.3 `apply` —— 照着 `tasks.md` 实现

**目的**：从 `tasks.md` 顶部开始逐项实现，每完成一项就把 `- [ ]` 改成 `- [x]`。

skill 实际流程（来自 `openspec-apply-change/SKILL.md`）：

1. **选 change**：用户给了名就用名；没给就 (a) 从上下文推断，(b) 只有一个活动 change 就自动选，(c) 多个就用 `AskUserQuestion` 让用户选。**永远要先宣布** `"Using change: <name>"` 并提示怎么覆盖。
2. **查状态**：`openspec status --change "<name>" --json`，确认 schema 是 `spec-driven`、定位 tasks 在哪个 artifact。
3. **取 apply 指引**：`openspec instructions apply --change "<name>" --json`，拿到 `contextFiles`（要读的上下文文件）、进度、任务清单、动态指令。
4. **读 contextFiles**（spec-driven schema 下通常是 `proposal.md` / `specs/` / `design.md` / `tasks.md`）。
5. **播报进度**：`<schema>` / N/M 已完成 / 剩余任务一览 / CLI 动态指令。
6. **循环实现**：每条 pending task 单独处理，最小化改动；改完立即把对应复选框打勾。

状态机：

- `state: "blocked"` —— 缺 artifact，引导用户回到 propose 阶段补齐。
- `state: "all_done"` —— 全部完成，建议 archive。
- 其他 —— 正常推进。

**会暂停的情况**：

- 任务描述含糊。
- 实现时发现 design 本身有问题。
- 出错 / 阻塞。
- 用户打断。

暂停时给三选一之类的选项让用户决定。

> **Workspace guard**：如果 `actionContext.mode` 是 `workspace-planning` 且 `allowedEditRoots` 为空，说明这次 apply 跨多个仓库 / 链接目录，当前切片不支持直接编辑；要把这些外部目录当 read-only 上下文，让用户先选好"作用范围"再继续。

---

### 4.4 `sync` —— 把 delta spec 合并进主规格

**目的**：在归档前（也可独立于归档）把 change 里的 delta spec 智能合并到 `openspec/specs/<capability>/spec.md`。**这是 agent 驱动的合并，不是程序化合并**，所以可以做到"只加一个 scenario 而不复制整个 requirement"。

skill 实际流程（来自 `openspec-sync-specs/SKILL.md`）：

1. **选 change**：delta spec 不存在就直接告诉用户并停。
2. **解析 change 上下文**：`openspec status --change "<name>" --json`。workspace-planning 模式下不支持。
3. **找 delta spec**：用 `artifactPaths.specs.existingOutputPaths`。
4. **逐个 delta 应用到主 spec**：

   - `## ADDED Requirements`：主 spec 没有就新增，已存在就当成隐式 MODIFIED。
   - `## MODIFIED Requirements`：定位到主 spec 的对应 requirement，**只改 delta 提到的部分**，其他 scenario 保留。
   - `## REMOVED Requirements`：整块删。
   - `## RENAMED Requirements`：按 `FROM:` / `TO:` 重命名。
   - 若主 spec 不存在则新建 `openspec/specs/<capability>/spec.md`，加 `Purpose`（可标 TBD）和 `## Requirements` 段。

5. **汇报**：列出哪些 capability 改了、新增/修改/删除/重命名了哪些 requirement。

**delta spec 格式速查**（来自 skill 文件原文）：

```markdown
## ADDED Requirements

### Requirement: 新功能
系统 SHALL 干某件新事。

#### Scenario: 基本场景
- **WHEN** 用户做 X
- **THEN** 系统做 Y

## MODIFIED Requirements

### Requirement: 已存在功能
#### Scenario: 要新增的场景
- **WHEN** 用户做 A
- **THEN** 系统做 B

## REMOVED Requirements

### Requirement: 已废弃功能

## RENAMED Requirements

- FROM: `### Requirement: 旧名`
- TO: `### Requirement: 新名`
```

合并是**幂等**的：跑两次结果应该一样。

---

### 4.5 `archive` —— 收尾

**目的**：把所有 artifact 已经 done、`tasks.md` 全部勾完的 change 打包放进 `openspec/changes/archive/YYYY-MM-DD-<change-name>/`。

skill 实际流程（来自 `openspec-archive-change/SKILL.md`）：

1. **选 change**：没给名就用 `AskUserQuestion` 让用户选（绝不自动猜）。
2. **查 artifact 完成度**：`openspec status --change "<name>" --json`。如有未 done 的 artifact，**警告** + 二次确认。
3. **查 task 完成度**：读 `tasks.md`，数 `- [ ]` vs `- [x]`。有未完成就警告 + 二次确认。`tasks.md` 不存在则跳过。
4. **评估 delta spec 同步状态**：
   - 没 delta spec → 不提示。
   - 有但未同步 → 提示 "Sync now (recommended)" vs "Archive without syncing"。
   - 已同步 → 提示 "Archive now" vs "Sync anyway" vs "Cancel"。
   - 用户选 sync 时，把工作派给 general-purpose subagent 去执行 `openspec-sync-specs`。
5. **执行归档**：
   ```bash
   mkdir -p "<planningHome.changesDir>/archive"
   mv "<changeRoot>" "<planningHome.changesDir>/archive/YYYY-MM-DD-<name>"
   ```
   目标已存在则失败（不要覆盖），让用户改名字或改日期。
6. **汇报**：change 名 / schema / 归档位置 / spec 同步状态 / 警告。

`.openspec.yaml` 跟着目录一起搬，不要漏。

---

## 5. 端到端最小流程示例

假设我们要给"writing-with-ai"项目加一个"暗色模式"。

```bash
# 1. 探索（可选，模糊需求时强烈推荐）
/opsx:explore 我想加暗色模式

# 2. 生成契约（一次性产出 proposal + design + tasks + 可选 specs）
/opsx:propose add-dark-mode

# 3. 实现（逐项打勾）
/opsx:apply add-dark-mode

# 4. 把 delta spec 并入主规格（如果 change 里有 specs/）
/opsx:sync add-dark-mode

# 5. 归档
/opsx:archive add-dark-mode
```

等价地用 skill 名触发也行：

```
openspec-explore ...       # 思考模式
openspec-propose add-dark-mode
openspec-apply-change add-dark-mode
openspec-sync-specs add-dark-mode
openspec-archive-change add-dark-mode
```

---

## 6. 这个项目里的实际情况

- **配置**：`openspec/config.yaml` 当前只有一行 `schema: spec-driven`，`context` / `rules` 都没填。一旦确定项目技术栈（路由、状态管理、API 调用方式等），应该写进 `config.yaml:context` —— 这样以后 propose 出来的 artifact 会自动带着这些约束生成。
- **现有内容**：
  - `openspec/specs/` 是空目录（没有任何 capability）。
  - `openspec/changes/` 只有一个空的 `archive/` 子目录。
  - 也就是说项目目前**没有任何活动 change**。
- **CLI 依赖**：skill 全部依赖外部 `openspec` CLI（`openspec new change` / `openspec status` / `openspec instructions` / `openspec list` 等）。如果环境里没装 CLI，这些 skill 都会失败；装一下就行，仓库本身不带 CLI。
- **不替代 npm 命令**：OpenSpec 只管"规约和任务编排"，具体构建/测试还是 `npm run dev` / `npm run build` / `npm run lint`（见根目录 `CLAUDE.md`）。

---

## 7. 一些容易踩的坑

1. **不要硬编码路径**。change 根目录、archive 目录、spec 输出位置都从 `openspec status --change ... --json` 的 `planningHome` / `changeRoot` / `artifactPaths` 里读，不要凭"应该是 `openspec/changes/<name>/`"去推断。
2. **propose 时不要把 `context` / `rules` 抄进 artifact**。这两个字段是给 AI 看的约束，不是文件内容。
3. **不要在 explore 模式里写实现代码**。explore 只产 OpenSpec artifact。
4. **apply 阶段改 task 复选框**。完成一条就把 `- [ ]` 改成 `- [x]`，否则下次跑会重复实现。
5. **delta spec 是"意图"，不是"完整文档"**。MODIFIED 只需要列要改的 scenario，不要把整个 requirement 复制过去。
6. **归档前先 sync**。delta spec 留在 change 里不会自动并入主线，归档之后它就只在 archive 里了。
7. **workspace-planning 模式**。如果你看到 `actionContext.mode: "workspace-planning"`，apply / archive / sync 都不支持直接编辑外部目录，要按"先选作用范围"的方式处理。
8. **OpenSpec 不替代 issue tracker**。它解决"AI 写代码前需要契约"，不解决"任务排期、负责人、SLA"。

---

## 8. 一页速查表

| 我想… | 用什么 |
| --- | --- |
| 想把一个模糊想法聊清楚 | `openspec-explore` 或 `/opsx:explore` |
| 从一个需求直接产出所有 artifact | `openspec-propose` 或 `/opsx:propose` |
| 把一个已写好的 change 落地实现 | `openspec-apply-change` 或 `/opsx:apply` |
| 把 change 的 delta spec 合并进主规格 | `openspec-sync-specs` 或 `/opsx:sync` |
| 收尾归档一个完成的 change | `openspec-archive-change` 或 `/opsx:archive` |
| 看现在有哪些活动 change | `openspec list --json`（外部 CLI） |
| 看某个 change 的当前状态 | `openspec status --change <name> --json` |
| 改 schema / 项目级 context | 编辑 `openspec/config.yaml`（schema 在最上面） |
| 装或更新 OpenSpec CLI | 外部命令，按 [GitHub README](https://github.com/Fission-AI/OpenSpec) 走 |

---

## 9. 参考链接

- OpenSpec 仓库：<https://github.com/Fission-AI/OpenSpec>
- 官方站：<https://www.openspec.dev/>（抓取时不可达，但域名属于项目）
- 本仓库内：
  - 配置文件：`openspec/config.yaml`
  - Skill 目录：`.claude/skills/openspec-*/`
  - 命令目录：`.claude/commands/opsx/`
  - 项目级指引：`CLAUDE.md`（根目录）
