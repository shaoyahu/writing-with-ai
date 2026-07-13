## Context

`improve-note-graph-readability` 已 shipped:4 方向标签避让算法(`LABEL_PRIORITY` + `pickFallbackDirection`)+ 边色/粗细升级 + 副标题 + 引导 banner + 空态文案一并上线。算法行为靠真机目视 + 本地 review r1 校验。

Review r1 的 M1 修了邻居 label box 仅按 RIGHT 估算的误判,把判定升级成 4 方向 OR 检测(复杂度 O(4n) = O(n),仍 < 1ms / 帧)。同时 review 给出建议:

> 下次 review 时建议:加 JVM 单测覆盖 `computeNodeLayouts` — 当前 4 方向碰撞算法没有单测,纯靠肉眼看图验收。

现状痛点(算法层面):
- `DrawScope.computeNodeLayouts` 是渲染层的副作用函数,依赖 `TextPaint.measureText`(`textPaint: Paint`),`Paint` 在 JVM 单测里不能直接 mock
- `LABEL_RIGHT/.../BELOW`、`LABEL_PRIORITY`、`NodeLayout`、`NodePre`、`labelBoxFor`、`nodeBBox`、`pickFallbackDirection`、`angularDiff` 全是 `private`,单测不可达
- 算法行为(spec D1-D4 "4 方向避让 + 兜底方向")完全靠视觉判断,改坏没有红 bar

本 change 把算法从 DrawScope 解耦出来,转为纯几何函数 `computeNodeLayoutsFor(snapshot, coords, canvasSize, density): Map<String, NodeLayout>`,把文本宽度量化为 `(String) -> Float` 入参,在 `app/src/test/.../NoteGraphLayoutTest.kt` 跑 7+ 个场景。

## Goals / Non-Goals

**Goals**:
- 把 `computeNodeLayouts` 从 `DrawScope.computeNodeLayouts` 改成纯函数 `computeNodeLayoutsFor(snapshot, coords, canvasSize, density, labelWidthFor)`,无 Compose / Android framework 副作用
- 渲染层入口保留 `DrawScope.computeNodeLayouts(snapshot, coords, canvasCenter, textPaint)`,只负责组装 `size / density / labelWidthFor` 然后转调纯函数,行为 100% 等价
- 把算法相关常量与 data class 从 `private` 升 `internal`,允许 main + test 之间可见
- 新增 `app/src/test/java/com/yy/writingwithai/feature/quicknote/graph/NoteGraphLayoutTest.kt`,JUnit5,覆盖 8 个 spec / 算法加固场景
- 测试在 `:app:testDebugUnitTest` 跑通;总耗时 < 1 秒,不需 Robolectric / 模拟器

**Non-Goals**:
- 不改算法行为(spec D1-D4 五个 REQUIREMENT 全部不变);只换"可测 + 测试覆盖"
- 不引入新依赖(沿用现有 `androidx.compose.ui.geometry.{Offset, Rect, Size}` + `android.graphics.Paint`)
- 不动 `DrawScope.computeNodeLayouts` 之外的渲染层代码(绘制顺序 / 边色 / chip overlay 不动)
- 不引入 Compose UI test / screenshot test(留作后续 polish,本 change 不强制)
- 不引入 `RobolectricTestRunner` 注解(测试必须可在纯 JVM 跑通)

## Decisions

### Decision 1: 算法入口从 `DrawScope` 拆出为纯函数

新增

```kotlin
internal fun computeNodeLayoutsFor(
    snapshot: GraphSnapshot,
    coords: Map<String, NodeCoords>,
    canvasSize: Size,
    density: Float,
    labelWidthFor: (String) -> Float
): Map<String, NodeLayout>
```

- `canvasSize: Size` — 渲染画布尺寸,从 `DrawScope.size` 取,替代原 `canvasCenter: Offset`(本算法不用中心,只用 viewport 边界让 label box 不越界 — `labelBoxFor` 返回 `null` 当越界)
- `density: Float` — `density.toPx()` 已完成的值,从 `DrawScope` 取
- `labelWidthFor: (String) -> Float` — 把"量宽度"抽出来;生产传 `textPaint::measureText`,测试传 `{ it.length * 8f }`(8 px/char 固定 stub)

