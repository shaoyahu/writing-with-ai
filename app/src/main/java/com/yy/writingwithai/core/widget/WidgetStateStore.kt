package com.yy.writingwithai.core.widget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import kotlinx.coroutines.flow.first

/**
 * widget-rome-compat · widget 状态 Application-scoped DataStore。
 *
 * Glance 1.1.x 的 `GlanceAppWidget.stateDefinition` 是 final 不可 override,因此本 change
 * 不走 [androidx.glance.state.GlanceStateDefinition] API,改用 application-scoped
 * DataStore 手动管理 widget 状态。
 *
 * 使用方式:
 * - widget host process 启动 → widget `provideGlance` 内调 [current] 拿 stale 状态作兜底
 * - `QuickNoteWidgetUpdater.updateAll` → 写入新 [WidgetState]
 */
object WidgetStateStore {
    private val Context.widgetStore: DataStore<WidgetState> by dataStore(
        fileName = "widget_state",
        serializer = WidgetStateSerializer
    )

    suspend fun current(context: Context): WidgetState = context.applicationContext.widgetStore.data.first()

    suspend fun update(context: Context, transform: (WidgetState) -> WidgetState) {
        context.applicationContext.widgetStore.updateData { current ->
            transform(current)
        }
    }
}
