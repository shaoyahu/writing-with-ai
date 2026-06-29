package com.yy.writingwithai.feature.onboarding

import android.content.Context
import android.content.res.AssetManager
import com.yy.writingwithai.R
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * onboarding-consent-card-redesign · OnboardingScreen 集成单测。
 *
 * spec: openspec/changes/onboarding-consent-card-redesign/tasks.md §3
 * 不挂 Compose,纯函数测 3 个可测面:
 * 1. [parseGroupedMarkdown] 5 H2 → 5 sections
 * 2. summaryResolver 命中 5 stringRes
 * 3. [computeScrollProgress] 0 / 1 / 边界用例
 * 4. [loadPrivacyPolicyOrNull] mock context(assets.open 抛 IOException)→ 返回 null
 */
class OnboardingScreenIntegrationTest {
    /** 真实生产代码用的 summaryResolver(从 OnboardingScreen 复制一份以保留测试独立性)。 */
    private val summaryResolver: (String) -> Int? = { title ->
        when {
            "第三方" in title || "third" in title.lowercase() -> R.string.consent_section_third_party_summary
            "AI" in title || "ai" in title.lowercase() -> R.string.consent_section_ai_summary
            "撤回" in title || "withdraw" in title.lowercase() -> R.string.consent_section_withdraw_summary
            "数据" in title || "data" in title.lowercase() -> R.string.consent_section_data_summary
            "联系" in title || "contact" in title.lowercase() -> R.string.consent_section_contact_summary
            else -> null
        }
    }

    private val policyZh =
        """
        # 隐私条款
        最后更新:2026-06-19

        ## 一、数据存储
        本应用所有数据(**笔记、标签、AI 调用历史**)均存储在**您本机设备**上,不向任何服务器上传您的笔记内容。
        我们**不**运营云端后端,**不**收集用户账户信息。卸载 App 即清除全部本地数据。

        ## 二、AI 功能与数据流
        启用 AI 操作(扩写 / 润色 / 整理)时:
        1. 您**手动选中的文本片段**会随 HTTPS 请求发送至您配置的 AI provider

        ## 三、第三方 AI provider 列表
        | provider | base URL |
        | --- | --- |
        | deepseek | `https://api.deepseek.com` |

        ## 四、如何撤回同意
        - **拒绝**:在同意页点击"拒绝并退出",App 立即退出

        ## 五、联系方式
        - 应用开发者:`com.yy.writingwithai`
        """.trimIndent()

    @Test
    fun `parseGroupedMarkdown produces 5 sections for zh policy`() {
        val sections = parseGroupedMarkdown(policyZh, summaryResolver)
        assertEquals(5, sections.size)
        // sectionId 0~4
        assertEquals(0, sections[0].id)
        assertEquals(1, sections[1].id)
        assertEquals(2, sections[2].id)
        assertEquals(3, sections[3].id)
        assertEquals(4, sections[4].id)
        // 标题依次为 5 个 H2
        assertEquals("一、数据存储", sections[0].title)
        assertEquals("二、AI 功能与数据流", sections[1].title)
        assertEquals("三、第三方 AI provider 列表", sections[2].title)
        assertEquals("四、如何撤回同意", sections[3].title)
        assertEquals("五、联系方式", sections[4].title)
    }

    @Test
    fun `summaryResolver matches all 5 stringRes`() {
        assertEquals(
            R.string.consent_section_data_summary,
            summaryResolver("一、数据存储")
        )
        assertEquals(
            R.string.consent_section_ai_summary,
            summaryResolver("二、AI 功能与数据流")
        )
        assertEquals(
            // "第三方" 必须排在 "AI" 之前匹配
            R.string.consent_section_third_party_summary,
            summaryResolver("三、第三方 AI provider 列表")
        )
        assertEquals(
            R.string.consent_section_withdraw_summary,
            summaryResolver("四、如何撤回同意")
        )
        assertEquals(
            R.string.consent_section_contact_summary,
            summaryResolver("五、联系方式")
        )
        // en 标题也能命中
        assertEquals(
            R.string.consent_section_data_summary,
            summaryResolver("1. Data Storage")
        )
        assertEquals(
            R.string.consent_section_third_party_summary,
            summaryResolver("3. Third-Party AI Providers")
        )
        assertEquals(
            R.string.consent_section_withdraw_summary,
            summaryResolver("4. How to Withdraw Consent")
        )
    }

    @Test
    fun `computeScrollProgress returns 1_0 at last item with zero offset`() {
        // (4 + 0/200) / max(5-1, 1) = 4 / 4 = 1.0
        val progress = computeScrollProgress(
            firstVisibleItemIndex = 4,
            offset = 0,
            avgItemSize = 200f,
            totalItems = 5
        )
        assertEquals(1.0f, progress, 0.0001f)
    }

    @Test
    fun `computeScrollProgress returns 0_0 at first item`() {
        // (0 + 0/200) / 4 = 0
        val progress = computeScrollProgress(
            firstVisibleItemIndex = 0,
            offset = 0,
            avgItemSize = 200f,
            totalItems = 5
        )
        assertEquals(0.0f, progress, 0.0001f)
    }

    @Test
    fun `computeScrollProgress returns 0_0 for short content totalItems 1`() {
        // 分母 0 保护:totalItems<=1 → 直接返回 0f
        val progress = computeScrollProgress(
            firstVisibleItemIndex = 0,
            offset = 0,
            avgItemSize = 200f,
            totalItems = 1
        )
        assertEquals(0.0f, progress, 0.0001f)

        val progressZero = computeScrollProgress(
            firstVisibleItemIndex = 0,
            offset = 0,
            avgItemSize = 200f,
            totalItems = 0
        )
        assertEquals(0.0f, progressZero, 0.0001f)
    }

    @Test
    fun `loadPrivacyPolicyOrNull returns null when assets open throws`() {
        // 模拟隐私条款 md 不存在的场景:两次 open 都抛 IOException → 返回 null
        val assetManager = mockk<AssetManager>()
        every { assetManager.open(any()) } throws IOException("simulated missing file")
        val context = mockk<Context>()
        every { context.assets } returns assetManager

        val result = loadPrivacyPolicyOrNull(context)
        assertNull(result)
    }

    @Test
    fun `parseGroupedMarkdown en policy produces 5 sections`() {
        val policyEn =
            """
            # Privacy Policy
            ## 1. Data Storage
            All data is stored locally.

            ## 2. AI Features and Data Flow
            Provider is deepseek.

            ## 3. Third-Party AI Providers
            Standard protocol.

            ## 4. How to Withdraw Consent
            Decline or uninstall.

            ## 5. Contact
            Email in About.
            """.trimIndent()
        val sections = parseGroupedMarkdown(policyEn, summaryResolver)
        assertEquals(5, sections.size)
        assertEquals("1. Data Storage", sections[0].title)
        assertEquals("2. AI Features and Data Flow", sections[1].title)
        // 第三方 必须在 AI 之前匹配 → 验证排序
        assertEquals(R.string.consent_section_third_party_summary, sections[2].summaryRes)
        assertTrue(sections[2].summaryRes != 0)
        assertNotNull(sections[4].summaryRes)
    }
}
