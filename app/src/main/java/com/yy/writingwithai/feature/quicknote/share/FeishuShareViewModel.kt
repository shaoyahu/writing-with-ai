package com.yy.writingwithai.feature.quicknote.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yy.writingwithai.core.feishu.api.FeishuError
import com.yy.writingwithai.core.feishu.sync.FeishuRefDao
import com.yy.writingwithai.core.feishu.sync.FeishuRefStatus
import com.yy.writingwithai.core.feishu.sync.FeishuSyncService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * feishu-sync-end-to-end · 飞书分享 ViewModel 薄包装(design D3)。
 *
 * 详情页 [com.yy.writingwithai.feature.quicknote.detail.QuickNoteDetailViewModel] 已用 inline
 * state 跑通 push/pull/conflict 4 入口;本 VM 抽出**同一份** push/pull 流程,目的是:
 * - 给单测提供稳定 sealed `ShareState`(避免 Composable / hiltViewModel 在 JVM 单测里跑)
 * - 不绑 UI,不替换详情页 push/pull 路径
 *
 * 公开 API 与 detail VM 一一对应(行为兼容),但 state 用 sealed class 暴露:
 * - [ShareState.Idle] / [ShareState.Pushing] / [ShareState.Pushed] / [ShareState.Pulling]
 * - [ShareState.Pulled] / [ShareState.Conflict] / [ShareState.Error]
 */
@HiltViewModel
class FeishuShareViewModel
@Inject
constructor(
    private val syncService: FeishuSyncService,
    private val refDao: FeishuRefDao
) : ViewModel() {

    private val _state = MutableStateFlow<ShareState>(ShareState.Idle)
    val state: StateFlow<ShareState> = _state.asStateFlow()

    /**
     * push 当前 note 到飞书。成功 → [ShareState.Pushed](docUrl),失败 → [ShareState.Error]。
     */
    fun push(noteId: String) {
        _state.value = ShareState.Pushing
        viewModelScope.launch {
            try {
                val msg = syncService.push(noteId)
                val ref = syncService.getRef(noteId)
                val docUrl = ref?.docUrl ?: msg.substringAfter("同步完成: ").trim()
                _state.value = ShareState.Pushed(docUrl = docUrl, message = msg)
            } catch (e: FeishuError) {
                _state.value = ShareState.Error(message = e.message ?: "同步失败")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.value = ShareState.Error(message = e.message ?: "同步失败")
            }
        }
    }

    /**
     * 从飞书拉取文档。VM 内 regex 提取 docId → 调 [FeishuSyncService.pull]。
     * 成功 → [ShareState.Pulled](noteId, title),失败 → [ShareState.Error]
     * (注:本 VM 简化,冲突场景不弹 [ShareState.Conflict] 单独态,统一 Error;
     * 详情页 ConflictResolutionDialog 三选项逻辑在 detail VM 里跑,不在本 VM 范围)。
     */
    fun pull(docUrl: String) {
        _state.value = ShareState.Pulling
        viewModelScope.launch {
            try {
                val docId = extractDocId(docUrl)
                val titleHint = "来自飞书"
                val msg = syncService.pull(docId, docUrl, titleHint)
                val ref = refDao.getByDocId(docId)
                val noteId = ref?.noteId ?: ""
                val title = msg.substringAfter("拉取完成: ").trim().ifBlank { titleHint }
                _state.value = ShareState.Pulled(noteId = noteId, title = title, message = msg)
            } catch (e: FeishuError) {
                _state.value = ShareState.Error(message = e.message ?: "拉取失败")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.value = ShareState.Error(message = e.message ?: "拉取失败")
            }
        }
    }

    /**
     * 冲突解决 — 保留本地:重置 ref 状态到 DIRTY,触发 push。
     */
    fun resolveConflictKeepLocal(noteId: String) {
        viewModelScope.launch {
            val ref = refDao.getByNoteId(noteId) ?: run {
                _state.value = ShareState.Error(message = "ref 不存在: $noteId")
                return@launch
            }
            refDao.upsert(ref.copy(status = FeishuRefStatus.DIRTY))
            clearState()
            push(noteId)
        }
    }

    /**
     * 冲突解决 — 保留飞书:重 pull 覆盖本地。
     */
    fun resolveConflictKeepRemote(noteId: String) {
        viewModelScope.launch {
            val ref = refDao.getByNoteId(noteId) ?: run {
                _state.value = ShareState.Error(message = "ref 不存在: $noteId")
                return@launch
            }
            try {
                syncService.pull(ref.docId, ref.docUrl, "来自飞书")
                clearState()
            } catch (e: FeishuError) {
                _state.value = ShareState.Error(message = e.message ?: "解决冲突失败")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.value = ShareState.Error(message = e.message ?: "解决冲突失败")
            }
        }
    }

    fun clearState() {
        _state.value = ShareState.Idle
    }

    private fun extractDocId(url: String): String {
        // 与 QuickNoteDetailViewModel.extractDocId 同步,正则 /docx?/(id) 末段
        val regex = Regex("""/docx?/([A-Za-z0-9_-]+)""")
        return regex.find(url)?.groupValues?.get(1) ?: url
    }
}

/**
 * feishu-sync-end-to-end · 飞书分享状态机 sealed。
 */
sealed interface ShareState {
    data object Idle : ShareState
    data object Pushing : ShareState
    data class Pushed(val docUrl: String, val message: String) : ShareState
    data object Pulling : ShareState
    data class Pulled(val noteId: String, val title: String, val message: String) : ShareState
    data class Conflict(val noteId: String, val docId: String, val docUrl: String) : ShareState
    data class Error(val message: String) : ShareState
}
