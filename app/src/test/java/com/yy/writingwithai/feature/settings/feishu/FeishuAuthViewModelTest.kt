package com.yy.writingwithai.feature.settings.feishu

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.yy.writingwithai.R
import com.yy.writingwithai.core.feishu.auth.FeishuAuthState
import com.yy.writingwithai.core.feishu.auth.FeishuAuthStore
import com.yy.writingwithai.core.feishu.auth.OAuthLauncher
import com.yy.writingwithai.core.feishu.sync.FeishuSyncEventDao
import com.yy.writingwithai.core.feishu.sync.FeishuSyncEventEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
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

@OptIn(ExperimentalCoroutinesApi::class)
class FeishuAuthViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: FeishuAuthViewModel
    private lateinit var context: Context
    private lateinit var authStore: FeishuAuthStore
    private lateinit var oauthLauncher: OAuthLauncher
    private lateinit var eventDao: FeishuSyncEventDao

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkStatic(android.util.Log::class)
        every { android.util.Log.e(any<String>(), any<String>(), any()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>()) } returns 0

        context = mockk(relaxed = true)
        authStore = mockk(relaxed = true)
        oauthLauncher = mockk(relaxed = true)
        eventDao = mockk(relaxed = true)

        every { authStore.authState } returns MutableStateFlow(FeishuAuthState.DISCONNECTED)
        every { authStore.folderToken } returns flowOf(null)
        every { authStore.getFolderTokenSnapshot() } returns null
        every { eventDao.observeLast(any()) } returns flowOf(emptyList<FeishuSyncEventEntity>())
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
    fun startOAuth_blankAppId_emitsOAuthFailedWithAppidEmpty() = runTest(UnconfinedTestDispatcher()) {
        viewModel = newViewModel()

        var received: FeishuAuthViewModel.OneShotEvent? = null
        val collectJob = backgroundScope.launch {
            viewModel.oneShot.collect { received = it }
        }

        viewModel.startOAuth(appId = "", appSecret = "secret123")

        val event = received
        assertTrue(event is FeishuAuthViewModel.OneShotEvent.OAuthFailed)
        (event as FeishuAuthViewModel.OneShotEvent.OAuthFailed).let {
            assertEquals(R.string.feishu_oauth_error_appid_empty, it.messageRes)
        }

        collectJob.cancel()
    }

    @Test
    fun startOAuth_blankAppSecret_emitsOAuthFailedWithSecretEmpty() = runTest(UnconfinedTestDispatcher()) {
        viewModel = newViewModel()

        var received: FeishuAuthViewModel.OneShotEvent? = null
        val collectJob = backgroundScope.launch {
            viewModel.oneShot.collect { received = it }
        }

        viewModel.startOAuth(appId = "cli_xxx", appSecret = "")

        val event = received
        assertTrue(event is FeishuAuthViewModel.OneShotEvent.OAuthFailed)
        (event as FeishuAuthViewModel.OneShotEvent.OAuthFailed).let {
            assertEquals(R.string.feishu_oauth_error_secret_empty, it.messageRes)
        }

        collectJob.cancel()
    }

    @Test
    fun startOAuth_validInput_callsOauthLauncherLaunch() = runTest {
        coEvery { oauthLauncher.launch(any(), any(), any()) } returns Unit

        viewModel = newViewModel()

        viewModel.startOAuth(appId = "cli_xxx", appSecret = "secret123")
        advanceUntilIdle()

        coVerify { oauthLauncher.launch(context, "cli_xxx", "secret123") }
    }

    @Test
    fun disconnect_callsAuthStoreClearAll() = runTest {
        viewModel = newViewModel()

        viewModel.disconnect()
        advanceUntilIdle()

        coVerify { authStore.clearAll() }
    }

    @Test
    fun setFolderToken_blank_storesNull() = runTest {
        viewModel = newViewModel()

        viewModel.setFolderToken("")
        advanceUntilIdle()

        coVerify { authStore.setFolderToken(null) }
    }

    @Test
    fun setFolderToken_nonBlank_storesValue() = runTest {
        viewModel = newViewModel()

        viewModel.setFolderToken("abc")
        advanceUntilIdle()

        coVerify { authStore.setFolderToken("abc") }
    }

    private fun newViewModel(): FeishuAuthViewModel = FeishuAuthViewModel(
        context = context,
        authStore = authStore,
        oauthLauncher = oauthLauncher,
        eventDao = eventDao
    )
}
