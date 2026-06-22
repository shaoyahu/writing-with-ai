package com.yy.writingwithai.feature.onboarding

import com.yy.writingwithai.core.prefs.FakeUserPrefsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * onboarding-apikey-prompt · ApikeyPromptViewModel 单测。
 *
 * spec: openspec/changes/onboarding-apikey-prompt/tasks.md §7.1
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ApikeyPromptViewModelTest {
    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `acked starts false when store is empty`() = runTest {
        val store = FakeUserPrefsStore()
        val vm = ApikeyPromptViewModel(store)
        assertFalse(vm.acked.value)
    }

    @Test
    fun `onAck writes true to store and emits Finished`() = runTest {
        val store = FakeUserPrefsStore()
        val vm = ApikeyPromptViewModel(store)
        vm.onAck()
        assertTrue(store.isApikeyPromptAcked())
        assertEquals(ApikeyPromptViewModel.Action.Finished, vm.action.value)
    }

    @Test
    fun `onSkip writes true to store and emits Finished (spec skip-also-acks)`() = runTest {
        val store = FakeUserPrefsStore()
        val vm = ApikeyPromptViewModel(store)
        vm.onSkip()
        assertTrue(store.isApikeyPromptAcked())
        assertEquals(ApikeyPromptViewModel.Action.Finished, vm.action.value)
    }

    @Test
    fun `onReset writes false to store and emits Reset`() = runTest {
        val store = FakeUserPrefsStore()
        store.seed(true)
        val vm = ApikeyPromptViewModel(store)
        assertTrue(vm.acked.value)
        vm.onReset()
        assertFalse(store.isApikeyPromptAcked())
        assertEquals(ApikeyPromptViewModel.Action.Reset, vm.action.value)
    }

    @Test
    fun `consumeAction clears the action signal`() = runTest {
        val store = FakeUserPrefsStore()
        val vm = ApikeyPromptViewModel(store)
        vm.onAck()
        vm.consumeAction()
        assertNull(vm.action.value)
    }

    @Test
    fun `acked mirrors store changes after seed`() = runTest {
        val store = FakeUserPrefsStore()
        val vm = ApikeyPromptViewModel(store)
        store.seed(true)
        assertTrue(vm.acked.value)
        store.seed(false)
        assertFalse(vm.acked.value)
    }
}
