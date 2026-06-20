package com.yy.writingwithai.core.data.model

/**
 * UI 侧使用的 Note 领域模型。
 *
 * 与 Room [com.yy.writingwithai.core.data.db.entity.NoteEntity] 同形但**不带** Room 注解,
 * 防止 UI / ViewModel 层意外引入持久化依赖(比如在 test 里直接 new 出来不需要 Room)。
 *
 * 字段语义见 [openspec.changes.quick-note-feature.specs.quick-note.spec] §"Note entity schema"。
 */
data class Note(
    val id: String,
    val title: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isPinned: Boolean,
    val lastAiOp: String?,
    val lastAiAt: Long?
) {
    companion object {
        // M4 修:空 title 时由正文前 N 字派生(roadmap §3.1 + spec §"Note entity schema")。
        // 列表行 + 详情屏共用此常量,避免裸 30 散落。
        const val TITLE_FALLBACK_LEN = 30
    }
}
