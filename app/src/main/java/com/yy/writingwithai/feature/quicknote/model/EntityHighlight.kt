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
    val contentEnd: Int,
    // entity-source-tagging: 实体来源
    val source: String = "AI_EXTRACTED"
)

/** 从 NoteEntityRow 映射到 UI 投影。 */
fun NoteEntityRow.toHighlight(titleLen: Int, contentLen: Int): EntityHighlight? {
    // fix-2026-07-06: spanStart/spanEnd 一直是纯 content 偏移(用户 addEntityFromSelection
    // 和 LlmEntityExtractor 都用 content 的 indexOf/substring,不再是全文偏移),
    // 所以 toHighlight 不再减 titleLen。titleLen 参数保留给后续可能的全文→content 转换预留。
    val rawSurface = surfaceForm
    val leadingQuotes = countLeadingQuotes(rawSurface)
    val trailingQuotes = countTrailingQuotes(rawSurface)
    val cleanedSurface = if (leadingQuotes + trailingQuotes >= rawSurface.length) {
        // 全是引号 —— 异常数据,回退原始
        rawSurface
    } else {
        rawSurface.substring(leadingQuotes, rawSurface.length - trailingQuotes)
    }
    // fix-2026-07-08:历史 LLM 提取的 surfaceForm 可能带前导/尾随引号,
    // DB 里 spanStart/spanEnd 是用带引号的 surface 在 content 里 indexOf 算出来的,
    // 所以 span 范围要往内收缩到 cleanedSurface 的真实字符区间。
    val start = (spanStart + leadingQuotes).coerceIn(0, contentLen)
    val end = (start + cleanedSurface.length).coerceAtMost(contentLen)
    if (start >= end) return null
    return EntityHighlight(
        surfaceForm = cleanedSurface,
        entityType = entityType.name.lowercase(),
        entityKey = entityKey,
        contentStart = start,
        contentEnd = end,
        source = source
    )
}

/** 计算前导连续引号字符数(LLM 经常输出 ""先天缺陷 这种格式)。 */
private fun countLeadingQuotes(s: String): Int {
    val quoteChars = setOf('“', '”', '‘', '’', '"', '\'')
    var n = 0
    for (c in s) {
        if (c in quoteChars) n++ else break
    }
    return n
}

/** 计算尾导连续引号字符数。 */
private fun countTrailingQuotes(s: String): Int {
    val quoteChars = setOf('“', '”', '‘', '’', '"', '\'')
    var n = 0
    for (i in s.indices.reversed()) {
        if (s[i] in quoteChars) n++ else break
    }
    return n
}
