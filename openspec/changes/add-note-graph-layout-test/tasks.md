## 1. Refactor `NoteGraphCanvas.computeNodeLayouts` 为可测纯函数

- [x] 1.1 升级可见性:`NodeLayout` / `NodePre` / `LABEL_NONE/RIGHT/LEFT/ABOVE/BELOW` / `LABEL_PRIORITY` / `labelBoxFor` / `nodeBBox` / `pickFallbackDirection` / `angularDiff` 从 `private` 改为 `internal`,在每个声明上方 1 行注释说明"only `:app` tests;future module split may require `public`"
- [x] 1.2 新增 `internal fun computeNodeLayoutsFor(snapshot: GraphSnapshot, coords: Map<String, NodeCoords>, canvasSize: Size, density: Float, labelWidthFor: (String) -> Float): Map<String, NodeLayout>`:搬迁 `computeNodeLayouts` 的几何逻辑,`labelHeightPx = density * 14f`(把 `spToPx` 抽出),`textPaint.measureText` 替换为 `labelWidthFor(labelText)`;签名变化:原 `canvasCenter: Offset` → `canvasSize: Size`(原算法不用中心,只让 label box 越界时返 null),`textPaint: Paint` → `(String) -> Float`
- [x] 1.3 改 `private fun DrawScope.computeNodeLayouts(...)` 为薄包装(≤ 10 行):取 `size` / `density.toPx()` / `textPaint::measureText` → 调 `computeNodeLayoutsFor`;函数体删空原 56 行几何实现,改成 4 行 forward;verify signature `Map<String, NodeLayout>` 返回值不变

## 2. 新建 JVM 单测 `NoteGraphLayoutTest`

- [x] 2.1 创建 `app/src/test/java/com/yy/writingwithai/feature/quicknote/graph/NoteGraphLayoutTest.kt`:
  - 顶部 KDoc 注明 "8 cases cover spec delta 6 scenarios + 2 算法加固 case,纯 JVM 单测,不需 Robolectric"
  - 私有 helper:
    - `fun snapshotOf(vararg nodes: GraphNode): GraphSnapshot`
    - `fun coordsOf(vararg pairs: Pair<String, Offset>): Map<String, NodeCoords>`
    - `const val CHAR_W = 8f; val LABEL_WIDTH_FOR: (String) -> Float = { it.length * CHAR_W }`
- [x] 2.2 写测试 1:"Empty snapshot" — `snapshotOf()`(无 nodes)→ `computeNodeLayoutsFor(...)` 返回空 map,不抛异常
- [x] 2.3 写测试 2:"Single node, no neighbors" — `snapshotOf(GraphNode("a", title = "Hello", score = 0f, hopLevel = 0))` + `coordsOf("a" to Offset.Zero)` + `Size(500f, 500f)`,断言 `layouts["a"]?.labelBox` 与手算 `labelBoxFor(Offset.Zero, radius=24f, labelWidth="Hello".length*8=40f, 14f, LABEL_RIGHT)` 一致(误差 < 0.5f)
- [x] 2.4 写测试 3:"Horizontal pair forces LEFT" — 节点 "a" 在 (100, 250),节点 "b" 在 (260, 250),labelWidth=40,半径≈24,`collisionRange=2*(24+40)=128`,b 在范围内 → "a" 的 label 必须选 LEFT;断言 `layouts["a"]!!.labelBox!!.right < 100f - 24f` 或 `Rect(54, ...)` 等显式位置
- [x] 2.5 写测试 4:"All four quadrants occupied" — 节点 "center" 在 (200, 200),4 邻居分别在 (+50,0)/(-50,0)/(0,+50)/(0,-50) 上下左右对称放置且都极近以触发 4 象限 label box 全重叠 → "center" 必须进 fallback,断言 `labelBox` 位置与 `pickFallbackDirection(center, ...)` 返回的方向一致
- [x] 2.6 写测试 5:"Multi-direction neighbor label check" — 节点 "self" 在 (0,0),节点 "other" 在 (5,0)(紧贴),self 候选 RIGHT label box(x>radius 段)与 other 的 `LABEL_LEFT` box(x<0 段)不相接,但若算法错把 other 当 RIGHT,other 的 RIGHT box(x>0)与 self 的 RIGHT box 重叠,误判 → 算法必须自我验证正确,本节点最终选 RIGHT 或其他合法方向
- [x] 2.7 写测试 6:"Untitled node has no label box" — `snapshotOf(GraphNode("a", title = "", score = 0f, hopLevel = 0))`(空白 title)→ `layouts["a"]?.labelBox` 必须为 null
- [x] 2.8 写测试 7:"Pick fallback when LABEL_RIGHT collides box boundary" — 节点 "a" 在 (5, 5)(极靠 canvas 边角),labelWidth=40,RIGHT box 会越界(`labelBoxFor` 在 box.left+labelWidth > canvasSize.width 时返 null),`labelBoxFor(..., LABEL_RIGHT) == null`,候选测跳过 RIGHT,继续 BELOW 等;最终若全部 null,fallback 兜底;断言最终 `labelBox != null` 且覆盖在 canvas 内
- [x] 2.9 写测试 8:"Rect.overlaps excludes edge-touching" — 两个相邻 box 公用一条边但不重叠(左 box right=10,右 box left=10),断言 `!left.overlaps(right)`,防止 review L8 collisionRange 误算回归

## 3. 跑全量验证

- [x] 3.1 `export JAVA_HOME=/opt/homebrew/opt/openjdk@17 && ./gradlew :app:testDebugUnitTest --tests "*NoteGraphLayoutTest"` — 8 个 case 全 pass;< 1 秒;无 Robolectric 警告;无设备依赖
- [x] 3.2 `export JAVA_HOME=/opt/homebrew/opt/openjdk@17 && ./gradlew :app:ktlintCheck` — 不破 lint;如有 `wildcard import` / `unused` 警告,fix
- [x] 3.3 `export JAVA_HOME=/opt/homebrew/opt/openjdk@17 && ./gradlew :app:compileDebugKotlin` — 重构后 `DrawScope.computeNodeLayouts` 仍是合法 Compose 调用链,无未解析引用
- [x] 3.4 `export JAVA_HOME=/opt/homebrew/opt/openjdk@17 && ./gradlew :app:assembleDebug` — 出包成功,APK 体积无可见变化(< 5 KB,可忽略)

## 4. 真机目视回归

- [ ] 4.1 用户在真机重跑关联图可读性 §5.1-5.4 4 个验收 case:节点标签 4 方向避让 + 4 邻居 fallback + 实 / 虚线边色 + 副标题节点数 + 首次引导 banner — 行为与 improve-note-graph-readability change 上线时一致(确认重构无视觉回归)

## 5. 收尾

- [x] 5.1 自查:`git diff main` 只动 2 个文件(`app/src/main/.../NoteGraphCanvas.kt` + 新增 `app/src/test/.../NoteGraphLayoutTest.kt`),无 `CLAUDE.md` 误改 / `app/build.gradle.kts` 误改(无新依赖)
- [x] 5.2 跑 `openspec validate add-note-graph-layout-test --strict`,确认 proposal / design / tasks / specs 全部通过 schema 检查
- [ ] 5.3 完成后停下等用户指令触发 commit + archive(CLAUDE.md "提交控制" 规则)
