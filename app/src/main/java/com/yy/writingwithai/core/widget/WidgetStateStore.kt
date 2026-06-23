package com.yy.writingwithai.core.widget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import kotlinx.coroutines.flow.first

/**
 * widget-rome-compat · widget 状态 Application-scoped DataStore。
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

    suspend fun incrementNoteIndex(context: Context, maxIndex: Int) {
        update(context) { state ->
            val next = if (maxIndex <= 0) 0 else (state.currentNoteIndex + 1) % maxIndex
            state.copy(currentNoteIndex = next)
        }
    }
}
