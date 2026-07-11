package com.yy.writingwithai.core.media

/**
 * attachment-inline-render · 正文 Markdown 切分单元。
 *
 * `Text` 段保留原文(包含未匹配的 `![...](attachment://...)` / 其它 scheme 的图片语法),
 * `AttachmentImage` 段携带已校验的 attachmentId,可直接喂给 `NoteAttachmentDao.observeById`。
 *
 * 用 `sealed interface` 而非 `Pair<String, Boolean>`:Compose 拿到 sealed 后
 * `when` 穷尽有编译器保证,未来加 `Link` / `Code` 段也好扩展(M2)。
 */
sealed interface MarkdownSegment {
    data class Text(val raw: String) : MarkdownSegment

    data class AttachmentImage(val attachmentId: String) : MarkdownSegment
}

/**
 * attachment-inline-render §2 · 正文 Markdown `attachment://` 切分器(纯函数,无依赖)。
 *
 * 只识别 `![](attachment://<id>)`,其中 `<id>` 必须满足 `[A-Za-z0-9_-]{1,64}`
 * (与 `AttachmentStore.PathSafety.SAFE_ID` 完全一致,避免 path traversal)。
 *
 * 不命中 / 不合法 scheme / 不合法 ID 一律落入 `Text` 段,保留原文,不吞用户输入。
 *
 * 用法:`InlineMarkdownText` 内 `remember(content) { AttachmentMarkdownParser.parse(content) }`,
 * 切一次后逐段渲染。
 */
object AttachmentMarkdownParser {

    /**
     * 正则严格锁定 `attachment://` scheme + `SAFE_ID`,group 1 是 alt(丢弃),
     * group 2 是 attachmentId。
     */
    private val ATTACHMENT_RE = Regex(
        """!\[([^\]]*)\]\(attachment://([A-Za-z0-9_\-]{1,64})\)"""
    )

    /**
     * 切分 Markdown content 为 `MarkdownSegment` 序列。
     *
     * 行为:
     * - 空 content → 空 list。
     * - 无匹配 → `[Text(content)]`。
     * - 匹配命中 → `[Text("前文"), AttachmentImage(id), Text("后文"), ...]`。
     * - 空 Text 段会被丢弃(避免渲染一行空白)。
     */
    fun parse(content: String): List<MarkdownSegment> {
        if (content.isEmpty()) return emptyList()

        val matches = ATTACHMENT_RE.findAll(content).toList()
        if (matches.isEmpty()) return listOf(MarkdownSegment.Text(content))

        val result = mutableListOf<MarkdownSegment>()
        var cursor = 0
        for (match in matches) {
            // 匹配前的纯文本
            if (match.range.first > cursor) {
                val text = content.substring(cursor, match.range.first)
                if (text.isNotEmpty()) {
                    result.add(MarkdownSegment.Text(text))
                }
            }
            // attachmentId 来自 group 2(正则已校验 SAFE_ID,无需二次 sanitize)
            result.add(MarkdownSegment.AttachmentImage(match.groupValues[2]))
            cursor = match.range.last + 1
        }
        // 匹配后的尾部纯文本
        if (cursor < content.length) {
            val tail = content.substring(cursor)
            if (tail.isNotEmpty()) {
                result.add(MarkdownSegment.Text(tail))
            }
        }
        return result
    }
}
