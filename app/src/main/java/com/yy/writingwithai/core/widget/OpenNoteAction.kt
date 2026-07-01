package com.yy.writingwithai.core.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback

/**
 * M4-1 · widget 笔记项点击 ActionCallback。
 *
 * `provideGlance` 内 `actionRunCallback<OpenNoteAction>(parametersOf(KEY_NOTE_ID to noteId))` 触发，
 * `onAction` 在 widget host process 内调，启动 MainActivity 到 `quicknote/detail/{noteId}`。
 * 走 MainActivity 解析 extra → AppNav 跳转(见 quick-note spec "WidgetIntent launcher routes pass through MainActivity")。
 */
class OpenNoteAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val noteId = parameters[KEY_NOTE_ID]?.toLongOrNull() ?: return
        // hardening H-4:走 sealed WidgetLaunchRoute.OpenNote，不再传裸 string。
        context.launchWithTaskStack(WidgetLaunchRoute.OpenNote(noteId))
    }

    companion object {
        val KEY_NOTE_ID: ActionParameters.Key<String> = ActionParameters.Key("noteId")
        const val EXTRA_ROUTE = "route"
    }
}
