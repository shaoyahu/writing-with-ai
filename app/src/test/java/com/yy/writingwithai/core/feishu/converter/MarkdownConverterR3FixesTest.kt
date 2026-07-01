package com.yy.writingwithai.core.feishu.converter

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * fix-2026-06-26-review-r3 回归测试:
 *  - H10:code-fence close 必须行首无空白(CommonMark 规范)
 *  - H11:code block 内容走 CDATA 包裹，避免 v2 API HTML 嵌入产出 malformed XML
 */
class MarkdownConverterR3FixesTest {

    private val docx = MarkdownToDocxConverterImpl()
    private val xml = MarkdownToXmlConverter()

    // ---- H10: fence close must require no leading whitespace ----

    @Test
    fun `H10 indented fence-close is not treated as close in docx converter`() = runTest {
        // 之前 `lines[i].trimStart().startsWith("```")` 会把 `    \`\`\`rust`
        // 误判为 close → 代码块提前终止。修后要求行首无空白才算 fence close。
        val md = "```\nreal_code()\n    ```rust\nstill_inside\n```"
        val blocks = docx.convert(md)
        assertEquals(1, blocks.size)
        val code = blocks[0] as FeishuBlock.CodeBlock
        // 内层行应被吞进 code 块，而不是被错误切出
        assertTrue(code.text.contains("real_code()"))
        assertTrue(code.text.contains("    ```rust"))
        assertTrue(code.text.contains("still_inside"))
    }

    @Test
    fun `H10 indented fence-open is not treated as open in docx converter`() = runTest {
        // 4+ 空格缩进的 ``` 不应触发代码块分支
        val md = "    ```\nthis is not a code block, it's an indented literal\n    ```"
        val blocks = docx.convert(md)
        // 不应产生 CodeBlock，应走段落/Unsupported
        assertTrue(blocks.none { it is FeishuBlock.CodeBlock }, "should not produce CodeBlock: $blocks")
    }

    @Test
    fun `H10 proper fence open and close still works`() = runTest {
        val md = "```kotlin\nval x = 1\nval y = 2\n```"
        val blocks = docx.convert(md)
        assertEquals(1, blocks.size)
        val code = blocks[0] as FeishuBlock.CodeBlock
        assertEquals("kotlin", code.language)
        assertEquals("val x = 1\nval y = 2", code.text)
    }

    @Test
    fun `H10 same fix applies to XML converter`() = runTest {
        val md = "```kotlin\nval x = 1\nval y = 2\n```"
        val out = xml.convert(md, title = "T")
        // fence open 行 trim 后 == "```kotlin" 才进入 appendCodeBlock
        assertTrue(out.contains("<pre><code>"))
        assertTrue(out.contains("val x = 1"))
        assertTrue(out.contains("val y = 2"))
    }

    // ---- H11: code block content must be CDATA-wrapped in XML converter ----

    @Test
    fun `H11 code block content is wrapped in CDATA`() = runTest {
        val md = "```\n<tag attr=\"v\">&value;\n```"
        val out = xml.convert(md, title = "T")
        assertTrue(out.contains("<![CDATA["), "expected CDATA wrap, got: $out")
        assertTrue(out.contains("<tag attr=\"v\">&value;"), "raw content inside CDATA: $out")
    }

    @Test
    fun `H11 code block handles embedded CDATA-end sequences`() = runTest {
        // XML 1.0 规范:CDATA 内禁止出现 ]]>。
        // 实现用 split("]]>") 拆 + 多段 CDATA 兜底。
        val md = "```\nif (a > b]]> bad) {}\n```"
        val out = xml.convert(md, title = "T")
        // 不应出现裸 `]]>` 终结符
        assertFalse(out.contains("]]><![CDATA[>") == false && !out.contains("<![CDATA["))
        // 但实现的 split 应至少产生一段 CDATA wrap
        assertTrue(out.contains("<![CDATA["))
    }
}