**为何不只把 `textPaint` 改成可选**:`DrawScope` 上的 `textPaint` 是渲染时构造的对象,带 `color` / `textSize` / `typeface`。构造与算法无关,可以让渲染层传进来,但**`Paint` 是 Android framework 类,在纯 JVM 单测里 `measureText` 默认 stub 返 `0f`,无法模拟真实字符宽度**。函数引用让测试完全摆脱 Paint,只关心"宽度"这个数。

**拒绝方案 B(参数全 Optional)**:把 `textPaint` / `canvasCenter` / `size` 都做成 nullable,签名变 `fun computeNodeLayouts(snapshot, coords, ..., canvasCenter = null, textPaint = null, size = null)`。看起来少一个函数,但参数语义模糊、空参数处理散,阅读时难判断实际行为。

**拒绝方案 C(算法移到 object + @JvmStatic)**:Kotlin idiom 不鼓励;仍要解决 `DrawScope` 依赖。

### Decision 2: 测试入口完全脱离 `Paint`

```kotlin
// 测试 fixture
val labelPx = 14f  // = density 1f × sp labelSize 14
val labelWidthFor: (String) -> Float = { it.length * 8f }  // 8 px/char stub

val layouts = computeNodeLayoutsFor(
    snapshot = GraphSnapshot(...),
    coords = mapOf("a" to NodeCoords(x = ..., y = ...)),
    canvasSize = Size(1000f, 1000f),
    density = 1f,
    labelWidthFor = { it.length * 8f }
)
```

纯函数零 framework 依赖:只引用 `androidx.compose.ui.geometry.{Offset, Rect, Size}`(纯数据结构,JVM 可达) + `com.yy.writingwithai.core.note.graph.{GraphSnapshot, NodeCoords, GraphNode}`(纯 data class)。

**确认路径**:`compose.ui.geometry.Offset/Size/Rect` 是普通 `data class`,无 Android framework 依赖;`GraphSnapshot / GraphNode / NodeCoords` 都是普通 data class。纯 JVM 可构造。

### Decision 3: 可见性升级范围

**升级到 `internal`**:
- `data class NodeLayout`
- `data class NodePre`
- `const val LABEL_NONE / LABEL_RIGHT / LABEL_LEFT / LABEL_ABOVE / LABEL_BELOW`
- `val LABEL_PRIORITY: IntArray`
- `internal fun computeNodeLayoutsFor(...)`
- `internal fun labelBoxFor(...)`
- `internal fun nodeBBox(...)`
- `internal fun pickFallbackDirection(...)`
- `internal fun angularDiff(...)`

**保持 `private`**:
- `private fun DrawScope.computeNodeLayouts(...)` — 只在渲染层内部被调,作为薄包装
- `private fun pickNode(...)` — 命中判定与渲染逻辑耦合,不必暴露
- `private fun nodeRadiusFor(...)` — 半径公式与渲染半径建模相关,Compose 之外不必暴露
- `private val chipCornerShape` — UI 单属

**为何不全升 `public`**:保持 `internal` 把算法能力限制在 `:app` module 内。`NoteGraphLayoutTest` 在 `app/src/test/` 下,与 main 共享 module,`internal` 即可访问。

### Decision 4: 测试 fixture 构造策略

**数据**:直接用构造函数构造 `GraphSnapshot` + `NodeCoords`,不 mock / 不假 provider:

```kotlin
private fun snapshot(nodes: List<GraphNode>): GraphSnapshot =
    GraphSnapshot(
        centerNodeId = nodes.firstOrNull()?.noteId ?: "center",
        nodes = nodes,
        edges = emptyList(),
        entityChips = emptyList(),
        truncated = false
    )

private fun coords(vararg pairs: Pair<String, Offset>): Map<String, NodeCoords> =
    pairs.associate { (id, off) -> id to NodeCoords(x = off.x, y = off.y) }
```

**算法预期值**:在测试里手工算 `labelBoxFor` 公式,把每个场景的 `chosen` 方向算出来作为断言的依据 — 不依赖算法自己(避免同义反复)。`labelBoxFor`/`nodeBBox` 现在 `internal`,测试可直接调构造预期 `Rect`,与算法返回值 `overlaps` 比较。

### Decision 5: 测试用例覆盖

8 个 case 对应 spec delta 的 6 个 Scenario + 2 个算法加固 case:

