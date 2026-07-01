## Context

M0-M4-1 已落地完整产品骨架:M0 / M1 quick-note CRUD / M2 AI 抽象层 / M3 AI 操作 UI / M4-1 Glance 桌面 widget。但 **Android 13+ predictive back gesture 未声明 / 启用**，违反 Google Play 2025-06 后上架要求(Android 14+ targetSdk 35 必备 `enableOnBackInvokedCallback="true"`)。

roadmap §7 拍板的 back 语义:
- **平台**:minSdk 26 → 26~27 走 `OnBackPressedDispatcher`;28+ 走 `predictive back gesture`;v1 targetSdk 35
- **Compose**:用 `androidx.navigation:navigation-compose` 类型安全路由，`NavHost` 自带返回集成
- **widget 任务栈**:M4-1 用 `popUpTo(QuicknoteList) { inclusive = true }` 但 widget 启动 Intent 用 `Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP` — **任务栈语义模糊**:back 时是回到 launcher 还是回到 App 列表?roadmap §7.4 要求用 `TaskStackBuilder` 显式构造

需求落地:
1. AndroidManifest 显式 `enableOnBackInvokedCallback="true"`
2. widget 启动 Intent 走 `TaskStackBuilder`(系统手势 back / 虚拟键 back 都走同一任务栈)
3. AppNav 已 M4-1 修过 `popUpTo(QuicknoteList)`，保持不变
4. 测试覆盖:widget Intent back stack flag 正确

## Goals / Non-Goals

**Goals:**
- AndroidManifest `enableOnBackInvokedCallback="true"`(targetSdk 35 必备 + Android 13+ 启用 predictive back)
- widget "+" Intent:`TaskStackBuilder` 构造 `PendingIntent`,`FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT`,`Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK`
- widget 笔记项 Intent:同样 `TaskStackBuilder` 路径
- back 行为:widget tap → MainActivity(指定 route) → 系统 back → launcher 桌面(走 `TaskStackBuilder` 任务栈独立)
- AppNav `LaunchedEffect` 已 M4-1 加 `popUpTo(QuicknoteList) { inclusive = true }`，保持不变
- 不引入第三方手势拦截库(roadmap §7.2 明确)
- 不自定义 `PredictiveBackHandler`(让 NavHost + OnBackPressedDispatcher 自管)

**Non-Goals:**
- 自定义 predictive back 动画过渡(M5 polish)
- Android 14+ predictive back 手势拦截 UI(M5 polish)
- 国产 ROM 手势适配(M5 polish)
- `AppNav` 内部 back 行为修改(M4-1 r2 已修，本 change 不重复)

## Decisions

### 1. AndroidManifest 显式 `enableOnBackInvokedCallback="true"`

```xml
<application
    android:name=".app.WritingApp"
    android:enableOnBackInvokedCallback="true"
    ...>
    <activity
        android:name=".app.MainActivity"
        android:enableOnBackInvokedCallback="true"
        ...>
    </activity>
</application>
```

**Why:** Android 13 (API 33)+ 推荐启用，Android 14 (API 34)+ 强制要求(targetSdk 35)。**不启用 Play Store 2025-06 后上架会卡审**。roadmap §7.1 明确 v1 targetSdk 35。

**替代方案:** 仅在 `<activity>` 设，不在 `<application>` 设 — Android 13+ 部分 ROM 仍走旧 `onBackPressed` 路径;`<application>` 设保证全 app 启用。

### 2. widget Intent 走 `TaskStackBuilder` + `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK`

```kotlin
internal fun Context.createTaskStackPendingIntent(route: String, requestCode: Int): PendingIntent {
    val intent = Intent(this, MainActivity::class.java)
        .putExtra(OpenNoteAction.EXTRA_ROUTE, route)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    return TaskStackBuilder.create(this)
        .addNextIntentWithParentStack(intent)
        .getPendingIntent(requestCode, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        ?: error("TaskStackBuilder.getPendingIntent returned null")
}
```

**Why:** roadmap §7.4 拍板"用 `TaskStackBuilder` 显式构造回退栈，返回时优先回主任务栈"。widget tap 启动后，系统 back 走 `TaskStackBuilder` 构造的栈，而不是裸 Intent 的新任务。

**M4-1 flag vs 本 change flag:**
- M4-1 `createNoteIntent`:`Intent.FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TOP` — `CLEAR_TOP` 让相同 task 顶部的 Activity 重用，可能不清理掉中间栈
- 本 change `CLEAR_TASK`:**完全清空** launcher task → 启动新 task(走 `TaskStackBuilder` 内部栈)— back 行为更可预测

### 3. `requestCode` 区分 + PendingIntent `FLAG_UPDATE_CURRENT`

```kotlin
private const val REQUEST_CODE_CREATE = 1001   // "+" 按钮
private const val REQUEST_CODE_OPEN = 1002     // 笔记项(每次 noteId 不同 → 通过 extras 区分)
// 创建 widget 后，两个 PendingIntent 共存;FLAG_UPDATE_CURRENT 让同 requestCode 的 extras 更新。
```

**Why:** PendingIntent 系统级缓存，同 requestCode 会被覆盖;不同 requestCode 共存互不干扰。`FLAG_UPDATE_CURRENT` 让 widget tap 时 extras(如 noteId)更新到最新。

### 4. `OpenNoteAction` 同样走 TaskStackBuilder

