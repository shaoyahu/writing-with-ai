package com.yy.writingwithai.core.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import javax.inject.Inject
import javax.inject.Singleton

/**
 * M4-1 · widget 刷新器(主路径)。
 *
 * 主路径触发点:
 * - `NoteRepository.upsert` / `delete` 末尾(在 `withContext(NonCancellable)` 外调)
 * - `QuickNoteDetailViewModel.delete` 后
 * - AI `AiActionViewModel.acceptReplace` 在 `_state.value = Idle` **之前**调
 *
 * fix-2026-06-26-review-r3 H24:`updateAll` 是 Glance 1.x 内部的 `WorkManager` 风格异步任务，
 * 其内部已自带 IO 调度。前一层 `withContext(Dispatchers.IO)` 让 update 任务注册发生在
 * 协程上下文中，可能与 widget host process 里的并发 updateAll 产生 race。
 * 移除 `withContext(IO)`，直接调 `updateAll(context)`，由 Glance 自带 scheduler 串行化。
 */
@Singleton
class QuickNoteWidgetUpdater
@Inject
constructor() {
    suspend fun updateAll(context: Context) {
        // fix-full-review M54:同时刷新主 widget (QuickNoteWidget)、1x1 (QuickNote1x1Widget)、
        // 1x4 (QuickNote1x4Widget)。过去只 refresh QuickNoteWidget,用户添加的 1x1/1x4
        // 实例不会自动更新,只能等 Glance 自带轮询(默认 30 分钟),与主 widget 行为不一致。
        QuickNoteWidget().updateAll(context)
        QuickNote1x1Widget().updateAll(context)
        QuickNote1x4Widget().updateAll(context)
    }
}
