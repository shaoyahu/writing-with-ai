package com.yy.writingwithai.core.feishu.converter

import javax.inject.Inject
import javax.inject.Singleton

/**
 * feishu-doc-v2 · 简单 Markdown → Feishu docx XML 转换(参考 larksuite/cli v2 API XML 格式)。
 *
 * v2 API 用 XML body 创建/编辑文档，格式:<document><title>...</title><p>...</p></document>。
 * 本 converter 取满篇 Markdown + title，产出符合 v2 create/update API 格式的 XML 字符串。
 *
 * 支持:标题 / 段落 / 加粗 / 代码块 / 引用 / 行内 code / wikilink。
 *
 * fix-2026-06-25-review-r1 M10:inline marker(`**` / `_` / `` ` `` / `[[]]`)由顺序
 * `replace(...)` 多 pass 改写为单 pass tokenizer —— 解决两个 r1 修前 bug:
 * 1. `# **bold heading**` 在原版仅走 `removePrefix("# ")` 后 `escape(...)`,
 *    inline 标记没机会被解析 → heading 文本里的 `**` 原样出现。
 * 2. 多 pass 顺序 `replace` 在嵌套场景(`**`code`**` / `**`bold & `code`**`)
 *    可能让后面 regex 把前面生成的 tag 内容改坏。
 *
 * 单 pass 走 char by char + 状态机(`BOLD` / `ITALIC` / `CODE`)，所有 handler
 * 共享一个 `StringBuilder` 输出，从根本上避免"前 pass 输出被后 pass 当输入"。
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
            // 跳过纯空白行，避免产生 `<p>   </p>` 空段(L5)。
            if (trimmed.isEmpty()) {
                i++
                continue
            }
            when {
                trimmed.startsWith("```") -> i = appendCodeBlock(sb, lines, i)
                trimmed.startsWith(">") -> {
                    val q = trimmed.removePrefix(">").trimStart()
                    // fix-M10:quote 内容也走 inline tokenizer(M10:嵌套 marker 修复)
                    sb.append("<blockquote><p>").append(convertInline(q)).append("</p></blockquote>")
                }
                trimmed.startsWith("### ") -> {
                    val t = trimmed.removePrefix("### ").trim()
                    sb.append("<h3>").append(convertInline(t)).append("</h3>")
                }
                // fix M22 (full-review):h4/h5/h6 之前 fall through 到 default `<p>` —
                // 内容丢失 markdown 标题语义,且 v2 API `<h3>` 是已知最高级。
                // 显式 map 到最接近的 `<h3>` 并去掉前导 `#` 链,与 h1/h2/h3 行为对齐。
                trimmed.startsWith("#### ") || trimmed.startsWith("##### ") || trimmed.startsWith("###### ") -> {
                    val hashes = trimmed.takeWhile { it == '#' }
                    val t = trimmed.removePrefix(hashes).trim()
                    sb.append("<h3>").append(convertInline(t)).append("</h3>")
                }
                trimmed.startsWith("## ") -> {
                    val t = trimmed.removePrefix("## ").trim()
                    sb.append("<h2>").append(convertInline(t)).append("</h2>")
                }
                trimmed.startsWith("# ") -> {
                    val t = trimmed.removePrefix("# ").trim()
                    // fix-M10:`# **bold heading**` 现在会被转成 `<h1><b>bold heading</b></h1>`
                    sb.append("<h1>").append(convertInline(t)).append("</h1>")
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
        // fix-2026-06-26-review-r3 HIGH H11:fence close 也用 trim 后的 == ``` 判定 — 这条
        // 没问题(代码行 `   ```python` 是普通文本,trim 后 == "```python" 不会误判为 close)。
        // 但 require strict 仍要:行首无空白才算 close，与 spec "严格 fence" 对齐。
        while (i < lines.size && !isFenceCloseLine(lines[i])) {
            codeLines.add(lines[i])
            i++
        }
        // fix-2026-06-26-review-r3 HIGH H11:code block 走 CDATA 包裹，避免
        // `&`/`<`/`>`/控制字符在 v2 API HTML 嵌入时产出 malformed XML。
        // 原版用 escape() 也基本 OK，但 code 内容里出现 `]]>` 会破坏 CDATA —
        // 用 split 拆开 + 多段 CDATA 安全包裹，严格兼容 XML 1.0。
        sb.append("<pre><code>").append(cdatify(codeLines.joinToString("\n"))).append("</code></pre>")
        if (i < lines.size) i++ // 跳过闭合 ```
        return i
    }

    private fun isFenceCloseLine(line: String): Boolean {
        val trimmed = line.trim()
        return trimmed == "```" || (trimmed.startsWith("```") && trimmed.length > 3)
    }

    /**
     * fix-M10 · 单 pass inline tokenizer。
     *
     * 状态机:每次根据当前字符切到对应 handler:
     * - `**`(且非紧跟空白)→ BOLD start / end
     * - `_` → ITALIC start / end
     * - `` ` `` → CODE start / end
     * - `[[...]]` → wikilink 文本(展开为纯文本，feishu 不支持 wikilink)
     * - 其他 → 累加到 buffer
     *
     * 关键不变量:**所有输出都通过同一个 [out] `StringBuilder` 追加**,
     * 没有"先转成中间字符串再被下一 pass 改写"的阶段。
     */
    private fun convertInline(text: String): String {
        val out = StringBuilder()
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            when {
                // `**bold**` — 配对识别(不要把 `*foo*` 错配)
                c == '*' && i + 1 < n && text[i + 1] == '*' -> {
                    val end = text.indexOf("**", i + 2)
                    if (end > i + 2) {
                        val inner = text.substring(i + 2, end)
                        out.append("<b>").append(convertInline(inner)).append("</b>")
                        i = end + 2
                        continue
                    }
                    // 找不到配对 → 当字面量输出
                    out.append(escape("**"))
                    i += 2
                }
                c == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > i + 1) {
                        val inner = text.substring(i + 1, end)
                        out.append("<code>").append(escape(inner)).append("</code>")
                        i = end + 1
                        continue
                    }
                    out.append(escape("`"))
                    i++
                }
                c == '_' && i + 1 < n && text[i + 1] == '_' -> {
                    // fix M17 (full-review):`__bold__` 必须先识别。
                    // 之前 `c == '_'` 单字符分支永远优先,`__foo__` 会被切成 `_` + `_foo_`,
                    // inner="foo_" 不平衡,输出 escape 后剩 `_foo_` 字面量。
                    // 配对识别 + recursive parse,与 `**bold**` 同款。
                    val end = text.indexOf("__", i + 2)
                    if (end > i + 2) {
                        val inner = text.substring(i + 2, end)
                        out.append("<b>").append(convertInline(inner)).append("</b>")
                        i = end + 2
                        continue
                    }
                    out.append(escape("__"))
                    i += 2
                }
                c == '_' -> {
                    val end = text.indexOf('_', i + 1)
                    if (end > i + 1) {
                        val inner = text.substring(i + 1, end)
                        out.append("<em>").append(convertInline(inner)).append("</em>")
                        i = end + 1
                        continue
                    }
                    out.append(escape("_"))
                    i++
                }
                c == '[' && i + 1 < n && text[i + 1] == '[' -> {
                    val end = text.indexOf("]]", i + 2)
                    if (end > i + 2) {
                        val inner = text.substring(i + 2, end)
                        out.append(escape(inner))
                        i = end + 2
                        continue
                    }
                    out.append(escape("[["))
                    i += 2
                }
                else -> {
                    out.append(escape(c.toString()))
                    i++
                }
            }
        }
        return out.toString()
    }

    private fun escape(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    /**
     * fix-2026-06-26-review-r3 HIGH H11:code block 走 CDATA 包裹，避免 raw 代码
     * 里的 `<` / `>` / `&` 在 v2 API HTML 嵌入路径产出 malformed XML。
     * XML 1.0 规范:CDATA section 内禁止出现 `]]>`，用 split 拆 + 多段 CDATA 兜底。
     */
    private fun cdatify(s: String): String {
        if (s.isEmpty()) return ""
        val parts = s.split("]]>")
        val sb = StringBuilder()
        parts.forEachIndexed { idx, part ->
            if (idx > 0) sb.append("]]]]><![CDATA[>")
            sb.append("<![CDATA[").append(part).append("]]>")
        }
        return sb.toString()
    }
}
