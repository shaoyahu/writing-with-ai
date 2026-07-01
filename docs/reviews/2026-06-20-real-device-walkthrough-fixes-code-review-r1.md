# Code Review: 真机 walkthrough 三处 fix(M5 polish follow-up)

**Reviewed**: 2026-06-20
**Branch**: main(本地未提交)
**Decision**: REQUEST CHANGES

## Summary

底部栏 + ActionSheet Popup+箭头方向正确，真机交互可用。**2 个 HIGH**(死代码 / 永远 false)+ **3 个 MEDIUM**(a11y 错配 / API 残留 / 无用 import)，无 CRITICAL。建议修完 HIGH 后合并。

## Findings

### CRITICAL

None.

### HIGH

#### H1 — `Stroke` import 未用 + 注释撒谎(`ActionSheet.kt:26,109`)

- `import androidx.compose.ui.graphics.drawscope.Stroke` 实际未用;注释写 "stroke with same color for anti-alias" 但代码只 `drawPath(path, color)`，误导后续维护者。
- 修复:删 import + 删注释中 "stroke with same color" 一句。

#### H2 — `selection.length == 0` 永 false(`QuickNoteDetailScreen.kt:250`)

- `TextRange.collapsed` 已是 `length == 0` 的别名，`|| selection.length == 0` 不可达。
- 修复:删 `|| selection.length == 0`，只留 `selection.collapsed`。

### MEDIUM

#### M1 — ✨ 按钮 contentDescription 错配(`QuickNoteDetailScreen.kt:247`)

- `contentDescription = stringResource(R.string.aiwriting_action_expand)` = "扩写"，但按钮是 ActionSheet 总入口(实际有 4 个 op)。TalkBack 用户听到"扩写"以为直接扩写。
- 修复:加新 R.string `aiwriting_action_menu`("AI 操作" / "AI actions")，改用它。

#### M2 — `modifier` 形参从未传值(`ActionSheet.kt:51`)

- `ActionSheet(modifier: Modifier = Modifier)` 形参残留，Popup 是 Window-level，语义弱。
- 修复:删形参。

#### M3 — 死代码:`Size` import(`ActionSheet.kt:24`)

- 修复:删 import。

### LOW

#### L1 — 三角箭头尺寸可提常量

- 仅风格:`private object Dimens { val ArrowWidth = 16.dp; val ArrowHeight = 8.dp }`。不阻塞。

#### L2 — `Icons.Filled.AutoAwesome` 视觉与 ActionSheet 多 op 语义不匹配

- 若 a11y 是 priority，考虑换 `Icons.Filled.AutoAwesomeMosaic` / `Icons.Filled.MoreHoriz`。不阻塞。

## Validation Results

| Check | Result | Note |
|---|---|---|
| `./gradlew :app:assembleDebug` | Pass | 已装机验证 |
| ktlintCheck | Skipped | baseline 已知 |
| 真机 walkthrough | Pass | FAB→bottomBar / ActionSheet anchor+箭头 / Onboarding padding 三处均修复 |
| 单测 | Skipped | 本轮无新业务逻辑 |

## Files Reviewed

- `app/src/main/java/com/yy/writingwithai/feature/quicknote/detail/QuickNoteDetailScreen.kt`(M)
- `app/src/main/java/com/yy/writingwithai/feature/aiwriting/action/ActionSheet.kt`(重写)
- `app/src/main/java/com/yy/writingwithai/feature/onboarding/OnboardingScreen.kt`(M)

## Required Fixes Before Merge

1. H1:删 `Stroke` import + 修注释
2. H2:删死分支
3. M1:加 `R.string.aiwriting_action_menu`，改 contentDescription
4. M3:删 `Size` import

M2 / L1 / L2 视后续节奏决定。