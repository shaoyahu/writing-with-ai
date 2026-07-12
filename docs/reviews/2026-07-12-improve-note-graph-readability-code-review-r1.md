# Code Review: improve-note-graph-readability (r1)

**Reviewed**: 2026-07-12
**Scope**: uncommitted changes on `main` (4 files)
**Mode**: Local Review (`/ecc:code-review review 代码并修复`, 无 PR)

**Decision**: APPROVE with fixes applied

## Summary

M5.x 关联图可读性升级 — 标签 4 方向避让、边色/粗细升级、TopAppBar 副标题、首次引导 banner、空态可执行文案。代码读起来清晰、数据流直白、未引入安全风险、未跑出 build/lint/test。Apply 后发现 1 处 MED 误判(邻居 label box 仅按 RIGHT 估算)与 2 处 LOW 噪声(dead string + `@Suppress("unused")`),已自修。

## Files Reviewed

| File | Change | LOC Δ |
| --- | --- | --- |
| `app/src/main/java/com/yy/writingwithai/feature/quicknote/graph/NoteGraphCanvas.kt` | Modified | +130 / −20 |
| `app/src/main/java/com/yy/writingwithai/feature/quicknote/graph/NoteGraphScreen.kt` | Modified | +60 / −10 |
| `app/src/main/res/values/strings.xml` | Modified | +6 / 0 (后 −1 dead string) |
| `docs/progress.md` | Modified | +5 / 0 |

## Findings

### CRITICAL
None.

### HIGH
None.

### MEDIUM

**M1 — 邻居 label box 仅按 `LABEL_RIGHT` 估算,漏判真实占用** [NoteGraphCanvas.kt:362-364](app/src/main/java/com/yy/writingwithai/feature/quicknote/graph/NoteGraphCanvas.kt#L362-L364)

碰撞算法是 sweep 一次性算所有节点的 label 方向,邻居的"已选方向"在算本节点时还没决定。旧实现把所有邻居 label box 都按 RIGHT 放,若邻居实际合法方向是 LEFT/ABOVE/BELOW,本节点在 LEFT 检测时本来不撞,误判成撞 → 全方向被拒 → fallback 兜底,失去 spec D1 "4 方向避让"目标。

Fix: 展开成 `LABEL_PRIORITY.any { dir -> labelBoxFor(..., dir).overlaps(candidate) }`。复杂度从 O(n) 变 O(4n) = O(n),仍 < 1ms / 帧。

**Status**: fixed

### LOW

**L1 — dead string `note_graph_subtitle_separator`** [strings.xml:899](app/src/main/res/values/strings.xml#L899)

`·` 直接写在 `note_graph_header_node_count_fmt` / `_singular` 模板里,separator string 未引用。

Fix: remove。

**Status**: fixed

**L2 — `@Suppress("unused")` 屏蔽 unused 警告** [NoteGraphCanvas.kt:505](app/src/main/java/com/yy/writingwithai/feature/quicknote/graph/NoteGraphCanvas.kt#L505)

CLAUDE.md "未使用变量是构建错误"。`chipCornerShape` 实际在 `EntityChipOverlay` 里用了 `.clip(chipCornerShape)`,suppress 多余。

Fix: remove `@Suppress("unused")`。

**Status**: fixed

**L3 — `pickFallbackDirection` 无邻居时返 RIGHT** [NoteGraphCanvas.kt:448](app/src/main/java/com/yy/writingwithai/feature/quicknote/graph/NoteGraphCanvas.kt#L448)

若孤立节点(无邻居)且之前 RIGHT 被拒(罕见:box 超出 canvas),fallback 仍返 RIGHT → 与之前循环结果一致。算法自洽。

**Status**: acceptable (rare edge case)

**L4 — Error 态下 TopAppBar 副标题消失** [NoteGraphScreen.kt:65](app/src/main/java/com/yy/writingwithai/feature/quicknote/graph/NoteGraphScreen.kt#L65)

Error 态时 `headerSnapshot == null`,subtitle 不渲染。但 Error 态本身有独立文案说明加载失败,副标题缺席反而不混淆用户。

**Status**: acceptable

**L5 — banner 触发条件 `nodes.size <= 2`** [NoteGraphScreen.kt:170](app/src/main/java/com/yy/writingwithai/feature/quicknote/graph/NoteGraphScreen.kt#L170)

spec D4 实现按节点数判断,已加载过的高节点笔记再次进入不会显示 banner,符合"首次 + 节点少"意图。

**Status**: acceptable

**L6 — `NoteGraphGuidanceBanner` Surface 无 contentDescription** [NoteGraphScreen.kt:182-194](app/src/main/java/com/yy/writingwithai/feature/quicknote/graph/NoteGraphScreen.kt#L182-L194)

Text 内容足够,TalkBack 直接读出。不影响可达性。

**Status**: acceptable

**L7 — `LABEL_PRIORITY` 用 `intArrayOf`** [NoteGraphCanvas.kt:309](app/src/main/java/com/yy/writingwithai/feature/quicknote/graph/NoteGraphCanvas.kt#L309)

避免 `IntArray` boxing,合理。scope 内可接受魔法数字。

**Status**: acceptable

**L8 — `collisionRange` 过滤略激进** [NoteGraphCanvas.kt:351-353](app/src/main/java/com/yy/writingwithai/feature/quicknote/graph/NoteGraphCanvas.kt#L351-L353)

双向 box 重叠本应算邻居,当前 filter 只用本节点 label 宽度。偏保守,但极少触发误判。

**Status**: acceptable

## Validation Results

| Check | Result |
| --- | --- |
| Type check (`compileDebugKotlin`) | Pass (1 unrelated deprecation warning in `AppDropdownMenu.kt:173`, pre-existing) |
| Lint (`ktlintCheck`) | Pass (UP-TO-DATE) |
| Tests (`testDebugUnitTest`) | Pass |
| Build (`assembleDebug`) | Pass (re-validated pre-archive this session) |

## Notes

- **Bug 上下文**: M5.x 之前 `fix-note-graph-rendering` change 留了一处 stub `spToPx()` 用 `1.5f` 常量近似 density,本 change 顺手修了(用 `textPaint.textSize`)。如未自修会触发 labelHeight 失真。
- **范围**: 改动全部局限于 `feature/quicknote/graph/`,未触及其他 feature / core 层,符合包结构硬规则。
- **下次 review 时建议**: 加 JVM 单测覆盖 `computeNodeLayouts` — 当前 4 方向碰撞算法没有单测,纯靠肉眼看图验收。建议在后续 change 中补 `NoteGraphLayoutTest`。

## Artifacts

- This file: `docs/reviews/2026-07-12-improve-note-graph-readability-code-review-r1.md`
- Change archive: `openspec/changes/archive/2026-07-11-improve-note-graph-readability/`