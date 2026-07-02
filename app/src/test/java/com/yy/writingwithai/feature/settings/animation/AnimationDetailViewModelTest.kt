package com.yy.writingwithai.feature.settings.animation

import android.content.Context
import com.yy.writingwithai.core.prefs.FakeUserPrefsStore
import com.yy.writingwithai.core.prefs.UserPrefsStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * animation-switch-redesign-followup §3.3:覆盖 [AnimationDetailViewModel] 的 nav/tab toggle 行为。
 *
 * - onNavAnimationsToggled / onTabAnimationsToggled 走 FakeUserPrefsStore 持久化。
 * - reduce-motion 状态由 VM 通过 `AccessibilityManager.isReduceMotionEnabled` 反射读取,
 *   这里用 MockK 给 Context 返回 null 的 AccessibilityManager → reduceMotion = false;
 *   显式测 reduce-motion 路径会牵涉 API 33+ 反射,改测"reduce-motion 状态从 VM 正确暴露"。
 *
 * spec 关联:openspec/changes/animation-switch-redesign-followup/specs/animation-system/spec.md
 * REQ ADDED AnimationDetailScreen exposes nav/tab animation toggles。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AnimationDetailViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: AnimationDetailViewModel
    private lateinit var userPrefsStore: UserPrefsStore

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context: Context = mockk(relaxed = true) {
            every { getSystemService(any<String>()) } returns null
        }
        userPrefsStore = FakeUserPrefsStore()
        viewModel = AnimationDetailViewModel(context, userPrefsStore)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onNavAnimationsToggled false makes navAnimationsEnabled emit false`() = runTest {
        // 初值 true(默认 + FakeUserPrefsStore MutableStateFlow(true))
        assertEquals(true, viewModel.navAnimationsEnabled.value)
        viewModel.onNavAnimationsToggled(false)
        assertEquals(false, viewModel.navAnimationsEnabled.value)
    }

    @Test
    fun `onTabAnimationsToggled false makes tabAnimationsEnabled emit false`() = runTest {
        assertEquals(true, viewModel.tabAnimationsEnabled.value)
        viewModel.onTabAnimationsToggled(false)
        assertEquals(false, viewModel.tabAnimationsEnabled.value)
    }

    @Test
    fun `nav and tab toggles are independent`() = runTest {
        // 初值都 true
        assertEquals(true, viewModel.navAnimationsEnabled.value)
        assertEquals(true, viewModel.tabAnimationsEnabled.value)

        // 翻 nav 不影响 tab
        viewModel.onNavAnimationsToggled(false)
        assertEquals(false, viewModel.navAnimationsEnabled.value)
        assertEquals(true, viewModel.tabAnimationsEnabled.value)

        // 翻 tab 不影响 nav
        viewModel.onTabAnimationsToggled(false)
        assertEquals(false, viewModel.navAnimationsEnabled.value)
        assertEquals(false, viewModel.tabAnimationsEnabled.value)
    }

    @Test
    fun `reduce-motion flag is exposed via reduceMotionEnabled StateFlow`() = runTest {
        // mock Context → AccessibilityManager = null → isReduceMotionEnabled() 返回 false。
        // 这里只验 VM 把状态正确暴露出去;reduce-motion 的真实影响(NONE 强切)在 Theme.kt 里。
        assertEquals(false, viewModel.reduceMotionEnabled.value)
    }
}
