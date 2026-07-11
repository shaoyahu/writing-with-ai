package com.yy.writingwithai.core.widget

/**
 * hardening-sse-and-widget-init H-4:Widget 启动路由 sealed 化。
 *
 * 旧实现:WidgetIntentHelpers.launchWithTaskStack(context, route: String) 接收裸字符串，
 * AppNav 内部 `route.startsWith("quicknote/edit")` / `route.contains("prefillFocus=true")`
 * 拼装，容易因 query param 边界(case / URL encoding / 误匹配)被构造攻击向量绕过。
 *
 * 新实现:sealed 路由 + 内部 `toRouteString()` / `fromRouteString()` 序列化，
 * 业务代码只跟 sealed 打交道，不再有 string prefix 解析。`when` 穷尽由编译器保证。
 *
 * 序列化格式(internal，不在 public API 暴露):
 * - `new_note`  → NewNote
 * - `open_note:<id>` → OpenNote(id)         // Long,parse fail → null
 * - `edit_note:<id>:<prefill>` → EditNote(id, prefillFocus)  // prefill 字符串 "true" / "false"
 * - `freewrite:<date>` → Freewrite(date)    // ISO LocalDate (yyyy-MM-dd),parse fail → null
 *
 * morning-freewrite §3.1:Freewrite(date) 走 Notifier PendingIntent 的 `route` extra
 * (serialized as `freewrite/$date`),MainActivity 解析后写入 widgetPendingRoute。
 */
sealed class WidgetLaunchRoute {
    /** 新建笔记入口(详情/列表 widget "+")。 */
    data object NewNote : WidgetLaunchRoute()

    /** 打开已有笔记(列表 widget tap 已有 note)。 */
    data class OpenNote(val noteId: Long) : WidgetLaunchRoute()

    /** 编辑入口(prefillFocus=true 时 editor 立即获焦)。 */
    data class EditNote(val noteId: Long, val prefillFocus: Boolean = false) : WidgetLaunchRoute()

    /**
     * morning-freewrite · 通知点入 → 沉浸晨写屏。`date` 是 ISO `yyyy-MM-dd`,
     * 用于屏内读上次 freewrite 状态 / 决定是否新建笔记。
     */
    data class Freewrite(val date: String) : WidgetLaunchRoute()

    fun toRouteString(): String = when (this) {
        is NewNote -> "new_note"
        is OpenNote -> "open_note:$noteId"
        is EditNote -> "edit_note:$noteId:$prefillFocus"
        is Freewrite -> "freewrite:$date"
    }

    companion object {
        /**
         * 解析 [EXTRA_ROUTE] 字符串到 sealed 路由。失败(未知 tag / id 非 Long / prefill
         * 非 "true"/"false" / date 非 ISO)返回 null。**不**做 string prefix 模糊匹配。
         */
        fun fromRouteString(raw: String?): WidgetLaunchRoute? {
            if (raw.isNullOrEmpty()) return null
            // morning-freewrite:Notifier 用 `freewrite/yyyy-MM-dd` 形式发 extra,统一 split 成两份
            val parts = raw.split(":", "/")
            return when (parts[0]) {
                "new_note" -> NewNote
                "open_note" -> parts.getOrNull(1)?.toLongOrNull()?.let(::OpenNote)
                "edit_note" -> {
                    val id = parts.getOrNull(1)?.toLongOrNull() ?: return null
                    val prefill = when (parts.getOrNull(2)) {
                        "true" -> true
                        "false", null -> false
                        else -> return null
                    }
                    EditNote(id, prefill)
                }
                "freewrite" -> parts.getOrNull(1)?.takeIf { it.isNotBlank() }?.let(::Freewrite)
                else -> null
            }
        }
    }
}
