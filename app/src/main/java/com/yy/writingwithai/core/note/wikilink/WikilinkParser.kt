package com.yy.writingwithai.core.note.wikilink

/**
 * fix-2026-06-25-review-r1 H2:`[[Alias|Target]]` 形态下，
 * 之前正则把整段当 title，带 `|` 的 title 永远 miss。
 * 现在拆出 [target] 与可选 [alias] 两段，alias 留作显示 label。
 */
data class WikilinkMatch(
    val target: String,
    val alias: String?
)

object WikilinkParser {
    // Wiki 约定 [[display|target]]:group1 = display(可选 alias),group2 = target(在 | 后)。
    // 无 | 时 group1 = target,group2 = null。
    // group1 段禁止 `[` `]` `\n` `|`;group2 段禁止 `[` `]` `\n`(允许 `|` 之外的字符)。
    private val REGEX = Regex("""\[\[([^\[\]\n|]+?)(?:\|([^\[\]\n]+?))?\]\]""")

    fun parse(content: String): List<WikilinkMatch> = REGEX.findAll(content)
        .map { match ->
            val aliasOrTarget = match.groupValues[1].trim()
            val afterPipe = match.groupValues.getOrNull(2)?.trim()?.takeIf { it.isNotEmpty() }
            WikilinkMatch(
                target = afterPipe ?: aliasOrTarget,
                alias = afterPipe?.let { aliasOrTarget }
            )
        }
        .toList()
}
