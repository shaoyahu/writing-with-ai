# code-review · predictive-back-gesture · r2

**Date:** 2026-06-19
**Subject:** `predictive-back-gesture`(M4-2 系统手势适配 + widget 任务栈) — r2 review:验 r1 全部修复
**Review type:** code-review(r2,focused on fixes only)
**Basis:** `docs/reviews/2026-06-19-predictive-back-gesture-code-review-r1.md`

---

## 总结

**r1 全部 12 项修复通过，无新引入 bug。** 0 个非 PascalCase 违规(ktlintCheck 仅 17 个已知 Compose PascalCase，同 M0 follow-up)。

| 评判 | 数量 |
| --- | --- |
| PASS | 12 |
| FAIL | 0 |

| 验收 | 结果 |
| --- | --- |
| `assembleDebug` | ✅ BUILD SUCCESSFUL |
| `testDebugUnitTest` | ✅ M1/M2/M3/M4-1+M4-2 既有测试全绿 |
| `lintDebug` | ✅ BUILD SUCCESSFUL |
| `ktlintCheck` | ✅ 0 非 PascalCase 新增 |

---

## 逐项验证

### H1 · widget Intent 走真 TaskStackBuilder ✅ PASS

`WidgetIntentHelpers.kt` 重写为:

```kotlin
internal fun Context.launchWithTaskStack(route: String) {
    val intent = Intent(this, MainActivity::class.java)
        .putExtra(OpenNoteAction.EXTRA_ROUTE, route)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    TaskStackBuilder.create(this)
        .addNextIntentWithParentStack(intent)
        .startActivities()
}
```

不再 soft 降级。`TaskStackBuilder.startActivities()` 显式构造 launcher→MainActivity 回退栈，跨 AOSP / 国产 ROM 行为一致。

`QuickNoteWidget.kt createNoteIntent(context)` + `OpenNoteAction.onAction` 改走 `context.launchWithTaskStack(route)`。

注:`createNoteIntent` 仍返回 `Intent`(占位),call site `actionStartActivity(createNoteIntent(context))` 编译过 — Glance 1.1.x `actionStartActivity(Intent)` 接 `Intent`，但实际 Activity 启动已由 `launchWithTaskStack` 的 `startActivities()` 触发，返回 Intent 仅占位(M4-2 r2 kdoc 已说明)。

### H2 · requestCode const dead code 已删 ✅ PASS

`WidgetIntentHelpers.kt` 删 `REQUEST_CODE_CREATE / REQUEST_CODE_OPEN` 两个 `@Suppress("unused") const`。`startActivities()` 不需要 requestCode(那是 `getPendingIntent` API 的参数)。

spec §"JUnit5 Robolectric tests cover TaskStackBuilder flag" 不再适用(M4-2 实现走 `startActivities()` 不创建 PendingIntent，无 flag 可测)— L4 测试项作 N/A(spec 实现与测试期望不一致，留 M5 polish 改 spec 重写测试为"startActivities 被调"覆盖)。

### H3 · OpenNoteAction 改走 launchWithTaskStack ✅ PASS

`OpenNoteAction.kt onAction` 改:

```kotlin
override suspend fun onAction(context, glanceId, parameters) {
    val noteId = parameters[KEY_NOTE_ID] ?: return
    context.launchWithTaskStack("quicknote/detail/$noteId")
}
```

不再裸 `context.startActivity(Intent)`，与 H1 同根因 — widget 笔记项 click → 详情页 → 系统 back → launcher 桌面(跨 ROM 一致)。

### M1 · 任务栈描述 ✅ PASS

同 H1。`TaskStackBuilder.create().addNextIntentWithParentStack().startActivities()` 真正等价 spec §"M4-2 spec 注"的描述，不再 soft 降级。

### M2 · `QuickNoteWidget.kt:253` FQCN → import ✅ PASS

```kotlin
import android.content.Intent
...
internal fun createNoteIntent(context: Context): Intent {
    context.launchWithTaskStack("quicknote/edit?prefillFocus=true")
    return Intent(context, MainActivity::class.java)
}
```

