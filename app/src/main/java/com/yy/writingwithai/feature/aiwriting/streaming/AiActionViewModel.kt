package com.yy.writingwithai.feature.aiwriting.streaming

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yy.writingwithai.core.ai.api.AiError
import com.yy.writingwithai.core.ai.api.AiGateway
import com.yy.writingwithai.core.ai.api.AiStreamEvent
import com.yy.writingwithai.core.ai.api.WritingOp
import com.yy.writingwithai.core.ai.prompt.DefaultPrompts
import com.yy.writingwithai.core.ai.provider.ProviderPrefsStore
import com.yy.writingwithai.core.data.repo.NoteRepository
import com.yy.writingwithai.core.prefs.ConsentStore
import com.yy.writingwithai.core.prefs.PromptTemplateStore
import com.yy.writingwithai.core.prefs.SecureApiKeyStore
import com.yy.writingwithai.core.widget.QuickNoteWidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * AI 写作操作 ViewModel(M3,M4-4 升级)。
 *
 * 持有 [AiActionUiState] 状态机;`start` 调 [AiGateway.streamWritingOp](providerId 由
 * [resolveProviderId] 决定 — M3 写死 fake,M4-4 起优先用 deepseek 真 apikey,fallback fake);
 * 接受 AI 输出走 `withContext(NonCancellable)` 事务(参考 M1 r1 M6 修的"用户点确认后 back 退出"场景)。
 *
 * M4-4 改动(ai-actions spec "AiActionViewModel gates AI calls behind user consent"):
 * - 构造注入 [ConsentStore] + [SecureApiKeyStore]
 * - `start()` 入口先读 consentFlow.value → false 直接 Failed(UserConsentRequired) return
 * - `resolveProviderId()` 优先 deepseek(已配 apikey) → fallback fake(M3 行为)
 *
 * 公开 API:
 * - `start(op, sourceText, noteId)` — 启动流(consent gate 在内)
 * - `acceptReplace()` — Done 态接受,落库 + 写 lastAiOp
 * - `reject()` — Done 态拒绝,不替换
 * - `cancel()` — Streaming 态取消
 * - `dismiss()` — 任何态关闭(等同 cancel + 状态重置)
 * - `regenerate()` — Done 态用上次 op / sourceText / noteId 重跑
 */
