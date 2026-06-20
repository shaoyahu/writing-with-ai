package com.yy.writingwithai.feature.onboarding

import com.yy.writingwithai.core.prefs.ConsentState
import com.yy.writingwithai.core.prefs.FakeConsentStore
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
 * M4-4 · OnboardingViewModel 单测(用 FakeConsentStore 验证 accept / reject 行为)。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `accept writes consent to store with BuildConfig version`() = runTest {
        val store = FakeConsentStore()
        val vm = OnboardingViewModel(store)
        vm.accept()
        val state = store.consentFlow.value
        assertTrue(state.accepted)
        assertEquals(com.yy.writingwithai.BuildConfig.CONSENT_VERSION, state.version)
    }

    @Test
    fun `reject does NOT write consent to store`() = runTest {
        val store = FakeConsentStore()
        val vm = OnboardingViewModel(store)
        vm.reject()
        assertFalse(store.consentFlow.value.accepted)
        assertEquals(0, store.consentFlow.value.version)
    }

    @Test
    fun `reject emits ExitApp action once`() = runTest {
        val store = FakeConsentStore()
        val vm = OnboardingViewModel(store)
        vm.reject()
        assertEquals(OnboardingViewModel.Action.ExitApp, vm.action.value)
        vm.consumeAction()
        assertNull(vm.action.value)
    }

    @Test
    fun `setScrolledToBottom updates state`() = runTest {
        val store = FakeConsentStore()
        val vm = OnboardingViewModel(store)
        assertFalse(vm.scrolledToBottom.value)
        vm.setScrolledToBottom(true)
        assertTrue(vm.scrolledToBottom.value)
    }

    @Test
    fun `seed state reflects in flow`() = runTest {
        val store = FakeConsentStore()
        store.seed(ConsentState(accepted = true, acceptedAt = 1L, version = 1))
        assertEquals(ConsentState(accepted = true, acceptedAt = 1L, version = 1), store.consentFlow.value)
    }
}
