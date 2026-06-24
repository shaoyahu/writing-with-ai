package com.yy.writingwithai.feature.aiwriting.streaming

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yy.writingwithai.BuildConfig
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
import com.yy.writingwithai.core.prefs.UserPrefsStore
import com.yy.writingwithai.core.widget.QuickNoteWidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
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
    private val providerPrefsStore: ProviderPrefsStore,
    private val userPrefsStore: UserPrefsStore
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

    /**
     * onboarding-apikey-prompt:ack 状态同步镜像(避免冷启动 race,同 `initialConsented` 模式)。
     * VM 构造期一次性 `runBlocking`,等价 `MainActivity.onCreate` 同步检查。
     */
    private val initialAckApikeyPrompt: Boolean =
        runBlocking { userPrefsStore.isApikeyPromptAcked() }

    private val _state = MutableStateFlow<AiActionUiState>(AiActionUiState.Idle)
    val state: StateFlow<AiActionUiState> = _state.asStateFlow()

    private var streamJob: Job? = null
    private var lastOp: WritingOp? = null
    private var lastSourceText: String? = null
    private var lastNoteId: String? = null
    private var lastUsage: AiStreamEvent.Usage? = null
    private var lastOriginalContent: String? = null

    fun start(op: WritingOp, sourceText: String, noteId: String) {
        streamJob?.cancel()
        lastOp = op
        lastSourceText = sourceText
        lastNoteId = noteId
        lastUsage = null
        lastOriginalContent = null
        // M4-4 consent gate:r1 H2 修后用构造期同步拿的 `initialConsented` 决策,
        // 避免 `consentFlow.value` 在 DataStore 冷启动 race 下误返 EMPTY.accepted=false。
        if (!initialConsented) {
            _state.value = AiActionUiState.Failed(op = op, error = AiError.UserConsentRequired)
            return
        }
        // onboarding-apikey-prompt · 二段门:apikey 教育未 ack → 阻断。
        // spec: openspec/changes/onboarding-apikey-prompt/specs/onboarding-consent/spec.md
        // "AI capability guard on first use"
        if (!initialAckApikeyPrompt) {
            _state.value = AiActionUiState.Failed(op = op, error = AiError.ApikeyPromptNotAcked)
            return
        }
        streamJob =
            viewModelScope.launch {
                // M5 polish · fix-m5-blockers: 同步取 providerId + 真 apikey,
                // 缺 apikey 阻断 AI 调用,真 apikey 透传 gateway。
                // H1 修:4 个 prefs read 全在 suspend 上下文(viewModelScope.launch 内) await,
                // 删原 gateway.streamWritingOp 内 runBlocking(主线程 ANR)。
                val providerId = providerPrefsStore.getSelectedProviderId()
                // fix-2026-06-24-review-r1-critical:null = 未配置 provider → 走 ProviderNotConfigured
                if (providerId == null) {
                    _state.value = AiActionUiState.Failed(op = op, error = AiError.ProviderNotConfigured)
                    return@launch
                }
                val apikey = secureApiKeyStore.get(providerId)
                val apiFormatOverride = providerPrefsStore.getApiFormat(providerId)
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
                        modelName = providerPrefsStore.getSelectedModel(providerId),
                        systemPrompt = systemPrompt,
                        apiFormatOverride = apiFormatOverride
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
                                        originalText = sourceText,
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
     * M13 修:删 `resolveProviderId()` 死代码(r1 已建议,本轮清)— `start()` 直接 inline 逻辑,
     * 没 caller,留函数只会跟着演化走偏。
     */

    fun acceptReplace() {
        // M1 修:release 包不打 noteId / op / e.message 到 logcat(隐私 + provider URL 泄露)。
        if (BuildConfig.DEBUG) android.util.Log.d("AiVM", "acceptReplace called")
        val current = _state.value as? AiActionUiState.Done ?: run {
            if (BuildConfig.DEBUG) android.util.Log.d("AiVM", "acceptReplace: not Done state")
            return
        }
        val noteId = lastNoteId ?: run {
            if (BuildConfig.DEBUG) android.util.Log.d("AiVM", "acceptReplace: lastNoteId null")
            return
        }
        val sourceText = lastSourceText ?: return
        val op = current.op
        val aiText = current.finalText
        viewModelScope.launch {
            try {
                withContext(NonCancellable) {
                    val existingFlow = noteRepository.observeNoteWithTags(noteId)
                    val existing = existingFlow.first() ?: return@withContext
                    val now = System.currentTimeMillis()
                    val originalContent = existing.note.content
                    // H6 修:`String.replace(sourceText, aiText)` 在原文不含 / 多次匹配时静默,
                    // 改用 indexOf 严格校验,缺失/多匹配 emit Failed,避免"接受了但内容没动"。
                    val idx = originalContent.indexOf(sourceText)
                    if (idx < 0) {
                        _state.value = AiActionUiState.Failed(
                            op = op,
                            error = AiError.Unknown(null, "原文已被修改,请重新生成")
                        )
                        return@withContext
                    }
                    if (originalContent.indexOf(sourceText, idx + sourceText.length) >= 0) {
                        _state.value = AiActionUiState.Failed(
                            op = op,
                            error = AiError.Unknown(null, "原文有多处匹配,请手动选择")
                        )
                        return@withContext
                    }
                    val updatedContent = originalContent.replaceRange(idx, idx + sourceText.length, aiText)
                    lastOriginalContent = originalContent
                    val updated = existing.note.copy(content = updatedContent, updatedAt = now)
                    noteRepository.upsert(updated, existing.tags)
                    noteRepository.updateAiMetadata(noteId, op.name.lowercase(), now)
                    widgetUpdater.updateAll(context)
                }
                // H7 修:删 `delay(150)` + `tryEmit` 强刷 + 误导 Log.d。
                // Room Flow 是 single source of truth,`NonCancellable { upsert }` 退栈时
                // invalidation 已传播,detail VM 主路径 Flow 自然收到更新,无需 push 强刷。
                _state.value = AiActionUiState.Replaced(op = op)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) android.util.Log.e("AiVM", "acceptReplace ${op.name}", e)
                _state.value = AiActionUiState.Failed(
                    op = op,
                    error = AiError.Unknown(null, e.message ?: "unknown")
                )
            }
        }
    }

    /** 撤回 AI 替换,恢复原始内容。 */
    fun undo() {
        val noteId = lastNoteId ?: return
        val original = lastOriginalContent ?: return
        viewModelScope.launch {
            try {
                withContext(NonCancellable) {
                    val existingFlow = noteRepository.observeNoteWithTags(noteId)
                    val existing = existingFlow.first() ?: return@withContext
                    val now = System.currentTimeMillis()
                    val reverted = existing.note.copy(content = original, updatedAt = now)
                    noteRepository.upsert(reverted, existing.tags)
                    widgetUpdater.updateAll(context)
                }
                lastOriginalContent = null
                _state.value = AiActionUiState.Idle
            } catch (e: Exception) {
                // M14 修:跟 acceptReplace 一致,DB 写异常 → Failed 而不是 stuck Replaced 态无法撤回。
                if (e is kotlinx.coroutines.CancellationException) throw e
                val currentOp = (_state.value as? AiActionUiState.Replaced)?.op ?: WritingOp.POLISH
                if (BuildConfig.DEBUG) android.util.Log.e("AiVM", "undo failed", e)
                _state.value = AiActionUiState.Failed(
                    op = currentOp,
                    error = AiError.Unknown(null, "撤回失败:${e.message}")
                )
            }
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
        lastOriginalContent = null
        _state.value = AiActionUiState.Idle
    }

    fun regenerate() {
        val current = _state.value as? AiActionUiState.Done ?: return
        val op = lastOp ?: current.op
        val sourceText = lastSourceText ?: return
        val noteId = lastNoteId ?: return
        start(op = op, sourceText = sourceText, noteId = noteId)
    }

    /** Failed 态重试:复用上次 op / sourceText / noteId 重新 start()。 */
    fun retry() {
        val current = _state.value as? AiActionUiState.Failed ?: return
        val op = lastOp ?: current.op
        val sourceText = lastSourceText ?: return
        val noteId = lastNoteId ?: return
        start(op = op, sourceText = sourceText, noteId = noteId)
    }
}
