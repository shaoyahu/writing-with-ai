package com.yy.writingwithai.feature.settings.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.yy.writingwithai.core.data.export.ImportReport
import com.yy.writingwithai.core.data.export.NoteExporter
import com.yy.writingwithai.core.data.export.NoteImporter
import com.yy.writingwithai.core.data.model.NoteWithTags
import com.yy.writingwithai.core.data.repo.NoteRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * M4-3 · SettingsDataViewModel 单元测试(JUnit5 + MockK)。
 *
 * 覆盖 spec §"SettingsDataViewModel 用 viewModelScope.launch + Dispatchers.IO" 场景:
 * - 导出成功 → state Idle → Exporting → Done(report.successCount = exported.size)
 * - 导出失败 → state Failed
 * - 导入成功 → state Done(report) + lastImportReportZipBytes 暂存
 * - 导入失败 → state Failed
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsDataViewModelTest {
    private val context: Context = mockk(relaxed = true)
    private val contentResolver: ContentResolver = mockk(relaxed = true)
    private val noteExporter: NoteExporter = mockk()
    private val noteImporter: NoteImporter = mockk()
    private val noteRepository: NoteRepository = mockk()
    private val dispatcher: CoroutineDispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: SettingsDataViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { context.contentResolver } returns contentResolver
        every { noteRepository.observeNotesWithTags(null, null) } returns flowOf(emptyList<NoteWithTags>())
        viewModel =
            SettingsDataViewModel(
                context = context,
                noteExporter = noteExporter,
                noteImporter = noteImporter,
                noteRepository = noteRepository,
                ioDispatcher = dispatcher
            )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun exportToJsonZip_success_drives_state_to_done_with_count() = runTest {
        val uri: Uri = mockk(relaxed = true)
        val outputStream = java.io.ByteArrayOutputStream()
        every { contentResolver.openOutputStream(uri) } returns outputStream
        coEvery { noteExporter.exportToJsonZip(outputStream) } returns 5

        viewModel.exportToJsonZip(uri)

        val state = viewModel.uiState.value
        assertTrue(state is DataUiState.Done, "export 成功 MUST → Done, 实际 = $state")
        state as DataUiState.Done
        assertEquals(5, state.report.successCount, "Done.successCount MUST = exported size")
        assertEquals(0, state.report.failedCount)
        assertFalse(state.isImport, "export 触发的 Done MUST isImport = false")
    }

    @Test
    fun exportToJsonZip_exception_drives_state_to_failed() = runTest {
        val uri: Uri = mockk(relaxed = true)
        val outputStream = java.io.ByteArrayOutputStream()
        every { contentResolver.openOutputStream(uri) } returns outputStream
        coEvery { noteExporter.exportToJsonZip(outputStream) } throws java.io.IOException("写 zip 失败")

        viewModel.exportToJsonZip(uri)

        val state = viewModel.uiState.value
        assertTrue(state is DataUiState.Failed, "export 失败 MUST → Failed, 实际 = $state")
        state as DataUiState.Failed
        assertTrue(state.error.contains("写 zip 失败"))
    }

    @Test
    fun importFromZip_success_drives_state_to_done_with_report() = runTest {
        val uri: Uri = mockk(relaxed = true)
        val inputStream = java.io.ByteArrayInputStream(byteArrayOf(1, 2, 3))
        val expectedReport =
            ImportReport(
                successCount = 3,
                skippedCount = 1,
                failedCount = 0,
                failedNotes = emptyList()
            )
        every { contentResolver.openInputStream(uri) } returns inputStream
        coEvery {
            noteImporter.importFromZip(any<java.io.InputStream>(), any<java.io.OutputStream>())
        } returns expectedReport

        viewModel.importFromZip(uri)

        val state = viewModel.uiState.value
        assertTrue(state is DataUiState.Done, "import 成功 MUST → Done, 实际 = $state")
        state as DataUiState.Done
        assertEquals(3, state.report.successCount)
        assertEquals(1, state.report.skippedCount)
        assertEquals(0, state.report.failedCount)
        assertTrue(state.isImport, "import 触发的 Done MUST isImport = true")
        assertNotNull(viewModel.lastImportReportZipBytes, "lastImportReportZipBytes MUST 被暂存")
    }

    @Test
    fun importFromZip_exception_drives_state_to_failed() = runTest {
        val uri: Uri = mockk(relaxed = true)
        val inputStream = java.io.ByteArrayInputStream(byteArrayOf())
        every { contentResolver.openInputStream(uri) } returns inputStream
        coEvery {
            noteImporter.importFromZip(any<java.io.InputStream>(), any<java.io.OutputStream>())
        } throws java.io.IOException("读 zip 失败")

        viewModel.importFromZip(uri)

        val state = viewModel.uiState.value
        assertTrue(state is DataUiState.Failed, "import 失败 MUST → Failed, 实际 = $state")
        state as DataUiState.Failed
        assertTrue(state.error.contains("读 zip 失败"))
    }

    // H2:r1 review。notesCount 暴露 Room 当前笔记总数,Screen 据此置灰导出按钮 + 显示 no_data。
    @Test
    fun notesCount_reflects_repository_value() = runTest {
        every { noteRepository.observeNotesWithTags(null, null) } returns
            flowOf(
                listOf(
                    NoteWithTags(
                        note =
                        com.yy.writingwithai.core.data.model.Note(
                            id = "n1",
                            title = "a",
                            content = "x",
                            createdAt = 0L,
                            updatedAt = 0L,
                            isPinned = false,
                            lastAiOp = null,
                            lastAiAt = null
                        ),
                        tags = emptyList()
                    ),
                    NoteWithTags(
                        note =
                        com.yy.writingwithai.core.data.model.Note(
                            id = "n2",
                            title = "b",
                            content = "y",
                            createdAt = 0L,
                            updatedAt = 0L,
                            isPinned = false,
                            lastAiOp = null,
                            lastAiAt = null
                        ),
                        tags = emptyList()
                    )
                )
            )
        viewModel =
            SettingsDataViewModel(
                context = context,
                noteExporter = noteExporter,
                noteImporter = noteImporter,
                noteRepository = noteRepository,
                ioDispatcher = dispatcher
            )
        // stateIn WhileSubscribed(5000) 不立即 collect;用 first { } 触发 collect + 等待非初始值
        val count = viewModel.notesCount.first { it > 0 }
        assertEquals(2, count, "notesCount MUST 反映 Repository 的笔记数")
    }

    // M4:r1 review。VM 入口 guard — 非 Idle 状态下重复触发 export/import MUST 静默忽略。
    @Test
    fun exportToJsonZip_when_state_not_idle_ignores_call() = runTest {
        val uri: Uri = mockk(relaxed = true)
        val outputStream = java.io.ByteArrayOutputStream()
        every { contentResolver.openOutputStream(uri) } returns outputStream
        coEvery { noteExporter.exportToJsonZip(outputStream) } returns 5

        viewModel.exportToJsonZip(uri)
        // 第二次调用,state 此时已是 Done(导出完成),guard 必须拒绝
        viewModel.exportToJsonZip(uri)

        // exporter 只被调一次
        io.mockk.coVerify(exactly = 1) { noteExporter.exportToJsonZip(any()) }
    }
}
