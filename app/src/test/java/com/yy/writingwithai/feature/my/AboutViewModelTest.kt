package com.yy.writingwithai.feature.my

import com.yy.writingwithai.core.update.ApkDownloader
import com.yy.writingwithai.core.update.AppUpdateChecker
import com.yy.writingwithai.core.update.AppUpdateManifest
import com.yy.writingwithai.core.update.UpdateError
import com.yy.writingwithai.core.update.UpdateManifestStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
 * app-self-hosted-update · AboutViewModel 状态机单测。
 *
 * 验证:
 * - checkForUpdate 拉取成功且 versionCode > 本地 → Available
 * - checkForUpdate 拉取成功且 versionCode <= 本地 → UpToDate → resetToIdle
 * - checkForUpdate 拉取失败 → Failed → resetToIdle
 * - startDownload 从 Available 调 ApkDownloader + 写 store + 转 Downloading
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AboutViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val checker = mockk<AppUpdateChecker>()
    private val downloader = mockk<ApkDownloader>(relaxed = true)
    private val store = mockk<UpdateManifestStore>(relaxed = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `checkForUpdate with newer remote version transitions to Available`() = runTest {
        val manifest = sampleManifest(versionCode = 99)
        coEvery { checker.fetch() } returns Result.success(manifest)
        every { downloader.start(any()) } returns 100L

        val vm = AboutViewModel(checker, downloader, store)
        vm.checkForUpdate(localVersionCode = 1)
        advanceUntilIdle()

        val s = vm.state.value
        assertTrue(s is AboutUiState.Available, "expected Available but got $s")
        assertEquals(99, (s as AboutUiState.Available).manifest.versionCode)
    }

    @Test
    fun `checkForUpdate with same remote version transitions to UpToDate`() = runTest {
        val manifest = sampleManifest(versionCode = 1)
        coEvery { checker.fetch() } returns Result.success(manifest)

        val vm = AboutViewModel(checker, downloader, store)
        vm.checkForUpdate(localVersionCode = 1)
        advanceUntilIdle()

        // VM 本身停在 UpToDate;AboutScreen 的 LaunchedEffect 调 resetToIdle 回到 Idle
        assertTrue(vm.state.value is AboutUiState.UpToDate)
        vm.resetToIdle()
        assertTrue(vm.state.value is AboutUiState.Idle)
    }

    @Test
    fun `checkForUpdate on network failure transitions to Failed`() = runTest {
        coEvery { checker.fetch() } returns Result.failure(UpdateError.Network())

        val vm = AboutViewModel(checker, downloader, store)
        vm.checkForUpdate(localVersionCode = 1)
        advanceUntilIdle()

        assertTrue(vm.state.value is AboutUiState.Failed, "got ${vm.state.value}")
        vm.resetToIdle()
        assertTrue(vm.state.value is AboutUiState.Idle)
    }

    @Test
    fun `startDownload from Available enqueues DownloadManager and writes store`() = runTest {
        val manifest = sampleManifest(versionCode = 99)
        coEvery { checker.fetch() } returns Result.success(manifest)
        every { downloader.start(any()) } returns 42L

        val vm = AboutViewModel(checker, downloader, store)
        vm.checkForUpdate(localVersionCode = 1)
        advanceUntilIdle()
        vm.startDownload()

        coVerify { downloader.start(manifest) }
        coVerify { store.put(42L, manifest) }
        assertTrue(vm.state.value is AboutUiState.Downloading)
    }

    @Test
    fun `startDownload ignored when not Available`() = runTest {
        val vm = AboutViewModel(checker, downloader, store)
        vm.startDownload()
        coVerify(exactly = 0) { downloader.start(any()) }
    }

    private fun sampleManifest(versionCode: Int) = AppUpdateManifest(
        versionCode = versionCode,
        versionName = "0.$versionCode.0",
        apkUrl = "https://example.com/writing-with-ai-$versionCode.apk",
        apkSize = 1024L,
        apkSha256 = "deadbeef",
        releaseNotes = "test",
        releasedAt = "2026-06-24T00:00:00Z",
        minSupportedVersionCode = 1,
        mandatory = false
    )
}
