package com.yy.writingwithai.feature.quicknote.list

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.yy.writingwithai.core.data.repo.NoteRepository
import com.yy.writingwithai.core.feishu.sync.FeishuSyncService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * note-list-card-actions · QuickNoteListViewModel 新增 3 方法的单测。
 *
 * 只测新加方法的 delegation + error swallow(列表 Flow 行为已由 NoteRepository 自身测试覆盖)。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class QuickNoteListViewModelTest {

    // UnconfinedTestDispatcher 让 VM init 块的 viewModelScope.launch 立即执行，避免 runTest 等无限 Flow 卡死
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: NoteRepository
    private lateinit var syncService: FeishuSyncService
    private lateinit var viewModel: QuickNoteListViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
        syncService = mockk(relaxed = true)
        val importService = mockk<com.yy.writingwithai.core.feishu.sync.FeishuImportService>(relaxed = true)
        viewModel = QuickNoteListViewModel(
            appContext = ApplicationProvider.getApplicationContext<Context>(),
            repository = repository,
            feishuSyncService = syncService,
            feishuImportService = importService
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun togglePinned_flips_state() = runTest(testDispatcher) {
        coEvery { repository.setPinned("n1", true) } returns Unit
        viewModel.togglePinned("n1", currentPinned = false)
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.setPinned("n1", true) }
    }

    @Test
    fun togglePinned_from_true_to_false() = runTest(testDispatcher) {
        coEvery { repository.setPinned("n1", false) } returns Unit
        viewModel.togglePinned("n1", currentPinned = true)
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.setPinned("n1", false) }
    }

    @Test
    fun togglePinned_error_is_logged_not_thrown() = runTest(testDispatcher) {
        coEvery { repository.setPinned("n1", true) } throws RuntimeException("db down")
        // 不应抛
        viewModel.togglePinned("n1", currentPinned = false)
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.setPinned("n1", true) }
    }

    @Test
    fun deleteNote_calls_repo_delete() = runTest(testDispatcher) {
        coEvery { repository.delete("n1") } returns Unit
        viewModel.deleteNote("n1")
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.delete("n1") }
    }

    @Test
    fun deleteNote_error_is_logged_not_thrown() = runTest(testDispatcher) {
        coEvery { repository.delete("n1") } throws RuntimeException("io error")
        viewModel.deleteNote("n1")
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.delete("n1") }
    }

    @Test
    fun addExistingTag_calls_repo_addTagToNote() = runTest(testDispatcher) {
        coEvery { repository.addTagToNote("n1", "kotlin") } returns Unit
        viewModel.addExistingTag("n1", "kotlin")
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.addTagToNote("n1", "kotlin") }
    }

    @Test
    fun addExistingTag_error_is_logged_not_thrown() = runTest(testDispatcher) {
        coEvery { repository.addTagToNote("n1", "kotlin") } throws RuntimeException("dup key")
        viewModel.addExistingTag("n1", "kotlin")
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.addTagToNote("n1", "kotlin") }
    }
}
