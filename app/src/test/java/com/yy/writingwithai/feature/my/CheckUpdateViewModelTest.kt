package com.yy.writingwithai.feature.my

import androidx.lifecycle.viewModelScope
import com.yy.writingwithai.BuildConfig
import com.yy.writingwithai.core.update.AppUpdateChecker
import com.yy.writingwithai.core.update.AppUpdateManifest
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * CheckUpdateViewModel 单测:
 * - 初始状态为 Idle
 * - check() 无更新时过渡到 UpToDate
 * - check() 有更新时过渡到 UpdateAvailable
 * - check() 异常时过渡到 Failed
 * - consume() 重置为 Idle
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CheckUpdateViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: CheckUpdateViewModel
    private lateinit var appUpdateChecker: AppUpdateChecker

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkStatic(android.util.Log::class)
        every { android.util.Log.e(any<String>(), any<String>(), any()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>(), any()) } returns 0

        appUpdateChecker = mockk(relaxed = true)
    }

    @AfterEach
    fun tearDown() {
        if (::viewModel.isInitialized) {
            viewModel.viewModelScope.cancel()
        }
        Dispatchers.resetMain()
        unmockkStatic(android.util.Log::class)
    }

    @Test
    fun initialState_isIdle() = runTest(dispatcher) {
        coEvery { appUpdateChecker.fetch() } returns Result.success(
            AppUpdateManifest(
                versionCode = BuildConfig.VERSION_CODE,
                versionName = "1.0.0",
                apkUrl = "https://example.com/app.apk",
                apkSha256 = "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2"
            )
        )
        viewModel = CheckUpdateViewModel(appUpdateChecker)
        advanceUntilIdle()

        assertTrue(viewModel.state.value is CheckUpdateState.Idle)
        viewModel.viewModelScope.cancel()
        advanceUntilIdle()
    }

    @Test
    fun check_noUpdate_transitionsToUpToDate() = runTest(dispatcher) {
        // 远端 versionCode == 本地，无更新
        coEvery { appUpdateChecker.fetch() } returns Result.success(
            AppUpdateManifest(
                versionCode = BuildConfig.VERSION_CODE,
                versionName = "1.0.0",
                apkUrl = "https://example.com/app.apk",
                apkSha256 = "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2"
            )
        )
        viewModel = CheckUpdateViewModel(appUpdateChecker)
        advanceUntilIdle()

        viewModel.check()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is CheckUpdateState.UpToDate, "Expected UpToDate, got $state")
        state as CheckUpdateState.UpToDate
        assertEquals(BuildConfig.VERSION_NAME, state.localVersion)
        viewModel.viewModelScope.cancel()
        advanceUntilIdle()
    }

    @Test
    fun check_updateAvailable_transitionsToUpdateAvailable() = runTest(dispatcher) {
        // 远端 versionCode > 本地
        val manifest = AppUpdateManifest(
            versionCode = BuildConfig.VERSION_CODE + 1,
            versionName = "2.0.0",
            apkUrl = "https://example.com/app-v2.apk",
            apkSha256 = "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2",
            releaseNotes = "Bug fixes and improvements"
        )
        coEvery { appUpdateChecker.fetch() } returns Result.success(manifest)
        viewModel = CheckUpdateViewModel(appUpdateChecker)
        advanceUntilIdle()

        viewModel.check()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is CheckUpdateState.UpdateAvailable, "Expected UpdateAvailable, got $state")
        state as CheckUpdateState.UpdateAvailable
        assertEquals(manifest, state.manifest)
        viewModel.viewModelScope.cancel()
        advanceUntilIdle()
    }

    @Test
    fun check_exception_transitionsToFailed() = runTest(dispatcher) {
        coEvery { appUpdateChecker.fetch() } returns Result.failure(
            java.io.IOException("network error")
        )
        viewModel = CheckUpdateViewModel(appUpdateChecker)
        advanceUntilIdle()

        viewModel.check()
        advanceUntilIdle()

        assertTrue(viewModel.state.value is CheckUpdateState.Failed)
        viewModel.viewModelScope.cancel()
        advanceUntilIdle()
    }

    @Test
    fun consume_resetsToIdle() = runTest(dispatcher) {
        coEvery { appUpdateChecker.fetch() } returns Result.failure(
            java.io.IOException("network error")
        )
        viewModel = CheckUpdateViewModel(appUpdateChecker)
        advanceUntilIdle()

        viewModel.check()
        advanceUntilIdle()
        assertTrue(viewModel.state.value is CheckUpdateState.Failed)

        viewModel.consume()
        assertTrue(viewModel.state.value is CheckUpdateState.Idle)
        viewModel.viewModelScope.cancel()
        advanceUntilIdle()
    }
}
