## Context

`NoteGraphScreen`(`feature/quicknote/graph/`)是用户在笔记详情页通过右上角菜单"查看关联图"进入的二级屏。数据流:`NoteGraphViewModel.loadSnapshot(noteId)` → `GraphRepository` 抓取 `GraphSnapshot(centerNodeId, nodes, edges, entityChips)` → `GraphDataLoader` 计算 `Map<noteId, NodeCoords>` → `NoteGraphCanvas(snapshot, coords)` 渲染。

`fix-note-graph-rendering` change 已修过 P0 坐标平移、P1 chip 下移 / 边可见度、P2 legend header;`fix-review-r1` 加了 a11y overlay。落地后真机目视验收暴露三个 UX 问题:

1. **标签碰撞**:中心节点和 1-hop 节点如果落在同一象限(本例 MSA 中心节点 + 左侧 Forge 节点),`drawText` 固定右偏移 (`center.x + radius + 4f, center.y + radius * 0.5f`) 会让两标签互相压字。
2. **边可见度**:light scheme `outline` ≈ `#79747E` 与 `surface` ≈ `#FEF7FF` 对比度仅 ~3:1;alpha 0.7 + stroke 1.5 后视觉上接近不可见。Material 3 dark scheme 对比度更高但仍偏细。
3. **首屏引导缺失**:`Empty` / `Loaded` 之间没有过渡解释,Loaded 态下用户看到 2 个孤立节点不知道能做什么。

## Goals / Non-Goals

**Goals:**
- 节点标签自动避让邻居,降低互相压字概率
- 边在 light / dark scheme 下都清晰可辨
- Loaded 态下用户能在 1-2 秒内知道"这是关联图,有 X 节点 Y 边,点击节点跳转、双指捏合缩放"
- Empty 态文案引导用户"如何让图出现内容"
- 不引入新依赖;沿用 Compose + Material 3

**Non-Goals:**
- 不重写布局算法(ForceLayout / CircularLayout 维持现状;只是渲染层调整)
- 不改 Room schema
- 不改 AI 调用流程
- 不引入图缩放 / 平移交互以外的复杂手势(已存在 pan + pinch)
- 不写视觉快照测试(单测已存在但视觉测试暂缺,以手动目视为准)

## Decisions

### D1. Label collision avoidance — 4 方向枚举 + 角度间隙最大

**决策**:对每个节点,在四个方向 (right, left, above, below) 各算一次"该方向放 label 后,标签矩形是否与邻居节点矩形重叠",选择无重叠的方向;若四个都重叠,选"与最近邻居夹角最大"的方向。

**算法**:
1. 对当前节点 N,先确定其渲染位置 `center = canvasCenter + Offset(p.x, p.y)`,半径 `r = nodeRadiusFor(...)`。
2. 计算 label 矩形 (`textPaint.measureText(label)` + 一行高度 + 内边距 4dp) 在四个方向上的 box。
3. 对每个候选 box,检查它是否与 `for other in nodes.filter { it != N && distance(canvasCenter + other.p, center) < 2*(r + other.r + labelW) }` 的渲染矩形(节点圆外切矩形 + 标签矩形)相交。
4. 取首个不重叠的方向;若全重叠,取与最近邻居夹角最大的方向(右优先打破平衡)。

**为什么不用 force-directed 重新布局**:本次 scope 是渲染层修复,改算法输出会牵动 `GraphDataLoader` 的 force 收敛参数,影响下游 `pickNode` hit-test 坐标系;风险大于收益。

**为什么不用 GreedyVD 这种成熟 layout**:依赖外部 lib,scope 内不允许新增依赖。

### D2. Edge color & weight — theme-aware

**决策**:Light scheme 用 `onSurfaceVariant.copy(alpha=0.6)`;Dark scheme 用 `outlineVariant`(不降 alpha,因为 dark 上 outline 已经够深)。stroke 宽度 `2.5f + weight * 3f`。

**为什么 `onSurfaceVariant` 而不是 `primary` / `tertiary`**:边需要"中性、低饱和度",避免与节点(已用 primary)抢视觉焦点;`onSurfaceVariant` 在 Material 3 调色板中就是为此存在的"次级文本/分割线"色。

**为什么 dark 不降 alpha**:`outlineVariant` 在 dark scheme 上 RGB 已经接近中灰,降 alpha 会让它消失在背景中。

