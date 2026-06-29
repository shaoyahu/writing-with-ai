package com.yy.writingwithai.core.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.action.ActionCallback

/**
 * 1x1 widget 点击跳编辑页(新建笔记)的 ActionCallback。
 */
class CreateNoteFromWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: androidx.glance.action.ActionParameters
    ) {
        context.launchWithTaskStack(WidgetLaunchRoute.NewNote)
    }
}
