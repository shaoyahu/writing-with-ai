package com.yy.writingwithai.core.feishu.converter

import javax.inject.Inject
import javax.inject.Singleton

/**
 * feishu-doc-v2 · 简单 Markdown → Feishu docx XML 转换(参考 larksuite/cli v2 API XML 格式)。
 *
 * v2 API 用 XML body 创建/编辑文档,格式:<document><title>...</title><p>...</p></document>。
 * 本 converter 取满篇 Markdown + title,产出符合 v2 create/update API 格式的 XML 字符串。
 *
 * 支持:标题 / 段落 / 加粗 / 代码块 / 引用。
 */
@Singleton
open class MarkdownToXmlConverter
@Inject
constructor() {

    open fun convert(markdown: String, title: String): String {
        val sb = StringBuilder()
        sb.append("<document>")
        sb.append("<title>").append(escape(title)).append("</title>")
        val lines = markdown.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()
            // 跳过纯空白行,避免产生 `<p>   </p>` 空段(L5)。
            if (trimmed.isEmpty()) {
                i++
                continue
            }
            when {
                trimmed.startsWith("```") -> i = appendCodeBlock(sb, lines, i)
                trimmed.startsWith(">") -> {
                    val q = trimmed.removePrefix(">").trimStart()
                    sb.append("<blockquote><p>").append(escape(q)).append("</p></blockquote>")
                }
                trimmed.startsWith("### ") -> {
                    sb.append("<h3>").append(escape(trimmed.removePrefix("### ").trim())).append("</h3>")
                }
                trimmed.startsWith("## ") -> {
                    sb.append("<h2>").append(escape(trimmed.removePrefix("## ").trim())).append("</h2>")
                }
                trimmed.startsWith("# ") -> {
                    sb.append("<h1>").append(escape(trimmed.removePrefix("# ").trim())).append("</h1>")
                }
                else -> {
                    sb.append("<p>").append(convertInline(trimmed)).append("</p>")
                }
            }
            i++
        }
        sb.append("</document>")
        return sb.toString()
    }

    private fun appendCodeBlock(sb: StringBuilder, lines: List<String>, start: Int): Int {
        var i = start + 1
        val codeLines = mutableListOf<String>()
        while (i < lines.size && lines[i].trim() != "```") {
            codeLines.add(lines[i])
            i++
        }
        sb.append("<pre><code>").append(escape(codeLines.joinToString("\n"))).append("</code></pre>")
        return i
    }

    private fun convertInline(text: String): String {
        var s = text
        s = s.replace(Regex("\\*\\*(.+?)\\*\\*")) { "<b>${escape(it.groupValues[1])}</b>" }
        s = s.replace(Regex("_(.+?)_")) { "<em>${escape(it.groupValues[1])}</em>" }
        s = s.replace(Regex("`(.+?)`")) { "<code>${escape(it.groupValues[1])}</code>" }
        s = s.replace(Regex("\\[\\[(.+?)\\]\\]")) { escape(it.groupValues[1]) }
        return s
    }

    private fun escape(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
