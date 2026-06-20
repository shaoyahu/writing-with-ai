package com.yy.writingwithai.core.widget

import com.yy.writingwithai.core.data.repo.NoteRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * M4-1 · widget 数据源:封装 `NoteRepository.observeRecent(limit)`,给 widget UI 层用。
 *
 * 独立 Hilt 单例便于 widget 测试 mock;Room 数据 schema 不变(走 M1 `observeAll()` + 内存 `take`)。
 */
@Singleton
class QuickNoteWidgetRepository
@Inject
constructor(
    private val noteRepository: NoteRepository
) {
    fun observeRecent(limit: Int) = noteRepository.observeRecent(limit)
}
