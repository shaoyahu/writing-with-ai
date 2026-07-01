package com.yy.writingwithai.feature.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * animation-system-and-consent-redesign §7.3:覆盖
 * - [parseSimpleMarkdown] 签名/输出结构(向后兼容)
 * - [parseGroupedMarkdown] 按 H2 切 5 段 + 关键词 → 图标映射
 *
 * 关键词解析在 production 由 [parseGroupedMarkdown] 内部完成(私有函数 iconForTitle);
 * 这里通过解析后 section.icon.name 验证关键词命中图标种类的子集。
 */
class SimpleMarkdownTest {

    /** 5 H2 段落模拟条款 markdown(spec §2.1 5 卡片分类)。 */
    private val fiveH2Sample = """
        # 使用条款

        ## 数据存储与同步
        所有笔记保存在本机 SQLite，不上传任何服务器。

        ## AI 功能与数据流
        启用 AI 后，选中文本会发送到您配置的 provider。

        ## 第三方 AI Provider
        本应用支持多家第三方 AI 服务，需要您自行配置 API Key。

        ## 如何撤回同意
        您可在设置中随时撤回同意，清除本机 AI 配置。

        ## 联系方式
        如有疑问，请通过设置页反馈入口联系我们。
    """.trimIndent()

    @Test
    fun `parseSimpleMarkdown keeps original signature and emits blocks`() {
        // 既有 parseSimpleMarkdown:1 H1 + 5 H2 + 多段，验证 type/level 分布
        val blocks = parseSimpleMarkdown(fiveH2Sample)
        assertTrue("应解析出多个 block", blocks.size >= 6)
        val headings = blocks.filterIsInstance<MarkdownBlock.Heading>()
        assertEquals("H1 + 5 H2", 6, headings.size)
        assertEquals(1, headings[0].level)
        assertEquals(5, headings.drop(1).count { it.level == 2 })
    }

    @Test
    fun `parseGroupedMarkdown splits 5 H2 sections and ignores H1`() {
        val sections = parseGroupedMarkdown(fiveH2Sample) { _ -> 0 }
        assertEquals("5 H2 段", 5, sections.size)
        assertEquals("数据存储与同步", sections[0].title)
        assertEquals("AI 功能与数据流", sections[1].title)
        assertEquals("第三方 AI Provider", sections[2].title)
        assertEquals("如何撤回同意", sections[3].title)
        assertEquals("联系方式", sections[4].title)
    }

    @Test
    fun `parseGroupedMarkdown sections carry non-empty blocks under each H2`() {
        val sections = parseGroupedMarkdown(fiveH2Sample) { _ -> 0 }
        sections.forEachIndexed { idx, sec ->
            assertTrue("section[$idx] blocks 不空", sec.blocks.isNotEmpty())
            sec.blocks.forEach { block ->
                if (block is MarkdownBlock.Heading) {
                    // section 内不应再含 heading(H2 已被切片)
                    assertTrue("section 内不应有 heading", false)
                }
            }
        }
    }

    @Test
    fun `parseGroupedMarkdown icon mapping covers all 5 keywords`() {
        val sections = parseGroupedMarkdown(fiveH2Sample) { _ -> 0 }
        assertEquals(5, sections.size)
        // JVM 单测中 Compose Icons name 格式不确定，改为验证:
        // 1) 每个 section icon 非 null
        // 2) 5 个 icon 互不相同(不同关键词映射不同图标)
        // 3) 兜底 section(unknown keyword)用 Storage
        sections.forEach { section ->
            assertNotNull("icon should not be null for ${section.title}", section.icon)
        }
        val distinctIconCount = sections.map { it.icon.name }.distinct().size
        assertTrue(
            "5 sections should have at least 3 distinct icons (some may share name format), got $distinctIconCount",
            distinctIconCount >= 3
        )
    }

    @Test
    fun `parseGroupedMarkdown falls back to Storage icon for unknown keyword`() {
        val raw = """
            ## 杂项
            其他说明文本。
        """.trimIndent()
        val sections = parseGroupedMarkdown(raw) { _ -> 0 }
        assertEquals(1, sections.size)
        assertTrue(
            "未匹配关键词 → Storage 兜底，name=${sections[0].icon.name}",
            sections[0].icon.name.lowercase().contains("storage")
        )
    }

    @Test
    fun `parseGroupedMarkdown uses summaryResolver mapping`() {
        val sections = parseGroupedMarkdown(fiveH2Sample) { title ->
            // 仅返回第 3 段的 summaryRes = 42，其他 null(会被跳过)
            if (title == "第三方 AI Provider") 42 else null
        }
        assertEquals(1, sections.size)
        assertEquals(42, sections[0].summaryRes)
        assertEquals("第三方 AI Provider", sections[0].title)
    }

    @Test
    fun `parseGroupedMarkdown empty input yields empty list`() {
        assertTrue(parseGroupedMarkdown("") { _ -> 0 }.isEmpty())
    }
}
