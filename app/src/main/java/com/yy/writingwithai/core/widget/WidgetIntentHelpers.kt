package com.yy.writingwithai.core.widget

import android.content.Context
import android.content.Intent
import androidx.core.app.TaskStackBuilder
import com.yy.writingwithai.app.MainActivity

/**
 * M4-2 r2 · widget Intent 启动 helper(走真正的 [TaskStackBuilder.startActivities],
 * 显式构造 launcher→MainActivity 的回退栈)。
 *
 * back 行为:widget tap → MainActivity(指定 route) → 系统 back → launcher 桌面
 * (roadmap §7.4 拍板),跨 ROM 行为一致(AOSP / 国产 ROM)。
 *
 * 替换 M4-1 r1 L1 删的 `createNotePendingIntent` + M4-2 r1 H1 soft 降级的 `createWidgetLaunchIntent`。
 *
 * 公共 caller:
 * - `QuickNoteWidget.createNoteIntent(context)` ("+",route="quicknote/edit?prefillFocus=true")
 * - `OpenNoteAction.onAction(...)` (route="quicknote/detail/{noteId}")
 *
 * M4-4 注:widget Intent 启动后的 consent 闸门在 `MainActivity.onCreate` 内统一处理
 * (走 `EntryPointAccessors` 拿 `ConsentStore.isConsented()` 同步决策 + `pendingRoute` 暂存),
 * 此 helper 不重复做 consent 检查。
 */
internal fun Context.launchWithTaskStack(route: String) {
    val intent =
        Intent(this, MainActivity::class.java)
            .putExtra(OpenNoteAction.EXTRA_ROUTE, route)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    TaskStackBuilder.create(this)
        .addNextIntentWithParentStack(intent)
        .startActivities()
}
