package com.yy.writingwithai.core.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * M4-1 · widget 刷新器(主路径)。
 *
 * 主路径触发点:
 * - `NoteRepository.upsert` / `delete` 末尾(在 `withContext(NonCancellable)` 外调)
 * - `QuickNoteDetailViewModel.delete` 后
 * - AI `AiActionViewModel.acceptReplace` 在 `_state.value = Idle` **之前**调
 *
 * 走 `Dispatchers.IO` 避免阻塞 caller 线程;`updateAll(context)` 是 Glance
 * 内部 `WorkManager`-style 异步任务,自己调度 widget 渲染。
 */
@Singleton
class QuickNoteWidgetUpdater
@Inject
constructor() {
    suspend fun updateAll(context: Context) {
        withContext(Dispatchers.IO) {
            QuickNoteWidget().updateAll(context)
        }
    }
}
