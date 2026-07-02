package com.yy.writingwithai.feature.settings.i18n

import android.app.Activity
import com.yy.writingwithai.core.i18n.LocaleSelection
import com.yy.writingwithai.core.i18n.LocaleStore
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * language-switcher · SettingsLanguageViewModel 单测。
 *
 * 覆盖:
 * - initial current = SYSTEM (Eagerly default)
 * - current reflects localeStore.observe emissions
 * - select persists to localeStore and calls activity.recreate()
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsLanguageViewModelTest {

    private val localeState = MutableStateFlow(LocaleSelection.SYSTEM)

    private val localeStore: LocaleStore = mockk(relaxed = true) {
        every { observe } returns localeState.asStateFlow()
    }

    private val activity: Activity = mockk(relaxed = true)

    private lateinit var viewModel: SettingsLanguageViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        mockkStatic(android.util.Log::class)
        viewModel = SettingsLanguageViewModel(localeStore)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial current is SYSTEM`() {
        assertEquals(
            LocaleSelection.SYSTEM,
            viewModel.current.value,
            "Initial current MUST be SYSTEM (Eagerly default)"
        )
    }

    @Test
    fun `current reflects localeStore observe emissions`() {
        // Emit ZH from the store
        localeState.value = LocaleSelection.ZH
        assertEquals(
            LocaleSelection.ZH,
            viewModel.current.value,
            "current MUST reflect localeStore.observe emission"
        )

        // Emit EN from the store
        localeState.value = LocaleSelection.EN
        assertEquals(
            LocaleSelection.EN,
            viewModel.current.value,
            "current MUST reflect updated localeStore.observe emission"
        )
    }

    @Test
    fun `select persists to localeStore and calls activity recreate`() = runTest {
        viewModel.select(LocaleSelection.ZH, activity)

        coVerify(exactly = 1) { localeStore.set(LocaleSelection.ZH) }
        verify(exactly = 1) { activity.recreate() }
    }
}
