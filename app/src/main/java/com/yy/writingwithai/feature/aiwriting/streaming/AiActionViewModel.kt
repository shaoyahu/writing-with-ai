package com.yy.writingwithai.feature.aiwriting.streaming

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yy.writingwithai.BuildConfig
import com.yy.writingwithai.core.ai.api.AiError
import com.yy.writingwithai.core.ai.api.AiGateway
import com.yy.writingwithai.core.ai.api.AiStreamEvent
import com.yy.writingwithai.core.ai.api.WritingOp
import com.yy.writingwithai.core.ai.api.resolveActualModel
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
 * - `acceptReplace()` — Done 态接受，落库 + 写 lastAiOp
 * - `reject()` — Done 态拒绝，不替换
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

    init {
        // review-M1:初始化期 eager 拉一次 provider list，把 id → defaultModel 缓存进
        // [defaultModelsByProvider]。start() 命中即用，避免每次 AI 调用穿透到
        // CustomProviderStore.getAll()。失败保持空 map 走 start() fallback inline。
        viewModelScope.launch {
            try {
                defaultModelsByProvider.value =
                    aiGateway.listProviders().associate { it.id to it.defaultModel }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 保持空 cache;start() 会在 cache miss 时 fallback inline 再拉一次。
            }
        }
    }

    /**
     * review r2 修:删 `runBlocking` 同步读取 consent/ack — 在 ViewModel 构造函数中
     * runBlocking 阻塞主线程等待 DataStore，冷启动或低端设备上可能 ANR;且构造时快照
     * 在用户同一会话内完成 onboarding 后仍为 false,AI 调用被错误阻断。
     * 改为在 start() 内异步读取最新 consent 状态，同时解决 ANR + 快照过期两个问题。
     * 首次 `consentFlow.first()` 在 DataStore 冷启动时约 50ms，但在 suspend 上下文
     * 内不阻塞主线程。
     */

    private val _state = MutableStateFlow<AiActionUiState>(AiActionUiState.Idle)
    val state: StateFlow<AiActionUiState> = _state.asStateFlow()

    private var streamJob: Job? = null

    // review-M1:start() 每次调 aiGateway.listProviders() 拿 defaultModel，频繁触发
    // CustomProviderStore.getAll()(潜在 Room query)。在 VM 构造时 eager 拉一次缓
    // 存到 MutableStateFlow,start() 命中缓存即用;缓存 miss(冷启动 init 块未完成
    // 极窄窗口、或用户 mid-session 加 custom provider)走 fallback inline，行为不
    // 退化但摊销单次 Remote/Local query。
    private val defaultModelsByProvider = MutableStateFlow<Map<String, String>>(emptyMap())

    // fix-2026-06-26-review-r3 M6:`streamJob?.cancel()` 之后旧协程可能还在 `_state.update`
    // 调用栈上(cancel 是异步)，新协程已置 Streaming → 旧协程在取消检查点前最后 emit
    // 一条 Delta / Failed 覆盖新状态。加一个 generation 计数器，emit 前比对，不一致就丢。
    @Volatile
    private var streamGeneration: Int = 0
    private var lastOp: WritingOp? = null
    private var lastSourceText: String? = null
    private var lastNoteId: String? = null
    private var lastUsage: AiStreamEvent.Usage? = null
    private var lastOriginalContent: String? = null

    fun start(op: WritingOp, sourceText: String, noteId: String) {
        streamJob?.cancel()
        // M6 fix:bump generation，旧协程(若仍在跑)将被 generation 比对拒绝写状态。
        val currentGeneration = streamGeneration + 1
        streamGeneration = currentGeneration
        lastOp = op
        lastSourceText = sourceText
        lastNoteId = noteId
        lastUsage = null
        lastOriginalContent = null
        // review r2 修:consent/ack gate 改为异步读取最新值(删 runBlocking 同步快照),
        // 用户在同一详情页会话内完成 onboarding 后，下次 start() 能拿到最新 consent。
        streamJob =
            viewModelScope.launch {
                val consented = consentStore.isConsented(com.yy.writingwithai.BuildConfig.CONSENT_VERSION)
                if (!consented) {
                    if (streamGeneration == currentGeneration) {
                        _state.value = AiActionUiState.Failed(op = op, error = AiError.UserConsentRequired)
                    }
                    return@launch
                }
                val acked = userPrefsStore.isApikeyPromptAcked()
                if (!acked) {
                    if (streamGeneration == currentGeneration) {
                        _state.value = AiActionUiState.Failed(op = op, error = AiError.ApikeyPromptNotAcked)
                    }
                    return@launch
                }
                // 删原 gateway.streamWritingOp 内 runBlocking(主线程 ANR)。
                val providerId = providerPrefsStore.getSelectedProviderId()
                // fix-2026-06-24-review-r1-critical:null = 未配置 provider → 走 ProviderNotConfigured
                if (providerId == null) {
                    if (streamGeneration == currentGeneration) {
                        _state.value = AiActionUiState.Failed(op = op, error = AiError.ProviderNotConfigured)
                    }
                    return@launch
                }
                val apikey = secureApiKeyStore.get(providerId)
                val apiFormatOverride = providerPrefsStore.getApiFormat(providerId)
                if (providerId != PROVIDER_ID_FAKE && apikey == null) {
                    if (streamGeneration == currentGeneration) {
                        _state.value = AiActionUiState.Failed(op = op, error = AiError.ProviderNotConfigured)
                    }
                    return@launch
                }
                // fix-2026-06-28-ai-model-selection-actually-used:start() 内显式算
                // actualModel(走 resolveActualModel)，与 `ModelManagementScreen` 卡片
                // 算法一致;透传 modelName 给 gateway,UI Streaming state 同步携带，
                // 让用户在 AI 跑的时候看到"正在用 X"。
                // review-M1:defaultModel 优先走 [defaultModelsByProvider] 缓存，cache miss
                // 时 (冷启动 init 块未完成、或 mid-session 加 custom provider) fallback
                // inline 再拉一次 listProviders。摊销单次 Remote/Local query。
                val selectedModel = providerPrefsStore.getSelectedModel(providerId)
                val cachedDefault = defaultModelsByProvider.value[providerId]
                val defaultModel = if (!cachedDefault.isNullOrBlank()) {
                    cachedDefault
                } else {
                    aiGateway.listProviders()
                        .firstOrNull { it.id == providerId }
                        ?.defaultModel
                        .orEmpty()
                }
                val actualModel = resolveActualModel(selectedModel, defaultModel)
                if (streamGeneration == currentGeneration) {
                    _state.value = AiActionUiState.Streaming(op = op, actualModel = actualModel)
                }
                val systemPrompt =
                    promptTemplateStore.getForOp(op) ?: DefaultPrompts.forOp(op)
                val builder = StringBuilder()
                aiGateway
                    .streamWritingOp(
                        op = op,
                        sourceText = sourceText,
                        providerId = providerId,
                        apikey = apikey ?: "",
                        modelName = actualModel,
                        systemPrompt = systemPrompt,
                        apiFormatOverride = apiFormatOverride
                    ).collect { event ->
                        // M6 fix:emit 前比对 generation;旧协程 race emit 直接丢弃。
                        if (streamGeneration != currentGeneration) {
                            return@collect
                        }
                        when (event) {
                            is AiStreamEvent.Started -> Unit
                            is AiStreamEvent.Delta -> {
                                // H21 fix:不再 `builder.toString()` 整段 emit，改为单次
                                // delta chunk + 累加长度;UI 自行拼接，O(n) 内存。
                                builder.append(event.text)
                                _state.update {
                                    AiActionUiState.Streaming(
                                        op = op,
                                        delta = event.text,
                                        accumulatedLength = builder.length,
                                        // fix-2026-06-28-ai-model-selection-actually-used:
                                        // Delta 阶段沿用 start() 计算的 actualModel,
                                        // 避免 UI 闪一次空值。
                                        actualModel = actualModel
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
     * M13 修:删 `resolveProviderId()` 死代码(r1 已建议，本轮清)— `start()` 直接 inline 逻辑，
     * 没 caller，留函数只会跟着演化走偏。
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
            // fix-2026-06-26-review-r3 H18:原实现 return@withContext 只跳出 NonCancellable,
            // 外层仍执行 `_state.value = Replaced` 覆盖内层已写入的 Failed。
            // 改为整个状态机决策放在 NonCancellable 内部，内层统一返回 success/failure 标志。
            val outcome: Boolean
            try {
                outcome = withContext(NonCancellable) {
                    val existingFlow = noteRepository.observeNoteWithTags(noteId)
                    val existing = existingFlow.first() ?: return@withContext false
                    val now = System.currentTimeMillis()
                    val originalContent = existing.note.content
                    // H6 修:`String.replace(sourceText, aiText)` 在原文不含 / 多次匹配时静默，
                    // 改用 indexOf 严格校验，缺失/多匹配 emit Failed，避免"接受了但内容没动"。
                    val idx = originalContent.indexOf(sourceText)
                    if (idx < 0) {
                        _state.value = AiActionUiState.Failed(
                            op = op,
                            error = AiError.Unknown(null, "原文已被修改，请重新生成")
                        )
                        return@withContext false
                    }
                    if (originalContent.indexOf(sourceText, idx + sourceText.length) >= 0) {
                        _state.value = AiActionUiState.Failed(
                            op = op,
                            error = AiError.Unknown(null, "原文有多处匹配，请手动选择")
                        )
                        return@withContext false
                    }
                    val updatedContent = originalContent.replaceRange(idx, idx + sourceText.length, aiText)
                    lastOriginalContent = originalContent
                    val updated = existing.note.copy(content = updatedContent, updatedAt = now)
                    noteRepository.upsert(updated, existing.tags)
                    noteRepository.updateAiMetadata(noteId, op.name.lowercase(), now)
                    widgetUpdater.updateAll(context)
                    true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) android.util.Log.e("AiVM", "acceptReplace ${op.name}", e)
                _state.value = AiActionUiState.Failed(
                    op = op,
                    error = AiError.Unknown(null, e.message ?: "unknown")
                )
                return@launch
            }
            // H7 修:删 `delay(150)` + `tryEmit` 强刷 + 误导 Log.d。
            // Room Flow 是 single source of truth,`NonCancellable { upsert }` 退栈时
            // invalidation 已传播，detail VM 主路径 Flow 自然收到更新，无需 push 强刷。
            // H18 fix:仅当 NonCancellable 块返回 true 才置 Replaced，避免覆盖 Failed。
            if (outcome) {
                _state.value = AiActionUiState.Replaced(op = op)
            }
        }
    }

    /** 撤回 AI 替换，恢复原始内容。 */
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
                // M14 修:跟 acceptReplace 一致，DB 写异常 → Failed 而不是 stuck Replaced 态无法撤回。
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
        // fix-2026-06-30-full-review-r1 HIGH H2:bump generation，旧协程在 cancel window
        // 期间残余的 Delta/Failed/Done 事件被 generation 检查过滤掉，不再覆盖 Idle。
        streamGeneration++
        streamJob?.cancel()
        _state.value = AiActionUiState.Idle
    }

    fun dismiss() {
        // fix-2026-06-30-full-review-r1 HIGH H2:同 cancel，旧协程残余事件不能覆盖 Idle。
        streamGeneration++
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