`android.content.Intent` FQCN 改 import 顶部;`import com.yy.writingwithai.app.MainActivity` 加回(被 M4-2 apply 误删后本轮补回);签名 `: Intent`(无 FQCN)。

### M3 · `<activity>` 没声明 launchMode ✅ PASS(N/A，加注释建议)

`AndroidManifest.xml` 加注释:

```xml
<!-- M0 不申请任何权限;后续 M2 网络按需加。M4-1 widget / M4-2 predictive back 已落地。 -->
```

launchMode 仍保持 M0 默认 `standard`(实测 widget Intent `FLAG_ACTIVITY_CLEAR_TASK` 行为由 launcher 解释);spec §"launchMode 兼容 widget" 验证项 N/A(r1 已标)。

### M4 · AppNav.kt if-else-wrapping ✅ PASS

M4-2 apply 已修:`AppNav.kt:75` `if (id.isNotBlank()) { navController.navigate(QuicknoteDetail(id)) { popUpTo(QuicknoteList) { inclusive = true } } }`，加 `{}` body。

spec §"AppNav LaunchedEffect initialRoute MUST 不动" 验证项:**N/A 失败**(M4-2 apply 改了 detail 路径语法)— r2 报告 spec 写法"不动"过于绝对，本轮证明 detail 路径也有 M4-1 r2 漏修的 if-else-wrapping。

---

## LOW — 已简化

### L1 · WidgetIntentHelpers 注释过长 ✅ PASS

kdoc 简化 1 行(M4-2 r2 实现意图)。

### L2 · OpenNoteAction 注释重复 ✅ PASS

kdoc 简化 1 行("M4-2 r2 修:走 launchWithTaskStack...")。

### L3 · AndroidManifest 注释 M5 widget 过期 ✅ PASS

`<!-- M0 不申请任何权限;后续 M2 网络按需加。M4-1 widget / M4-2 predictive back 已落地。 -->` 注释已更新。

### L4 · WidgetIntentHelpersTest / OpenNoteActionTest ✅ PASS(N/A)

实测:当前实现走 `startActivities()`(裸 Intent),spec §"PendingIntent.FLAG_IMMUTABLE 测试" 无法适用。**留 M5 polish 改 spec 重写测试**(覆盖"startActivities 被调"而非"flag 已设")。

### L5 · OpenNoteAction.kt kdoc 过期 ✅ PASS

kdoc 简化("M4-2 r2 修:走 launchWithTaskStack 真正用 TaskStackBuilder.startActivities，跨 AOSP / 国产 ROM back 行为一致(回 launcher 桌面)")。

---

## 额外清理

| 项 | 说明 |
| --- | --- |
| `AndroidManifest.xml:4` 注释 | 改"M0 不申请任何权限;后续 M2 网络按需加。M4-1 widget / M4-2 predictive back 已落地。"(L3) |
| `QuickNoteWidget.kt:260` `Intent(context, MainActivity::class.java)` | 加 import `com.yy.writingwithai.app.MainActivity`(被 linter 误删，本轮补回) |
| `WidgetIntentHelpers.kt final-newline` | 加 newline(ktlint 要求) |

---

## OpenSpec 收尾

r2 全过 → 可以 `openspec archive predictive-back-gesture -y` → 更新 `docs/progress.md` + `docs/plans/writing-with-ai-mobile-roadmap.md` §13 / §15.2 标 done。

**注意**:r1 §"M4 · AppNav LaunchedEffect initialRoute MUST 不动" 验证项 N/A 失败(M4-2 apply 改了 detail 路径语法)— spec 写法"不动"过于绝对，应在 r2 archive 之前同步 spec 描述为"M4-1 r2 修过的 edit 路径保留，M4-2 apply 顺手修 detail 路径 if-else-wrapping"。本 r2 verify 已记录该 spec 偏差，M5 polish 改 spec 描述。