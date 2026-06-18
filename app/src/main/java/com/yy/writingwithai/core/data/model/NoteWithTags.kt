package com.yy.writingwithai.core.data.model

/**
 * 笔记 + 它挂的 tag 列表。
 *
 * 列表屏一次拿到完整渲染需要的字段,不需要在 UI 层再次拼装。
 */
data class NoteWithTags(
    val note: Note,
    val tags: List<String>,
)