@HiltViewModel
class AiActionViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val aiGateway: AiGateway,
    private val noteRepository: NoteRepository,
    private val widgetUpdater: QuickNoteWidgetUpdater,
    private val consentStore: ConsentStore,
    private val secureApiKeyStore: SecureApiKeyStore,
    private val promptTemplateStore: PromptTemplateStore,
    private val providerPrefsStore: ProviderPrefsStore
) : ViewModel() {
    companion object {
        /** M3 阶段写死 fake provider;M4-4 起 [resolveProviderId] 优先 deepseek。 */
        const val PROVIDER_ID_FAKE = "fake"

        /** M4-4 默认 provider(SecureApiKeyStore.has(<id>) 决定走真 apikey 或 fake)。 */
        const val DEFAULT_PROVIDER = "deepseek"
    }

    /**
     * r1 H2 修:VM 构造同步拿权威 consent(避免 `stateIn(Eagerly, EMPTY)` 冷启动 race
     * 让 `consentFlow.value` 在 DataStore 第一份真值到达前返回 `false` → 已同意用户
     * 被错误 fail `UserConsentRequired`)。`runBlocking` 阻塞 ~50ms 在主线程,
     * VM 构造期一次性,等价于 `MainActivity.onCreate` 的同步检查。
     */
    private val initialConsented: Boolean =
        runBlocking { consentStore.isConsented(com.yy.writingwithai.BuildConfig.CONSENT_VERSION) }

    private val _state = MutableStateFlow<AiActionUiState>(AiActionUiState.Idle)
    val state: StateFlow<AiActionUiState> = _state.asStateFlow()

    private var streamJob: Job? = null
    private var lastOp: WritingOp? = null
    private var lastSourceText: String? = null
    private var lastNoteId: String? = null
    private var lastUsage: AiStreamEvent.Usage? = null

    fun start(op: WritingOp, sourceText: String, noteId: String) {
        streamJob?.cancel()
        lastOp = op
        lastSourceText = sourceText
        lastNoteId = noteId
        lastUsage = null
        // M4-4 consent gate:r1 H2 修后用构造期同步拿的 `initialConsented` 决策,
        // 避免 `consentFlow.value` 在 DataStore 冷启动 race 下误返 EMPTY.accepted=false。
        if (!initialConsented) {
            _state.value = AiActionUiState.Failed(op = op, error = AiError.UserConsentRequired)
            return
        }
        streamJob =
            viewModelScope.launch {
                // M5 polish · fix-m5-blockers: 同步取 providerId + 真 apikey,
                // 缺 apikey 阻断 AI 调用,真 apikey 透传 gateway。
                val providerId = providerPrefsStore.getSelectedProviderId()
                val apikey = secureApiKeyStore.get(providerId)
                if (providerId != PROVIDER_ID_FAKE && apikey == null) {
                    _state.value = AiActionUiState.Failed(op = op, error = AiError.ProviderNotConfigured)
                    return@launch
                }
                _state.value = AiActionUiState.Streaming(op = op)
                val systemPrompt =
                    promptTemplateStore.getForOp(op) ?: DefaultPrompts.forOp(op)
                val builder = StringBuilder()
                aiGateway
                    .streamWritingOp(
                        op = op,
                        sourceText = sourceText,
                        providerId = providerId,
                        apikey = apikey ?: "",
                        modelName = null,
                        systemPrompt = systemPrompt
                    ).collect { event ->
                        when (event) {
                            is AiStreamEvent.Started -> Unit
                            is AiStreamEvent.Delta -> {
                                builder.append(event.text)
                                _state.update {
                                    AiActionUiState.Streaming(
                                        op = op,
                                        partialText = builder.toString()
                                    )
                                }
                            }
                            is AiStreamEvent.Usage -> lastUsage = event
                            is AiStreamEvent.Done -> {
                                _state.value =
                                    AiActionUiState.Done(
                                        op = op,
                                        finalText = builder.toString(),
                                        usage = lastUsage
                                    )
                            }
                            is AiStreamEvent.Failed -> {
                                _state.value = AiActionUiState.Failed(op = op, error = event.error)
                            }
                        }
                    }
            }
    }

    /**
     * M5 polish · provider-real-integration:从 [ProviderPrefsStore] 拿用户选定
     * 的 providerId;若 apikey 未填 → emit `ProviderNotConfigured`(让 UI 引导用户
     * 去设置 → 模型管理)。FakeProvider 仅作为 `defaultProviderId=="fake"` 时的
     * 测试兜底(用户未在模型管理设置过)。
     */
    private suspend fun resolveProviderId(): String {
        val selected = providerPrefsStore.getSelectedProviderId()
        if (selected == PROVIDER_ID_FAKE) return PROVIDER_ID_FAKE
        return if (secureApiKeyStore.has(selected)) selected else PROVIDER_ID_FAKE
    }

    fun acceptReplace() {
        val current = _state.value as? AiActionUiState.Done ?: return
        val noteId = lastNoteId ?: return
        val op = current.op
        val aiText = current.finalText
        viewModelScope.launch {
            // M1 r1 M6 修同款:用户接受后立刻 back → viewModelScope 取消,但
            // "替换正文 + 写 lastAiOp" 必须落库,NonCancellable 保护。
            withContext(NonCancellable) {
                // M2 修:一次 IO 拿 Note + tags,避免 getNote + observeNoteWithTags
                // 之间的 race(用户改 tag 后被本 upsert 覆盖)。
                val existingFlow = noteRepository.observeNoteWithTags(noteId)
                val existing = existingFlow.first() ?: return@withContext
                val now = System.currentTimeMillis()
                val updated = existing.note.copy(content = aiText, updatedAt = now)
                noteRepository.upsert(updated, existing.tags)
                noteRepository.updateAiMetadata(noteId, op.name.lowercase(), now)
                // H4 修:widget 刷新也包 NonCancellable,避免 viewModelScope 取消时
                // Note 已落库但 widget 没刷新的 race。
                widgetUpdater.updateAll(context)
            }
            _state.value = AiActionUiState.Idle
        }
    }

    fun reject() {
        if (_state.value !is AiActionUiState.Done) return
        _state.value = AiActionUiState.Idle
    }

    fun cancel() {
        if (_state.value !is AiActionUiState.Streaming) return
        streamJob?.cancel()
        _state.value = AiActionUiState.Idle
    }

    fun dismiss() {
        streamJob?.cancel()
        _state.value = AiActionUiState.Idle
    }

    fun regenerate() {
        val current = _state.value as? AiActionUiState.Done ?: return
        val op = lastOp ?: current.op
        val sourceText = lastSourceText ?: return
        val noteId = lastNoteId ?: return
        start(op = op, sourceText = sourceText, noteId = noteId)
    }
}