### D3. TopAppBar 副标题 — `titleContent` slot + `Column` 嵌套

**决策**:用 `CenterAlignedTopAppBar` 的 `title` slot 包 `Column { Text(title); Text(subtitle, style = labelSmall) }`,副标题文案 `X 个节点 · Y 条关联`。

**为什么不用 `BottomAppBar` 或 `SecondaryTopAppBar`**:Material 3 `CenterAlignedTopAppBar` 已有 `title` slot 支持多行 Column,改起来最便宜;新增 BottomAppBar 会改变屏高分配。

**为什么用 `labelSmall` 而不是 `bodySmall`**:副标题是元信息,字号小一档符合 Material type scale 的层级语义。

### D4. Guidance banner — BottomCenter,`surfaceContainerHigh` 背景

**决策**:`snapshot.nodes.size <= 2` 时,在画布 BottomCenter 叠一行 Surface(`surfaceContainerHigh` 背景 + `padding(horizontal=16dp, vertical=12dp)` + `labelMedium` 文本),文案见 spec。如果同时有 entity chips,chips 不再单独显示(banner 取代),避免双重浮层互相压。

**为什么 `surfaceContainerHigh`**:Material 3 token 体系下,这个色比 `surface` 略深一档,适合做浮层卡。

**为什么 chips 让位给 banner**:小图屏空间宝贵,chips 信息已包含在图本身(节点 = 笔记 = 实体来源),小图不需要重复浮层;只有节点多时 chips 才有帮助,因为标签截到 12 字符看不到实体名。

### D5. Empty-state 改造 — Icon + actionable copy

**决策**:`EmptyGraphBlock` 内部用 `Column { Icon(Icons.Filled.AccountTree); Text(note_graph_empty_actionable) }`,居中布局,padding 24dp。

**为什么用 `AccountTree`**:Material Icons 里的"树状"图标,语义上接近"笔记关联结构"。

## Risks / Trade-offs

- **[R1] Label collision 算法 O(n²) 复杂度** → 当前 ≤ 50 节点,实际开销 < 1ms / 帧,可接受。100+ 节点时再考虑 spatial index。**Mitigation**:在 `drawGraph` 入口对 n > 100 打 TODO 注释,留后续优化点。
- **[R2] Light scheme 新边色与 `outline` chip / Divider 区分度下降** → `onSurfaceVariant` 与 `outline` 在 M3 light 中接近但不等同(前者带 alpha 偏柔)。**Mitigation**:视觉验收时如果 chip 边线和图边线混淆,再切到 `primary.copy(alpha=0.3)`。
- **[R3] Guidance banner 与 entity chip overlay 共存时谁优先** → 已规定 banner 取代 chips(见 D4),但用户可能在小图里既想看实体又想要引导。**Mitigation**:banner 文案里已经包含 "通过标签、实体连过来的笔记",信息覆盖。
- **[R4] `loaded` 副标题在 0 节点时显示 `0 个节点 · 0 条关联` 看起来很怪** → spec 明确要求仍显示(避免布局抖动),但空态 body 会解释原因。**Mitigation**:可接受。
- **[R5] 用户首屏不知道 banner 是 dismiss-able 还是永久** → banner 没有 dismiss 按钮(每次 ≤ 2 节点都显示),用户可能觉得"它一直挡着"。**Mitigation**:banner 高度低(~40dp),且 chips 让位后画布上没别的浮层,视觉负担小。

## Migration Plan

1. 实现 + 单测 + 模拟器目视验证(见 tasks.md)
2. 不涉及 schema 变更,无需 DataStore / Room migration
3. 不涉及功能开关 / feature flag,直接生效
4. 回滚策略:本 change 在 `git revert` 后,旧 `fix-note-graph-rendering` 行为仍可工作(`drawGraph` 改动独立)

## Open Questions

- Q1: 用户测试时如果觉得 banner 文案太长(50+ 字),是否需要精简为两行?—— 实现期先按 spec 文案,验收后再迭代
- Q2: 边配色在 dark scheme 上是否要进一步加粗?—— 先按 spec,验收后看
- Q3: 节点标签碰撞算法是否要写成 `pickNode` 集成测试?—— 当前没视觉快照测试,先以手动目视为准