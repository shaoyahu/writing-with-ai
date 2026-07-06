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

/** 从 NoteEntityRow 映射到 UI 投影。 */
fun NoteEntityRow.toHighlight(titleLen: Int, contentLen: Int): EntityHighlight? {
    // fix-2026-07-06: spanStart/spanEnd 一直是纯 content 偏移(用户 addEntityFromSelection
    // 和 LlmEntityExtractor 都用 content 的 indexOf/substring,不再是全文偏移),
    // 所以 toHighlight 不再减 titleLen。titleLen 参数保留给后续可能的全文→content 转换预留。
    val start = spanStart.coerceIn(0, contentLen)
    val end = spanEnd.coerceIn(start, contentLen)
    if (start >= end) return null
    return EntityHighlight(
        surfaceForm = surfaceForm,
        entityType = entityType.name.lowercase(),
        entityKey = entityKey,
        contentStart = start,
        contentEnd = end
    )
}
