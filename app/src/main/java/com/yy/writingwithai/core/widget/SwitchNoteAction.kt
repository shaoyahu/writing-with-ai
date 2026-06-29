package com.yy.writingwithai.core.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import kotlinx.coroutines.flow.first

/**
 * 2x2 widget 切换下一条笔记 ActionCallback。
 */
class SwitchNoteAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val repo = QuickNoteWidgetHiltBridge.resolveRepository(context) ?: return
        val notes = repo.observeRecent(10).first()
        WidgetStateStore.incrementNoteIndex(context, notes.size)
        QuickNoteWidget().update(context, glanceId)
    }
}
