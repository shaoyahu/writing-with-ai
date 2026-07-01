# Code Review: 全项目 review(本轮 session 所有改动)

**Reviewed**: 2026-06-20
**Branch**: main(本地未提交)
**Decision**: APPROVE with comments

## Summary

32 files modified + ~20 new files(ui-redesign-m5-glass / provider-real-integration / widget-1x4-compact 三个 change 合并落地)。安全(apikey 加密 / 数据访问)与架构(Glance 约束 / feature self-containment)均合规，无 CRITICAL / HIGH。**2 MEDIUM**(重复 color token + 无用 import)建议顺手修。

## Findings

### CRITICAL

None.

### HIGH

None.

### MEDIUM

#### M1 — Widget color tokens 重复定义

- 位置:`QuickNoteWidget.kt:80-86` + `QuickNote1x4Widget.kt:61-65`
- 问题:`cPrimary / cOnPrimary / cBg / cText / cTextMuted` 两个文件各定义一份，值相同。DRY 违反;若改主色需改两处。
- 修复:抽到同包 `WidgetTheme.kt`(或直接在这些文件中声明 `internal val`),1x4 widget 引用。

#### M2 — 无用 import:`size` / `width`(`QuickNoteWidget.kt:30-31`)

- 问题:`import androidx.glance.layout.size` / `import androidx.glance.layout.width` 本轮未用(改成了 `padding(horizontal, vertical)` 模式)
- 修复:删掉两个 import。

### LOW

#### L1 — `QuickNote1x4Widget.kt` KDoc 仍写 `SizeMode.Exact`

- 位置:`QuickNote1x4Widget.kt:40` 注释 `[sizeMode] = Exact` → 实际代码是 `SizeMode.Single`
- 修复:更新注释。

#### L2 — 同包 `cSurface` 只在一方定义另一方未用

- `QuickNoteWidget.kt:83` 有 `cSurface`，仅 2x2/4x2 用到;`QuickNote1x4Widget.kt` 无笔记卡片(整行占满)。不影响功能，可删。

#### L3 — Glance widget 仍有 hex 字面量

- Glance 环境无 MaterialTheme,hex 字面量是必需的。但已从散落 `Color(0xFF...)` 改为 `private val c*` token，门槛已满足。不阻塞。

## Validation Results

| Check | Result |
|---|---|
| `./gradlew :app:assembleDebug` | ✅ Pass |
| `./gradlew :app:testDebugUnitTest` | ✅ Pass |
| Glance constraint(`grep compose.foundation\|material3` in widget/) | ✅ 0 matches |
| Self-containment(`grep feature.quicknote\|aiwriting` in widget/) | ✅ 0 matches |
| Hardcoded colors in widget | ✅ All via `private val c*` tokens |

## Files Reviewed

**本轮核心改动**:
- `core/widget/QuickNoteWidget.kt`(M — 视觉重设计)
- `core/widget/QuickNote1x4Widget.kt`(M — 视觉重设计)
- `app/ui/theme/Color.kt`(M — 种子色 #3B82F6 蓝)
- `app/ui/theme/Type.kt`(M — 中文 typography)
- `app/ui/theme/Spacing.kt`(M — 加 xs token)
- `core/ai/provider/ProviderPrefsStore.kt`(A)
- `core/ai/api/AiError.kt`(M — 加 ProviderNotConfigured)
- `feature/settings/model/`—— ModelManagement 4 文件(A)
- `feature/quicknote/list/NoteRow.kt`(M — Surface → Card)
- `feature/settings/Settings{Entry,Screen}.kt`(M — 加 model mgmt 入口)
- `app/AppNav.kt`(M — 加 3 个 route)
- `feature/aiwriting/streaming/AiActionViewModel.kt`(M — ProviderPrefsStore 注入)
- `feature/aiwriting/error/AiErrorDisplay.kt`(M — ProviderNotConfigured 分支)
- Test:`FakeProviderPrefsStore.kt` + `ProviderPrefsStoreTest.kt`(A × 2)

## Required Fixes Before Commit

1. **M1**:合并 color tokens(放同包 shared file 或改 `internal val`)
2. **M2**:删无用 `size` / `width` import

L1 / L2 / L3 顺延。