| ID | 场景 | spec 映射 |
| --- | --- | --- |
| T1 | 空 snapshot → 空 map | Scenario "Empty snapshot" |
| T2 | 单节点无邻居 → labelBox 走 LABEL_RIGHT | Scenario "Single node, no neighbors" |
| T3 | 两节点水平相邻 → 本节点选 LEFT 避让 | Scenario "Horizontal pair forces LEFT avoidance" |
| T4 | 4 邻居包夹 4 象限 → fallback 取最近邻居反方向 | Scenario "All four quadrants occupied triggers fallback" |
| T5 | 邻居 label 多方向 OR 检测:邻居若用 RIGHT 会撞,但 LEFT 不撞 → 本节点能选 RIGHT | Scenario "Multi-direction neighbor label check" |
| T6 | 节点 title=null → labelBox = null | Scenario "Untitled node has no label box" |
| T7 | 1 节点无邻居 + labelWidth=0 + LABEL_RIGHT box 越界 → fallback 仍返 RIGHT | spec "fallback 兜底方向" 加固(L3 review finding) |
| T8 | `Rect.overlaps` 严格不相接语义验证:T3 边界 case | spec "测试架构约束" 加固 |

T7/T8 是补 specs 没显式列但算法行为必须的 case;在测试文件顶部注释里说明是 spec delta 的"补充覆盖",不冲突 spec。

## Risks / Trade-offs

### Risk 1: 拆分后 `computeNodeLayouts` 与 `computeNodeLayoutsFor` 行为漂移
- **风险**:`DrawScope.computeNodeLayouts` 调纯函数时漏传某个值(如忘记用 `density.toPx()` 计算 labelHeightPx),行为与之前不一致。
- **缓解**:`DrawScope.computeNodeLayouts` 写成 5 行薄包装,review 时对每行打勾;tasks.md 标注"重构 commit 自带 review + 真机目视双验证"(沿用现有 flow);新增 `T3` 等场景本身就覆盖核心算法路径。

### Risk 2: `labelWidthFor` 是 function reference,JVM 闭包对象残留
- **风险**:主流程 `computeNodeLayoutsFor(..., textPaint::measureText)` 误传 lambda,意外捕获 `Paint` 引发内存泄漏。
- **缓解**:`textPaint::measureText` 是 Kotlin bound function reference,`textPaint` 仅持有调用栈期间,canvas 销毁时同时释放;`NoteGraphCanvas` 整个 Composable 离开 composition 时 `textPaint` 自动 GC,无需手动管理。`NoteGraphScreen` 已有 `remember` 节制重建,不存在泄漏路径。

### Risk 3: `internal` 暴露过宽,后续重构被外部模块依赖
- **风险**:未来从 `:app` 抽 `:core-ui` 时,`internal` 变跨 module 不可见,迫使升级 `public`。
- **缓解**:本 change 在 `NodeLayout` / `computeNodeLayoutsFor` 等处注释里写明"仅 `:app` 测试使用,如未来抽 module 升级 `public`,需 review 一致性";spec deltas 不写"依赖 internal"的契约。

### Risk 4: 8 个测试不能覆盖真机所有边界(如真实 labelWidth 与 title 中含中文 / 全角字符的处理)
- **风险**:`labelWidth = it.length * 8f` 是简化模型,中文 / 全角字符宽度差异不影响 box collision 测试,因为我们用固定 charWidth。
- **缓解**:Test framework 不测字符串宽度,只测 box 几何冲突;"title 长度 > 12 截断"另走 `LabelTruncationTest`(不在本 change scope)。

### Risk 5: 后人误引入 Robolectric
- **风险**:本 change 走纯 JVM,但若后人补 `LabelTruncationTest` 时引入 `@RunWith(RobolectricTestRunner::class)`。
- **缓解**:spec delta 显式禁止;`NoteGraphLayoutTest` 不带 `@RunWith`,review 时一律驳回。

## Migration Plan

无 migration。对外行为 100% 等价:
- 渲染层 `DrawScope.computeNodeLayouts` 接口签名 / 返回值 / 副作用零变化
- `NoteGraphScreen.kt` 等上层 caller 不动
- 用户可见行为零变化(纯算法路径对外)

回滚路径: revert 单 commit `feat(add-note-graph-layout-test)` 即可恢复到原 `private` 渲染函数。

## Open Questions

无。spec delta 已明确"不加 REQUIREMENT 变更,只加测试要求";refactor scope 已锁定(只动 `computeNodeLayouts` 入口与 `textPaint.measureText` 抽出);测试 8 case 覆盖 spec 6 scenarios + 2 个算法补充 case 全部经 review r1 验证。
