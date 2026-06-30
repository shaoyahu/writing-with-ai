# Code Review: app-tab-bar-redesign r1

**Reviewed**: 2026-06-30
**Reviewer**: ecc:code-review (local mode)
**Scope**: uncommitted changes — AppShell.kt + design.md + spec.md + tasks.md
**Decision**: APPROVE(2026-06-30 用户采纳 Fix A 后)

## Round Status
- **r1**: REQUEST CHANGES → 用户采纳 Fix A → r1 决议升 APPROVE

## Summary

内嵌子卡设计实现正确,API 兼容性保留,代码 / spec / tasks 一致,验证全通过。中央 `CenterCreateCard` 高度(56dp)与 `TabCard`(≈68dp)不等,Row 内视觉基线错位(中心 icon 比两侧 icon 下沉约 10dp),需修复。

## Findings

### CRITICAL
None.

### HIGH
None.

### MEDIUM

**1. AppShell.kt:287-296 — 中心卡片高度与侧 tab 不等,icon 基线错位**

`TabCard` Surface 高 ≈68dp(padding 12 + icon 24 + spacer 4 + text 16 + padding 12)。`CenterCreateCard` Surface 高 =56dp(padding 16 + icon 24 + padding 16)。Row `verticalAlignment = CenterVertically` 只对齐子项垂直中心,不强制等高 → 中心 Surface 在 Row 内上下各留 6dp 空白,中心 icon 比侧 tab icon 低约 10dp。

视觉后果:三槽表面上 inline,但中央 primary 卡视觉下凹,跟「跟其他 UI 风格一致」诉求不符。

Fix(任选一):
- 在 `CenterCreateCard` Icon 后加 `Spacer(Modifier.size(20.dp))`,padding 改 `vertical = 12.dp`,凑齐 68dp(选项 A,推荐,与 TabCard 同尺寸)
- 接受现状,在 design.md R1 加一行说明「中心卡片高度刻意小于侧 tab,作为 primary action 视觉简化」

### LOW

**1. AppShell.kt:210 — 槽 2 注释冗余**

```
// 槽 2 — 中央创建入口(内嵌 primary 子卡,无 label,无 elevation,无凸起)
```

信息已在 `AppTabBar` KDoc + `CenterCreateCard` KDoc 重复。可删除。

**2. AppShell.kt:172 — docstring 提及不存在的测试文件**

KDoc 写 `app/src/test/.../app/AppTabBarTest`,但仓库无此文件。**预存问题**,本次变更未引入,但应在后续补上或删除该引用。

## Validation Results

| Check | Result |
| --- | --- |
| `./gradlew :app:assembleDebug` | PASS (BUILD SUCCESSFUL in 31s) |
| `./gradlew :app:ktlintCheck` | PASS (0 violations, ktlintFormat auto-fix 后) |
| `./gradlew :app:testDebugUnitTest` | PASS (419 总 / 413 passed / 6 skipped / 0 failed) |
| `grep NavigationBar\|NavigationBarItem` AppShell.kt | 0 匹配 ✅ |
| `grep FloatingActionButton\|Box\|offset` AppShell.kt | 0 匹配 ✅ |
| `grep CenterCreateCard` AppShell.kt | 3 匹配(1 docstring + 1 call + 1 def)✅ |
| spec.md delta vs source | 100% 对齐 ✅ |

## Files Reviewed

- `app/src/main/java/com/yy/writingwithai/app/AppShell.kt`(Modified — 净 -44 行)
- `openspec/changes/app-tab-bar-redesign/design.md`(Modified — D2 新增,Goals/Non-Goals/Context/Risks 更新)
- `openspec/changes/app-tab-bar-redesign/specs/app-tab-bar/spec.md`(Modified — 槽 2 + 新 Scenarios)
- `openspec/changes/app-tab-bar-redesign/tasks.md`(Modified — 1.4 / 2.2 / 4.5 / 4.6 新增/修订)

## Recommended Next Steps

1. 修 MEDIUM #1:CenterCreateCard 加 `Spacer(20dp)` + `padding(vertical = 12.dp)` 凑齐 68dp,跟 TabCard 等高
2. (可选)清 LOW #1 / #2 注释与过期 docstring 引用
3. 装机肉眼比对中央卡片基线对齐

## Follow-up (2026-06-30 后续采纳)

**MEDIUM #1 修复**:采纳 Fix A — CenterCreateCard Column padding 从 `vertical = 16.dp` 改为 `vertical = 12.dp`,Icon 后追加 `Spacer(Modifier.size(20.dp))`。新表面高度 = 12 + 24 + 20 + 12 = 68dp,跟 TabCard 等高,Row 内三 Surface 视觉基线对齐。spec.md Scenario「中央槽位触控目标 ≥ 56dp」+ 表格 2 号槽位 padding 描述同步更新。`CenterCreateCard` KDoc 重写,说明占位 Spacer 用意。

验证全通过:
- `./gradlew :app:assembleDebug` BUILD SUCCESSFUL
- `./gradlew :app:ktlintCheck` 0 violations
- `./gradlew :app:testDebugUnitTest` BUILD SUCCESSFUL(419 总 / 0 failed)

**剩余 LOW**:槽 2 行内注释 + docstring 提及不存在测试文件 — 后续清理。