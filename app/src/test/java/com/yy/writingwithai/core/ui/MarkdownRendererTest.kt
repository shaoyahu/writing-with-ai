package com.yy.writingwithai.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.yy.writingwithai.feature.quicknote.model.EntityHighlight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * markdown-live-preview · 覆盖 [render] 的子集语法 / 容错 / 注入防御 / entity 叠加 / 性能。
 *
 * [render] 是 internal 顶层函数,与测试同 module,可直接调用。
 */
class MarkdownRendererTest {

    private val primary = Color(0xFF1976D2)

    /* ========== §D2 子集语法 ========== */

    @Test
    fun `h1 heading strips hash and adds bold span`() {
        val result = render("# 标题", primaryColor = primary)
        assertEquals("标题", result.text)
        val styles = result.spanStyles.filter { it.item.fontWeight == FontWeight.Bold }
        assertTrue("H1 should have at least one Bold style", styles.isNotEmpty())
    }

    @Test
    fun `h2 and h3 headings strip prefix and bold`() {
        val h2 = render("## 次标题", primaryColor = primary)
        assertEquals("次标题", h2.text)
        assertTrue(h2.spanStyles.any { it.item.fontWeight == FontWeight.Bold })

        val h3 = render("### 三级", primaryColor = primary)
        assertEquals("三级", h3.text)
        assertTrue(h3.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
    }

    @Test
    fun `bullet list prefix dot and visible text`() {
        val md = "- 项目一\n- 项目二"
        val result = render(md, primaryColor = primary)
        // 每行渲染为 "· 项目N";行间默认不带 \n(Composable 层由 Column 拆块)
        assertEquals("· 项目一· 项目二", result.text)
    }

    @Test
    fun `ordered list preserves original number`() {
        val md = "1. 一\n2. 二\n3. 三"
        val result = render(md, primaryColor = primary)
        assertEquals("1. 一2. 二3. 三", result.text)
    }

    @Test
    fun `bold strips trigger chars and adds bold span`() {
        val result = render("这是 **粗体** 字", primaryColor = primary)
        // 闭区间:触发符被剥除
        assertEquals("这是 粗体 字", result.text)
        assertTrue(result.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
    }

    @Test
    fun `italic star and underscore both strip and add italic span`() {
        val r1 = render("这是 *斜体* 字", primaryColor = primary)
        assertEquals("这是 斜体 字", r1.text)
        assertTrue(r1.spanStyles.any { it.item.fontStyle == FontStyle.Italic })

        val r2 = render("这是 _斜体_ 字", primaryColor = primary)
        assertEquals("这是 斜体 字", r2.text)
        assertTrue(r2.spanStyles.any { it.item.fontStyle == FontStyle.Italic })
    }

    @Test
    fun `inline code strips backticks and adds monospace span`() {
        val result = render("跑一下 `foo()` 试试", primaryColor = primary)
        // 触发符被剥除;spanStyle 含 Monospace
        assertEquals("跑一下 foo() 试试", result.text)
        assertTrue(
            "code 段应有 Monospace spanStyle",
            result.spanStyles.any { it.item.fontFamily == FontFamily.Monospace }
        )
    }

    @Test
    fun `wikilink strips brackets and adds primary color span`() {
        val result = render("见 [[晨跑计划]] 一文", primaryColor = primary)
        assertEquals("见 晨跑计划 一文", result.text)
        assertTrue(result.spanStyles.any { it.item.color == primary })
    }

    @Test
    fun `link strips brackets and adds underline primary span`() {
        val result = render("[官网](https://example.com)", primaryColor = primary)
        assertEquals("官网", result.text)
        assertTrue(
            result.spanStyles.any {
                it.item.color == primary &&
                    it.item.textDecoration == androidx.compose.ui.text.style.TextDecoration.Underline
            }
        )
    }

    @Test
    fun `paragraph separation handles blank lines via space folding`() {
        val md = "第一段。\n\n第二段。"
        val result = render(md, primaryColor = primary)
        // 段落合并规则:连续非空行合并,行间 `\n` → 空格。空行当段落分隔不进入 visibleText。
        assertEquals("第一段。 第二段。", result.text)
    }

    /* ========== 容错 / 未闭合 ========== */

    @Test
    fun `unclosed bold keeps stars as literal`() {
        val result = render("这是 **未闭合粗体", primaryColor = primary)
        // 未闭合 → 解析失败,触发符原样保留
        assertEquals("这是 **未闭合粗体", result.text)
    }

    @Test
    fun `hash without space stays literal`() {
        val result = render("#不算标题", primaryColor = primary)
        // 没有空格 → 当普通段
        assertEquals("#不算标题", result.text)
    }

    @Test
    fun `italic and bold co-existence without overlap`() {
        val result = render("**bold** and *italic*", primaryColor = primary)
        assertEquals("bold and italic", result.text)
        assertTrue(result.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
        assertTrue(result.spanStyles.any { it.item.fontStyle == FontStyle.Italic })
    }

    /* ========== §D5 XSS / 注入防御 ========== */

    @Test
    fun `script tag rendered as literal text no parsing`() {
        val evil = "Hello <script>alert(1)</script>"
        val result = render(evil, primaryColor = primary)
        assertEquals(evil, result.text)
    }

    @Test
    fun `iframe tag rendered as literal text`() {
        val evil = "<iframe src=x></iframe>"
        val result = render(evil, primaryColor = primary)
        assertEquals(evil, result.text)
    }

    @Test
    fun `javascript link URL is visual only no url annotation`() {
        val evil = "[点我](javascript:alert(1))"
        val result = render(evil, primaryColor = primary)
        // 触发符被剥,只保留 inner "点我"
        assertEquals("点我", result.text)
        // 关键:不产生 UrlAnnotation / LinkAnnotation
        val urlAnnotations = result.getStringAnnotations(0, result.length)
            .filter { it.tag == "URL" || it.tag == "androidx.compose.ui.text.LinkAnnotation" }
        assertEquals("不应有 URL annotation", 0, urlAnnotations.size)
    }

    @Test
    fun `html entities not auto decoded`() {
        val result = render("5 &lt; 10 &amp;&amp; 10 &gt; 5", primaryColor = primary)
        // Compose Text 不解析 HTML 实体,字面保留
        assertEquals("5 &lt; 10 &amp;&amp; 10 &gt; 5", result.text)
    }

    /* ========== Entity 叠加 ========== */

    @Test
    fun `entity highlight adds color span and cross star at content level`() {
        val md = "普通文本 [[小明]] 后面"
        // wikilink 字符 [[小明]] 在 markdown 中位于索引 5..11
        // "小明" 表面 text 落在 content 偏移 7..9(content 整体偏移 5 + 2 后 [[)
        val highlights = listOf(
            EntityHighlight(
                surfaceForm = "小明",
                entityType = "person",
                entityKey = "entity:小明",
                contentStart = 7,
                contentEnd = 9
            )
        )
        val result = render(md, highlights, primaryColor = primary)
        // ✦ 必须出现在输出文本中(供 starsBefore 计数)
        assertTrue("输出应含 ✦ 字符", result.text.contains(EntityCrossStar))
        // primary 色 span 至少有 1 个(entity)
        assertTrue(result.spanStyles.any { it.item.color == primary })
        // entity annotation 也必须保留
        val entityAnno = result.getStringAnnotations("entity", 0, result.length).toList()
        assertTrue("应有至少 1 个 entity annotation", entityAnno.isNotEmpty())
        assertEquals("entity:小明", entityAnno.first().item)
    }

    @Test
    fun `entity in markdown with heading still preserves star character`() {
        val md = "# 标题\n\n普通 [[实体]] 内容"
        val highlights = listOf(
            EntityHighlight(
                surfaceForm = "实体",
                entityType = "concept",
                entityKey = "entity:实体",
                contentStart = 11,
                contentEnd = 13
            )
        )
        val result = render(md, highlights, primaryColor = primary)
        assertTrue(result.text.contains(EntityCrossStar))
        assertTrue("标题 bold 应保留", result.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
    }

    /* ========== 性能 ========== */

    @Test
    fun `1KB note renders under 50ms`() {
        val para = "这是一段普通文本,没有触发 Markdown 语法。"
        val md = buildString {
            repeat(20) {
                append(para)
                append("\n\n")
            }
        }
        val start = System.currentTimeMillis()
        val result = render(md, primaryColor = primary)
        val elapsed = System.currentTimeMillis() - start
        assertTrue("1KB 渲染应 < 50ms,实际 ${elapsed}ms", elapsed < 50L)
        assertNotEquals(0, result.length)
    }

    /* ========== 边界 ========== */

    @Test
    fun `empty markdown returns empty annotated string`() {
        val result = render("", primaryColor = primary)
        assertEquals("", result.text)
    }

    @Test
    fun `plain text with no markdown triggers renders as-is`() {
        val md = "纯文本无 Markdown 语法"
        val result = render(md, primaryColor = primary)
        assertEquals(md, result.text)
        assertTrue(
            "纯文本不应有 Bold span",
            result.spanStyles.none { it.item.fontWeight == FontWeight.Bold }
        )
        assertTrue(
            "纯文本不应有 Italic span",
            result.spanStyles.none { it.item.fontStyle == FontStyle.Italic }
        )
    }
}
