package com.yy.writingwithai.feature.settings.data

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yy.writingwithai.core.common.di.IoDispatcher
import com.yy.writingwithai.core.data.export.ImportReport
import com.yy.writingwithai.core.data.export.NoteExporter
import com.yy.writingwithai.core.data.export.NoteImporter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface DataUiState {
    data object Idle : DataUiState

    data object Exporting : DataUiState

    data object Importing : DataUiState

    /**
     * @param isImport true = 完成是导入结果(显示 `import_report_summary` 三项计数),
     *                false = 完成是导出结果(显示 `settings_data_done` 仅成功条数)。
     */
    data class Done(
        val report: ImportReport,
        val isImport: Boolean
    ) : DataUiState

    data class Failed(val error: String) : DataUiState
}

/**
 * last-import-report-save · "保存导入报告"按钮反馈状态。
 *
 * 独立于 [DataUiState](避免覆盖 Done 态丢上下文)。`Idle` 默认;
 * `Success` 由屏监听 Snackbar 显示 + reset;`Failed` 同样 reset 前显示错误。
 */
sealed interface SaveReportResult {
    data object Idle : SaveReportResult

    data object Success : SaveReportResult

    data class Failed(val reason: String) : SaveReportResult
}

@HiltViewModel
class SettingsDataViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val noteExporter: NoteExporter,
    private val noteImporter: NoteImporter,
    private val noteRepository: com.yy.writingwithai.core.data.repo.NoteRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {
    private val _uiState = MutableStateFlow<DataUiState>(DataUiState.Idle)
    val uiState: StateFlow<DataUiState> = _uiState.asStateFlow()

    /**
     * last-import-report-save · 保存导入报告按钮反馈事件流。屏上 Snackbar
     * LaunchedEffect 监听,Success/Failed 显示 + 调 [resetSaveReportResult] 回 Idle。
     */
    private val _lastSaveReportResult = MutableStateFlow<SaveReportResult>(SaveReportResult.Idle)
    val lastSaveReportResult: StateFlow<SaveReportResult> = _lastSaveReportResult.asStateFlow()

    /**
     * H2 修:r1 review 发现空数据仍允许导出 → Done(0) 困惑。
     * 暴露当前 Room 笔记总数给 Screen,导出按钮在 count == 0 时置灰 + 显示 `settings_data_no_data` 文案。
     */
    val notesCount: StateFlow<Int> =
        noteRepository
            .observeNotesWithTags(null, null)
            .map { it.size }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /**
     * SAF 触发回调:导出全部数据为 JSON zip。
     *
     * M4 修:r1 review 发现无入口 guard,Idle 态重复触发会并发写 zip。入口先 if non-Idle 直接 return。
     * 出口:Exporting → Done(successCount = exportedNotes.size, isImport = false) / Failed。
     */
    fun exportToJsonZip(uri: Uri) {
        if (_uiState.value !is DataUiState.Idle) return
        viewModelScope.launch {
            _uiState.value = DataUiState.Exporting
            try {
                val exportedCount =
                    withContext(ioDispatcher) {
                        context.contentResolver.openOutputStream(uri)?.use { stream ->
                            noteExporter.exportToJsonZip(stream)
                        } ?: 0
                    }
                _uiState.value =
                    DataUiState.Done(
                        ImportReport(successCount = exportedCount),
                        isImport = false
                    )
            } catch (e: Exception) {
                _uiState.value = DataUiState.Failed(e.message ?: "未知错误")
            }
        }
    }

    /**
     * SAF 触发回调:导入 zip 到 Room。
     *
     * M4 修:同 exportToJsonZip,入口 guard。
     * 出口:Importing → Done(report, isImport = true) / Failed。
     * - 缓存 input bytes(SAF InputStream 不可 seek,importer 需要 in + out 双 stream)
     * - 调 [NoteImporter.importFromZip] 走完整闭循环(含 `import_report.md` 写回 zip 副本 + ai_history 同步)
     * - 输出 bytes 暂存到 [lastImportReportZipBytes],M5 polish 暴露"保存报告"按钮
     */
    fun importFromZip(uri: Uri) {
        if (_uiState.value !is DataUiState.Idle) return
        viewModelScope.launch {
            _uiState.value = DataUiState.Importing
            try {
                val report =
                    withContext(ioDispatcher) {
                        val bytes =
                            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                                ?: return@withContext ImportReport()
                        val out = ByteArrayOutputStream()
                        val r =
                            noteImporter.importFromZip(
                                ByteArrayInputStream(bytes),
                                out
                            )
                        lastImportReportZipBytes = out.toByteArray()
                        r
                    }
                _uiState.value = DataUiState.Done(report, isImport = true)
            } catch (e: Exception) {
                _uiState.value = DataUiState.Failed(e.message ?: "未知错误")
            }
        }
    }

    /** 上次导入生成的 zip bytes(含 `import_report.md`),null = 未触发过导入或已清空。 */
    var lastImportReportZipBytes: ByteArray? = null
        private set

    /**
     * last-import-report-save · 把 [lastImportReportZipBytes] 写到用户选的 SAF URI。
     *
     * 缓存为 null 时 no-op(不抛错,UI 端按钮已置灰,正常路径不会走到这里);
     * 写入走 `Dispatchers.IO`;失败 catch 后 emit [SaveReportResult.Failed],
     * **不**切 [DataUiState](避免覆盖 Done 态丢用户上下文)。
     */
    fun saveImportReport(uri: Uri) {
        val bytes = lastImportReportZipBytes ?: return
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(bytes)
                    } ?: error("openOutputStream returned null")
                }
                _lastSaveReportResult.value = SaveReportResult.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _lastSaveReportResult.value = SaveReportResult.Failed(e.message ?: "unknown")
            }
        }
    }

    /** 把 [lastSaveReportResult] 回到 [SaveReportResult.Idle],屏 LaunchedEffect 在 Snackbar 显示后调。 */
    fun resetSaveReportResult() {
        _lastSaveReportResult.value = SaveReportResult.Idle
    }
}
