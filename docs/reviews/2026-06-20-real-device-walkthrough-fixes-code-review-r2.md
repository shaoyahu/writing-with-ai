# Code Review r2: 真机 walkthrough 三处 fix follow-up

**Reviewed**: 2026-06-20
**Branch**: main(本地未提交)
**Decision**: APPROVE

## Summary

r1 的 4 项必修全修，H/M/L 残留项决定顺延。代码已编译 + 装机验证通过。

## r1 项关闭情况

| r1 # | 项 | 状态 | 证据 |
|---|---|---|---|
| H1 | 删 `Stroke` import + 修注释 | ✅ Closed | `ActionSheet.kt` 无 `Stroke` import;注释 "stroke with same color" 已删 |
| H2 | 删 `selection.length == 0` 死分支 | ✅ Closed | `QuickNoteDetailScreen.kt:249` 只剩 `if (selection.collapsed)` |
| M1 | 加 `aiwriting_action_menu` 中/英文 + contentDescription | ✅ Closed | `values/strings.xml` + `values-en/strings.xml` 新增;detail screen 引用 |
| M3 | 删 `Size` import | ✅ Closed | `ActionSheet.kt` 无 `Size` import |

## 顺手修

- **M2**(`modifier` 形参残留):顺手删。
- **英文 TODO 占位**:M1 改动时把 4 个老 TODO 占位 (`aiwriting_action_expand/polish/organize/copy`) 顺手补成正经英文(Expand/Polish/Organize/Copy)。原本属 M5 polish 待办。

## r1 未关项 / 顺延

| r1 # | 项 | 决定 |
|---|---|---|
| L1 | 三角箭头尺寸提常量 | 顺延，后续 a11y pass 一起做 |
| L2 | `AutoAwesome` icon 与 ActionSheet 多 op 语义不匹配 | 顺延，本轮已用 `aiwriting_action_menu` contentDescription 兜住语义 |

## 验证

| Check | Result |
|---|---|
| `./gradlew :app:assembleDebug` | Pass |
| `./gradlew :app:installDebug` | Pass(PGU110) |
| 真机回归 | 用户确认正常 |

## Files Reviewed

- `app/src/main/java/com/yy/writingwithai/feature/quicknote/detail/QuickNoteDetailScreen.kt`(M)
- `app/src/main/java/com/yy/writingwithai/feature/aiwriting/action/ActionSheet.kt`(M)
- `app/src/main/java/com/yy/writingwithai/feature/onboarding/OnboardingScreen.kt`(无新改)
- `app/src/main/res/values/strings.xml`(M)
- `app/src/main/res/values-en/strings.xml`(M)