`OpenNoteAction.onAction(context, ...)` 当前用 `context.startActivity(intent)` — 改走 `TaskStackBuilder.create(context).addNextIntentWithParentStack(intent).startActivities()`。

**Why:** 系统手势 back 与虚拟键 back 行为一致。M4-1 r1 没要求 OpenNoteAction 改 TaskStackBuilder，但本 change 一并统一。

**实现细节:** `TaskStackBuilder.startActivities()` 不是 suspend，直接在 `onAction`(`suspend fun` 但本步无需 suspend)调。

### 5. `AppNav` `LaunchedEffect(initialRoute)` 不动

M4-1 r2 已加 `popUpTo(QuicknoteList) { inclusive = true }`(edit + detail 两条)。**本 change 不重复改 AppNav**。

**Why:** widget 启动 Intent 已走 TaskStackBuilder,**AppNav 内的 LaunchedEffect 不再承担"清理列表栈"职责**(因为 widget Intent 启动时任务栈已经是干净的新栈，不含 QuicknoteList)。`popUpTo` 反而冗余，保留即可无害。

### 6. 测试覆盖:TaskStackBuilder flag 验证

```kotlin
@Test
fun widget_create_intent_uses_task_stack_builder() {
    val context: Context = ApplicationProvider.getApplicationContext()
    val pending = context.createTaskStackPendingIntent("quicknote/edit?prefillFocus=true", 1001)
    // 验证 FLAG_IMMUTABLE
    assertTrue(pending and PendingIntent.FLAG_IMMUTABLE != 0)
    // 验证 FLAG_UPDATE_CURRENT
    assertTrue(pending and PendingIntent.FLAG_UPDATE_CURRENT != 0)
}
```

**Why:** JUnit5 + Robolectric 验证 PendingIntent flag 设置正确(国产 ROM launcher 与 AOSP launcher 行为差异较大，但 flag 一致就保证 task stack 行为一致)。

**替代方案:** Instrumentation test 跑模拟 back — Robolectric 不模拟 gesture,**M5 polish 阶段做 instrumentation**。

### 7. predictive back 动画:不自定义

Android 14+ `OnBackPressedDispatcher` 默认提供 `predictiveBackProgress` 回调，Compose Material 3 自动支持。**不写 `BackHandler { enabled = ... }` 自定义拦截**(会让系统手势失效)。

**Why:** roadmap §7.2 明确"不自定义手势拦截，避免和系统手势打架"。

## Risks / Trade-offs

- **[Risk] widget Intent `CLEAR_TASK` 让 widget 列表里其他 widget host 也清空任务栈** → 多 widget 场景(用户加 2 个 widget，点一个 + 后，另一个 widget 的状态也清)— **接受**:用户极少加 2 个相同 App widget，且 widget state 本就在 Glance 持久化(M5 polish 改 GlanceStateDefinition)，不依赖 Activity 栈
- **[Risk] Android 13 国产 ROM `enableOnBackInvokedCallback` 不生效** → 国产 ROM(小米 MIUI / 华为 EMUI / OPPO ColorOS)部分系统修改 back 框架，无视 `enableOnBackInvokedCallback` — **接受**:M5 polish 阶段标注国产 ROM 已知问题
- **[Risk] `CLEAR_TASK` 让 widget 更新后，widget host 重启** → Glance widget 渲染在 widget host 进程，与 App 主进程独立;`CLEAR_TASK` 只影响 App 进程，不影响 widget host — **无影响**
- **[Risk] `OnBackPressedDispatcher` 与 `NavHost` 自带 back 集成冲突** → Navigation Compose 2.8.x 已适配 `enableOnBackInvokedCallback`;无冲突
- **[Risk] M4-1 测试 `AiActionViewModelTest` 是否受 AndroidManifest 修改影响** → 测试不读 manifest,AndroidManifest 修改不影响单测 — **无影响**

## Migration Plan

无(纯配置 + Intent 行为变更，无 schema 变更)。

回滚:`git revert` 即可，AndroidManifest 改 1 行，PendingIntent 改 2 文件。

## Open Questions

- **AndroidManifest `enableOnBackInvokedCallback` 加 `<application>` 还是仅 `<activity>`?** 倾向:两者都加 — `<application>` 是保险，`<activity>` 是显式声明 spec §7.1 要求;实测两个都加无副作用
- **widget `OpenNoteAction` 启动 Intent 用 `startActivities()` 还是 `getPendingIntent().send()`?** 倾向:`startActivities()`(直接启动，Glance `onAction` 不需要 PendingIntent)— 但 spec 设计 OpenNoteAction 是 `ActionCallback`,Glance 内部用 `actionRunCallback` 触发，**不走 PendingIntent**。**实测 OpenNoteAction 不需要 PendingIntent**，只需要 `startActivities()`
- **AppNav `popUpTo(QuicknoteList) { inclusive = true }` 是否在 widget 路径冗余?** 倾向:保留 — widget Intent 走 TaskStackBuilder,**AppNav 内的 `popUpTo` 是双保险**，即使某天 TaskStackBuilder 出问题，`popUpTo` 也能兜底
- **`requestCode` 全局唯一** vs **per-instance 唯一?** 倾向:**全局唯一**(每个 Intent action 一份 PendingIntent);per-instance 会导致 widget 缓存大量 PendingIntent，占内存