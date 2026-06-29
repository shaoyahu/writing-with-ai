package com.yy.writingwithai.core.widget

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.TaskStackBuilder
import com.yy.writingwithai.app.MainActivity

/**
 * hardening-sse-and-widget-init H-4:Widget Intent 启动 helper,改用 sealed [WidgetLaunchRoute]。
 *
 * 旧实现 (`route: String` + `route.startsWith`):
 * - `launchWithTaskStack(context, route: String)` 接收裸字符串
 * - `AppNav` 内 `route.startsWith("quicknote/edit")` / `route.contains("prefillFocus=true")`
 *   拼装导航,易因 query param 边界(case / encoding / 误匹配)被构造攻击向量绕过
 *
 * 新实现:
 * - `launchWithTaskStack(context, route: WidgetLaunchRoute)` 接受 sealed
 * - `parseLaunchRoute(intent)` 反向解析 → sealed
 * - 业务代码(`AppNav.navigatePendingRoute` 等)改用 `when` 穷尽
 * - 序列化格式 `new_note` / `open_note:<id>` / `edit_note:<id>:<prefill>` 在 EXTRA_ROUTE 中传递
 *
 * back 行为:widget tap → MainActivity(指定 route) → 系统 back → launcher 桌面
 * (roadmap §7.4 拍板),跨 ROM 行为一致(AOSP / 国产 ROM)。
 *
 * 公共 caller:
 * - `QuickNoteWidget.AddButton` ("+", `WidgetLaunchRoute.NewNote`)
 * - `QuickNote1x4Widget` ("+", `WidgetLaunchRoute.NewNote`)
 * - `OpenNoteAction.onAction(...)` (`WidgetLaunchRoute.OpenNote(noteId)`)
 * - `CreateNoteFromWidgetAction` (`WidgetLaunchRoute.NewNote`)
 *
 * M4-4 注:widget Intent 启动后的 consent 闸门在 `MainActivity.onCreate` 内统一处理
 * (走 `EntryPointAccessors` 拿 `ConsentStore.isConsented()` 同步决策 + `pendingRoute` 暂存),
 * 此 helper 不重复做 consent 检查。
 */
internal fun Context.launchWithTaskStack(route: WidgetLaunchRoute) {
    val intent =
        Intent(this, MainActivity::class.java)
            .putExtra(OpenNoteAction.EXTRA_ROUTE, route.toRouteString())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    TaskStackBuilder.create(this)
        .addNextIntentWithParentStack(intent)
        .startActivities()
}

/**
 * 从 widget 启动的 Intent 中解析 [WidgetLaunchRoute]。
 * 失败时返回 null 并 log warn,不抛异常 —— caller 据此 fallback。
 */
internal fun parseLaunchRoute(intent: Intent?): WidgetLaunchRoute? {
    if (intent == null) return null
    val raw = intent.getStringExtra(OpenNoteAction.EXTRA_ROUTE) ?: return null
    return WidgetLaunchRoute.fromRouteString(raw).also { parsed ->
        if (parsed == null) {
            Log.w("WidgetIntentHelpers", "unparseable widget launch route: $raw")
        }
    }
}
