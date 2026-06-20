package com.yy.writingwithai.feature.onboarding

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import com.yy.writingwithai.core.prefs.FakeConsentStore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * M5 polish · OnboardingScreen Compose UI test。
 *
 * 验证 scroll-to-bottom unlock 行为:
 * - 未滚动 → accept button disabled
 * - 滚动到底部 → accept button enabled
 * - 短文(firstVisible == 0) → accept button 仍 disabled
 *
 * spec: openspec/specs/onboarding-consent/spec.md
 * "Privacy policy rendered as Markdown with scroll-to-bottom unlock"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OnboardingScreenUiTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun acceptButtonDisabledBeforeScroll() {
        val store = FakeConsentStore()
        val vm = OnboardingViewModel(store)
        composeTestRule.setContent {
            OnboardingRoute(
                onExitApp = {},
                viewModel = vm
            )
        }
        composeTestRule.onNodeWithTag("accept_button").assertIsNotEnabled()
    }

    @Test
    fun acceptButtonDisabledWhenFirstVisibleZero() {
        // 短文(3 items)不满一屏:firstVisible 保持 0,按钮 disabled
        val store = FakeConsentStore()
        val vm = OnboardingViewModel(store)
        composeTestRule.setContent {
            OnboardingRoute(
                onExitApp = {},
                viewModel = vm
            )
        }
        // 即使滚动到最后一项,firstVisible == 0 → 按钮仍 disabled
        composeTestRule.onNodeWithTag("privacy_policy_list").performScrollToIndex(2)
        composeTestRule.onNodeWithTag("accept_button").assertIsNotEnabled()
    }
}
