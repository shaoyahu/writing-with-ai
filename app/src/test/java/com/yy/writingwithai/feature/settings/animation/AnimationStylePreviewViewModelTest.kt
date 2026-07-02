package com.yy.writingwithai.feature.settings.animation

import android.content.Context
import com.yy.writingwithai.core.prefs.FakeUserPrefsStore
import com.yy.writingwithai.core.prefs.UserPrefsStore
import com.yy.writingwithai.core.ui.animation.AnimationStyle
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
 * animation-system-and-consent-redesign §10.1 + animation-switch-redesign-followup §3.3:
 * 覆盖 [AnimationStylePreviewViewModel] 的 style / reduce-motion 部分。
 *
 * - onStyleSelected 写盘走 FakeUserPrefsStore,与其它 toggle 完全隔离(spec followup §Decisions 3)。
 * - reduce-motion 状态由 VM 通过 `AccessibilityManager.isReduceMotionEnabled` 反射读取,
 *   这里用 MockK 给 Context 返回 null 的 AccessibilityManager → reduceMotion = false;
 *   显式测 reduce-motion 路径会牵涉 API 33+ 反射,改测"reduce-motion 状态从 VM 正确暴露"。
 *
 * nav/tab toggle 行为已迁出本 VM,相关 case 见 [AnimationDetailViewModelTest]。
 *
 * spec 关联:openspec/changes/animation-switch-redesign-followup/specs/animation-system/spec.md
 * REQ MODIFIED AnimationStylePreviewScreen lists 4 styles。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AnimationStylePreviewViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: AnimationStylePreviewViewModel
    private lateinit var userPrefsStore: UserPrefsStore

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        // AccessibilityManager 通过 Context.getSystemService 拿;
        // 这里 Context mock relaxed → getSystemService 返回 null → AccessibilityManager = null → reduceMotion = false。
        val context: Context = mockk(relaxed = true) {
            every { getSystemService(any<String>()) } returns null
        }
        userPrefsStore = FakeUserPrefsStore()
        viewModel = AnimationStylePreviewViewModel(context, userPrefsStore)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onStyleSelected writes the new style to prefs`() = runTest {
        // 初值 MINIMAL(FakeUserPrefsStore 默认)
        assertEquals(AnimationStyle.MINIMAL, viewModel.animationStyle.value)

        // 切到 IMMERSIVE
        viewModel.onStyleSelected(AnimationStyle.IMMERSIVE)
        assertEquals(AnimationStyle.IMMERSIVE, viewModel.animationStyle.value)
    }

    @Test
    fun `onStyleSelected accepts every AnimationStyle enum value`() = runTest {
        AnimationStyle.values().forEach { style ->
            viewModel.onStyleSelected(style)
            assertEquals(style, viewModel.animationStyle.value)
        }
    }

    @Test
    fun `reduce-motion flag is exposed via reduceMotionEnabled StateFlow`() = runTest {
        // mock Context → AccessibilityManager = null → isReduceMotionEnabled() 返回 false。
        // 这里只验 VM 把状态正确暴露出去;reduce-motion 的真实影响(NONE 强切)在 Theme.kt 里。
        assertEquals(false, viewModel.reduceMotionEnabled.value)
    }
}
