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

    // H2:r1 review。notesCount 暴露 Room 当前笔记总数，Screen 据此置灰导出按钮 + 显示 no_data。
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
        // 第二次调用，state 此时已是 Done(导出完成),guard 必须拒绝
        viewModel.exportToJsonZip(uri)

        // exporter 只被调一次
        io.mockk.coVerify(exactly = 1) { noteExporter.exportToJsonZip(any()) }
    }

    // ===== last-import-report-save · saveImportReport 行为 =====

    @Test
    fun saveImportReport_writesBytesToUri() = runTest {
        // arrange:触发 import 让 VM 缓存 bytes
        val importUri: Uri = mockk(relaxed = true)
        val inputStream = java.io.ByteArrayInputStream(byteArrayOf(1, 2, 3))
        val expectedReport =
            ImportReport(
                successCount = 1,
                skippedCount = 0,
                failedCount = 0,
                failedNotes = emptyList()
            )
        every { contentResolver.openInputStream(importUri) } returns inputStream
        coEvery {
            noteImporter.importFromZip(any<java.io.InputStream>(), any<java.io.OutputStream>())
        } coAnswers
            {
                // mock importer 写非空 bytes 到 out，模拟真实闭循环报告 zip
                val out = secondArg<java.io.OutputStream>()
                out.write(byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte()))
                expectedReport
            }
        viewModel.importFromZip(importUri)
        val cachedBytes = viewModel.lastImportReportZipBytes
        assertNotNull(cachedBytes, "import 成功 MUST 缓存 bytes")
        assertTrue(cachedBytes!!.isNotEmpty(), "mock importer 写入 out 后 cachedBytes MUST 非空")

        // act:saveImportReport 写另一个 uri
        val saveUri: Uri = mockk(relaxed = true)
        val saveOutputStream = java.io.ByteArrayOutputStream()
        every { contentResolver.openOutputStream(saveUri) } returns saveOutputStream
        viewModel.saveImportReport(saveUri)

        // assert:openOutputStream 被调 + bytes 等于 cachedBytes
        io.mockk.verify { contentResolver.openOutputStream(saveUri) }
        assertEquals(cachedBytes.size, saveOutputStream.size(), "saved bytes size MUST == cachedBytes size")
        assertEquals(
            cachedBytes.toList(),
            saveOutputStream.toByteArray().toList(),
            "saved bytes content MUST == cachedBytes"
        )
        // Done 态不变
        assertTrue(viewModel.uiState.value is DataUiState.Done, "saveImportReport 成功 MUST 不破坏 Done 态")
        // Success 信号触发后会被 LaunchedEffect reset;在 viewModel 层面，value 已被 set 为 Success
        assertTrue(
            viewModel.lastSaveReportResult.value is SaveReportResult.Success,
            "saveImportReport 成功 MUST → lastSaveReportResult.Success, 实际 = ${viewModel.lastSaveReportResult.value}"
        )
    }

    @Test
    fun saveImportReport_nullBytesIsNoOp() = runTest {
        // arrange:VM 初始(未触发 import)lastImportReportZipBytes == null
        assertEquals(null, viewModel.lastImportReportZipBytes, "VM 初始 bytes MUST null")

        // act:直接调 saveImportReport
        val saveUri: Uri = mockk(relaxed = true)
        viewModel.saveImportReport(saveUri)

        // assert:openOutputStream 0 次调用 + lastSaveReportResult 仍 Idle
        io.mockk.verify(exactly = 0) { contentResolver.openOutputStream(any<Uri>()) }
        assertTrue(
            viewModel.lastSaveReportResult.value is SaveReportResult.Idle,
            "null bytes MUST no-op, lastSaveReportResult 保持 Idle, 实际 = ${viewModel.lastSaveReportResult.value}"
        )
    }

    @Test
    fun saveImportReport_outputStreamFailurePreservesDoneState() = runTest {
        // arrange:先 import 成功，vm 进入 Done(isImport=true)
        val importUri: Uri = mockk(relaxed = true)
        val inputStream = java.io.ByteArrayInputStream(byteArrayOf(1, 2, 3))
        val expectedReport =
            ImportReport(successCount = 2, skippedCount = 0, failedCount = 0, failedNotes = emptyList())
        every { contentResolver.openInputStream(importUri) } returns inputStream
        coEvery {
            noteImporter.importFromZip(any<java.io.InputStream>(), any<java.io.OutputStream>())
        } coAnswers
            {
                val out = secondArg<java.io.OutputStream>()
                out.write(byteArrayOf(0x01, 0x02, 0x03, 0x04))
                expectedReport
            }
        viewModel.importFromZip(importUri)
        assertTrue(viewModel.uiState.value is DataUiState.Done, "import 成功 MUST → Done")

        // act:saveImportReport 模拟 SAF URI 失效抛 FileNotFoundException
        val saveUri: Uri = mockk(relaxed = true)
        every { contentResolver.openOutputStream(saveUri) } throws java.io.FileNotFoundException("SAF URI 失效")
        viewModel.saveImportReport(saveUri)

        // assert:uiState 仍 Done 不切 Failed + lastSaveReportResult = Failed
        val finalUiState = viewModel.uiState.value
        assertTrue(
            finalUiState is DataUiState.Done,
            "SAF 写失败 MUST 不破坏 Done 态， 实际 = $finalUiState"
        )
        finalUiState as DataUiState.Done
        assertTrue(finalUiState.isImport, "Done MUST 仍 isImport = true")
        val saveResult = viewModel.lastSaveReportResult.value
        assertTrue(
            saveResult is SaveReportResult.Failed,
            "SAF 写失败 MUST → SaveReportResult.Failed, 实际 = $saveResult"
        )
        saveResult as SaveReportResult.Failed
        assertTrue(
            saveResult.reason.contains("SAF URI 失效"),
            "Failed.reason MUST 含原异常 message, 实际 = ${saveResult.reason}"
        )
    }
}
