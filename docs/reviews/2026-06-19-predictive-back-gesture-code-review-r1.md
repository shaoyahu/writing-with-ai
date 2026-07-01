# code-review · predictive-back-gesture · r1

**Date:** 2026-06-19
**Subject:** `predictive-back-gesture`(M4-2 系统手势适配 + widget 任务栈) — r1 review:全文件 AI 自审
**Review type:** code-review(r1,initial)
**Basis:** `openspec/changes/predictive-back-gesture/`(4 artifacts)+ 4 个产物文件

---

## 总结

**M4-2 主体配置(AndroidManifest `enableOnBackInvokedCallback`)落地正确。但 widget Intent 任务栈语义**降级**:`createWidgetLaunchIntent` 用裸 `Intent` + `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK`，未真正走 `TaskStackBuilder.addNextIntentWithParentStack`,spec §"TaskStackBuilder 等价行为"是降级描述，实际 back 行为可能在某些 launcher 上回到 App 列表(非 launcher)。**有 3 个 HIGH 阻断 Play Store 上架与 widget 体验，2 个 MEDIUM 待补**。

| 严重度 | 数量 |
| --- | --- |
| HIGH | 3 |
| MEDIUM | 4 |
| LOW | 5 |

| 验收 | 结果 |
| --- | --- |
| `assembleDebug` | ✅ BUILD SUCCESSFUL |
| `testDebugUnitTest` | ✅ M1/M2/M3/M4-1 既有测试全绿(M4-2 spec 测试项 L4 未落地，见下面) |
| `lintDebug` | ✅ BUILD SUCCESSFUL |
| `ktlintCheck` | ✅ 0 非 PascalCase 新增(17 个 `function-naming` = 已知 Compose PascalCase M0 follow-up) |

---

## HIGH — 必须修

### H1 · widget Intent 任务栈未真正走 `TaskStackBuilder`,back 行为降级 ⚠️ 阻断 widget 体验

**文件:** `app/src/main/java/com/yy/writingwithai/core/widget/WidgetIntentHelpers.kt:18-29`

```kotlin
internal fun Context.createWidgetLaunchIntent(route: String): Intent =
    Intent(this, MainActivity::class.java)
        .putExtra(OpenNoteAction.EXTRA_ROUTE, route)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
```

**问题:** spec §"M4-2 spec 注:`TaskStackBuilder` 等价行为通过 `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK` 实现;完整 `TaskStackBuilder` 路径留 M5 polish(若国产 ROM launcher 不识别 CLEAR_TASK,fallback)" — 这是 soft 降级描述。

**实测问题:**
- `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK` 让 widget 启动 Activity 时，**清空 launcher task 并创建新 task**;Activity 在新 task 内启动
- 系统 back 时，新 task 内 Activity 出栈 → 回到 launcher(task 已 CLEAR_TASK)
- **AOSP launcher 行为符合预期**(回 launcher 桌面)
- **国产 ROM launcher(小米 MIUI / 华为 EMUI)**:部分 ROM 把 widget host 与 launcher task 关联，**back 可能回 App 内列表**(因 task affinity 处理差异)
- **`TaskStackBuilder.addNextIntentWithParentStack`**:显式构造 launcher→MainActivity 的回退栈，**AOSP/国产 ROM 行为一致**

**后果:** AOSP 上架 OK(预期 back 回 launcher);国产 ROM 上 widget tap 后 back 行为不可预测(可能回到 App 列表)。**Play Store 上架通过(targetSdk 35 + enableOnBackInvokedCallback 已声明)，但 widget 跨 ROM 兼容性差**。

**修法:** 用真正的 `TaskStackBuilder.startActivities()` 替代裸 `startActivity(intent)`，让回退栈显式构造;Glance `actionStartActivity(Intent)` + `startActivity(intent)` 等价 — 改用 `TaskStackBuilder.startActivities()`:

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

`QuickNoteWidget.kt createNoteIntent(context)` + `OpenNoteAction.onAction` 改 `context.launchWithTaskStack(route)` 替代裸 Intent。

注:`requestCode` 1001/1002 是 `getPendingIntent` API 用的，`startActivities()` 不需要 — **删 dead const**(见 H2)。

### H2 · `REQUEST_CODE_CREATE / REQUEST_CODE_OPEN` dead code ⚠️ 阻断 spec 合规

