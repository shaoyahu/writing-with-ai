package com.yy.writingwithai.feature.quicknote.model

import com.yy.writingwithai.core.data.db.entity.entity.NoteEntityRow

/**
 * note-decompose-highlight M4:实体高亮的 UI 层投影。
 *
 * 从 NoteEntityRow（Room Entity）映射而来，UI 层不直接引用 persistence 类型。
 * spanStart/spanEnd 已转换为纯 content 偏移（不含 title 前缀）。
 */
data class EntityHighlight(
    val surfaceForm: String,
    val entityType: String,
    val entityKey: String,
    val contentStart: Int,
    val contentEnd: Int
)

/** 从 NoteEntityRow 映射到 UI 投影，titleLen = title.length + 1。 */
fun NoteEntityRow.toHighlight(titleLen: Int, contentLen: Int): EntityHighlight? {
    val start = (spanStart - titleLen).coerceIn(0, contentLen)
    val end = (spanEnd - titleLen).coerceIn(start, contentLen)
    if (start >= end) return null
    return EntityHighlight(
        surfaceForm = surfaceForm,
        entityType = entityType.name.lowercase(),
        entityKey = entityKey,
        contentStart = start,
        contentEnd = end
    )
}
