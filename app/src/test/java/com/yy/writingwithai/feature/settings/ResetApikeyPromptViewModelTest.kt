package com.yy.writingwithai.feature.settings

import com.yy.writingwithai.core.prefs.UserPrefsStore
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * onboarding-apikey-prompt · ResetApikeyPromptViewModel 单测。
 *
 * 覆盖:
 * - onResetConfirm → setAckApikeyPrompt(false) + emit ResetDone
 * - consumeAction 不抛异常(SharedFlow no-op 兼容)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ResetApikeyPromptViewModelTest {

    private val userPrefsStore: UserPrefsStore = mockk(relaxed = true)

    private lateinit var viewModel: ResetApikeyPromptViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        mockkStatic(android.util.Log::class)
        viewModel = ResetApikeyPromptViewModel(userPrefsStore)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onResetConfirm emits ResetDone`() = runTest(UnconfinedTestDispatcher()) {
        var received: ResetApikeyPromptViewModel.Action? = null
        val collectJob = backgroundScope.launch {
            viewModel.action.collect { received = it }
        }

        viewModel.onResetConfirm()

        assertEquals(
            ResetApikeyPromptViewModel.Action.ResetDone,
            received,
            "onResetConfirm MUST emit ResetDone"
        )

        collectJob.cancel()
    }

    @Test
    fun `onResetConfirm calls setAckApikeyPrompt with false`() = runTest {
        viewModel.onResetConfirm()

        coVerify(exactly = 1) { userPrefsStore.setAckApikeyPrompt(false) }
    }

    @Test
    fun `consumeAction does not throw`() = runTest {
        // consumeAction is a no-op retained for backward compatibility;
        // verify it doesn't throw when called.
        viewModel.consumeAction()
        // No assertion needed — the test passes if no exception is thrown.
    }
}