**文件:** `app/src/main/java/com/yy/writingwithai/core/widget/WidgetIntentHelpers.kt:36-41`

```kotlin
@Suppress("unused")
internal const val REQUEST_CODE_CREATE = 1001

@Suppress("unused")
internal const val REQUEST_CODE_OPEN = 1002
```

**问题:** `requestCode` 是 `TaskStackBuilder.getPendingIntent(requestCode, ...)` API 用的(创建 `PendingIntent` 时区分多个 PendingIntent)。但 M4-2 实现走 `startActivities()`(裸 Intent,**不需要 PendingIntent**)，这两个 const **完全没用**。

**后果:** dead code,@Suppress("unused") 警告掩盖了真问题(spec 写"测试覆盖 `PendingIntent.FLAG_IMMUTABLE`" 但实现根本没创建 PendingIntent)— spec §"JUnit5 Robolectric tests cover TaskStackBuilder flag" 是写测试时检查 flag，但**实现没创建 PendingIntent**，测试会找不到测试对象。

**修法:**
- 选项 A:删 `REQUEST_CODE_CREATE / REQUEST_CODE_OPEN`(最稳，因 M4-2 走 `startActivities()` 不需要)
- 选项 B:实现 `createTaskStackPendingIntent(route, requestCode): PendingIntent`，让 widget UI 走 `actionStartActivity(pendingIntent)`(Glance 1.1.x 不支持 PendingIntent，需 Glance 1.2+)— **不可行**,Glance 1.1.x 没 PendingIntent overload

**建议选项 A**(删 const)，同时把 helper 改返回 `Unit`(调 `startActivities()`)— spec §"AndroidManifest declares enableOnBackInvokedCallback" 是 AndroidManifest 层，不依赖 widget Intent 实现细节。

### H3 · `OpenNoteAction.onAction` 与 `QuickNoteWidget.createNoteIntent` 走裸 Intent，真任务栈差异 ⚠️ 阻断

**文件:** `app/src/main/java/com/yy/writingwithai/core/widget/OpenNoteAction.kt:22-27`

```kotlin
override suspend fun onAction(...) {
    val noteId = parameters[KEY_NOTE_ID] ?: return
    context.startActivity(
        context.createWidgetLaunchIntent("quicknote/detail/$noteId"),
    )
}
```

**问题:** 走裸 `startActivity(Intent)` — 与 H1 同根因，**任务栈语义未真正通过 `TaskStackBuilder`**。`OpenNoteAction` 笔记项点击同样有 back 回 launcher 不可预测风险。

**后果:** widget 笔记项 click → 详情页 → 系统 back → 可能回 App 列表(国产 ROM 风险)。

**修法:** 与 H1 同改，改用 `TaskStackBuilder.startActivities()`。

---

## MEDIUM — 应该修

### M1 · `Intent(this, MainActivity::class.java)` 用全限定类引用，应走 `TaskStackBuilder` 路径

**文件:** `WidgetIntentHelpers.kt:24`

`Intent(this, MainActivity::class.java)` 是裸 Intent,**完全等同 M4-1 之前的实现**(只是 flag 改 `CLEAR_TOP` → `CLEAR_TASK`)。TaskStackBuilder 等价描述是 soft 掉 — 真要等价，应该用 `TaskStackBuilder.create(this).addNextIntentWithParentStack(intent).startActivities()`。

**修法:** H1 修法顺带修。

### M2 · `QuickNoteWidget.kt:253` 全限定 `android.content.Intent` 应 import

**文件:** `QuickNoteWidget.kt:253`

```kotlin
internal fun createNoteIntent(context: Context): android.content.Intent {
    return context.createWidgetLaunchIntent("quicknote/edit?prefillFocus=true")
}
```

`createNoteIntent` return type 用全限定名 `android.content.Intent`,**应 import** `android.content.Intent` 而不是 FQCN(ktlint 规则)。

**修法:** `import android.content.Intent` 顶部，签名改 `: Intent`。

### M3 · `<activity>` 没声明 `launchMode`,widget Intent 行为不可预测

**文件:** `AndroidManifest.xml:19-23`

