package com.yy.writingwithai.core.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback

/**
 * M4-1 · widget 笔记项点击 ActionCallback。
 *
 * `provideGlance` 内 `actionRunCallback<OpenNoteAction>(parametersOf(KEY_NOTE_ID to noteId))` 触发,
 * `onAction` 在 widget host process 内调,启动 MainActivity 到 `quicknote/detail/{noteId}`。
 * 走 MainActivity 解析 extra → AppNav 跳转(见 quick-note spec "WidgetIntent launcher routes pass through MainActivity")。
 */
class OpenNoteAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val noteId = parameters[KEY_NOTE_ID] ?: return
        // M4-2 r2 修:走 launchWithTaskStack 真正用 TaskStackBuilder.startActivities,
        // 跨 AOSP / 国产 ROM back 行为一致(回 launcher 桌面)。
        context.launchWithTaskStack("quicknote/detail/$noteId")
    }

    companion object {
        val KEY_NOTE_ID: ActionParameters.Key<String> = ActionParameters.Key("noteId")
        const val EXTRA_ROUTE = "route"
    }
}
