## Why

`improve-note-graph-readability` change 在真机视觉验收中跑通,但 `NoteGraphCanvas.computeNodeLayouts` 的 4 方向碰撞算法 + atan2 fallback 完全靠肉眼看图验证。本地 code review `2026-07-12-improve-note-graph-readability-code-review-r1.md` 也指出 "4 方向碰撞算法没有单测,纯靠肉眼看图验收",建议补 `NoteGraphLayoutTest`。

不补测试的隐患:
- 算法被无意识改坏(降级回固定偏移 / 改错 `atan2` 方向 / 漏判邻居 label box)时,只能等下一次真机目视才发现
- `pickFallbackDirection` 的 atan2 行为已 review 标记过 L3 罕见 edge case,无测试意味着边缘行为永远不会被强制验证
- `LABEL_PRIORITY` 顺序改了不会触发 CI 失败

现在补:算法还热,改动小;推到 M5 polish 大包里反而增加 review 噪音。

## What Changes

- **重构** `NoteGraphCanvas.computeNodeLayouts` 为可测函数:
  - 从 `private fun DrawScope.computeNodeLayouts(...)` 改为 `internal fun computeNodeLayoutsFor(snapshot, coords, canvasSize, density): Map<String, NodeLayout>`(纯函数,无 Compose 依赖)
  - 原 `DrawScope` 入口变成薄包装:取 `size` + `density` 调纯函数
- **新增** `app/src/test/java/com/yy/writingwithai/feature/quicknote/graph/NoteGraphLayoutTest.kt`,JUnit5,覆盖:
  - 空 snapshot → 空 map
  - 1 节点无邻居 → label 默认 RIGHT
  - 2 节点水平相邻(右邻居在 RIGHT 方向)→ 本节点 label 走 LEFT 避让
  - 2 节点水平相邻(右邻居挤在 RIGHT)→ 本节点 LEFT 或 BELOW(由 LABEL_PRIORITY 决定)
  - 4 邻居包夹 4 象限 → 走 `pickFallbackDirection` 取最近邻居反方向
  - 邻居 label 多方向 OR 检测:邻居若用 RIGHT 会撞,但用 LEFT 不撞,本节点仍能选 RIGHT
  - 节点无 title(`null`) → labelBox = null
  - 边粗细 + 颜色升级不归本 change(走 DrawScope 渲染验证,见 §"Out of scope")
- **不**改 spec 行为契约:算法不变,只是可测化 + 测试覆盖。

## Capabilities

### New Capabilities
(无 — 不引入新用户可见能力)

### Modified Capabilities
- `note-graph-readability`: 加入 "算法行为可由 JVM 单测验证" 作为隐式 quality requirement;不改原有 5 条 REQUIREMENT,在每条 REQUIREMENT 末尾追加 `#### Scenario: Automated coverage` 指针,显式列出本 change 落地的测试场景名(`NoteGraphLayoutTest.2 节点水平相邻 → LEFT 避让` 等)。算法行为契约本身不变。

## Impact

**Affected code**:
- `app/src/main/java/com/yy/writingwithai/feature/quicknote/graph/NoteGraphCanvas.kt`
  - `NodeLayout` / `NodePre` 从 `private` 升 `internal`(只在 main + test 之间可见)
  - `LABEL_NONE/RIGHT/LEFT/ABOVE/BELOW` / `LABEL_PRIORITY` 升 `internal`
  - `labelBoxFor` / `nodeBBox` / `pickFallbackDirection` / `angularDiff` 升 `internal`
  - 新增 `internal fun computeNodeLayoutsFor(snapshot, coords, canvasSize: Size, density: Float): Map<String, NodeLayout>`
  - 旧 `DrawScope.computeNodeLayouts` 改为 1 行调用 `computeNodeLayoutsFor(size, density.toPx())`
- `app/src/test/java/com/yy/writingwithai/feature/quicknote/graph/NoteGraphLayoutTest.kt`(新建,~120 行)

**Affected APIs**:
- 无公开 API 变更。`internal` 只对本 module 可见。

**Affected dependencies**:
- 无。沿用现有 JUnit5 + kotlinx-coroutines-test。

**Affected tests**:
- 新增 `NoteGraphLayoutTest`(本 change 主交付)。
- 既有测试不破。

**Affected UX**: 无。

## Out of scope

- 渲染层视觉验证(edge color / strokeWidth / Canvas 文本绘制)仍是人工目视,不归本 change。
- `pickNode` 命中算法已有测试(若缺,本 change 一并补;若有,跳过)。
- Compose UI test / screenshot test 留给后续 polish,不强制本 change 落地。

## Approach summary

把 `DrawScope` 上的副作用拆掉:纯几何计算走 `Size` + `Density`,Compose 渲染负责把这两个值喂进去。算法本身无状态,可重复执行,无 race condition,无需 fake / mock。这是 M5 polish 范围内 "渲染逻辑可测化" 的最小动作。