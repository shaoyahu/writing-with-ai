package com.yy.writingwithai.core.ai.prompt

object NoteAssociationPrompt {

    fun build(sourceTitle: String, sourceContent: String, candidates: List<CandidateLine>): String = buildString {
        appendLine("你是一个笔记关联分析助手。给定新笔记 A,和候选笔记列表 B,判断 B 中哪些与 A 真正相关。")
        appendLine()
        appendLine("新笔记 A:")
        appendLine("标题: $sourceTitle")
        appendLine("正文: $sourceContent")
        appendLine()
        appendLine("候选笔记(每行:id | title | 摘要):")
        candidates.forEachIndexed { i, c ->
            appendLine("${i + 1}. ${c.id} | ${c.title} | ${c.summary}")
        }
        appendLine()
        appendLine("要求:只挑 confidence ≥ 0.5 的,最多 5 条。")
        appendLine("输出严格 JSON(无 markdown 代码块):")
        appendLine("""{"links":[{"id":"...","confidence":0.85,"reason":"..."}]}""")
    }

    data class CandidateLine(val id: String, val title: String, val summary: String)

    data class LlmLinkResult(val id: String, val confidence: Float, val reason: String)

    data class LlmResponse(val links: List<LlmLinkResult>)
}
