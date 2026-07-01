package com.yy.writingwithai.core.widget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore

/**
 * widget-rome-compat · widget 状态 Application-scoped DataStore。
 *
 * M12 修:`current()` 读 与 `incrementNoteIndex` 写 都走 [DataStore.updateData]。
 * DataStore 内部将 read-modify-write 串行化在单一 actor 上，避免两个并发 caller
 * (QuickNoteWidget.provideGlance + SwitchNoteAction)出现 stale read → skip / repeat index。
 */
object WidgetStateStore {
    private val Context.widgetStore: DataStore<WidgetState> by dataStore(
        fileName = "widget_state",
        serializer = WidgetStateSerializer
    )

    /**
     * 读当前 state(走 updateData 保证与写操作串行化)。
     * 传入的 transform 不修改 state(返回原值即可);若需修改，使用 [update]。
     */
    suspend fun current(context: Context): WidgetState = context.applicationContext.widgetStore.updateData { it }

    suspend fun update(context: Context, transform: (WidgetState) -> WidgetState) {
        context.applicationContext.widgetStore.updateData { current ->
            transform(current)
        }
    }

    suspend fun incrementNoteIndex(context: Context, maxIndex: Int) {
        update(context) { state ->
            val next = if (maxIndex <= 0) 0 else (state.currentNoteIndex + 1) % maxIndex
            state.copy(currentNoteIndex = next)
        }
    }
}