M4-2 加 `windowSoftInputMode="adjustResize"` 是好，但 widget 启动 Activity 时 — M0 默认 `launchMode` 是 `standard`(每次 tap 创建新实例)。配合 `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK`,**任务栈与 launcher 关联较弱**,back 行为依赖 launcher 实现细节。

**修法:** 加 `android:launchMode="singleTask"` 或 `singleInstance`(避免多个 MainActivity 实例)— 但 singleTask 影响 launcher 图标启动(可能不复用现有实例)。**建议保持 `standard` + 加注释**:`<!-- M4-2:launchMode=standard 默认,widget Intent 走 FLAG_ACTIVITY_CLEAR_TASK 行为由 launcher 解释 -->`

### M4 · `AppNav.kt:75` M4-1 r2 if-else-wrapping 漏修，r2 verify 才发现(本次 M4-2 顺手修)

**文件:** `app/src/main/java/com/yy/writingwithai/app/AppNav.kt:73-79`

```kotlin
initialRoute.startsWith("quicknote/detail/") -> {
    val id = initialRoute.removePrefix("quicknote/detail/")
    if (id.isNotBlank()) navController.navigate(QuicknoteDetail(id)) {
        popUpTo(QuicknoteList) { inclusive = true }
    }
}
```

M4-1 r2 review §"M2 · AppNav 启动闪列表页" 修过 edit 路径加 `popUpTo`，但 detail 路径语法未对齐 — `if (...) navController.navigate(...){}` 无 `{}` body,kotlin 把它当 statement。**功能 OK 但易读性差，ktlint 报 multiline-if-else**。

**修法:** 加 `{}`:
```kotlin
if (id.isNotBlank()) {
    navController.navigate(QuicknoteDetail(id)) {
        popUpTo(QuicknoteList) { inclusive = true }
    }
}
```

(本次 M4-2 apply 已修，但 spec §"MUST 不动" 验证项是 N/A 失败，需要 r2 verify 报告)

---

## LOW — 可选

### L1 · `WidgetIntentHelpers.kt:34` 注释过长(3 行)

可简化 1 行。

### L2 · `OpenNoteAction.kt:22-24` 注释重复 M4-2 摘要

可在 kdoc 一句话带过(参考 H3 修法时一起简化)。

### L3 · `AndroidManifest.xml:4` 注释提到"M5 widget 按需加"，实际 M4-2 已落地 widget

`<!-- M0 不申请任何权限;后续 M2 网络 / M4 通知 / M5 widget 按需加。 -->` — M4-2 已落 widget，这条注释过期。

### L4 · `WidgetIntentHelpersTest.kt` / `OpenNoteActionTest.kt` 未落地 ⚠️ spec 必需

spec §"JUnit5 Robolectric tests cover TaskStackBuilder flag" 明确要求:
- `createTaskStackPendingIntent(route, requestCode=1001).flags and FLAG_IMMUTABLE != 0` 
- `... and FLAG_UPDATE_CURRENT != 0`

**实测:** 当前实现用 `createWidgetLaunchIntent` 返回 `Intent`(非 `PendingIntent`),**测试无法检查 FLAG_IMMUTABLE / FLAG_UPDATE_CURRENT**(这两个 flag 是 PendingIntent 专属，Intent 上不存在)。

**修法:** H1+H2 一起改 — 实现改成走 `TaskStackBuilder.startActivities()` + 删 requestCode const,**改 spec 验收标准**(写测试覆盖"startActivities 被调"而非"flag 已设")。

### L5 · `OpenNoteAction.kt:9` kdoc "走 MainActivity 解析 extra" 描述过期

应改为"M4-2:走 `WidgetIntentHelpers.launchWithTaskStack(route)` 统一任务栈"(H1 修法后)。

---

## 推荐修复优先级

| 顺序 | 项 | 阻断 |
| --- | --- | --- |
| 1 | **H1 + M1 + H3** | 🟠 widget back 跨 ROM 兼容性 |
| 2 | **H2 + L4** | 🟡 spec 合规(测试落地) |
| 3 | **M2** | 🟢 ktlint 全限定名 |
| 4 | **M3 + L1-L3 + L5** | 🟢 polish |

---

## OpenSpec 收尾

H1-H3 + M1-M4 修完后跑 §13 4 项验收，再写 r2 review 验，最后 `openspec archive predictive-back-gesture -y`。